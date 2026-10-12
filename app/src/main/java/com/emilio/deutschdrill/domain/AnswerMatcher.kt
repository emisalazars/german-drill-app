package com.emilio.deutschdrill.domain

/**
 * Checks a typed or spoken answer against the accepted answers.
 *
 * Tolerant of: missing umlauts (ae/oe/ue, ss for ß), surrounding and repeated
 * whitespace, punctuation, typographic quotes, and (unless [caseSensitive]) case.
 * Not tolerant of anything that is grammar: a wrong ending is a wrong answer.
 */
class AnswerMatcher(private val caseSensitive: Boolean = false) {

    data class Result(
        val correct: Boolean,
        /** The accepted answer closest to what was typed (shown as "correct answer"). */
        val target: String,
        /** Character diff from the typed answer to [target]. */
        val diff: List<DiffSegment>,
        /** Wrong, but only one character away after normalization. */
        val nearMiss: Boolean,
    )

    fun check(input: String, accepted: List<String>): Result {
        require(accepted.isNotEmpty()) { "A card needs at least one accepted answer" }
        val normalizedInput = normalize(input)
        val exact = accepted.firstOrNull { normalize(it) == normalizedInput }
        if (exact != null && normalizedInput.isNotEmpty()) {
            return Result(true, exact, Diff.chars(input.trim(), exact), nearMiss = false)
        }
        // Closest = most similar set of words (so word-order mistakes diff against the
        // variant that uses the same words), then fewest character edits.
        val closest = accepted.minWith(
            compareBy<String> { wordBagDistance(normalize(it), normalizedInput) }
                .thenBy { levenshtein(normalize(it), normalizedInput) }
        )
        val distance = levenshtein(normalize(closest), normalizedInput)
        return Result(
            correct = false,
            target = closest,
            diff = Diff.chars(input.trim(), closest),
            nearMiss = normalizedInput.isNotEmpty() && distance == 1,
        )
    }

    fun normalize(raw: String): String {
        var s = raw.trim()
        s = s.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"').replace('„', '"')
        if (!caseSensitive) s = s.lowercase()
        s = foldUmlauts(s)
        s = s.replace(PUNCTUATION, " ")
        s = s.replace(WHITESPACE, " ").trim()
        return s
    }

    companion object {
        private val PUNCTUATION = Regex("[.,;:!?\"()\\[\\]–—-]")
        private val WHITESPACE = Regex("\\s+")

        fun foldUmlauts(s: String): String = buildString(s.length + 4) {
            for (c in s) when (c) {
                'ä' -> append("ae"); 'ö' -> append("oe"); 'ü' -> append("ue")
                'Ä' -> append("Ae"); 'Ö' -> append("Oe"); 'Ü' -> append("Ue")
                'ß', 'ẞ' -> append("ss")
                else -> append(c)
            }
        }

        fun wordBagDistance(a: String, b: String): Int {
            val counts = HashMap<String, Int>()
            for (w in a.split(' ')) counts[w] = (counts[w] ?: 0) + 1
            for (w in b.split(' ')) counts[w] = (counts[w] ?: 0) - 1
            return counts.values.sumOf { kotlin.math.abs(it) }
        }

        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}

enum class DiffKind {
    /** Same in both. */
    SAME,
    /** In the correct answer but missing from yours. */
    MISSING,
    /** In your answer but not in the correct one. */
    EXTRA,
}

data class DiffSegment(val text: String, val kind: DiffKind)

object Diff {
    /** Case-insensitive character LCS diff, merged into runs. Inputs are short sentences. */
    fun chars(yours: String, correct: String): List<DiffSegment> {
        val a = yours
        val b = correct
        val n = a.length
        val m = b.length
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a[i].equals(b[j], ignoreCase = true)) lcs[i + 1][j + 1] + 1
            else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = ArrayList<DiffSegment>()
        fun push(c: Char, kind: DiffKind) {
            val last = out.lastOrNull()
            if (last != null && last.kind == kind) out[out.size - 1] = last.copy(text = last.text + c)
            else out.add(DiffSegment(c.toString(), kind))
        }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i].equals(b[j], ignoreCase = true) -> { push(b[j], DiffKind.SAME); i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { push(a[i], DiffKind.EXTRA); i++ }
                else -> { push(b[j], DiffKind.MISSING); j++ }
            }
        }
        while (i < n) push(a[i++], DiffKind.EXTRA)
        while (j < m) push(b[j++], DiffKind.MISSING)
        return out
    }
}
