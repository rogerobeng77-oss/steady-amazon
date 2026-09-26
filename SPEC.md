# Steady — SPEC

## What it is

A falls-prevention strength and balance course, built for the Fire TV remote, for a
person who does not want to learn a new device to use it. Turn on the TV, press one
button, do the routine, turn the TV off. Progression from seated to standing work is
automatic and conservative. A weekly streak lives on the television. A plain-language
weekly update goes to one named family member, so an adult child two hundred miles away
stops guessing whether anything is happening.

## Who it is for

Two people, not one:

1. **The person doing the exercises.** Likely 70s or older, likely living alone, likely
   fall-risk. Already owns and uses a Fire TV. Does not want an account, a login, a phone
   pairing step, or a second device.
2. **The family member who cares whether it's happening.** Does not live in the house.
   Currently has no way to know whether their parent did anything today. This person sets
   the TV up once and then checks in from a text message, not from the TV itself.

## The one thing that carries the demo

Home screen, one button, auto-focused: **Start Today's Session**. Press it. Walk through
five to seven seated exercises with plain cues and a self-paced progress bar. Finish, pick
one of three feelings ("Too much" / "Just right" / "Too easy"). The streak on the home
screen updates. Press "Send now" on the caregiver card and the weekly update is composed
and marked sent. That whole loop is one unbroken run, no login, no camera, no wearable, no
second screen.

## The evidence this is built on

- Falling is not a minor complaint: over 14 million older adults fall every year in the
  US and the age-adjusted fall death rate rose 21% from 2018 to 2024 (CDC). Medical costs
  for older-adult falls ran to $50.0bn in 2015, with Medicare carrying $28.9bn of it
  (Florence et al., J Am Geriatr Soc 2018).
- The intervention this app delivers has high-certainty evidence: exercise programmes
  reduce the rate of falls by 23% (rate ratio 0.77, 95% CI 0.71 to 0.83), from a Cochrane
  review of 59 trials and 12,981 participants graded high-certainty. That review covers
  108 randomised trials in total across 25 countries; the 23% figure is the sub-analysis,
  not the whole review, and the two numbers should not be quoted together (Sherrington et
  al., Cochrane Database Syst Rev 2019).
- The variable that decides whether it works is adherence, not the exercise itself.
  Programmes reaching 75%-plus adherence saw a far larger reduction in fall incidence
  (IRR 0.501) than the pooled effect across all adherence levels (IRR 0.776) (Hua et al.,
  Arch Gerontol Geriatr 2026). That 75% figure is why the seated-to-standing progression
  in this app only unlocks after two full weeks at 75% of the prescribed dose, and why a
  single fully-missed week drops the tier back down rather than assuming nothing changed.
- The enforcement record sits on both sides of this problem: the CPSC's $19,065,000 civil
  penalty against Peloton over the Tread+ entrapment hazard, and the FTC/Florida Attorney
  General action that shut down a robocall operation selling "free" medical alert systems
  to fall-risk seniors, plus the related $1.8m in Lifewatch refunds. Steady sells nothing,
  monitors nothing, and makes no medical claim; it exists because the two sides of this
  market that do sell something have both been fined for it.
- The demand signal is thin but real and asymmetric: in 200 posts across r/AgingParents
  and r/eldercare coded for this research, nineteen people were shopping for a device to
  tell them their parent had already fallen, against five asking about exercise that would
  prevent the fall in the first place. That imbalance, not a Fire TV fitness community
  that barely exists, is the argument for building this.

## What it deliberately does not do

- **No camera, ever.** No Fire TV device ships with one, and no developer camera API was
  verifiable from Amazon's own docs. Nothing in this app infers whether an exercise was
  done correctly, whether the person is still in frame, or whether they are moving at all.
  The only signal Steady has is a remote press.
- **No form correction, no pose feedback.** That is a camera-shaped problem and this is
  not a camera-shaped product.
- **No account, no cloud sync, no login.** Everything is stored locally with DataStore.
  There is nothing to hack, leak, or forget the password to.
- **No real message sending.** "Send now" on the caregiver card composes the real summary
  text using the real adherence numbers (optionally narrated by Bedrock, see below) and
  marks it sent in local history. It does not place an SMS or email API call, because none
  is wired into this build. This is mocked at the boundary, honestly: the generation logic
  is real and tested; the delivery is not. See the README for exactly what would need to
  change to make delivery real.
- **No diagnosis, no medical claim, no "prevents your fall."** The Cochrane number is a
  population-level rate reduction across supervised and unsupervised trials, not a
  guarantee, and the app never states otherwise.

## Where a model touches this product, and where it never does

