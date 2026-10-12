package com.emilio.deutschdrill.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emilio.deutschdrill.domain.CardDraft
import com.emilio.deutschdrill.domain.CardType
import com.emilio.deutschdrill.domain.Diagnostic
import com.emilio.deutschdrill.domain.FsrsCard
import com.emilio.deutschdrill.domain.FsrsState
import com.emilio.deutschdrill.domain.ReviewRecord

private const val SEP = "\u001F"

@Entity(tableName = "cards", indices = [Index("due"), Index("state")])
data class CardEntity(
    @PrimaryKey val id: String,
    val type: String,
    val prompt: String,
    /** Accepted answers, separated by U+001F. */
    val answers: String,
    val hint: String?,
    val explanation: String?,
    /** Space-separated tags. */
    val tags: String,
    val source: String?,
    val instruction: String?,
    val example: String?,
    val contrast: String?,
    val diagnosticQuestion: String?,
    val diagnosticAnswers: String?,
    val createdAt: Long,
    /** Higher = introduced earlier (tag priority and weak baseline). */
    val introScore: Double,
    // FSRS state
    val state: Int = FsrsState.NEW.ordinal,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0L,
    val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val step: Int = 0,
    val introducedAt: Long? = null,
    /** Consecutive failed attempts across sessions; 2+ triggers the Fix-it flow. */
    val failStreak: Int = 0,
) {
    fun withFsrs(c: FsrsCard) = copy(
        state = c.state.ordinal, stability = c.stability, difficulty = c.difficulty, due = c.due,
        lastReview = c.lastReview, reps = c.reps, lapses = c.lapses, step = c.step,
    )

    /** Replace the content from a re-import while keeping all scheduling state. */
    fun withContent(d: CardDraft, introScore: Double) = copy(
        type = d.type.name, prompt = d.prompt, answers = d.answers.joinToString(SEP), hint = d.hint,
        explanation = d.explanation, tags = d.tags.joinToString(" "), source = d.source,
        instruction = d.instruction, example = d.example, contrast = d.contrast,
        diagnosticQuestion = d.diagnostic?.question, diagnosticAnswers = d.diagnostic?.answers?.joinToString(SEP),
        introScore = introScore,
    )

    fun withExtraAnswer(answer: String) = copy(answers = (answerList + answer.trim()).distinct().joinToString(SEP))

    fun toDraft() = CardDraft(
        id, cardType, prompt, answerList, hint, explanation, tagList, source, instruction, example, contrast, diagnostic,
    )

    companion object {
        fun from(d: CardDraft, now: Long, introScore: Double) = CardEntity(
            id = d.id, type = d.type.name, prompt = d.prompt, answers = d.answers.joinToString(SEP), hint = d.hint,
            explanation = d.explanation, tags = d.tags.joinToString(" "), source = d.source,
            instruction = d.instruction, example = d.example, contrast = d.contrast,
            diagnosticQuestion = d.diagnostic?.question, diagnosticAnswers = d.diagnostic?.answers?.joinToString(SEP),
            createdAt = now, introScore = introScore,
        )
    }
}

// Derived views of a card. Kept as extensions so Room does not treat them as columns.
val CardEntity.cardType: CardType get() = CardType.valueOf(type)
val CardEntity.answerList: List<String> get() = answers.split(SEP).filter { it.isNotEmpty() }
val CardEntity.tagList: List<String> get() = tags.split(' ').filter { it.isNotEmpty() }
val CardEntity.diagnostic: Diagnostic?
    get() = if (diagnosticQuestion != null && !diagnosticAnswers.isNullOrEmpty())
        Diagnostic(diagnosticQuestion, diagnosticAnswers.split(SEP)) else null
val CardEntity.fsrs: FsrsCard
    get() = FsrsCard(FsrsState.entries[state], stability, difficulty, due, lastReview, reps, lapses, step)

@Entity(tableName = "reviews", indices = [Index("cardId"), Index("timestamp")])
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val sessionId: Long,
    val timestamp: Long,
    val rating: Int,
    val correct: Boolean,
    val hintUsed: Boolean,
    val timed: Boolean,
    val mixed: Boolean,
    val elapsedMs: Long,
    val userAnswer: String,
    val expected: String,
    val prompt: String,
    val tags: String,
    val type: String,
) {
    fun toRecord() = ReviewRecord(
        cardId, timestamp, rating, correct, hintUsed, timed, mixed, elapsedMs, userAnswer, expected, prompt,
        tags.split(' ').filter { it.isNotEmpty() }, CardType.valueOf(type),
    )
}

@Entity(tableName = "sessions", indices = [Index("startedAt")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long = startedAt,
    val durationMs: Long = 0,
    val mode: String,
    val cards: Int = 0,
    val correct: Int = 0,
)

/** Sentences written in the Fix-it flow; exported for the tutor. */
@Entity(tableName = "own_sentences")
data class OwnSentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val text: String,
    val timestamp: Long,
)
