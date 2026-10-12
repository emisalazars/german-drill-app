package com.emilio.deutschdrill.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.emilio.deutschdrill.domain.AnkiCsv
import com.emilio.deutschdrill.domain.CardDraft
import com.emilio.deutschdrill.domain.CardJson
import com.emilio.deutschdrill.domain.ImportException
import java.io.File
import java.util.zip.ZipInputStream

/** Reads a user-picked file (JSON, CSV/TXT, or Anki .apkg) into card drafts. */
object FileImporter {
    fun read(context: Context, uri: Uri): List<CardDraft> {
        val name = displayName(context, uri) ?: uri.lastPathSegment ?: "import"
        val source = "import-" + name.substringBeforeLast('.')
        val lower = name.lowercase()
        if (lower.endsWith(".apkg") || lower.endsWith(".colpkg")) return readApkg(context, uri, source)
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw ImportException("Could not open $name")
        val firstChar = text.trimStart('﻿', ' ', '\n', '\r', '\t').firstOrNull()
        return if (lower.endsWith(".json") || firstChar == '{' || firstChar == '[') {
            CardJson.parse(text, defaultSource = source)
        } else {
            AnkiCsv.parse(text, source)
        }
    }

    private fun readApkg(context: Context, uri: Uri, source: String): List<CardDraft> {
        val dir = File(context.cacheDir, "apkg").apply { deleteRecursively(); mkdirs() }
        val found = HashMap<String, File>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("collection.anki2")) {
                        val f = File(dir, entry.name)
                        f.outputStream().use { zip.copyTo(it) }
                        found[entry.name] = f
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: throw ImportException("Could not open the .apkg file")

        val file = found["collection.anki21"]
            ?: if (found.containsKey("collection.anki21b")) {
                throw ImportException(
                    "This .apkg uses Anki's newest format. In Anki, export again with " +
                        "\"Support older Anki versions\" ticked, or export as Notes in Plain Text."
                )
            } else found["collection.anki2"]
        file ?: throw ImportException("No Anki collection found in this file")

        val notes = ArrayList<AnkiCsv.AnkiNote>()
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT flds, tags FROM notes", null).use { c ->
                    while (c.moveToNext()) {
                        val fields = c.getString(0).split('\u001F')
                        if (fields.size >= 2) notes += AnkiCsv.AnkiNote(fields[0], fields[1], c.getString(1) ?: "")
                    }
                }
            }
        } finally {
            dir.deleteRecursively()
        }
        return AnkiCsv.fromNotes(notes, html = true, source = source)
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
