package com.emilio.deutschdrill.ui.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emilio.deutschdrill.DrillApp
import com.emilio.deutschdrill.data.answerList
import com.emilio.deutschdrill.data.cardType
import com.emilio.deutschdrill.data.diagnostic
import com.emilio.deutschdrill.data.tagList
import com.emilio.deutschdrill.data.CardEntity
import com.emilio.deutschdrill.data.DrillRepository
import com.emilio.deutschdrill.data.SettingsStore
import com.emilio.deutschdrill.domain.AnswerMatcher
import com.emilio.deutschdrill.domain.CardType
import com.emilio.deutschdrill.domain.FsrsState
import com.emilio.deutschdrill.domain.Grader
import com.emilio.deutschdrill.domain.InterleavedQueue
import com.emilio.deutschdrill.domain.Rating
import com.emilio.deutschdrill.domain.Stats
import com.emilio.deutschdrill.domain.Tags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { LOADING, EMPTY, DIAGNOSTIC, ANSWER, FEEDBACK, FIX_REPEAT, FIX_SENTENCE, SUMMARY }

data class DiagnosticFeedback(val correct: Boolean, val expected: String)

data class Mistake(val prompt: String, val yours: String, val expected: String)

data class SessionSummary(
    val answered: Int,
    val correct: Int,
    val minutes: Double,
    val mistakes: List<Mistake>,
    val missedTags: List<String>,
    /** Cards failed earlier in the session and then answered correctly. */
    val fixedInSession: Int,
    val streak: Int,
    val minutesToday: Double,
    val goalMinutes: Int,
    val suggestBreak: Boolean,
)

data class DrillUiState(
    val phase: Phase = Phase.LOADING,
    val card: CardEntity? = null,
    /** Increments for every card shown; UI keys timers and focus on it. */
    val cardKey: Int = 0,
    val input: String = "",
    val diagnosticFeedback: DiagnosticFeedback? = null,
    val ruleShown: Boolean = false,
    val result: AnswerMatcher.Result? = null,
    val userAnswer: String = "",
    val correct: Boolean = false,
    val rating: Rating? = null,
    /** Speaking cards: waiting for the learner's own grade. */
    val awaitingSelfGrade: Boolean = false,
    val suggestedRating: Rating? = null,
    val timedOut: Boolean = false,
    /** Countdown for this card, or null when the card is untimed. */
    val timerSeconds: Int? = null,
    val canOverride: Boolean = false,
    val fixCount: Int = 0,
    val fixError: Boolean = false,
    val answered: Int = 0,
    val correctCount: Int = 0,
    val sessionStart: Long = 0,
    val sessionLengthMs: Long = 0,
    val mixed: Boolean = true,
    val summary: SessionSummary? = null,
)

