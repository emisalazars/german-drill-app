package com.emilio.deutschdrill.data

import androidx.room.withTransaction
import com.emilio.deutschdrill.domain.AnkiCsvExporter
import com.emilio.deutschdrill.domain.CardDraft
import com.emilio.deutschdrill.domain.DailyStats
import com.emilio.deutschdrill.domain.Fsrs
import com.emilio.deutschdrill.domain.Mastery
import com.emilio.deutschdrill.domain.Rating
import com.emilio.deutschdrill.domain.SessionSpan
import com.emilio.deutschdrill.domain.Stats
import com.emilio.deutschdrill.domain.Tags
import com.emilio.deutschdrill.domain.TutorExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

data class ImportResult(val added: Int, val updated: Int)

data class TagStat(
    val tag: String,
    val title: String,
    val priority: Int,
    val cards: Int,
    val attempts: Int,
    val accuracy: Double?,
    val mastered: Int,
    val relapse: Boolean,
)

class DrillRepository(
    private val db: DrillDatabase,
    private val settings: SettingsStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val dao = db.dao()
    val fsrs = Fsrs()

    fun dueCount(now: Long): Flow<Int> = dao.dueCount(now)
    fun newCount(): Flow<Int> = dao.newCount()
    fun cardCount(): Flow<Int> = dao.cardCount()

    fun dailyStats(now: () -> Long): Flow<DailyStats> = dao.sessionsFlow().map { sessions ->
        Stats.compute(sessions.map { SessionSpan(it.startedAt, it.durationMs) }, now(), zone())
    }

    suspend fun dailyStatsNow(now: Long): DailyStats =
        Stats.compute(dao.allSessions().map { SessionSpan(it.startedAt, it.durationMs) }, now, zone())

    /** Insert new cards; for existing ids update the content but keep the learning history. */
    suspend fun import(drafts: List<CardDraft>, now: Long): ImportResult = db.withTransaction {
        var added = 0
        var updated = 0
        for (d in drafts.distinctBy { it.id }) {
            val score = Tags.introductionScore(d.tags)
            val existing = dao.card(d.id)
            if (existing == null) {
                dao.insertCards(listOf(CardEntity.from(d, now, score)))
                added++
            } else {
                dao.updateCard(existing.withContent(d, score))
                updated++
            }
        }
        ImportResult(added, updated)
    }

    /**
     * Cards for one session. Mixed mode: due reviews interleaved with today's new cards
     * (weakest high-priority tags first); falls back to the soonest-due cards for extra practice.
     * Tag mode: every card of one tag (isolated practice). Contrast partners are pulled in.
     */
    suspend fun buildSession(tag: String?, now: Long): List<CardEntity> {
        val cards: List<CardEntity> = if (tag != null) {
            dao.cardsWithTag(tag).sortedWith(compareBy({ it.state == 0 }, { it.due })).take(60)
        } else {
            val due = dao.dueCards(now, 150)
            val introducedToday = dao.introducedSince(startOfDay(now))
            val newLimit = (settings.settings.value.newCardsPerDay - introducedToday).coerceAtLeast(0)
            val fresh = if (newLimit > 0) dao.newCards(newLimit) else emptyList()
            val merged = interleave(due, fresh).ifEmpty { dao.soonestCards(30) }
            merged
        }
        return withContrastPartners(cards)
    }

    private fun interleave(due: List<CardEntity>, fresh: List<CardEntity>): List<CardEntity> {
        val out = ArrayList<CardEntity>(due.size + fresh.size)
        val d = due.iterator()
        val f = fresh.iterator()
        while (d.hasNext() || f.hasNext()) {
            repeat(2) { if (d.hasNext()) out += d.next() }
            if (f.hasNext()) out += f.next()
        }
        return out
    }

    private suspend fun withContrastPartners(cards: List<CardEntity>): List<CardEntity> {
        if (cards.none { it.contrast != null }) return cards
        val byGroup = dao.allCards().filter { it.contrast != null }.groupBy { it.contrast!! }
        val seen = HashSet<String>()
        val out = ArrayList<CardEntity>()
        for (c in cards) {
            if (!seen.add(c.id)) continue
            out += c
            c.contrast?.let { g -> byGroup[g].orEmpty().filter { seen.add(it.id) }.forEach { out += it } }
        }
        return out
    }

    suspend fun startSession(mode: String, now: Long): Long = dao.insertSession(SessionEntity(startedAt = now, mode = mode))

    suspend fun finishSession(id: Long, now: Long, cards: Int, correct: Int) {
        val s = dao.session(id) ?: return
        dao.updateSession(s.copy(endedAt = now, durationMs = now - s.startedAt, cards = cards, correct = correct))
    }

    /** Apply an FSRS review and log the attempt. Returns the updated card and the log id. */
    suspend fun recordAttempt(
        card: CardEntity,
        rating: Rating,
        correct: Boolean,
        hintUsed: Boolean,
        timed: Boolean,
        mixed: Boolean,
        elapsedMs: Long,
        userAnswer: String,
        expected: String,
        sessionId: Long,
        now: Long,
    ): Pair<CardEntity, Long> = db.withTransaction {
        val updated = card.withFsrs(fsrs.review(card.fsrs, rating, now)).copy(
            introducedAt = card.introducedAt ?: now,
            failStreak = if (correct) 0 else card.failStreak + 1,
        )
        dao.updateCard(updated)
        val reviewId = dao.insertReview(
            ReviewEntity(
                cardId = card.id, sessionId = sessionId, timestamp = now, rating = rating.value, correct = correct,
                hintUsed = hintUsed, timed = timed, mixed = mixed, elapsedMs = elapsedMs, userAnswer = userAnswer,
                expected = expected, prompt = card.prompt, tags = card.tags, type = card.type,
            )
        )
        updated to reviewId
    }

    /** "My answer was right": accept it as a variant and redo the review from the pre-review state as Good. */
    suspend fun overrideAsCorrect(before: CardEntity, reviewId: Long, userAnswer: String, now: Long): CardEntity =
        db.withTransaction {
            val updated = before.withFsrs(fsrs.review(before.fsrs, Rating.GOOD, now))
                .withExtraAnswer(userAnswer)
                .copy(introducedAt = before.introducedAt ?: now, failStreak = 0)
            dao.updateCard(updated)
            dao.review(reviewId)?.let { dao.updateReview(it.copy(correct = true, rating = Rating.GOOD.value)) }
            updated
        }

    suspend fun saveOwnSentence(cardId: String, text: String, now: Long) {
        if (text.isNotBlank()) dao.insertSentence(OwnSentenceEntity(cardId = cardId, text = text.trim(), timestamp = now))
    }

    suspend fun tutorExportJson(now: Long): String {
        val records = dao.allReviews().map { it.toRecord() }
        val sentences = dao.allSentences().map { it.text }
        return TutorExporter.toJson(TutorExporter.build(records, sentences, now, zone()))
    }

    suspend fun ankiCsv(): String = AnkiCsvExporter.export(dao.allCards().map { it.toDraft() })

    suspend fun tagStats(now: Long): List<TagStat> {
        val cards = dao.allCards()
        val reviews = dao.allReviews().map { it.toRecord() }
        val byCard = reviews.groupBy { it.cardId }
        val z = zone()
        val mastered = byCard.filterValues { Mastery.masteredAt(it, z) != null }.keys
        val relapsed = byCard.filterValues { Mastery.relapsed(it, z, now) }.keys
        val tags = cards.flatMap { it.tagList }.distinct()
        return tags.map { tag ->
            val tagCards = cards.filter { tag in it.tagList }.map { it.id }.toSet()
            val rs = reviews.filter { it.cardId in tagCards && it.timestamp >= now - 30 * Fsrs.DAY }
            val info = Tags.info(tag)
            TagStat(
                tag = tag,
                title = info.title,
                priority = info.priority,
                cards = tagCards.size,
                attempts = rs.size,
                accuracy = if (rs.isEmpty()) null else rs.count { it.correct }.toDouble() / rs.size,
                mastered = tagCards.count { it in mastered },
                relapse = tagCards.any { it in relapsed },
            )
        }.sortedWith(compareByDescending<TagStat> { it.priority }.thenBy { it.tag })
    }

    private fun startOfDay(now: Long): Long =
        LocalDate.now(zone()).atStartOfDay(zone()).toInstant().toEpochMilli().coerceAtMost(now)
}