Amazon Bedrock (`us.anthropic.claude-sonnet-4-5-20250929-v1:0`, us-east-1) is called in
exactly two places, and both follow the same rule: **the rules decide, the model only
describes.** Nothing a model can output ever changes which exercise a person sees, what
tier they are on, or what the adherence numbers say. That decision path
(`ProgramEngine`, `AdherenceTracker`, `IntensityAdjuster`) is pure, deterministic Kotlin
with no model in it anywhere, on purpose: this is a falls-prevention app, and a model that
could hallucinate its way into recommending a standing exercise to someone who is not
ready for one is not an acceptable risk. A safety-relevant decision needs a rule you can
read, not a model you can prompt.

1. **The weekly caregiver update.** `CaregiverSummaryBuilder` already computes the exact
   facts (session count, streak, tier). `NarrationService` hands those facts, and nothing
   else, to Bedrock and asks it to write two or three warm sentences. If Bedrock answers
   in time, that text is used and tagged "AI-personalised" on screen. If it does not
   (unreachable, slow, empty response), the deterministic template from
   `CaregiverSummaryBuilder` is used instead and tagged "standard wording" instead, with no
   visible failure and no retry the person has to sit through.
2. **The "why does today look different" line.** When the person's last feedback
   ("Too much" / "Just right" / "Too easy") caused `IntensityAdjuster` to change today's
   session, a one-sentence explanation appears under the Start button. The deterministic
   version appears immediately, always, with no network dependency
   (`IntensityAdjuster.fallbackExplanation`). If Bedrock answers in time, that sentence is
   swapped in in place. Nobody waits for a network call before they can start their
   session.

**Architecture, and why it is not a client calling Bedrock directly.** Steady is a native
Android app with no server of its own. An Android client that embedded a long-lived AWS
secret key to call Bedrock directly would be shipping a real vulnerability, not a
shortcut, the first time this repository became public. `backend/bedrock_proxy.py` is a
small local proxy that holds the AWS credentials on the machine that owns them and gives
the app a plain HTTP endpoint (`10.0.2.2:8765` from the emulator) to call instead. A real
deployment would replace it with a small managed backend; the contract from the app's side
(POST facts, get prose back, four-second timeout, fall back to the deterministic text)
would not change.

**What makes this an honest use of a model and not decoration:** every model call has a
timeout (`withTimeoutOrNull`, 4 seconds), a deterministic fallback that is never a loading
spinner or an error message, and a unit test (`NarrationServiceTest`) that proves the
fallback branch works correctly with the network call stubbed out entirely, so the app's
correctness never depends on Bedrock, or even network access, being available during a
demo.

**Neither use ever sits in the path of the exercise itself.** `SteadyViewModel.deriveState`
computes today's exercises with `IntensityAdjuster`, a pure function, before any narration
call is even considered; a person standing in their living room pressing "Start Today's
Session" is never waiting on a network round trip to do that. The adjustment banner shows
its deterministic sentence the instant the tier and dose are known and only swaps in
Bedrock's version afterward, in place, if it answers in time. The weekly summary is
narrated out of band entirely, on an explicit "Send now" press, never on the session path.
If Bedrock is unreachable for the whole session, the only visible difference is that the
banner and the summary read in plain rule-generated sentences instead of narrated ones;
nothing is blocked and nothing errors.

The proxy tries a short preference list of model ids
(`us.anthropic.claude-sonnet-4-5-20250929-v1:0`, then `us.anthropic.claude-sonnet-4-6`)
rather than one hard-coded id, because `anthropic.claude-sonnet-5` and
`anthropic.claude-opus-5` list as available on this account's Bedrock console but return
`AccessDeniedException` from `InvokeModel`; a model that lists but cannot actually be
invoked should fall through to the next preference, not take the feature down.

## Day-two depth

A person who has used Steady for three weeks needs things a first-time demo does not
show:

- **History.** Every completed session date, most recent first, reachable from a quiet
  button in the top corner of Home. Not a streak number to trust, the actual dates.
- **Settings.** The only place to fix a wrong family member name after the one-time setup
  screen. Setup happens once, under time pressure, often by someone who is not the person
  the summary is about; getting it wrong and having no way to fix it short of reinstalling
  is the kind of thing that reads as an oversight to an experienced reviewer.
- **Adaptive intensity.** "That was too much" now changes tomorrow's session
  (`IntensityAdjuster`): shorter durations, one fewer exercise if there were enough to
  spare, always within the same tier, always with hard floors and ceilings, always
  reported back to the person in one sentence. Before this, feedback would have been a
  number sitting in local storage nobody read; now it is the one thing in the app that
  responds to what a person actually says about their own experience.
