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

Google is the primary login button. An expandable email/password section sits below it: there is no separate register mode, the app signs in and transparently creates the account when it does not exist yet. Offline guest entry is also available.

### 3. Supabase database

Run the SQL files manually in the Supabase SQL Editor.

| File | When to run it | What it does |
| --- | --- | --- |
| [`supabase/schema.sql`](supabase/schema.sql) | New project (contains everything below) | Tables, constraints, triggers, RLS policies, grants, indexes, Realtime publications |
| [`supabase/room_creation_fix.sql`](supabase/room_creation_fix.sql) | Existing project with room creation/join problems | Host `rooms` INSERT/UPDATE/**DELETE** policies, `room_slots` INSERT policy, "claim an empty online slot only" join policy, corrected ready-update policy, missing grants, Realtime publications |
| [`supabase/update_username_cooldown.sql`](supabase/update_username_cooldown.sql) | Existing project without usernames | Globally unique lowercase `profiles.username`, `display_name_updated_at`, and the trigger enforcing the 10-day display-name cooldown |

Both fix scripts are idempotent and can be re-run safely.

Key points these policies guarantee:

- room slots are inserted **without** an `id`, so `gen_random_uuid()` generates it;
- the host can delete a room whose slots failed, so no ghost lobbies remain;
- a joiner can only claim a slot that is still empty *and* belongs to the online slot range;
- ready flags can only be toggled on the caller's own slot;
- a display name can only change once every 10 days, enforced in Postgres as well as in the app.

The app contains only the supplied publishable key. **Never add a `service_role` key to this repository or an Android client.**

### Troubleshooting: Could not create player seats

If online room creation fails with `Could not create player seats, so the room was cancelled.` there are two possible causes:

**a) App bug (now fixed) — PGRST102 bulk insert mismatch**

`RoomRepository.createRoom()` inserts all seats as a single JSON array into `room_slots`. PostgREST rejects a bulk insert when objects don't share exactly the same keys: error `PGRST102 "All object keys must match"`.

`NewRoomSlotDto` previously had defaults (`filled_by = null`, `filled_by_name = null`, `is_ready = false`) and Supabase's Kotlin serializer uses `Json { encodeDefaults = false }`, so any property left at its default is omitted from the JSON:

- host seat (index 0) → 5 keys (`room_id`, `slot_index`, `filled_by`, `filled_by_name`, `is_ready`)
- local seat → 4 keys (`filled_by` omitted)
- empty online seat → 2 keys (`room_id`, `slot_index` only)

Every valid shape (1+1, 2+1, 1+2, 3+1, 2+2, 1+3) produced a different key set, so PostgREST rejected all modes and the repository rolled the room back. Fixed by removing defaults from `NewRoomSlotDto` (no `= null` / `= false`) and always passing five explicit arguments in `createRoom()`:

- `index == 0` → `filledBy = uid`, `filledByName = displayName`, `isReady = true`
- `index < localSlots` → `filledBy = null`, `filledByName = "Local Player ${index+1}"`, `isReady = true`
- otherwise → `filledBy = null`, `filledByName = null`, `isReady = false`

A regression test `NewRoomSlotDtoTest` encodes with `Json { encodeDefaults = false }` and asserts uniform keys, explicit nulls for empty seats, no `id` key, and coverage of all room shapes.

**b) Missing `room_slots` INSERT policy**

Early database installs had no INSERT policy on `room_slots`, so the room row was created but slot inserts were denied by RLS. The symptom is the same error message.

Solution: run `supabase/room_creation_fix.sql` (or the full `supabase/schema.sql` on a new project) in the Supabase SQL Editor. The file is idempotent and creates:

- `Host can create room slots` INSERT policy
- `Host can delete own room` DELETE policy (lets the app roll back ghost lobbies)
- `Player can claim an empty online slot` join policy (only empty online seats)
- corrected ready-update policies, missing grants, and Realtime publications

Optionally run `supabase/verify_setup.sql` — the `room_slots INSERT policy` row should show `OK`.

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

Core tests cover contiguous horizontal/vertical detection, sub-words inside a longer run, cross words, blocked overwrites, the per-letter point, case-insensitive global used words, ranking/finish behavior, invalid turns, offline AI move selection, and English/Bengali string parity.

## Scoring rules

- Placing a letter always pays **1 point**, even when it forms nothing.
- If the placement completes a valid dictionary word, the player also earns **1 point per letter of that word** (so `CAT` pays 3 on top of the placement point).
- A word counts as long as it is a contiguous horizontal or vertical run through the new cell, **including a sub-segment of a longer run**: dropping `T` after `…BDOCA` scores `CAT`.
- Both axes are scored, so a cross placement can bank two words at once. Inside one axis the longest still-unused dictionary word wins.
- A word can only be scored once per match (case-insensitively). Repeating it still pays the placement point and shows a warning.
- First player to **100 points** is ranked; play continues until only one player is unranked.

## Sound and win animation

All audio is generated offline by [`tools/generate_sounds.py`](tools/generate_sounds.py) (Python standard library only, no downloads) into `app/src/main/res/raw`:

| File | Used for |
| --- | --- |
| `music_theme.wav` | looping battle theme, started when a game opens |
| `snd_letter_place.wav` | every letter dropped on the board |
| `snd_word_scored.wav` | a new word was formed |
| `snd_timer_tick.wav` | last 10 seconds of a turn |
| `snd_timer_warning.wav` | last 5 seconds of a turn |
| `snd_victory.wav` / `snd_defeat.wav` | end of the match |

`SoundManager` (`data/audio`) loads the effects into a `SoundPool` and the theme into a looping `MediaPlayer`. It lives in `AppContainer`, so it survives configuration changes, and the Profile sound toggle mutes everything instantly. The results screen pairs the victory fanfare with `WinCelebration`: falling confetti, rotating light rays, a popping trophy, and staggered standings rows.

Re-running the generator is safe and idempotent:

```bash
python3 tools/generate_sounds.py
```

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
    audio/        SoundManager and the GameSound catalogue
    game/         WordEngine and ComputerAI
    local/        Room database, DAOs, cache entities
    remote/       Supabase provider and wire DTOs
  ui/
    screens/      screen composables
    components/   shared tiles, cards, buttons, bars, toasts, win celebration
    theme/        colors, bundled Baloo 2/Nunito typography
    navigation/   state-driven app graph
    MainViewModel.kt
  MainActivity.kt
```

## Connectivity

`NetworkConnectivityObserver` wraps `ConnectivityManager` + `NetworkCallback` and exposes a `StateFlow<Boolean>`. Everything that needs the internet — Google login, email login, Create Room, Join Room, Ready, Rank, Friends — is guarded by it. When the device is offline the app shows a dialog with **Retry** and **Network settings** instead of failing silently. If an online match loses the link, a reconnecting banner appears and the Realtime room/game subscriptions are rebuilt automatically once the connection returns.

Computer mode and fully local pass-and-play never touch the network and keep working in airplane mode.

## Online turn ownership

Only the device that owns `current_turn_player_id` can use the rack and the board. Every other device is read-only and shows "Waiting for <player>". The host device drives its own local seats, so a 2-local + 1-online room still plays correctly. Room and game status are synchronized through Realtime.

## Profiles, usernames and language

- The first login opens an identity screen asking for a display name (3–20 characters) and a globally unique username (3–20 lowercase letters, digits, or underscore).
- Friend search matches usernames server-side.
- Display names can only be changed once every 10 days; the profile screen shows the remaining days and Postgres rejects earlier attempts.
- Every user-facing string lives in `res/values/strings.xml` (English) and `res/values-bn/strings.xml` (Bengali). Switching the language in Profile calls `AppCompatDelegate.setApplicationLocales`, which applies instantly and is persisted through `AppLocalesMetadataHolderService`.

## Security note

`games` rows can now only be updated by the host or by a player seated in that room (`is_room_participant`). Turn ownership itself is still enforced on the client. Before a public production launch, tighten it further to verify that `auth.uid()` really owns `current_turn_player_id` and add server-side transactional move validation (RPC/Edge Function) so a modified client cannot submit illegal state.

The app ships with the publishable (anon) key only. `SupabaseConfig` refuses configurations that look like a `service_role` key, and no service key belongs in this repository.
