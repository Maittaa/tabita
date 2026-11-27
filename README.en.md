# Tabita

[Русская версия](README.md)

**A personal health and habit diary for Android.** One app instead of several: a daily checklist, food, sleep, workouts, weight and body measurements, wellbeing and spending. Data from your watch and scale comes in automatically, and the app shows what actually affects how you feel.

Version **5.69** · Android 9+ · Russian and English interface (the strength training section is Russian only for now)

**Download:** the latest APK is in [Releases](https://github.com/Maittaa/tabita/releases/latest). It installs as an update over the existing version.

---

## What makes Tabita different

- **Everything in one place.** Food, sleep, steps, heart rate, workouts, weight, habits and spending are recorded in one day and analysed together. No need to combine data from five apps.
- **Minimal manual input.** Sleep, steps, resting heart rate, workouts and weight come from Health Connect and openScale. “Same as yesterday” copies yesterday’s marks in one tap, meal templates add a whole lunch at once, and AI estimates calories from a photo.
- **Compared with you, not with averages.** Hints compare today’s sleep, heart rate and steps with your own usual level: the median of past weeks.
- **Conclusions from your own data.** “What affects the day index” finds on which days your wellbeing index is higher or lower. Mini‑experiments test a before / after hypothesis with a confidence interval, and “Compare” shows how two measures are related.
- **Grounded in research.** Thresholds and formulas come from published studies and guidelines (listed below), not made‑up norms.
- **No guilt.** A missed day does not reset your streak: you get a freeze for every 7 filled days, up to three in stock. After a break the app welcomes you back without blame, and milestones at 14, 30 and 66 days show what the collected data now allows.
- **Your data stays with you.** Everything is stored on the phone. There is a full backup with settings, CSV export, and fingerprint or PIN lock. AI is optional and uses your own key.
- **Flexible interface.** The “Today” screen is built from tiles: size, order and content are up to you.

## Features

### Today: a checklist of tiles
- Tiles of any size on a 4–6 column grid; drag and resize, with an ergonomic default layout.
- Item types: checks, scales, numbers, durations, multi‑option lists, text, water, food, expenses, body measurements, wellbeing, tips, workout, goal, daily rhythm.
- Day marks grouped into sections or kept as one compact set. Any mark can count towards the day index and the workout advice.
- Progress line: what is still missing for the index, what filled in by itself, streak and freezes, and one or two hints on what today’s data means.
- Daily tips by goal and time of day; a daily rhythm with wake‑up, meals, workout and bedtime.

### Food
- A reference of about 180 foods plus your own; a 0–10 food score, glycaemic index and load (GL is calculated automatically).
- Calories and macros from a photo via AI: GigaChat, OpenAI‑compatible services, Anthropic.
- Meal templates with photos and a confirmation before adding; a template list in settings.
- A weekly menu and a shopping list with checkmarks that you can share.
- A dynamic water target; protein absorption per meal.

### A programme for your goal
- Five goals: fat loss while keeping muscle, muscle gain, weight gain, closing gaps (protein, water, calories), and wellbeing (sleep, movement, routine). Calorie and protein targets, menu options.
- **Forecast** of weight and lean mass from a linear trend, with an estimated date for reaching the goal.
- **Refined calorie burn** from your actual weight trend — after 28 days of data.
- **Week:** a workout template by day (strength, cardio, yoga) with a check of how the loads combine, a week review and fixes for the next week.
- Charts for any measure, with values shown under your finger; a monthly summary with the day index and goals.

### Health and training
- **Strength:** a programme generated for home equipment or the gym, workout flow and progress; daily advice based on readiness (sleep, heart rate, wellbeing marks).
- **Cardio:** heart rate zones from heart rate reserve, active minutes, pace, a timer.
- **Yoga:** sequences, your own sequences, self‑checks.
- **Activity:** steps and active minutes for the week.
- **Sleep:** last night, a map of nights, sleep stages, the Sleep Regularity Index (SRI).
- **Body:** weight, tape measurements, waist‑to‑height ratio, before/after photos for comparison.
- **Wellbeing:** a check‑up with standard questionnaires (PHQ‑8, GAD‑7 and others).
- A combined history of all workouts with filters; workouts are written to Health Connect.

### Analysis
- **Day index** — calculated automatically for your goal or from items you choose.
- **What affects the day index:** which marks and habits go with a higher or lower index or day rating on average. Reliable findings are separated from chance ones.
- **Experiments:** before / after for the index, sleep, heart rate, weight and other measures, with a 95 % confidence interval.
- **Compare:** two measures on one chart with a correlation coefficient.

### Motivation and reminders
- One evening reminder — only if the day is not filled in.
- A Sunday week review at 19:00: sleep, activity, weight, protein, one fix and the top finding.
- A recording streak with freezes and a soft “N of 7” streak; a gentle return after a break.
- Milestones at 14, 30 and 66 filled days.
- A bedtime reminder; a reminder to rebuild the training programme.
- A sound for when timers end (rest, timed exercises, cardio and yoga blocks) — any Android sound from the system picker.

### Data and privacy
- All data is stored locally, with format migrations between versions.
- Full backup with settings and restore; diary and workout export to CSV.
- Login protection: biometrics and PIN.
- A reference with scientific explanations behind the tips and targets.

## Data sources

| Source | What comes in |
|---|---|
| **Health Connect** (e.g. Pixel Watch / Fitbit) | sleep with stages and naps, steps, resting heart rate, workouts with heart rate and distance, weight |
| **openScale** (e.g. Xiaomi scales) | weight and body composition |
| **AI with your own key** | calories and macros from a food photo |

## What the calculations are based on

- Hall & Chow, 2011 — weight change and energy balance; refining calorie burn after 28 days, 7,700 kcal per kg.
- Iraki et al., 2019 — weight gain rate of 0.25–0.5 % of body weight per week.
- Eddens et al., 2018; Schumann et al., 2022 — combining strength and cardio (strength first, or at least 3 h apart).
- NICE NG246 — waist‑to‑height ratio: < 0.5 / 0.5–0.59 / ≥ 0.6.
- Phillips et al., 2017; Windred et al., 2024 (UK Biobank) — the Sleep Regularity Index.
- Lally et al., 2010 — a habit takes 66 days on average to form (18 to 254); occasional misses barely matter.
- ISSN, 2017 — protein distribution across meals.
- Statistics: Welch’s confidence interval for experiments, Pearson correlation with Cohen’s thresholds of 0.1 / 0.3 / 0.5.

## Building

Kotlin 2.2 · Jetpack Compose + Material 3 · AGP 8.13 · compileSdk 36, targetSdk 35, minSdk 28 · JDK 21. Tests use Robolectric, including screen checks.

The signing key is not in the repository. Put `keystore/dnevnik.jks` in place and create `keystore.properties` in the root:

```
storeFile=keystore/dnevnik.jks
storePassword=...
keyAlias=dnevnik
keyPassword=...
```

Passwords can also be passed through the environment variables `TABITA_STORE_PASSWORD` and `TABITA_KEY_PASSWORD`.

```
./gradlew assembleRelease   # main build → app/build/outputs/apk/release/
./gradlew assembleFresh     # “Tabita Test”: installs alongside the main app and does not touch its data
./gradlew testDebugUnitTest # tests
```

What’s new in each version — [CHANGELOG.en.md](CHANGELOG.en.md). Development notes (in Russian) — [docs/HISTORY.md](docs/HISTORY.md).
