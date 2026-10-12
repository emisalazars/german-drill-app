# Deutsch Drill

A local-only Android app for making German grammar automatic under time pressure. It's built with Kotlin, Jetpack Compose and Room.
No backend, no ads, no account and no INTERNET permission. The only permission is the microphone, for the speaking drill.

## Build and install

You need JDK 17+ and the Android SDK (API 35). Android Studio Ladybug or newer includes both.

**Android Studio:** File → Open → this folder. Wait for the Gradle sync, plug in your phone with USB debugging on, then press Run.

**Command line:**
```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # Android Studio usually writes this for you
./gradlew testDebugUnitTest      # FSRS, answer matching, import/export, seed deck tests
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
`./gradlew assembleRelease` builds a smaller APK, shrunk with R8 and signed with the debug key so it installs directly.

**Without Android Studio (GitHub Actions):** push this folder to a GitHub repo. `.github/workflows/build.yml` runs
the tests, builds both APKs and attaches them to the run as the `deutschdrill-apk` artifact. Download it on your phone
and install it (allow "install unknown apps" for your browser or files app).

## How a session works

- **Start N-minute drill** (3 by default, adjustable from 2 to 12): due reviews interleaved with new cards. Weak, high-priority patterns come first.
  The queue never shows more than 3 cards of the same type in a row and avoids back-to-back cards from the same topic.
  Contrast pairs (mochte/möchte, im/in den, wegen des/wegen dem) come up back to back.
- Everything is **production**: you type or speak, never multiple choice. Answers ignore case, extra spaces, punctuation and
  missing umlauts (ae/oe/ue/ss), but never a wrong ending. A wrong answer shows a character diff of your answer against the correct one.
- **Grading is automatic:** wrong → Again, right but slow or after "Show rule" → Hard, right → Good, right and fast → Easy.
  These ratings feed **FSRS-5** (`domain/Fsrs.kt`, a port of py-fsrs with default weights and 90% retention).
- **Failed cards** return 3+ cards later in the same session. **New cards** always get a surprise retest the next day.
- **Two-step cards:** first the diagnostic question (Kasus? wo/wohin?), then the form. The diagnostic step is dropped once the card is stable (7+ days).
- **Show rule** is off by default. Using it caps the rating at Hard and re-queues the card for a retest without help.
- **Fix-it:** after a card's second failure you type the correct form 5×, then write one sentence of your own (saved for the tutor). The card is retested later.
- **Speaking:** Android SpeechRecognizer (de-DE, prefers offline). You see the recognized text and a suggested grade, then grade yourself.
  "Recognition error" retries without counting it against you. Offline recognition needs the German speech pack
  (Android Settings → System → Languages → Speech / on-device recognition).
- **Fill-in countdown** (default 10 s, toggle in Settings). Running out counts as a miss.
- Back-to-back sessions over 12 minutes trigger a break suggestion. The streak counts any day with a session of 2+ minutes.

**Mastery** = correct on 3 different days AND at least once in a mixed session without a hint.
**Relapse** = a mastered card failed again within 14 days.

## Import and export (Library screen)

| | Format |
|---|---|
| Import | Card JSON (below), Anki "Notes in Plain Text" / CSV (front, back, tags; tab, comma or semicolon), Anki `.apkg` (legacy format: tick *Support older Anki versions* when exporting) |
| Export for tutor | JSON with per-tag accuracy split by isolated/mixed/no-help/timed, relapse flags, median answer time, recent mistakes with your exact answers, your Fix-it sentences, slowest cards. **Copy for tutor** puts it on the clipboard to paste into the chat. |
| Export Anki | Tab-separated text with Anki header lines (front, back, tags) |

Re-importing a file with the same card ids updates the content and keeps your learning history.
CSV/Anki notes become two vocabulary cards (front → back and back → front).

### Card JSON schema

```json
{
  "source": "session-2026-10-12",
  "cards": [
    {
      "id": "avc-01",
      "type": "fill_blank",
      "prompt": "Am Wochenende besuche ich ___ Onkel in Bern. (mein)",
      "answers": ["meinen"],
      "hint": "Rule shown only when you tap 'Show rule'",
      "explanation": "besuchen + Akkusativ (wen?). Onkel ist maskulin → meinen.",
      "tags": ["akk-dat-verb-case", "akk-mask-endings"],
      "instruction": "optional line above the prompt, e.g. 'Verbinde mit weil.'",
      "example": "optional example sentence (vocab)",
      "contrast": "optional group id; cards in the same group are shown back-to-back",
      "diagnostic": {"question": "Kasus? (Akk/Dat)", "answers": ["Akk", "Akkusativ"]}
    }
  ]
}
```
`type`: `fill_blank` | `transform` | `error_correction` | `vocab` | `speaking`. Only `id`, `prompt` and `answers` (or `answer`) are required.
A bare array of cards is accepted too. `instruction`, `example`, `contrast` and `diagnostic` are optional additions to your original schema.

Tags with priorities and baselines from the tutoring diagnostic live in `domain/Tags.kt`. Unknown tags work and get priority 5.

## Seed decks

`app/src/main/assets/seed/` holds 282 cards generated by `tools/make_seed.py` from the diagnostic:
grammar for all 26 tags (with two-step, contrast, transformation, error-correction, speaking and 2-sentence mini-text cards),
noun gender (article only), spelling and interference traps, and the transport-planning vocabulary in both directions.
Edit the script, run `python3 tools/make_seed.py`, and bump `SEED_VERSION` in `DrillApp.kt` so the app re-imports it.
Your progress is kept.

## Project structure

```
app/src/main/java/com/emilio/deutschdrill/
  domain/        pure Kotlin, unit-tested, no Android imports
    Fsrs.kt             FSRS-5 scheduler (+ next-day retest cap for new cards)
    AnswerMatcher.kt    normalization (umlauts, case, punctuation) + LCS character diff
    Grader.kt           correctness + speed + hint → FSRS rating
    InterleavedQueue.kt session queue: max 3 same type in a row, contrast pairs, re-queue of failures
    CardImport.kt       card JSON + Anki CSV/text parsing
    Export.kt           tutor JSON export, mastery/relapse rules, Anki CSV export
    Stats.kt            streak, minutes today, fatigue block
    Tags.kt             error-pattern catalog with priorities and baselines
  data/          Room entities/DAO/database, repository, .apkg reader, settings
  ui/            Compose screens: home, drill (+ summary, Fix-it, speech), library, settings
app/src/test/    JUnit tests for the domain layer and the seed decks
tools/make_seed.py
```

## Not built yet (iteration 2)

Writing mode and forced-production cards with regex checks, XP/levels/history of your own weeks, boss round, bonus rounds,
streak freezes, adaptive difficulty, reminder notification, home-screen widget, progress charts, no-help retest mode.
