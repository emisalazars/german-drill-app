package com.emilio.deutschdrill.domain

/**
 * Turns a production attempt into an FSRS rating, so typed drills need no self-grading.
 * Speed matters because the goal is automaticity under time pressure.
 */
object Grader {
    /** Rough time a fluent answer should take for this card. */
    fun expectedMs(type: CardType, answer: String): Long {
        val base = when (type) {
            CardType.FILL_BLANK -> 3_000L
            CardType.VOCAB -> 4_000L
            CardType.ERROR_CORRECTION -> 7_000L
            CardType.TRANSFORM -> 8_000L
            CardType.SPEAKING -> 6_000L
        }
        val perChar = if (type == CardType.FILL_BLANK || type == CardType.VOCAB) 150L else 120L
        return base + perChar * answer.length
    }

    fun rate(correct: Boolean, usedHint: Boolean, elapsedMs: Long, expectedMs: Long): Rating = when {
        !correct -> Rating.AGAIN
        usedHint || elapsedMs > expectedMs * 2 -> Rating.HARD
        elapsedMs < expectedMs / 2 -> Rating.EASY
        else -> Rating.GOOD
    }
}
