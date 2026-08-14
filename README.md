# Word Battle

A Kotlin/Jetpack Compose word game with fully offline computer and pass-and-play modes, plus Supabase-backed authentication, rooms, realtime multiplayer, friends, profiles, and leaderboards.

## Stack

- Kotlin only; Jetpack Compose UI (no XML layouts)
- MVVM with `MainViewModel` + `StateFlow`
- Room 2.8.4 local cache
- Supabase Kotlin 3.7.0: Auth, PostgREST, and Realtime
- Credential Manager Google sign-in
- Min SDK 24, compile/target SDK 37
- Gradle Kotlin DSL

## First-time setup

### 1. Android Studio

Open the repository in a current Android Studio release with Android API 37 and JDK 17+ installed. Let Gradle sync, then run the `app` configuration.

### 2. Google sign-in

Create both Android and Web OAuth clients as described by Google/Supabase. Add the **Web OAuth client ID** to an untracked `local.properties` file:

```properties
sdk.dir=/path/to/Android/sdk
GOOGLE_WEB_CLIENT_ID=000000000000-example.apps.googleusercontent.com
```

Enable Google under Supabase Authentication providers and enter its client ID/secret. The application uses Credential Manager, creates a SHA-256 nonce, and exchanges the native Google ID token with Supabase Auth.

Email/password and offline guest entry are also available on the login screen.

### 3. Supabase database

Run [`supabase/schema.sql`](supabase/schema.sql) manually in the Supabase SQL Editor. It includes:

- all five required tables;
- constraints and RLS policies;
- the host `room_slots` INSERT policy needed by room creation;
- a host-only `games` INSERT policy; and
- Realtime publication for `profiles`, `rooms`, `room_slots`, and `games`.

The app contains only the supplied publishable key. **Never add a `service_role` key to this repository or an Android client.**

### 4. ENABLE dictionary

The complete 172,823-word ENABLE1 list is bundled at:

```text
app/src/main/assets/dictionary/enable1.txt
```

Words are loaded once on a background dispatcher into an uppercase `HashSet`. A small development fallback still protects local development if the asset is accidentally removed.

### 5. Signed GitHub APK

Follow [`docs/RELEASE_SIGNING.md`](docs/RELEASE_SIGNING.md). The GitHub workflow requires `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`, builds only the release APK, verifies its signing certificate, and fails instead of using any fallback keystore.

## Build and test

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Core tests cover contiguous horizontal/vertical detection, cross words, blocked overwrites, one-letter moves, case-insensitive global used words, ranking/finish behavior, invalid turns, and offline AI move selection.

## App flow

```text
Splash → Supabase session check → Login or Main shell
Main shell → Home / Rank / Friends / Profile
Home → Computer, 2P, 3P, or 4P
  Computer → local game
  friend modes → local/online seat assignment
    all local → pass-and-play
    any online → host room / join room → realtime lobby → game
Game → rankings assigned as target score is reached → Results
```

Computer gameplay and pass-and-play never call Supabase during the match. `WordEngine`, `ComputerAI`, and `GameRepository` contain no remote SDK dependency. The AI exhaustively simulates A–Z on eligible cells, favors scoring moves adjacent to existing letters, and falls back to common letters.

## Online behavior

- Join validates the real room code and passcode. It never creates a fallback room.
- Full and started rooms return explicit errors.
- Host start remains disabled until every online slot is filled and ready.
- Room slots, room status, board, players, score, used words, turn, and rankings are synchronized through filtered Realtime channels.
- A turn has a 45-second timeout. The host advances timed-out online turns; the owning local device can also skip manually.
- Realtime channels are removed when leaving lobby/game screens.

## Source layout

```text
com.wordbattle.com/
  data/
    model/        serializable domain models
    repository/   offline game, auth, users, rooms/realtime
    dictionary/   ENABLE loader
    game/         WordEngine and ComputerAI
    local/        Room database, DAOs, cache entities
    remote/       Supabase provider and wire DTOs
  ui/
    screens/      screen composables
    components/   shared tiles, cards, buttons, bars, toasts
    theme/        colors, bundled Baloo 2/Nunito typography
    navigation/   state-driven app graph
    MainViewModel.kt
  MainActivity.kt
```

## Security note

The supplied `games` update policy intentionally starts permissive for authenticated players, matching the requested bring-up plan. Before a public production launch, tighten it to verify that `auth.uid()` occurs in the row's `players` JSON and add server-side transactional move validation (RPC/Edge Function) to prevent modified clients from submitting illegal state.
