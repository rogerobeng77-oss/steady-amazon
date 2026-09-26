# Steady

A falls-prevention strength and balance course for the Fire TV remote. See `SPEC.md` for
the evidence, the design reasoning, the two objections this product has to answer, and
where Amazon Bedrock is and is not allowed to touch it.

## What it is, in one screen

Turn the app on. One button is focused: **Start Today's Session**. Press it, walk through
today's exercises at your own pace (each has a "Done, next" button, never a forced
countdown), pick one of three feelings when you finish ("Too much" / "Just right" /
"Too easy"), and you're back on the home screen with an updated weekly streak. A quiet
card below the streak shows the weekly update meant for one named family member, with a
"Send now" button. A "Pause" tile lets the person say they're away or unwell without
losing their streak or their level. History and Settings sit in a quiet corner for the
second day onward.

No camera. No wearable. No login. No account. Nothing about the exercises or the
adherence rules ever depends on a network call.

## Running it

Requires JDK 21. `gradle.properties` pins `org.gradle.java.home` to a JDK 21 install
path; edit that line if yours is elsewhere. Also requires the Android SDK, with its
location either in `local.properties` or in `ANDROID_HOME`:

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # or your SDK path; gitignored, not in the clone

# unit tests — the domain logic, no emulator needed
./gradlew testDebugUnitTest

# build the debug APK
./gradlew assembleDebug

# install and run on a running emulator or connected device
./gradlew installDebug
adb shell am start -n com.steady.app/.MainActivity
```

### On the emulator, and on a real Fire TV

Developed and driven day to day against a real `FireTV_API28` AVD (Android TV system
image, API 28, 1920x1080 at density 320 — Fire OS 16's actual profile) as well as a
phone AVD rotated to landscape early in the build. Drive it with `adb shell input
keyevent` (19 UP, 20 DOWN, 21 LEFT, 22 RIGHT, 23 CENTER, 4 BACK), not with taps: a tap
can land correctly even when D-pad focus itself is wrong, which is exactly the class of
bug this app had and fixed. The manifest is written for the real target: minSdk 28,
`android.software.leanback` declared, `LEANBACK_LAUNCHER` category on the launch intent,
a 320x180dp banner. Built for Fire OS and tested on a real Fire TV as well, not only the
emulator that stood in for it during development.

### The Bedrock narration proxy (optional)

Two places in this app ask a model to turn rule-computed facts into prose: the weekly
caregiver update, and the one-sentence explanation when a session's intensity changes.
Both are fully optional at runtime — every call has a 4-second timeout and a
deterministic fallback, and 134 unit tests including all of `NarrationServiceTest` pass
with no proxy running and no network at all.

To see the AI-personalised path instead of the fallback:

```bash
cd backend
python3 -m venv venv                      # first time only
./venv/bin/pip install -r requirements.txt  # first time only
./venv/bin/python bedrock_proxy.py
```

This listens on `127.0.0.1:8765` and is reached from the Android emulator at
`10.0.2.2:8765` (the standard emulator alias for the host loopback), which is also the
one address this app's `network_security_config.xml` permits cleartext HTTP to — nowhere
else. It picks up AWS credentials from your own `~/.aws` configuration via
boto3's default credential chain; the app itself never holds an AWS credential, since
shipping one inside an Android client is a real vulnerability, not a shortcut. See
`SPEC.md`, "Where a model touches this product," for the full account, including why
neither call is ever allowed to sit in the path of starting a session.

## Project layout

```
steady/
  app/src/main/java/com/steady/app/
    domain/     pure Kotlin: ProgramEngine, AdherenceTracker, WeekCalendar,
                IntensityAdjuster, PausePeriod/PauseTracker, SessionFeedback,
                CaregiverSummaryBuilder, Exercise/ExerciseLibrary, plus
                CatchUpNotice/CatchUpEvaluator (is there a gap) and
                AdherenceNoticePolicy (may we mention it). No Android dependency
                anywhere in this package; every rule that decides what the person
                sees is tested here, on the JVM, with no emulator.
    data/       SteadyRepository (local-only persistence via DataStore
                Preferences — no network, no account), Narrator/BedrockHttpNarrator/
                NarrationService (the optional Bedrock path and its fallback).
    work/       AdherenceCheckWorker, the daily check that runs when nobody is
                looking; SteadyWorkScheduler; AdherenceNotifier, which documents
                what Fire TV's notification support actually is.
    ui/         Compose screens (Setup, Home, Session, History, Settings, Pause),
                the SteadyButton component built for three-channel D-pad focus,
                the SteadyViewModel that wires domain state to the UI, and the
                "bark / ember / moss / linen" theme.
  app/src/test/java/com/steady/app/
    domain/     ProgramEngineTest, AdherenceTrackerTest, IntensityAdjusterTest,
                PauseBehaviorTest, CaregiverSummaryBuilderTest, and for the daily
                check: CatchUpEvaluatorTest, AdherenceNoticePolicyTest and
                CatchUpNoticeWordingTest, all driven by an injected date rather
                than a clock
    data/       NarratorTest, NarrationServiceTest — the model-call fallback logic,
                proven correct with the network call stubbed out entirely;
                SummaryGuardTest, including the rule that an invented day count is
                refused even when a real one exists
  app/src/debug/
    DemoHooksReceiver  debug-only adb hooks (seed history, move the app's clock, set
                       the text size) so the noticing behaviour can be demonstrated
                       without waiting nine days or touching the device clock
  backend/
    bedrock_proxy.py   the local narration proxy described above