- **A weekly dose indicator for the person, not just the family member.** Filled and empty
  dots under the tier label show sessions done against sessions prescribed this week,
  without requiring the person to read a sentence or check a family member's phone to know
  where they stand.

## Design

**Shell:** a single, near-empty screen with one auto-focused button. No menu, no tab bar,
no side rail, no settings icon competing for the D-pad. A second, quieter card for the
caregiver update sits below the fold, reachable with one press down, and is not part of
the daily path. The session itself is a single full-screen exercise at a time with one
button, "Done, next," always focused: the person controls the pace, never a countdown.
This shell is deliberately barer than anything else in this hackathon's inventory
(left sidebars, top navs with metrics strips, centred-hero card grids): the objection
that the person who needs this app is the least likely to install or navigate one only
has an answer if there is nothing here to get lost in.

**Palette**, named in words per the house rule, checked against every project already
listed in an internal survey of this batch's other apps' screens:
- **Ground: "bark,"** a deep warm brown-black. Every other dark ground already in use in
  this hackathon leans purple or navy (a dusk purple, a plum-black, a navy). Bark is warm
  brown instead, and it is dark on purpose: a ten-foot living-room screen, watched by
  older eyes, benefits from less glare and higher contrast than a light background would
  give it, and a warm ground does not fight with the skin tones of a person standing in
  front of their own television mid-exercise.
- **Accent: "ember,"** a muted gold, used for exactly one thing at a time: whatever is
  focused. Every other warm accent already in use here (a cartridge pink, a coral, an
  apricot, a bright orange) sits closer to red or pink; ember sits in gold, which reads
  calm rather than urgent, on purpose, since nothing in this app should ever feel like an
  alarm.
- **"Moss,"** a quiet green, is reserved for exactly one meaning: the streak is alive and
  the current week is on track. It never appears as a button or a focus ring, only as a
  status. No other project in the inventory uses green as an accent.
- **"Linen,"** a warm off-white, is text only, never a ground, which keeps this palette
  clear of the several warm-off-white *grounds* already used elsewhere (a cream, a warm
  sand, a blush).

**Type:** a ten-foot scale. The smallest text on any screen (secondary labels) is 22sp;
the primary button label is 30sp; the home screen title is 56sp. Nothing on this app was
sized for a phone and then left alone.

## The two objections, faced honestly

**"YouTube is free, already on every Fire TV, and full of senior strength-and-balance
videos."** This is true and the research corpus proves it: one of the five preventive
posts found in the caregiving-subreddit research was someone linking exactly such a
video. The honest answer is not that Steady's content is better than a free video; it
probably is not. The honest answer is that a video has no progression, no record, and no
way for anyone else to know it happened. A person can watch the same 40-minute video
every week for a year, or once, or never, and YouTube's interface cannot tell the
difference, cannot tell the person which exercises are safe to try standing yet, and
cannot tell an adult child two hours away whether anything happened this week. Steady is
a bet that the missing piece was never the exercise content, it was the adherence
machinery around it. That is a product argument, not an evidence argument, and it has to
be won in the demo, not asserted here.

**"The person who needs this is the least likely person alive to install it."** This is
also true, and this spec does not pretend the app solves it. Every technology-delivered
falls-prevention trial in the evidence base needed to hand-hold participants onto the
platform: supply the tablet, supply the internet access, supply the training. A hackathon
build cannot manufacture a support relationship that does not exist. What this app can
do, and does, is refuse to add friction on top of an existing one: it assumes a Fire TV
that is already turned on, already in the living room, already the device this person
uses for something else every day, and it puts exactly one new decision in front of
them ("press this button") rather than several (open an app store, create an account,
pair a phone, learn a new remote gesture). The setup screen, the only screen with a text
field anywhere in this app, is written for the family member, not for the person doing
the exercises, and says so on screen. If nobody sets it up, Steady does not work. That is
stated here rather than hidden behind a demo that only shows the happy path.

## What "conservative progression" means, concretely

- New program: chair-supported exercises only.
- Standing exercises unlock only after two consecutive fully-elapsed weeks each meeting
  75% of the prescribed three-sessions-a-week dose (i.e., all three sessions in both
  weeks, since 75% of three rounds up to three).
- A single fully-elapsed week with zero sessions drops a standing-tier person back to
  chair-supported, regardless of how long they had been standing before. The logic never
  looks at the week still in progress, only at weeks that have fully finished, so nobody
  is downgraded mid-week for not having gotten to today's session yet.

