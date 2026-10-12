package com.emilio.deutschdrill.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ImportException(message: String) : Exception(message)

/**
 * Parses the app's card JSON. Accepts either a bare array of cards or {"cards": [...]}.
 *
 * Card fields: id, type, prompt, answers (array) or answer (string), hint, explanation,
 * tags (array or space/comma separated string), source, instruction, example,
 * contrast, diagnostic {question, answers}. Unknown fields are ignored.
 */
object CardJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String, defaultSource: String? = null): List<CardDraft> {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ImportException("Not valid JSON: ${e.message}")
        }
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root["cards"]?.jsonArray ?: throw ImportException("Expected a \"cards\" array")
            else -> throw ImportException("Expected a JSON array or object")
        }
        val deckSource = (root as? JsonObject)?.string("source") ?: defaultSource
        return array.mapIndexed { index, element ->
            val o = element as? JsonObject ?: throw ImportException("Card #${index + 1} is not an object")
            parseCard(o, index, deckSource)
        }
    }

    private fun parseCard(o: JsonObject, index: Int, deckSource: String?): CardDraft {
        val prompt = o.string("prompt") ?: o.string("front")
            ?: throw ImportException("Card #${index + 1} has no prompt")
        val answers = (o.stringList("answers") + o.stringList("answer") + o.stringList("back")).distinct()
        if (answers.isEmpty()) throw ImportException("Card #${index + 1} (\"$prompt\") has no answer")
        val diagnostic = (o["diagnostic"] as? JsonObject)?.let { d ->
            val q = d.string("question") ?: return@let null
            val a = d.stringList("answers") + d.stringList("answer")
            if (a.isEmpty()) null else Diagnostic(q, a)
        }
        val type = CardType.parse(o.string("type"))
        val id = o.string("id") ?: stableId(type.name, prompt, answers.first())
        return CardDraft(
            id = id,
            type = type,
            prompt = prompt,
            answers = answers,
            hint = o.string("hint"),
            explanation = o.string("explanation"),
            tags = o.stringList("tags").flatMap { it.split(' ', ',') }.map { it.trim() }.filter { it.isNotEmpty() },
            source = o.string("source") ?: deckSource,
            instruction = o.string("instruction"),
            example = o.string("example"),
            contrast = o.string("contrast"),
            diagnostic = diagnostic,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.stringList(key: String): List<String> = when (val v: JsonElement? = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotEmpty() }
        is JsonPrimitive -> listOfNotNull(v.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
        else -> emptyList()
    }
}

/** Deterministic id so re-importing the same deck updates cards instead of duplicating them. */
fun stableId(vararg parts: String): String {
    var h = 1125899906842597L
    for (p in parts) for (c in p) h = 31 * h + c.code
    return "c" + java.lang.Long.toHexString(h)
}

/**
 * Anki-compatible text/CSV import: front, back, tags. Handles Anki's "#separator:" / "#tags column:"
 * header lines, tab, semicolon or comma separators, quoted fields and HTML in fields.
 * Each note becomes two vocabulary cards (front → back and back → front).
 */
object AnkiCsv {
    fun parse(text: String, source: String? = null): List<CardDraft> {
        var separator: Char? = null
        var tagsColumn: Int? = null
        var html = true
        val body = StringBuilder()
        for (line in text.removePrefix("﻿").lines()) {
            if (line.startsWith("#") && body.isEmpty() && line.contains(':')) {
                val key = line.substringBefore(':').removePrefix("#").trim().lowercase()
                val value = line.substringAfter(':').trim()
                when (key) {
                    "separator" -> separator = when (value.lowercase()) {
                        "tab" -> '\t'; "comma" -> ','; "semicolon" -> ';'; "space" -> ' '; "pipe" -> '|'
                        else -> value.firstOrNull()
                    }
                    "tags column" -> tagsColumn = value.toIntOrNull()
                    "html" -> html = value.equals("true", ignoreCase = true)
                }
                continue
            }
            body.append(line).append('\n')
        }
        val sep = separator ?: detectSeparator(body.lineSequence().firstOrNull { it.isNotBlank() } ?: "")
        val rows = splitRows(body.toString(), sep).filter { row -> row.any { it.isNotBlank() } }
        val dataRows = if (rows.firstOrNull()?.firstOrNull()?.trim()?.lowercase() == "front") rows.drop(1) else rows

        val notes = dataRows.filter { it.size >= 2 }.map { row ->
            val tagIndex = (tagsColumn?.minus(1)) ?: if (row.size >= 3) 2 else null
            AnkiNote(row[0], row[1], tagIndex?.let { row.getOrNull(it) } ?: "")
        }
        return fromNotes(notes, html, source)
    }

    data class AnkiNote(val front: String, val back: String, val tags: String)

    /** Each note becomes two vocabulary cards: front → back and back → front. */
    fun fromNotes(notes: List<AnkiNote>, html: Boolean, source: String?): List<CardDraft> {
        val cards = ArrayList<CardDraft>()
        for (note in notes) {
            val front = clean(note.front, html)
            val back = clean(note.back, html)
            if (front.isEmpty() || back.isEmpty()) continue
            val tags = note.tags.split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
            val allTags = (tags + "vocab").distinct()
            cards += CardDraft(stableId("anki", front, back), CardType.VOCAB, front, variants(back), tags = allTags, source = source)
            cards += CardDraft(stableId("anki-r", front, back), CardType.VOCAB, back, variants(front), tags = allTags, source = source)
        }
        return cards
    }

    /** "to visit; to attend" accepts the whole string or any single meaning. */
    internal fun variants(answer: String): List<String> =
        (listOf(answer) + answer.split(';', ',', '/').map { it.trim() }).filter { it.isNotEmpty() }.distinct()

    internal fun clean(field: String, html: Boolean): String {
        var s = field.replace(Regex("\\[sound:[^]]*]"), "")
        if (html) {
            s = s.replace(Regex("(?i)<br\\s*/?>"), " ")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun detectSeparator(line: String): Char = when {
        line.contains('\t') -> '\t'
        line.count { it == ';' } > line.count { it == ',' } -> ';'
        else -> ','
    }

    /** RFC 4180-style splitting: quoted fields may contain separators, newlines and "" escapes. */
    internal fun splitRows(text: String, sep: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> if (field.isEmpty()) inQuotes = true else field.append(c)
                sep -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = ArrayList() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
