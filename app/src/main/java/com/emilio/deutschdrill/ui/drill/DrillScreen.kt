package com.emilio.deutschdrill.ui.drill

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emilio.deutschdrill.DrillApp
import com.emilio.deutschdrill.data.answerList
import com.emilio.deutschdrill.data.cardType
import com.emilio.deutschdrill.data.diagnostic
import com.emilio.deutschdrill.data.tagList
import com.emilio.deutschdrill.domain.CardType
import com.emilio.deutschdrill.domain.DiffKind
import com.emilio.deutschdrill.domain.DiffSegment
import com.emilio.deutschdrill.domain.Rating
import com.emilio.deutschdrill.domain.Tags
import com.emilio.deutschdrill.ui.theme.FeedbackColors
import com.emilio.deutschdrill.ui.theme.isDarkTheme
import kotlinx.coroutines.delay

@Composable
fun DrillScreen(app: DrillApp, tag: String?, onClose: () -> Unit) {
    val vm: DrillViewModel = viewModel(factory = DrillViewModel.factory(app, tag))
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
        TopBar(state, tag, onClose = {
            if (state.phase == Phase.SUMMARY || state.phase == Phase.EMPTY) onClose() else vm.endSession()
        })
        when (state.phase) {
            Phase.LOADING -> Unit
            Phase.EMPTY -> EmptyState(onClose)
            Phase.SUMMARY -> SummaryView(state.summary, onDone = onClose, onAgain = vm::startSession)
            else -> CardView(state, vm)
        }
    }
}

