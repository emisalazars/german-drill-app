package com.emilio.deutschdrill.ui.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emilio.deutschdrill.DrillApp
import com.emilio.deutschdrill.data.DrillRepository
import com.emilio.deutschdrill.data.FileImporter
import com.emilio.deutschdrill.data.TagStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryState(
    val tags: List<TagStat> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
)

class LibraryViewModel(private val repo: DrillRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(tags = repo.tagStats(System.currentTimeMillis())) }
        }
    }

    fun import(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val message = try {
                val drafts = withContext(Dispatchers.IO) { FileImporter.read(context.applicationContext, uri) }
                val r = repo.import(drafts, System.currentTimeMillis())
                "Imported: ${r.added} new, ${r.updated} updated."
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
            _state.update { it.copy(busy = false, message = message) }
            refresh()
        }
    }

    suspend fun tutorJson(): String = repo.tutorExportJson(System.currentTimeMillis())

    fun copyTutorJson(copy: (String) -> Unit) {
        viewModelScope.launch {
            copy(tutorJson())
            _state.update { it.copy(message = "Copied. Paste it into your tutor chat.") }
        }
    }

    fun write(context: Context, uri: Uri, content: suspend () -> String) {
        viewModelScope.launch {
            val message = try {
                val text = content()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                "Saved."
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            _state.update { it.copy(message = message) }
        }
    }

    suspend fun ankiCsv(): String = repo.ankiCsv()

    companion object {
        fun factory(app: DrillApp) = viewModelFactory { initializer { LibraryViewModel(app.repository) } }
    }
}

@Composable
fun LibraryScreen(app: DrillApp, onBack: () -> Unit, onDrillTag: (String) -> Unit) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) { vm.refresh() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(context, uri)
    }
    val saveTutor = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.write(context, uri) { vm.tutorJson() }
    }
    val saveAnki = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.write(context, uri) { vm.ankiCsv() }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Library", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.weight(1f)) {
            item {
                Text("Import", style = MaterialTheme.typography.titleMedium)
                Text("Card JSON, Anki text/CSV (front, back, tags) or Anki .apkg. Re-importing updates cards and keeps your progress.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(if (state.busy) "Importing…" else "Import file")
                }
                Spacer(Modifier.height(8.dp))
                Text("Export", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Button(onClick = { vm.copyTutorJson { clipboard.setText(AnnotatedString(it)) } }, modifier = Modifier.weight(1f)) {
                        Text("Copy for tutor")
                    }
                    OutlinedButton(onClick = { saveTutor.launch("deutschdrill-tutor-export.json") }, modifier = Modifier.weight(1f)) {
                        Text("Save JSON")
                    }
                }
                OutlinedButton(onClick = { saveAnki.launch("deutschdrill-anki.txt") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export Anki CSV")
                }
                state.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Text("Error patterns (tap to drill one)", style = MaterialTheme.typography.titleMedium)
                Text("Accuracy over the last 30 days. Mastered = correct on 3 days, once mixed without help.",
                    style = MaterialTheme.typography.bodySmall)
            }
            items(state.tags, key = { it.tag }) { t ->
                Column(Modifier.fillMaxWidth().clickable { onDrillTag(t.tag) }.padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(t.accuracy?.let { "${(it * 100).toInt()}%" } ?: "–", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "P${t.priority} · ${t.cards} cards · ${t.mastered} mastered · ${t.attempts} tries" +
                            if (t.relapse) " · ⚠ relapse" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
