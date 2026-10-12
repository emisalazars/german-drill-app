package com.emilio.deutschdrill.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * FSRS-5 scheduler, ported from the open-source py-fsrs (open-spaced-repetition, MIT).
 * Same state machine: New -> Learning (steps) -> Review <-> Relearning (steps).
 * Fuzz is off so scheduling stays deterministic and testable.
 */
class Fsrs(
    private val w: DoubleArray = DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    private val learningStepsMs: List<Long> = listOf(1 * MINUTE, 10 * MINUTE),
    private val relearningStepsMs: List<Long> = listOf(10 * MINUTE),
    private val maximumIntervalDays: Int = 36_500,
    /**
     * When a new card first graduates, its first review is capped to this many days, so every
     * new pattern gets an unannounced retest the next day (things that look solved at the end
     * of a session often relapse overnight).
     */
    private val firstRetestCapDays: Long = 1,
) {
    init {
        require(w.size == 19) { "FSRS-5 needs 19 parameters" }
    }

    fun review(card: FsrsCard, rating: Rating, now: Long): FsrsCard {
        val elapsedDays = card.lastReview?.let { (now - it).toDouble() / DAY }
        var stability = card.stability
        var difficulty = card.difficulty
        var state = card.state
        var step = card.step
        var lapses = card.lapses
        val intervalMs: Long

        // Memory state update (identical for every state once the card has been seen).
        if (card.state == FsrsState.NEW || card.stability <= 0.0) {
            stability = initialStability(rating)
            difficulty = initialDifficulty(rating)
        } else if (elapsedDays != null && elapsedDays < 1.0) {
            stability = shortTermStability(stability, rating)
            difficulty = nextDifficulty(difficulty, rating)
        } else {
            val r = retrievability(card, now)
            stability = nextStability(difficulty, stability, r, rating)
            difficulty = nextDifficulty(difficulty, rating)
        }

        when (card.state) {
            FsrsState.NEW, FsrsState.LEARNING, FsrsState.RELEARNING -> {
                val steps = if (card.state == FsrsState.RELEARNING) relearningStepsMs else learningStepsMs
                if (card.state == FsrsState.NEW) step = 0
                val graduatingDays = if (card.state == FsrsState.RELEARNING) nextIntervalDays(stability)
                else min(nextIntervalDays(stability), firstRetestCapDays)
                if (steps.isEmpty() || (step >= steps.size && rating != Rating.AGAIN)) {
                    state = FsrsState.REVIEW
                    step = 0
                    intervalMs = graduatingDays * DAY
                } else {
                    state = if (card.state == FsrsState.RELEARNING) FsrsState.RELEARNING else FsrsState.LEARNING
                    when (rating) {
                        Rating.AGAIN -> {
                            step = 0
                            intervalMs = steps[0]
                        }
                        Rating.HARD -> intervalMs = when {
                            step == 0 && steps.size == 1 -> (steps[0] * 1.5).toLong()
                            step == 0 -> (steps[0] + steps[1]) / 2
                            else -> steps[step]
                        }
                        Rating.GOOD -> if (step + 1 >= steps.size) {
                            state = FsrsState.REVIEW
                            step = 0
                            intervalMs = graduatingDays * DAY
                        } else {
                            step += 1
                            intervalMs = steps[step]
                        }
                        Rating.EASY -> {
                            state = FsrsState.REVIEW
                            step = 0
                            intervalMs = graduatingDays * DAY
                        }
                    }
                }
            }
            FsrsState.REVIEW -> {
                if (rating == Rating.AGAIN) {
                    lapses += 1
                    if (relearningStepsMs.isEmpty()) {
                        intervalMs = nextIntervalDays(stability) * DAY
                    } else {
                        state = FsrsState.RELEARNING
                        step = 0
                        intervalMs = relearningStepsMs[0]
                    }
                } else {
                    intervalMs = nextIntervalDays(stability) * DAY
                }
            }
        }

        return card.copy(
            state = state,
            stability = stability,
            difficulty = difficulty,
            due = now + intervalMs,
            lastReview = now,
            reps = card.reps + 1,
            lapses = lapses,
            step = step,
        )
    }

    /** Probability of recall right now, in [0, 1]. New cards return 0. */
    fun retrievability(card: FsrsCard, now: Long): Double {
        val last = card.lastReview ?: return 0.0
        if (card.stability <= 0.0) return 0.0
        val elapsedDays = max(0.0, (now - last).toDouble() / DAY)
        return (1 + FACTOR * elapsedDays / card.stability).pow(DECAY)
    }

    fun nextIntervalDays(stability: Double): Long {
        val raw = stability / FACTOR * (desiredRetention.pow(1 / DECAY) - 1)
        return raw.roundToLong().coerceIn(1L, maximumIntervalDays.toLong())
    }

    internal fun initialStability(rating: Rating): Double = max(w[rating.value - 1], MIN_STABILITY)

    internal fun initialDifficulty(rating: Rating): Double =
        clampDifficulty(w[4] - exp(w[5] * (rating.value - 1)) + 1)

    private fun nextDifficulty(d: Double, rating: Rating): Double {
        val delta = -(w[6] * (rating.value - 3))
        val damped = d + (10.0 - d) * delta / 9.0
        val reverted = w[7] * initialDifficulty(Rating.EASY) + (1 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    private fun shortTermStability(s: Double, rating: Rating): Double =
        max(s * exp(w[17] * (rating.value - 3 + w[18])), MIN_STABILITY)

    private fun nextStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val next = if (rating == Rating.AGAIN) {
            val longTerm = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])
            val shortTerm = s / exp(w[17] * w[18])
            min(longTerm, shortTerm)
        } else {
            val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
            val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
            s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus)
        }
        return max(next, MIN_STABILITY)
    }

    private fun clampDifficulty(d: Double) = d.coerceIn(1.0, 10.0)

    companion object {
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
        const val DECAY = -0.5
        val FACTOR = 0.9.pow(1 / DECAY) - 1 // = 19/81
        private const val MIN_STABILITY = 0.01

        val DEFAULT_PARAMETERS = doubleArrayOf(
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
            1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
        )
    }
}