@Composable
private fun TopBar(state: DrillUiState, tag: String?, onClose: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.sessionStart) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val remainingMs = (state.sessionLengthMs - (now - state.sessionStart)).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "End session") }
        if (state.sessionStart > 0 && state.phase != Phase.SUMMARY) {
            LinearProgressIndicator(
                progress = { 1f - remainingMs.toFloat() / state.sessionLengthMs.coerceAtLeast(1) },
                modifier = Modifier.weight(1f),
            )
            Text(
                "  %d:%02d".format(remainingMs / 60_000, (remainingMs / 1000) % 60),
                style = MaterialTheme.typography.labelLarge,
            )
            Text("  ✓ ${state.correctCount}/${state.answered}", style = MaterialTheme.typography.labelLarge)
        } else {
            Text(tag?.let { Tags.info(it).title } ?: "", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyState(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No cards yet.", style = MaterialTheme.typography.titleLarge)
        Text("Import a deck in the Library.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose) { Text("Back") }
    }
}

@Composable
private fun CardView(state: DrillUiState, vm: DrillViewModel) {
    val card = state.card ?: return
    val focus = remember { FocusRequester() }
    val isSpeaking = card.cardType == CardType.SPEAKING

    // Countdown for timed fill-in cards: running out counts as a miss.
    var timerFraction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(state.cardKey, state.phase) {
        val seconds = state.timerSeconds
        if (state.phase != Phase.ANSWER || seconds == null) return@LaunchedEffect
        val start = System.currentTimeMillis()
        val total = seconds * 1000L
        while (true) {
            val left = total - (System.currentTimeMillis() - start)
            timerFraction = (left.toFloat() / total).coerceIn(0f, 1f)
            if (left <= 0) {
                vm.submitAnswer(timedOut = true)
                break
            }
            delay(50)
        }
    }

    LaunchedEffect(state.cardKey, state.phase) {
        val typing = state.phase == Phase.DIAGNOSTIC || state.phase == Phase.FIX_REPEAT || state.phase == Phase.FIX_SENTENCE ||
            (state.phase == Phase.ANSWER && !isSpeaking)
        if (typing) runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.cardType.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                card.tagList.firstOrNull()?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("· " + Tags.info(it).title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.timerSeconds != null && state.phase == Phase.ANSWER) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { timerFraction }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(12.dp))
            card.instruction?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
            }
            Text(card.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            if (state.phase == Phase.DIAGNOSTIC) {
                Spacer(Modifier.height(16.dp))
                Text("Step 1: " + card.diagnostic!!.question, style = MaterialTheme.typography.titleMedium)
            }
            state.diagnosticFeedback?.let { fb ->
                Spacer(Modifier.height(12.dp))
                Text(
                    if (fb.correct) "✓ ${fb.expected}" else "✗ It's ${fb.expected}. Now the form:",
                    color = feedbackColor(fb.correct),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.ruleShown && state.phase == Phase.ANSWER) {
                Spacer(Modifier.height(12.dp))
                RuleBox(card.hint ?: card.explanation ?: "No rule stored for this card.")
            }
            if (state.phase == Phase.FEEDBACK || state.phase == Phase.FIX_REPEAT || state.phase == Phase.FIX_SENTENCE) {
                Spacer(Modifier.height(16.dp))
                FeedbackView(state)
            }
            if (state.phase == Phase.FIX_REPEAT) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Fix-it: type it ${DrillViewModel.FIX_REPEATS} times (${state.fixCount}/${DrillViewModel.FIX_REPEATS})",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.fixError) Text("Not quite. Copy it exactly.", color = feedbackColor(false))
            }
            if (state.phase == Phase.FIX_SENTENCE) {
                Spacer(Modifier.height(16.dp))
                Text("Fix-it: write one sentence of your own that uses it.", style = MaterialTheme.typography.titleMedium)
                Text("It's saved for your tutor export. The card comes back later.", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Bottom: input and actions, in the thumb zone.
        when (state.phase) {
            Phase.DIAGNOSTIC -> AnswerField(state.input, vm::onInput, focus, "Your answer", vm::submitDiagnostic)
            Phase.ANSWER -> if (isSpeaking) SpeakingInput(state, vm, focus) else {
                AnswerField(state.input, vm::onInput, focus, "Your answer", { vm.submitAnswer() })
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.ruleShown) OutlinedButton(onClick = vm::showRule) { Text("Show rule") }
                    Button(onClick = { vm.submitAnswer() }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Check") }
                }
            }
            Phase.FEEDBACK -> FeedbackActions(state, vm)
            Phase.FIX_REPEAT -> {
                AnswerField(state.input, vm::onInput, focus, state.result?.target ?: "", vm::submitFixRepeat)
                Button(onClick = vm::submitFixRepeat, modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) { Text("Enter") }
            }
            Phase.FIX_SENTENCE -> {
                AnswerField(state.input, vm::onInput, focus, "Mein Satz …", vm::submitFixSentence, singleLine = false)
                Button(onClick = vm::submitFixSentence, modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) { Text("Save and continue") }
            }
            else -> Unit
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AnswerField(
    value: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    placeholder: String,
    onSubmit: () -> Unit,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
}

@Composable
private fun SpeakingInput(state: DrillUiState, vm: DrillViewModel, focus: FocusRequester) {
    val context = LocalContext.current
    val speech = rememberSpeechInput { vm.onSpeechResult(it) }
    var typeInstead by remember(state.cardKey) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start()
    }
    if (!speech.available || typeInstead) {
        if (!speech.available) Text("Speech recognition isn't available on this device; type instead.", style = MaterialTheme.typography.bodySmall)
        AnswerField(state.input, vm::onInput, focus, "Say it in writing", { vm.onSpeechResult(state.input) })
        Button(onClick = { vm.onSpeechResult(state.input) }, modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) { Text("Check") }
        return
    }
    if (speech.partial.isNotBlank()) Text("„${speech.partial}“", style = MaterialTheme.typography.titleMedium)
    speech.error?.let { Text(it, color = feedbackColor(false), style = MaterialTheme.typography.bodyMedium) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { typeInstead = true }) { Text("Type instead") }
        Button(
            onClick = {
                if (speech.listening) speech.stop()
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.start()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.weight(1f).height(64.dp),
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null)
            Text(if (speech.listening) "  Listening… tap to stop" else "  Tap and speak (de-DE)")
        }
    }
}

@Composable
private fun FeedbackView(state: DrillUiState) {
    val card = state.card ?: return
    val result = state.result ?: return
    Text(
        when {
            state.timedOut -> "⏱ Time's up"
            state.correct && state.ruleShown -> "✓ Correct (with the rule shown; it comes back later)"
            state.correct -> "✓ Correct"
            result.nearMiss -> "✗ Almost: one letter off"
            else -> "✗ Not quite"
        },
        color = feedbackColor(state.correct),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    if (!state.correct && state.userAnswer.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text("Yours vs. correct:", style = MaterialTheme.typography.labelMedium)
        Text(diffText(result.diff), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
    }
    Spacer(Modifier.height(8.dp))
    Text("Answer:", style = MaterialTheme.typography.labelMedium)
    Text(result.target, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    val others = card.answerList.filter { it != result.target }
    if (others.isNotEmpty()) Text("Also accepted: " + others.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    card.explanation?.let { Spacer(Modifier.height(10.dp)); RuleBox(it) }
    card.example?.let {
        Spacer(Modifier.height(8.dp))
        Text("Beispiel: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedbackActions(state: DrillUiState, vm: DrillViewModel) {
    if (state.awaitingSelfGrade) {
        Text("How did it go? Suggested: ${state.suggestedRating?.name?.lowercase() ?: "-"}", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Rating.entries.forEach { r ->
                val suggested = r == state.suggestedRating
                val label = r.name.lowercase().replaceFirstChar { it.uppercase() }
                if (suggested) Button(onClick = { vm.selfGrade(r) }, modifier = Modifier.weight(1f).height(56.dp)) { Text(label) }
                else FilledTonalButton(onClick = { vm.selfGrade(r) }, modifier = Modifier.weight(1f).height(56.dp)) { Text(label) }
            }
        }
        TextButton(onClick = vm::recognitionError, modifier = Modifier.fillMaxWidth()) {
            Text("Recognition error: it misheard me (not counted)")
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.canOverride) OutlinedButton(onClick = vm::overrideCorrect, modifier = Modifier.height(56.dp)) { Text("I was right") }
        Button(
            onClick = vm::continueNext,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = if (state.correct) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        ) { Text("Continue") }
    }
}

@Composable
private fun RuleBox(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun feedbackColor(correct: Boolean): Color {
    val dark = isDarkTheme()
    return when {
        correct && dark -> FeedbackColors.correctDark
        correct -> FeedbackColors.correctLight
        dark -> FeedbackColors.wrongDark
        else -> FeedbackColors.wrongLight
    }
}

@Composable
private fun diffText(diff: List<DiffSegment>): AnnotatedString {
    val ok = feedbackColor(true)
    val bad = feedbackColor(false)
    return buildAnnotatedString {
        for (seg in diff) when (seg.kind) {
            DiffKind.SAME -> append(seg.text)
            DiffKind.MISSING -> withStyle(SpanStyle(color = ok, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(seg.text) }
            DiffKind.EXTRA -> withStyle(SpanStyle(color = bad, textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
        }
    }
}

@Composable
private fun SummaryView(summary: SessionSummary?, onDone: () -> Unit, onAgain: () -> Unit) {
    val s = summary ?: return
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Text("Session done", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val acc = if (s.answered > 0) s.correct * 100 / s.answered else 0
            Text("$acc% correct · ${s.correct}/${s.answered} · %.1f min".format(s.minutes), style = MaterialTheme.typography.titleLarge)
            Text("🔥 ${s.streak}-day streak · today %.0f / %d min".format(s.minutesToday, s.goalMinutes), style = MaterialTheme.typography.titleMedium)
            if (s.fixedInSession > 0) Text("Fixed in this session: ${s.fixedInSession}", color = feedbackColor(true))
            if (s.suggestBreak) {
                Spacer(Modifier.height(12.dp))
                RuleBox("You've drilled 12+ minutes in a row. Accuracy drops after that, so take a break and come back later.")
            }
            if (s.missedTags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Patterns to watch", style = MaterialTheme.typography.titleMedium)
                s.missedTags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
            if (s.mistakes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Mistakes", style = MaterialTheme.typography.titleMedium)
                s.mistakes.forEach { m ->
                    Spacer(Modifier.height(8.dp))
                    Text(m.prompt, style = MaterialTheme.typography.bodyMedium)
                    Text("✗ ${m.yours}", color = feedbackColor(false))
                    Text("✓ ${m.expected}", color = feedbackColor(true))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f).height(56.dp)) { Text("Another round") }
            Button(onClick = onDone, modifier = Modifier.weight(1f).height(56.dp)) { Text("Done") }
        }
    }
}
