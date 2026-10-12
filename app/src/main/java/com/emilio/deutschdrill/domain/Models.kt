package com.emilio.deutschdrill.domain

/** The five production-based drill types. Never multiple choice. */
enum class CardType(val label: String) {
    FILL_BLANK("Fill in the blank"),
    TRANSFORM("Transform"),
    ERROR_CORRECTION("Fix the mistake"),
    VOCAB("Vocabulary"),
    SPEAKING("Speaking");

    companion object {
        fun parse(raw: String?): CardType = when (raw?.trim()?.lowercase()?.replace('-', '_')) {
            "fill", "fill_blank", "fill_in", "fill_in_the_blank", "cloze", "blank" -> FILL_BLANK
            "transform", "transformation", "sentence_transformation" -> TRANSFORM
            "error", "error_correction", "fix", "correction" -> ERROR_CORRECTION
            "speak", "speaking", "speech" -> SPEAKING
            else -> VOCAB
        }
    }
}

enum class Rating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    companion object {
        fun of(value: Int): Rating = entries.first { it.value == value }
    }
}

enum class FsrsState { NEW, LEARNING, REVIEW, RELEARNING }

/** FSRS memory state of one card. Times are epoch milliseconds. */
data class FsrsCard(
    val state: FsrsState = FsrsState.NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0L,
    val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val step: Int = 0,
)

/** A card as it comes from an import file, before it has any scheduling state. */
data class CardDraft(
    val id: String,
    val type: CardType,
    val prompt: String,
    val answers: List<String>,
    val hint: String? = null,
    val explanation: String? = null,
    val tags: List<String> = emptyList(),
    val source: String? = null,
    val instruction: String? = null,
    val example: String? = null,
    /** Cards sharing a contrast group (minimal pairs) are served back-to-back. */
    val contrast: String? = null,
    /** Optional first step of a two-step card: answer the diagnostic question, then the form. */
    val diagnostic: Diagnostic? = null,
)

/** "Which case?" style question asked before the form itself (two-step cards). */
data class Diagnostic(val question: String, val answers: List<String>)
