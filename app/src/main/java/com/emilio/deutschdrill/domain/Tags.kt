package com.emilio.deutschdrill.domain

/**
 * Known error patterns. [priority] 10 = most important; [baseline] is the isolated accuracy
 * measured in tutoring sessions (null = unknown). Weak, high-priority tags are introduced first.
 * Imported cards may use any other tag too; unknown tags get priority 5.
 */
data class TagInfo(val id: String, val title: String, val priority: Int, val baseline: Double? = null)

object Tags {
    val all: List<TagInfo> = listOf(
        TagInfo("akk-dat-verb-case", "Verb case: wen/was (Akk) vs. wem (Dat)", 10, 0.60),
        TagInfo("akk-mask-endings", "Masculine accusative: einen/keinen/diesen/den", 10, 0.55),
        TagInfo("weil-subject-position", "weil/dass: subject right after the conjunction", 9, 0.65),
        TagInfo("verb-final", "Verb-final in long subordinate clauses", 9),
        TagInfo("v2-main-clause", "V2: exactly one element before the verb", 9),
        TagInfo("wuerde-vs-wurde", "würde (Konj. II) vs. wurde (Präteritum)", 9),
        TagInfo("adjective-endings", "Adjective endings", 8, 0.60),
        TagInfo("nominalized-participles", "Nominalized participles (die Reisenden)", 8),
        TagInfo("partizip-ii", "Partizip II forms", 8, 0.75),
        TagInfo("perfekt-aux", "Perfekt: sein vs. haben; hätte/wäre + Partizip", 8),
        TagInfo("passive", "Passive: wird/wurde, von + Dativ", 8),
        TagInfo("konjunktiv-1", "Konjunktiv I", 8, 0.80),
        TagInfo("dative-plural", "Dative plural: den + -n", 7),
        TagInfo("genitive", "Genitive and genitive prepositions", 7, 0.90),
        TagInfo("two-way-prepositions", "Two-way prepositions: wohin (Akk) / wo (Dat)", 7),
        TagInfo("relative-clauses", "Relative clauses", 7),
        TagInfo("verb-preposition-pairs", "Fixed verb + preposition pairs", 7),
        TagInfo("zu-infinitive", "zu-infinitive and um ... zu", 7),
        TagInfo("als-wenn-wie", "als / wenn / wie", 6, 1.00),
        TagInfo("superfluous-es", "Superfluous es", 6),
        TagInfo("je-desto", "je ... desto", 6),
        TagInfo("modal-praeteritum", "Modal verbs in Präteritum; mochte vs. möchte", 6, 0.85),
        TagInfo("reflexive", "Reflexive verbs", 6),
        TagInfo("nominalization-from-adjective", "Nouns from adjectives (lang → Länge)", 5),
        TagInfo("noun-gender", "Noun gender", 7),
        TagInfo("spelling", "Spelling: umlauts, ß, capitals, compounds", 6),
        TagInfo("vocab", "Vocabulary and collocations", 5),
    )

    private val byId = all.associateBy { it.id }

    fun info(id: String): TagInfo = byId[id] ?: TagInfo(id, id, 5)

    /** Higher = introduce earlier. Weak baselines push a tag up. */
    fun introductionScore(tags: List<String>): Double =
        tags.maxOfOrNull { tag ->
            val info = info(tag)
            info.priority + (1.0 - (info.baseline ?: 0.7)) * 5
        } ?: 0.0
}
