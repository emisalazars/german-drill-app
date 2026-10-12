package com.emilio.deutschdrill.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emilio.deutschdrill.DrillApp
import com.emilio.deutschdrill.data.AppSettings
import com.emilio.deutschdrill.data.DrillRepository
import com.emilio.deutschdrill.data.SettingsStore
import com.emilio.deutschdrill.domain.DailyStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val due: Int = 0,
    val new: Int = 0,
    val total: Int = 0,
    val stats: DailyStats = DailyStats(0, false, 0.0, 0.0),
    val settings: AppSettings = AppSettings(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(repo: DrillRepository, settings: SettingsStore) : ViewModel() {
    private val now = MutableStateFlow(System.currentTimeMillis())

    val state: StateFlow<HomeState> = combine(
        now.flatMapLatest { repo.dueCount(it) },
        repo.newCount(),
        repo.cardCount(),
        repo.dailyStats { System.currentTimeMillis() },
        settings.settings,
    ) { due, new, total, stats, s -> HomeState(due, new, total, stats, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun refresh() {
        now.value = System.currentTimeMillis()
    }

    companion object {
        fun factory(app: DrillApp) = viewModelFactory { initializer { HomeViewModel(app.repository, app.settings) } }
    }
}

@Composable
fun HomeScreen(app: DrillApp, onStart: () -> Unit, onLibrary: () -> Unit, onSettings: () -> Unit) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app))
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Deutsch Drill", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onLibrary) { Icon(Icons.Filled.LibraryBooks, contentDescription = "Library") }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }

        Spacer(Modifier.weight(1f))

        val goal = state.settings.dailyGoalMinutes.coerceAtLeast(1)
        GoalRing(
            progress = (state.stats.minutesToday / goal).toFloat().coerceIn(0f, 1f),
            label = "%.0f / %d min".format(state.stats.minutesToday, goal),
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = if (state.stats.practicedToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Text(
                " ${state.stats.streakDays}-day streak" + if (!state.stats.practicedToday && state.stats.streakDays > 0) " (practice today to keep it)" else "",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.due} due · ${state.new} new · ${state.total} cards",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        // Primary action sits in the thumb zone at the bottom.
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(88.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text(
                "Start ${state.settings.sessionMinutes}-minute drill",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GoalRing(progress: Float, label: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(fill, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("today", style = MaterialTheme.typography.bodySmall)
        }
    }
}
