package com.emilio.deutschdrill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {
    private val fsrs = Fsrs()
    private val t0 = 1_760_000_000_000L
    private val min = Fsrs.MINUTE
    private val day = Fsrs.DAY

    @Test
    fun `new card good goes to second learning step`() {
        val c = fsrs.review(FsrsCard(), Rating.GOOD, t0)
        assertEquals(FsrsState.LEARNING, c.state)
        assertEquals(1, c.step)
        assertEquals(t0 + 10 * min, c.due)
        assertEquals(3.173, c.stability, 1e-9)
        assertEquals(1, c.reps)
    }

    @Test
    fun `initial difficulty follows FSRS-5 formula`() {
        val w = Fsrs.DEFAULT_PARAMETERS
        val expected = w[4] - kotlin.math.exp(w[5] * 2) + 1
        assertEquals(expected, fsrs.initialDifficulty(Rating.GOOD), 1e-9)
        assertEquals(w[4], fsrs.initialDifficulty(Rating.AGAIN), 1e-9)
    }

    @Test
    fun `again on new card restarts learning in one minute`() {
        val c = fsrs.review(FsrsCard(), Rating.AGAIN, t0)
        assertEquals(FsrsState.LEARNING, c.state)
        assertEquals(0, c.step)
        assertEquals(t0 + min, c.due)
    }

    @Test
    fun `hard on first step waits average of first two steps`() {
        val c = fsrs.review(FsrsCard(), Rating.HARD, t0)
        assertEquals(t0 + (min + 10 * min) / 2, c.due)
    }

    @Test
    fun `graduating new card is always retested the next day`() {
        val easy = fsrs.review(FsrsCard(), Rating.EASY, t0)
        assertEquals(FsrsState.REVIEW, easy.state)
        assertEquals(t0 + day, easy.due)

        var c = fsrs.review(FsrsCard(), Rating.GOOD, t0)
        c = fsrs.review(c, Rating.GOOD, t0 + 10 * min)
        assertEquals(FsrsState.REVIEW, c.state)
        assertEquals(t0 + 10 * min + day, c.due)
    }

    @Test
    fun `without the retest cap intervals follow stability`() {
        val uncapped = Fsrs(firstRetestCapDays = 36_500)
        val easy = uncapped.review(FsrsCard(), Rating.EASY, t0)
        assertEquals(uncapped.nextIntervalDays(15.69105), (easy.due - t0) / day)
        assertEquals(16, (easy.due - t0) / day)
    }

    @Test
    fun `retrievability equals desired retention after interval equal to stability`() {
        val card = FsrsCard(FsrsState.REVIEW, stability = 10.0, difficulty = 5.0, due = t0, lastReview = t0)
        assertEquals(0.9, fsrs.retrievability(card, t0 + 10 * day), 1e-9)
        assertEquals(10, fsrs.nextIntervalDays(10.0))
    }

    @Test
    fun `review ratings order intervals and difficulty`() {
        val card = FsrsCard(FsrsState.REVIEW, stability = 5.0, difficulty = 5.0, due = t0, lastReview = t0 - 5 * day, reps = 3)
        val hard = fsrs.review(card, Rating.HARD, t0)
        val good = fsrs.review(card, Rating.GOOD, t0)
        val easy = fsrs.review(card, Rating.EASY, t0)
        assertTrue(hard.due < good.due)
        assertTrue(good.due < easy.due)
        assertTrue(good.stability > card.stability)
        assertTrue(hard.difficulty > good.difficulty)
        assertTrue(good.difficulty > easy.difficulty)
    }

    @Test
    fun `lapse goes to relearning and lowers stability`() {
        val card = FsrsCard(FsrsState.REVIEW, stability = 20.0, difficulty = 5.0, due = t0, lastReview = t0 - 20 * day, reps = 5)
        val c = fsrs.review(card, Rating.AGAIN, t0)
        assertEquals(FsrsState.RELEARNING, c.state)
        assertEquals(1, c.lapses)
        assertEquals(t0 + 10 * min, c.due)
        assertTrue(c.stability < card.stability)

        val back = fsrs.review(c, Rating.GOOD, t0 + 10 * min)
        assertEquals(FsrsState.REVIEW, back.state)
        assertTrue(back.due >= t0 + 10 * min + day)
    }

    @Test
    fun `difficulty stays within bounds after many failures`() {
        var c = FsrsCard()
        var now = t0
        repeat(30) {
            c = fsrs.review(c, Rating.AGAIN, now)
            now = c.due
        }
        assertTrue(c.difficulty in 1.0..10.0)
        assertTrue(c.stability > 0)
    }

    @Test
    fun `stability grows over repeated good reviews`() {
        var c = FsrsCard()
        var now = t0
        var lastInterval = 0L
        repeat(6) {
            c = fsrs.review(c, Rating.GOOD, now)
            val interval = c.due - now
            if (c.state == FsrsState.REVIEW && it > 2) assertTrue(interval >= lastInterval)
            lastInterval = interval
            now = c.due
        }
        assertTrue((c.due - now) / day >= 0)
        assertTrue(c.stability > 3.173)
    }
}
