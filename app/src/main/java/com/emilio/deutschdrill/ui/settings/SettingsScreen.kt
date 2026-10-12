package com.emilio.deutschdrill.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emilio.deutschdrill.DrillApp
import com.emilio.deutschdrill.data.ThemeMode

@Composable
fun SettingsScreen(app: DrillApp, onBack: () -> Unit) {
    val store = app.settings
    val s by store.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Stepper("Session length", "${s.sessionMinutes} min", { store.update { it.copy(sessionMinutes = (it.sessionMinutes - 1).coerceAtLeast(2)) } },
            { store.update { it.copy(sessionMinutes = (it.sessionMinutes + 1).coerceAtMost(12)) } })
        Stepper("Daily goal", "${s.dailyGoalMinutes} min", { store.update { it.copy(dailyGoalMinutes = (it.dailyGoalMinutes - 1).coerceAtLeast(2)) } },
            { store.update { it.copy(dailyGoalMinutes = (it.dailyGoalMinutes + 1).coerceAtMost(60)) } })
        Stepper("New cards per day", "${s.newCardsPerDay}", { store.update { it.copy(newCardsPerDay = (it.newCardsPerDay - 5).coerceAtLeast(0)) } },
            { store.update { it.copy(newCardsPerDay = (it.newCardsPerDay + 5).coerceAtMost(100)) } })
        Toggle("Countdown on fill-in cards", s.fillTimerEnabled) { v -> store.update { it.copy(fillTimerEnabled = v) } }
        if (s.fillTimerEnabled) {
            Stepper("Countdown", "${s.fillTimerSeconds} s", { store.update { it.copy(fillTimerSeconds = (it.fillTimerSeconds - 1).coerceAtLeast(3)) } },
                { store.update { it.copy(fillTimerSeconds = (it.fillTimerSeconds + 1).coerceAtMost(30)) } })
        }
        Toggle("Case-sensitive answers", s.caseSensitive) { v -> store.update { it.copy(caseSensitive = v) } }
        Spacer(Modifier.height(12.dp))
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = s.theme == mode,
                    onClick = { store.update { it.copy(theme = mode) } },
                    label = { Text(mode.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Answers ignore missing umlauts (ae/oe/ue/ss), extra spaces and punctuation. Hints are off unless you tap \"Show rule\", " +
                "which counts against the card and brings it back later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = onMinus) { Text("−") }
        Text("  $value  ", style = MaterialTheme.typography.titleMedium)
        FilledTonalButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
