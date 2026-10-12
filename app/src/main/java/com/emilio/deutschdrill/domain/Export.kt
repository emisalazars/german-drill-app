package com.emilio.deutschdrill.domain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** One logged attempt, with the context needed for the accuracy splits. */
data class ReviewRecord(
    val cardId: String,
    val timestamp: Long,
    val rating: Int,
    val correct: Boolean,
    val hintUsed: Boolean,
    val timed: Boolean,
    /** Session mixed several topics (false = drilling a single tag). */
    val mixed: Boolean,
    val elapsedMs: Long,
    val userAnswer: String,
    val expected: String,
    val prompt: String,
    val tags: List<String>,
    val type: CardType,
)

/** Mastery rule: correct on 3 different days AND at least once in a mixed session without a hint. */
object Mastery {
    const val DAYS_REQUIRED = 3
    const val RELAPSE_WINDOW_DAYS = 14L

    /** Time the card first met the mastery rule, or null if it never has. */
    fun masteredAt(recordsOfCard: List<ReviewRecord>, zone: ZoneId): Long? {
        val days = HashSet<LocalDate>()
        var mixedNoHelp = false
        for (r in recordsOfCard.sortedBy { it.timestamp }) {
            if (!r.correct) continue
            days += Instant.ofEpochMilli(r.timestamp).atZone(zone).toLocalDate()
            if (r.mixed && !r.hintUsed) mixedNoHelp = true
            if (days.size >= DAYS_REQUIRED && mixedNoHelp) return r.timestamp
        }
        return null
    }

    /** Mastered earlier, then failed within the last 14 days. */
    fun relapsed(recordsOfCard: List<ReviewRecord>, zone: ZoneId, now: Long): Boolean {
        val mastered = masteredAt(recordsOfCard, zone) ?: return false
        val windowStart = now - RELAPSE_WINDOW_DAYS * Fsrs.DAY
        return recordsOfCard.any { !it.correct && it.timestamp > mastered && it.timestamp >= windowStart }
    }
}

@Serializable
data class Split(val attempts: Int, val accuracy: Double?)

@Serializable
data class TagReport(
    val tag: String,
    val title: String,
    val priority: Int,
    val baseline: Double?,
    val attempts: Int,
    val accuracy: Double?,
    val accuracyLast7Days: Double?,
    val isolated: Split,
    val mixed: Split,
    val noHelp: Split,
    val timed: Split,
    /** Writing mode is not built yet; always empty for now. */
    val freeText: Split,
    val medianAnswerMs: Long?,
    val cardsSeen: Int,
    val cardsMastered: Int,
    val relapse: Boolean,
)

@Serializable
data class MistakeReport(
    val cardId: String,
    val tags: List<String>,
    val prompt: String,
    val yourAnswer: String,
    val expected: String,
    val at: String,
    val answerMs: Long,
)

@Serializable
data class CardTiming(val cardId: String, val attempts: Int, val accuracy: Double, val medianAnswerMs: Long)

@Serializable
data class TutorExport @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val format: String = "deutschdrill-tutor-export-v1",
    @EncodeDefault val howToUse: String = "Learner progress from the Deutsch Drill app. Tutor: focus the next session on " +
        "weakTags and relapses, use recentMistakes as material, and compare mixed/timed accuracy against isolated.",
    val generatedAt: String,
    val periodDays: Int,
    val reviews: Int,
    val accuracy: Double?,
    val cardsMastered: Int,
    val weakTags: List<String>,
    val relapsingTags: List<String>,
    val tags: List<TagReport>,
    val recentMistakes: List<MistakeReport>,
    val ownSentences: List<String>,
    val slowestCards: List<CardTiming>,
)

object TutorExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = false; explicitNulls = false }

    fun build(
        records: List<ReviewRecord>,
        ownSentences: List<String>,
        now: Long,
        zone: ZoneId,
        periodDays: Int = 30,
    ): TutorExport {
        val since = now - periodDays * Fsrs.DAY
        val inPeriod = records.filter { it.timestamp >= since }
        val byCard = records.groupBy { it.cardId }
        val masteredCards = byCard.filterValues { Mastery.masteredAt(it, zone) != null }.keys
        val relapsedCards = byCard.filterValues { Mastery.relapsed(it, zone, now) }.keys

        val tagIds = inPeriod.flatMap { it.tags }.distinct()
        val tagReports = tagIds.map { tag ->
            val rs = inPeriod.filter { tag in it.tags }
            val cardIds = records.filter { tag in it.tags }.map { it.cardId }.toSet()
            val info = Tags.info(tag)
            TagReport(
                tag = tag,
                title = info.title,
                priority = info.priority,
                baseline = info.baseline,
                attempts = rs.size,
                accuracy = acc(rs),
                accuracyLast7Days = acc(rs.filter { it.timestamp >= now - 7 * Fsrs.DAY }),
                isolated = split(rs.filter { !it.mixed }),
                mixed = split(rs.filter { it.mixed }),
                noHelp = split(rs.filter { !it.hintUsed }),
                timed = split(rs.filter { it.timed }),
                freeText = Split(0, null),
                medianAnswerMs = median(rs.filter { it.correct }.map { it.elapsedMs }),
                cardsSeen = cardIds.size,
                cardsMastered = cardIds.count { it in masteredCards },
                relapse = cardIds.any { it in relapsedCards },
            )
        }.sortedWith(compareBy<TagReport> { it.accuracy ?: 1.0 }.thenByDescending { it.priority })

        val mistakes = inPeriod.filter { !it.correct }.sortedByDescending { it.timestamp }
            .distinctBy { it.cardId to it.userAnswer.trim().lowercase() }
            .take(40)
            .map {
                MistakeReport(it.cardId, it.tags, it.prompt, it.userAnswer, it.expected,
                    Instant.ofEpochMilli(it.timestamp).toString(), it.elapsedMs)
            }

        val slowest = inPeriod.groupBy { it.cardId }.mapNotNull { (id, rs) ->
            val med = median(rs.filter { it.correct }.map { it.elapsedMs }) ?: return@mapNotNull null
            CardTiming(id, rs.size, acc(rs) ?: 0.0, med)
        }.sortedByDescending { it.medianAnswerMs }.take(15)

        return TutorExport(
            generatedAt = Instant.ofEpochMilli(now).toString(),
            periodDays = periodDays,
            reviews = inPeriod.size,
            accuracy = acc(inPeriod),
            cardsMastered = masteredCards.size,
            weakTags = tagReports.filter { it.attempts >= 3 && (it.accuracy ?: 1.0) < 0.8 }.map { it.tag },
            relapsingTags = tagReports.filter { it.relapse }.map { it.tag },
            tags = tagReports,
            recentMistakes = mistakes,
            ownSentences = ownSentences.takeLast(30),
            slowestCards = slowest,
        )
    }

    fun toJson(export: TutorExport): String = json.encodeToString(TutorExport.serializer(), export)

    private fun acc(rs: List<ReviewRecord>): Double? =
        if (rs.isEmpty()) null else ((rs.count { it.correct }.toDouble() / rs.size) * 100).roundToInt() / 100.0

    private fun split(rs: List<ReviewRecord>) = Split(rs.size, acc(rs))

    private fun median(values: List<Long>): Long? =
        if (values.isEmpty()) null else values.sorted()[values.size / 2]
}

/** Anki import format: tab-separated front, back, tags with Anki header lines. */
object AnkiCsvExporter {
    fun export(cards: List<CardDraft>): String = buildString {
        append("#separator:tab\n#html:false\n#tags column:3\n")
        for (c in cards) {
            val front = listOfNotNull(c.instruction, c.prompt).joinToString(" — ")
            val back = c.answers.joinToString(" / ") + (c.explanation?.let { " — $it" } ?: "")
            append(clean(front)).append('\t').append(clean(back)).append('\t')
                .append(c.tags.joinToString(" ") { it.replace(' ', '_') }).append('\n')
        }
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