This logic lives in `ProgramEngine`, `AdherenceTracker` and `WeekCalendar`
(`app/src/main/java/com/steady/app/domain/`) as pure, dependency-free Kotlin, and is unit
tested directly (see README).

## Pausing without penalty

The regression rule above has one real failure mode: the most likely reason for a
fully-empty week in this population is not motivation, it is a hospital stay, an
illness, or a trip to see family, and a rule that cannot tell the difference punishes
the person for the event the whole app exists to prevent. "Pause" (`I'm away` /
`I'm unwell`, one press, no typing) fixes this directly rather than as an afterthought:

- A paused week is *excluded*, not treated as zero. `PauseTracker` turns every paused
  span into a set of week indices; `ProgramEngine` and `AdherenceTracker` both skip
  those weeks entirely when deciding a tier or a streak, so a pause can neither regress
  a tier nor break a streak, no matter how many weeks it covers.
  (`PauseBehaviorTest` proves both halves of this directly, including the case where the
  same history *without* the exclusion would have regressed.)
- The tier is held, never dropped, for the duration.
- On "Resume," the first session back is automatically eased in
  (`IntensityAdjuster.welcomeBackAdjustment`, the same shortening `IntensityAdjuster.adjust`
  applies for "that was too much"), at the same tier as before, with a plain sentence on
  Home saying so. Nobody returns from a pause to find the programme resumed as if nothing
  happened, and nobody returns to find themselves demoted for having been unwell.

## Where a model may not go: intelligence and the ten-foot interface

Two things came directly from review, and both are load-bearing enough to say
explicitly rather than leave implicit in the code:

**Neither Bedrock use may ever sit in the exercise path.** A person standing in their
living room pressing "Start Today's Session" is never waiting on a network call: the
exercises and the tier are decided by pure rule functions before any narration is even
considered, and both narration calls (the caregiver summary, the intensity-change
sentence) either run out of band on an explicit button press or upgrade an
already-correct deterministic sentence in place if they answer in time. See "Where a
model touches this product" above for the full account.

**A ten-foot interface has its own physics, not phone habits shrunk down.** This build
was reviewed against the shared Fire TV craft reference, a reference derived from Amazon's own
Fire TV documentation, Apple's tvOS guidelines and arithmetic anyone can redo: at three
metres on a 50-inch panel, a 13sp phone-habit caption subtends about 12 arcminutes,
well under the 16-arcminute reading floor, and a 2dp focus ring is 2.6 arcminutes,
technically visible and practically invisible. Applied here:

- **Type.** Six steps, none of them a phone size: Display 56sp, Title 40sp, Heading
  32sp (also the button-label size), Lead 28sp, Body 28sp and Meta 24sp, Body and Meta
  both raised a full step above the reference's generic floor because this app's
  audience skews older and presbyopia is the default in that audience, not an edge
  case. Nothing on any screen is smaller than Meta.
- **Focus.** Every focusable control signals focus on three independent channels at
  once — an 8dp near-white ring (geometry), a 1.1x grow matching `tv-material3`'s own
  button default (scale), and a brighter container colour (luminance) — so it survives
  an off-axis seat, a washed-out panel or a colour-blind viewer, and transitions run in
  120ms so they never visibly lag a held D-pad direction (key repeat is roughly 50ms).
- **Initial focus.** Every screen sets it deliberately rather than letting the system
  choose. This took two attempts: a plain `LaunchedEffect(Unit)` calling
  `FocusRequester.requestFocus()`, the pattern shown in most Compose-for-TV sample
  code, lost the race against Android's own default-focus-on-attach dispatch often
  enough to be a real bug (see friction log #11). Requesting focus from
  `Modifier.onGloballyPositioned` with a short deliberate delay fixed it, confirmed by
  driving the emulator with `adb shell input keyevent` end to end and never a
  coordinate tap.
- **Back.** Goes up one level from every screen but Home; Home has no handler, so the
  system default (exit to the Fire TV launcher) applies there, which is also this
  app's fixed start destination.
- **Content descriptions.** The weekly dose indicator (a row of plain dots) carries a
  merged `contentDescription` stating the count in words, since a screen reader riding
  the focus system, which is what Fire OS's VoiceView does, cannot read a colour.

**What this pass did not reach, said plainly rather than left to be discovered:** no
device pass with VoiceView actually switched on; no in-app text-size control, which
matters because Fire TV has no system one; `Settings.Global.ANIMATOR_DURATION_SCALE`
(reduced motion) is not read anywhere; and `fontScale` above 1.0 has not been tested
against this layout. All three are real gaps against the shared Fire TV craft reference's
55-item checklist, not implemented in this pass, and are the first things a next day
should pick up.

## Licence

MIT. See README.