```

134 tests, all passing, zero network dependency to run them.

## Noticing a session that did not happen

An adherence programme that only knows anything when somebody opens it cannot tell
anyone that adherence stopped. Steady now runs a check of its own.

**A daily `WorkManager` check.** `AdherenceCheckWorker` runs once a day whether or not
anybody opens the app, reads the recorded session dates, and writes down what it found.
Settings shows the date of the last run, so "this app checks on its own" is something
you can read off the television rather than a claim in a README. The worker is a thin
shell around two pure functions, `CatchUpEvaluator` and `AdherenceNoticePolicy`, both of
which take a date instead of reading a clock — which is why a fortnight of somebody's
programme runs in a unit test in under a millisecond.

**A line on Home when the television comes back on.** This is the part that does the
work. The moment the set is switched on and Steady is opened is the only moment you are
certain the person is looking, and it needs no permission, no channel and no promise
from the platform. The line sits *below* the primary button, in the slot the streak line
already used, so "Start Today's Session" never moves.

> The last session was 6 days ago.
> Today's session is ready when you are.

One press of **Not today** clears it for the rest of the day, and focus returns to the
primary button rather than wandering into the corner.

**A notification, honestly described.** Amazon's own documentation
(`developer.amazon.com/docs/fire-tv/notifications.html`) says Fire TV supports the
standard Android Notification API with two shapes. A *heads-up* notice covers the bottom
of whatever is playing and requires `PRIORITY_HIGH`; Steady does not use it, because
interrupting a film to tell a 78-year-old they have not exercised is the exact failure
this feature is written to avoid. A *standard* notice never interrupts anything and goes
into a Notification Center that lives under the Settings menu. **Steady posts that one,
silent and at default importance, and it is the second channel rather than the
mechanism** — realistically, this app's user will not go looking in that list. At most
one is ever raised per quiet stretch, so somebody who stops for a month is told once and
then left alone.

Amazon publishes no launch-on-boot API for Fire TV. WorkManager re-enqueues its own
persisted work after a reboot, which is the library's behaviour and not a promise from
Amazon, so nothing the person sees depends on the check having run: every number on the
Home screen is still recomputed from the session dates the moment the app opens.

**The weekly family update carries it.** A count of sessions in a week says nothing about
*when* in the week they were, so "2 of 3" and "2 of 3, both nine days ago" used to read
identically to the person two hundred miles away. `CaregiverSummaryBuilder` now adds one
sentence — "The last one was 9 days ago." — computed by `AdherenceTracker` from the
recorded dates and written by the app. A model is told the number and never writes the
observation. `SummaryGuard` accepts that number only when the app actually computed one.

Nothing here scolds. "missed", "failed", "behind", "should" and "streak broken" appear in
no string the person can see, and `CatchUpNoticeWordingTest` checks that across every
sentence the type can generate rather than against a handful of examples.

Screenshots of every state, including the longest notice at the largest text size, are in
`docs/shots/noticing/`.

## What is mocked, and why

The caregiver "Send now" button composes a real summary string from real adherence
numbers — either the deterministic `CaregiverSummaryBuilder` template, or, when the
Bedrock proxy is reachable, a model-narrated version of the same facts — and marks it
sent in local history, tagged honestly on screen as "AI-personalised" or "standard
wording" depending on which one actually ran. It does not place any SMS, email or push
API call. No such integration is wired into this build; nothing about the delivery path
is simulated as if it were real. Making delivery real would mean adding one outbound
call (for example, Amazon SES or a carrier SMS API) at the point
`SteadyViewModel.sendWeeklySummary()` already calls `repository.recordSummarySent(...)`;
the text it would send is already correct today.

## Licence

MIT. Third-party photograph credits: see [`ATTRIBUTION.md`](ATTRIBUTION.md).
