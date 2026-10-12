package com.emilio.deutschdrill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {
    private val m = AnswerMatcher()

    @Test fun `exact answer is correct`() = assertTrue(m.check("einen", listOf("einen")).correct)

    @Test fun `umlaut-less input is accepted`() {
        assertTrue(m.check("wuerde", listOf("würde")).correct)
        assertTrue(m.check("Strasse", listOf("Straße")).correct)
        assertTrue(m.check("Hoehe", listOf("Höhe")).correct)
    }

    @Test fun `umlaut confusion is still wrong`() {
        // "wurde" vs "würde" is exactly the grammar being drilled.
        assertFalse(m.check("wurde", listOf("würde")).correct)
        assertFalse(m.check("mochte", listOf("möchte")).correct)
        assertFalse(m.check("schon", listOf("schön")).correct)
    }

    @Test fun `case whitespace and punctuation are ignored`() {
        assertTrue(m.check("  ich gehe nach hause,  weil ich müde bin ", listOf("Ich gehe nach Hause, weil ich müde bin.")).correct)
    }

    @Test fun `case can be made strict`() {
        assertFalse(AnswerMatcher(caseSensitive = true).check("das projekt", listOf("das Projekt")).correct)
        assertTrue(AnswerMatcher(caseSensitive = true).check("das Projekt", listOf("das Projekt")).correct)
    }

    @Test fun `wrong ending is wrong and flagged as near miss`() {
        val r = m.check("ein", listOf("einen"))
        assertFalse(r.correct)
        assertFalse(r.nearMiss)
        val r2 = m.check("diesem", listOf("diesen"))
        assertFalse(r2.correct)
        assertTrue(r2.nearMiss)
    }

    @Test fun `any accepted variant matches and closest is used as target`() {
        val accepted = listOf("Ich glaube, er kommt morgen.", "Ich glaube, dass er morgen kommt.")
        assertTrue(m.check("ich glaube dass er morgen kommt", accepted).correct)
        val wrong = m.check("Ich glaube, dass er kommt morgen.", accepted)
        assertFalse(wrong.correct)
        assertEquals("Ich glaube, dass er morgen kommt.", wrong.target)
    }

    @Test fun `empty input is wrong`() = assertFalse(m.check("   ", listOf("den")).correct)

    @Test fun `diff marks missing ending`() {
        val diff = Diff.chars("ein", "einen")
        assertEquals(listOf(DiffSegment("ein", DiffKind.SAME), DiffSegment("en", DiffKind.MISSING)), diff)
    }

    @Test fun `diff marks substituted letters`() {
        val diff = Diff.chars("wurde", "würde")
        assertEquals("w", diff.first().text)
        assertTrue(diff.any { it.kind == DiffKind.EXTRA && it.text == "u" })
        assertTrue(diff.any { it.kind == DiffKind.MISSING && it.text == "ü" })
        assertEquals("würde", diff.filter { it.kind != DiffKind.EXTRA }.joinToString("") { it.text })
        assertEquals("wurde", diff.filter { it.kind != DiffKind.MISSING }.joinToString("") { it.text })
    }

    @Test fun `levenshtein basics`() {
        assertEquals(0, AnswerMatcher.levenshtein("abc", "abc"))
        assertEquals(2, AnswerMatcher.levenshtein("ein", "einen"))
        assertEquals(1, AnswerMatcher.levenshtein("diesem", "diesen"))
    }
}
