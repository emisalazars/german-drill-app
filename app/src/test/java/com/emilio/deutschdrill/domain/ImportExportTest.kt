package com.emilio.deutschdrill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ImportExportTest {
    @Test
    fun `parses card json with answer variants and diagnostic`() {
        val json = """
            {"source": "session-2026-10-12", "cards": [
              {"id": "a1", "type": "fill_blank", "prompt": "Ich besuche ___ Freund.", "answers": ["meinen"],
               "tags": ["akk-mask-endings", "akk-dat-verb-case"], "explanation": "besuchen + Akk",
               "diagnostic": {"question": "Kasus?", "answers": ["Akkusativ", "Akk"]}},
              {"type": "vocab", "prompt": "delay (to be late)", "answer": "sich verspäten", "tags": "vocab reflexive"}
            ]}
        """.trimIndent()
        val cards = CardJson.parse(json)
        assertEquals(2, cards.size)
        assertEquals(CardType.FILL_BLANK, cards[0].type)
        assertEquals("session-2026-10-12", cards[0].source)
        assertEquals(listOf("Akkusativ", "Akk"), cards[0].diagnostic!!.answers)
        assertEquals(listOf("sich verspäten"), cards[1].answers)
        assertEquals(listOf("vocab", "reflexive"), cards[1].tags)
        assertEquals(CardJson.parse(json)[1].id, cards[1].id)
    }

    @Test(expected = ImportException::class)
    fun `card without answer is rejected`() {
        CardJson.parse("""[{"prompt": "x"}]""")
    }

    @Test
    fun `parses anki text export with headers html and quotes`() {
        val text = "#separator:tab\n#html:true\n#tags column:3\n" +
            "das Projekt\tthe project<br>(n.)\tnoun-gender vocab\n" +
            "\"sich freuen auf\"\tto look forward to; to be happy about\tverb-preposition-pairs\n"
        val cards = AnkiCsv.parse(text)
        assertEquals(4, cards.size)
        assertEquals("das Projekt", cards[0].prompt)
        assertEquals("the project (n.)", cards[0].answers.first())
        assertTrue("to look forward to" in cards[2].answers)
        assertEquals(listOf("das Projekt"), cards[1].answers)
        assertTrue("noun-gender" in cards[0].tags)
    }

    @Test
    fun `parses plain comma csv with header row`() {
        val cards = AnkiCsv.parse("front,back,tags\nder Stau,traffic jam,noun-gender\n\"die Brücke, -n\",bridge,\n")
        assertEquals(4, cards.size)
        assertEquals("die Brücke, -n", cards[2].prompt)
    }

    @Test
    fun `anki export round trips through import`() {
        val drafts = listOf(CardDraft("x", CardType.FILL_BLANK, "Ich warte ___ den Bus.", listOf("auf"), tags = listOf("verb-preposition-pairs")))
        val csv = AnkiCsvExporter.export(drafts)
        val back = AnkiCsv.parse(csv)
        assertEquals("Ich warte ___ den Bus.", back[0].prompt)
        assertEquals("auf", back[0].answers.first())
    }

    private fun rec(card: String, day: Int, correct: Boolean, mixed: Boolean = true, hint: Boolean = false) = ReviewRecord(
        cardId = card, timestamp = T0 + day * Fsrs.DAY, rating = if (correct) 3 else 1, correct = correct,
        hintUsed = hint, timed = false, mixed = mixed, elapsedMs = 3000, userAnswer = if (correct) "den" else "dem",
        expected = "den", prompt = "Ich sehe ___ Mann.", tags = listOf("akk-mask-endings"), type = CardType.FILL_BLANK,
    )

    @Test
    fun `mastery needs three days and a mixed no-help success`() {
        val zone = ZoneOffset.UTC
        assertNull(Mastery.masteredAt(listOf(rec("c", 0, true), rec("c", 0, true), rec("c", 1, true)), zone))
        assertNull(Mastery.masteredAt((0..3).map { rec("c", it, true, mixed = false) }, zone))
        assertNotNull(Mastery.masteredAt(listOf(rec("c", 0, true, mixed = false), rec("c", 1, true, mixed = false), rec("c", 2, true)), zone))
    }

    @Test
    fun `relapse is a failure after mastery within 14 days`() {
        val zone = ZoneOffset.UTC
        val records = listOf(rec("c", 0, true), rec("c", 1, true), rec("c", 2, true), rec("c", 10, false))
        assertTrue(Mastery.relapsed(records, zone, T0 + 12 * Fsrs.DAY))
        assertFalse(Mastery.relapsed(records, zone, T0 + 30 * Fsrs.DAY))
    }

    @Test
    fun `tutor export contains splits mistakes and relapses`() {
        val records = listOf(rec("c", 0, true, mixed = false), rec("c", 1, true), rec("c", 2, true), rec("c", 3, false), rec("c", 3, true, hint = true))
        val export = TutorExporter.build(records, listOf("Ich besuche meinen Onkel."), T0 + 4 * Fsrs.DAY, ZoneOffset.UTC)
        val tag = export.tags.single()
        assertEquals(5, tag.attempts)
        assertEquals(1, tag.isolated.attempts)
        assertEquals(4, tag.mixed.attempts)
        assertEquals(4, tag.noHelp.attempts)
        assertTrue(tag.relapse)
        assertEquals(1, export.recentMistakes.size)
        assertEquals("dem", export.recentMistakes[0].yourAnswer)
        val json = TutorExporter.toJson(export)
        assertTrue(json.contains("\"format\": \"deutschdrill-tutor-export-v1\""))
        assertTrue(json.contains("relapsingTags"))
    }

    @Test
    fun `grader turns speed and hints into ratings`() {
        assertEquals(Rating.AGAIN, Grader.rate(false, false, 1000, 5000))
        assertEquals(Rating.HARD, Grader.rate(true, true, 1000, 5000))
        assertEquals(Rating.HARD, Grader.rate(true, false, 11000, 5000))
        assertEquals(Rating.EASY, Grader.rate(true, false, 2000, 5000))
        assertEquals(Rating.GOOD, Grader.rate(true, false, 4000, 5000))
    }

    companion object {
        const val T0 = 1_760_000_000_000L
    }
}