class DrillViewModel(
    private val repo: DrillRepository,
    private val settingsStore: SettingsStore,
    private val tag: String?,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(DrillUiState(mixed = tag == null))
    val state: StateFlow<DrillUiState> = _state.asStateFlow()

    private val settings get() = settingsStore.settings.value
    private val matcher get() = AnswerMatcher(settings.caseSensitive)

    private val cards = HashMap<String, CardEntity>()
    private var queue: InterleavedQueue<String>? = null
    private var sessionId = 0L
    private var answerStartedAt = 0L
    private var diagnosticCorrect: Boolean? = null
    private val sessionFails = HashMap<String, Int>()
    private val fixItDone = HashSet<String>()
    private val fixedInSession = HashSet<String>()
    private val mistakes = ArrayList<Mistake>()
    private val missedTags = LinkedHashSet<String>()
    private var lastBefore: CardEntity? = null
    private var lastReviewId: Long = 0

    init {
        startSession()
    }

    fun startSession() {
        viewModelScope.launch {
            _state.value = DrillUiState(mixed = tag == null)
            cards.clear(); sessionFails.clear(); fixItDone.clear(); fixedInSession.clear()
            mistakes.clear(); missedTags.clear()
            val now = clock()
            val list = repo.buildSession(tag, now)
            if (list.isEmpty()) {
                _state.update { it.copy(phase = Phase.EMPTY) }
                return@launch
            }
            list.forEach { cards[it.id] = it }
            queue = InterleavedQueue(
                items = list.map { it.id },
                typeOf = { cards[it]?.type ?: "" },
                topicOf = { cards[it]?.tagList?.firstOrNull() },
                groupOf = { cards[it]?.contrast },
            )
            sessionId = repo.startSession(if (tag == null) "mixed" else "tag:$tag", now)
            _state.update {
                it.copy(sessionStart = now, sessionLengthMs = settings.sessionMinutes * 60_000L)
            }
            showNext()
        }
    }

    fun onInput(text: String) = _state.update { it.copy(input = text, fixError = false) }

    fun submitDiagnostic() {
        val s = _state.value
        val card = s.card ?: return
        val diag = card.diagnostic ?: return
        val r = matcher.check(s.input, diag.answers)
        diagnosticCorrect = r.correct
        enterAnswerPhase(card, DiagnosticFeedback(r.correct, diag.answers.first()))
    }

    /** Showing the rule caps the rating at Hard and brings the card back later without help. */
    fun showRule() = _state.update { it.copy(ruleShown = true) }

    fun submitAnswer(timedOut: Boolean = false) {
        val s = _state.value
        if (s.phase != Phase.ANSWER) return
        val card = s.card ?: return
        val elapsed = clock() - answerStartedAt
        val result = matcher.check(s.input, card.answerList)
        val correct = !timedOut && result.correct && diagnosticCorrect != false
        val rating = Grader.rate(correct, s.ruleShown, elapsed, Grader.expectedMs(card.cardType, result.target))
        _state.update {
            it.copy(
                phase = Phase.FEEDBACK, result = result, userAnswer = s.input, correct = correct, rating = rating,
                timedOut = timedOut, canOverride = !correct && !timedOut && s.input.isNotBlank() && diagnosticCorrect != false,
            )
        }
        persist(card, rating, correct, s.ruleShown, timed = s.timerSeconds != null, elapsed, s.input, result.target)
    }

    /** Speaking: show what was recognized and suggest a grade; the learner decides. */
    fun onSpeechResult(text: String) {
        val s = _state.value
        val card = s.card ?: return
        val elapsed = clock() - answerStartedAt
        val result = matcher.check(text, card.answerList)
        val suggested = Grader.rate(result.correct, s.ruleShown, elapsed, Grader.expectedMs(card.cardType, result.target))
        _state.update {
            it.copy(
                phase = Phase.FEEDBACK, input = text, userAnswer = text, result = result, correct = result.correct,
                awaitingSelfGrade = true, suggestedRating = suggested,
            )
        }
    }

    fun selfGrade(rating: Rating) {
        val s = _state.value
        val card = s.card ?: return
        if (!s.awaitingSelfGrade) return
        val correct = rating != Rating.AGAIN
        _state.update { it.copy(awaitingSelfGrade = false, rating = rating, correct = correct) }
        persist(card, rating, correct, s.ruleShown, timed = false, clock() - answerStartedAt, s.userAnswer, s.result?.target ?: "")
    }

    /** Speech recognition misheard: not the learner's mistake, so nothing is logged. Try again. */
    fun recognitionError() {
        val card = _state.value.card ?: return
        enterAnswerPhase(card, _state.value.diagnosticFeedback)
    }

    fun overrideCorrect() {
        val before = lastBefore ?: return
        val s = _state.value
        if (!s.canOverride) return
        _state.update { it.copy(canOverride = false, correct = true, rating = Rating.GOOD, correctCount = it.correctCount + 1) }
        mistakes.removeLastOrNull()
        sessionFails[before.id] = ((sessionFails[before.id] ?: 1) - 1).coerceAtLeast(0)
        queue?.remove(before.id)
        viewModelScope.launch {
            cards[before.id] = repo.overrideAsCorrect(before, lastReviewId, s.userAnswer, clock())
        }
    }

    fun continueNext() {
        val s = _state.value
        val card = s.card
        if (s.phase == Phase.FEEDBACK && s.awaitingSelfGrade) return
        if (card != null && s.phase == Phase.FEEDBACK && !s.correct && card.id !in fixItDone &&
            ((sessionFails[card.id] ?: 0) >= 2 || (cards[card.id]?.failStreak ?: 0) >= 2)
        ) {
            fixItDone += card.id
            _state.update { it.copy(phase = Phase.FIX_REPEAT, fixCount = 0, input = "", fixError = false) }
            return
        }
        showNext()
    }

    /** Fix-it step 1: type the correct form five times. */
    fun submitFixRepeat() {
        val s = _state.value
        val target = s.result?.target ?: return
        if (matcher.check(s.input, listOf(target)).correct) {
            val count = s.fixCount + 1
            _state.update {
                if (count >= FIX_REPEATS) it.copy(phase = Phase.FIX_SENTENCE, fixCount = count, input = "")
                else it.copy(fixCount = count, input = "")
            }
        } else {
            _state.update { it.copy(fixError = true) }
        }
    }

    /** Fix-it step 2: one sentence of your own (saved for the tutor), then on with the session. */
    fun submitFixSentence() {
        val s = _state.value
        val card = s.card ?: return
        viewModelScope.launch { repo.saveOwnSentence(card.id, s.input, clock()) }
        showNext()
    }

    fun endSession() {
        viewModelScope.launch { finish() }
    }

    private fun persist(
        card: CardEntity, rating: Rating, correct: Boolean, hintUsed: Boolean, timed: Boolean,
        elapsed: Long, answer: String, expected: String,
    ) {
        _state.update { it.copy(answered = it.answered + 1, correctCount = it.correctCount + if (correct) 1 else 0) }
        if (correct) {
            if ((sessionFails[card.id] ?: 0) > 0) fixedInSession += card.id
        } else {
            sessionFails[card.id] = (sessionFails[card.id] ?: 0) + 1
            mistakes += Mistake(card.prompt, answer.ifBlank { "—" }, expected)
            missedTags += card.tagList
        }
        if (!correct || hintUsed) queue?.requeue(card.id)
        lastBefore = card
        viewModelScope.launch {
            val (updated, reviewId) = repo.recordAttempt(
                card, rating, correct, hintUsed, timed, mixed = tag == null, elapsed, answer, expected, sessionId, clock(),
            )
            cards[card.id] = updated
            lastReviewId = reviewId
        }
    }

    private fun showNext() {
        val s = _state.value
        val timeUp = s.sessionStart > 0 && clock() - s.sessionStart >= s.sessionLengthMs
        val nextId = if (timeUp) null else queue?.next()
        val card = nextId?.let { cards[it] }
        if (card == null) {
            viewModelScope.launch { finish() }
            return
        }
        diagnosticCorrect = null
        val showDiagnostic = card.diagnostic != null &&
            (card.state != FsrsState.REVIEW.ordinal || card.stability < STABLE_DAYS)
        if (showDiagnostic) {
            _state.update {
                it.copy(
                    phase = Phase.DIAGNOSTIC, card = card, cardKey = it.cardKey + 1, input = "",
                    diagnosticFeedback = null, ruleShown = false, result = null, userAnswer = "", correct = false,
                    rating = null, awaitingSelfGrade = false, suggestedRating = null, timedOut = false,
                    timerSeconds = null, canOverride = false,
                )
            }
        } else {
            _state.update { it.copy(card = card, cardKey = it.cardKey + 1) }
            enterAnswerPhase(card, null)
        }
    }

    private fun enterAnswerPhase(card: CardEntity, feedback: DiagnosticFeedback?) {
        answerStartedAt = clock()
        val timed = card.cardType == CardType.FILL_BLANK && settings.fillTimerEnabled
        _state.update {
            it.copy(
                phase = Phase.ANSWER, card = card, input = "", diagnosticFeedback = feedback,
                ruleShown = false, result = null, userAnswer = "", correct = false, rating = null,
                awaitingSelfGrade = false, suggestedRating = null, timedOut = false,
                timerSeconds = if (timed) settings.fillTimerSeconds else null, canOverride = false,
            )
        }
    }

    private suspend fun finish() {
        if (_state.value.phase == Phase.SUMMARY) return
        val now = clock()
        val s = _state.value
        if (sessionId != 0L) repo.finishSession(sessionId, now, s.answered, s.correctCount)
        val daily = repo.dailyStatsNow(now)
        _state.update {
            it.copy(
                phase = Phase.SUMMARY,
                summary = SessionSummary(
                    answered = s.answered,
                    correct = s.correctCount,
                    minutes = if (s.sessionStart > 0) (now - s.sessionStart) / 60_000.0 else 0.0,
                    mistakes = mistakes.toList(),
                    missedTags = missedTags.map { t -> Tags.info(t).title },
                    fixedInSession = fixedInSession.size,
                    streak = daily.streakDays,
                    minutesToday = daily.minutesToday,
                    goalMinutes = settings.dailyGoalMinutes,
                    suggestBreak = daily.currentBlockMinutes >= Stats.BLOCK_CAP_MINUTES,
                ),
            )
        }
    }

    companion object {
        const val FIX_REPEATS = 5
        /** Two-step cards drop the diagnostic question once the card is this stable (days). */
        const val STABLE_DAYS = 7.0

        fun factory(app: DrillApp, tag: String?) = viewModelFactory {
            initializer { DrillViewModel(app.repository, app.settings, tag) }
        }
    }
}