class StatsTest {
    private val zone = java.time.ZoneOffset.UTC
    private val day = Fsrs.DAY
    private val t0 = 1_760_000_000_000L - (1_760_000_000_000L % Fsrs.DAY) + 12 * 3_600_000L // noon UTC

    @Test
    fun `streak counts consecutive days with a 2-minute session`() {
        val s = listOf(
            SessionSpan(t0 - 3 * day, 180_000), // too far back after the gap
            SessionSpan(t0 - 2 * day, 60_000), // too short: breaks the streak
            SessionSpan(t0 - 1 * day, 150_000),
            SessionSpan(t0, 200_000),
        )
        val stats = Stats.compute(s, t0 + 600_000, zone)
        assertEquals(2, stats.streakDays)
        assertTrue(stats.practicedToday)
    }

    @Test
    fun `streak survives until today's session is done`() {
        val s = listOf(SessionSpan(t0 - 1 * day, 150_000))
        val stats = Stats.compute(s, t0, zone)
        assertEquals(1, stats.streakDays)
        assertFalse(stats.practicedToday)
    }

    @Test
    fun `back to back sessions form one block`() {
        val s = listOf(
            SessionSpan(t0, 4 * 60_000),
            SessionSpan(t0 + 5 * 60_000, 4 * 60_000),
            SessionSpan(t0 + 10 * 60_000, 4 * 60_000),
        )
        val stats = Stats.compute(s, t0 + 14 * 60_000, zone)
        assertEquals(12.0, stats.currentBlockMinutes, 1e-9)
        assertEquals(12.0, stats.minutesToday, 1e-9)
    }
}

class SeedDeckTest {
    private val dir = listOf("src/main/assets/seed", "app/src/main/assets/seed", "../german-drill-app/app/src/main/assets/seed")
        .map { java.io.File(it) }.first { it.isDirectory }

    @Test
    fun `seed decks parse and are well formed`() {
        val all = dir.listFiles()!!.filter { it.name.endsWith(".json") }.flatMap { CardJson.parse(it.readText()) }
        assertTrue(all.size > 200)
        assertEquals("ids must be unique", all.size, all.map { it.id }.toSet().size)
        for (c in all) {
            assertTrue("${c.id} needs tags", c.tags.isNotEmpty())
            if (c.type == CardType.FILL_BLANK) assertTrue("${c.id} needs a blank", "___" in c.prompt)
            // A card's own answer must be accepted.
            assertTrue(c.id, AnswerMatcher().check(c.answers.first(), c.answers).correct)
        }
        val known = Tags.all.map { it.id }.toSet()
        val unknown = all.flatMap { it.tags }.filter { it !in known }.toSet()
        assertTrue("unknown tags: $unknown", unknown.isEmpty())
    }
}
