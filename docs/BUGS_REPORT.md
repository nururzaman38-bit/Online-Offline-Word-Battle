# Word Battle — Bug / Incomplete-Feature Report

Static code review of `main` @ `4cef267` (branch `arena/01a01fc7-online-offline-word-battle`).
No build/emulator available in the sandbox, so everything below is from reading the source.
File paths are relative to the repo root; line numbers are approximate.

## Severity legend
- 🔴 High — breaks a core flow or makes a documented feature not work
- 🟠 Medium — works but wrong/annoying
- 🟡 Low — cosmetic / dead code / edge case

---

## 1. Campaign / Puzzle mode (matches "works a few steps, then stops")

### 🔴 1.1 Puzzle "wrong guess" fires on *incomplete* lines → lives burn out instantly
`app/src/main/java/com/wordbattle/com/data/game/PuzzleEngine.kt:135-149` (`isWrongGuess`)

`extractRunContaining()` only returns the filled, contiguous segment around the placed
cell — it does **not** check whether the whole puzzle line is complete. So any 2-letter
*partial* of a longer word that isn't itself a word is treated as a wrong guess:

- Grid `C _ _` (level 25 sample): place `A` in the middle → run `"CA"` → not a word → **life consumed**,
  even though the line is unfinished and the final word `CAT` is correct.
- Grid `_ _ _`: place `C`, then `A` → `"CA"` invalid → life lost before `T` is ever placed.

With only 3 lives this locks a player out of a puzzle ("Out of lives" bottom sheet) after
one or two valid attempts. The comment says "only count as wrong if the run is completely
filled", but the code cannot know that. Fix: only flag wrong when the run is complete
w.r.t. the puzzle definition (no BLANK left in the segment between blockers), and also
allow the current cell to be overwritten without a life cost when the line is not finished.

### 🔴 1.2 `turnTimeSeconds = null` means "unlimited" per the level data, but code uses 45 s
`app/src/main/java/com/wordbattle/com/data/game/CampaignLevels.kt:43-44` (levels 1–2 have
`turnTimeSeconds = null`, comment says "null = unlimited")
`app/src/main/java/com/wordbattle/com/ui/MainViewModel.kt:837`:

```kotlin
val timeout = if (game.mode == GameMode.CAMPAIGN_SCORE) {
    uiState.value.selectedLevel?.turnTimeSeconds ?: TURN_TIMEOUT_SECONDS   // null → 45s!
}
```

Levels 1–2 therefore auto-skip the player's turn after 45 s instead of being unlimited.

### 🟠 1.3 The 45 s timer also runs on the AI's campaign turn — and the human can skip the AI's turn
`MainViewModel.kt:715-718` (`skipCurrentTurn` allows campaign regardless of ownership) plus
`MainViewModel.kt:852-855` (timer calls `skipCurrentTurn()` when `remaining == 0` and
`mode == CAMPAIGN_SCORE`). Waiting out the AI's turn skips the AI for free.

### 🟠 1.4 "Play Again" after a completed puzzle goes to Home, not back to the puzzle
`MainViewModel.kt:757-793` (`playAgain`): puzzles never set `uiState.game`, so the branch
`rootScreen == PUZZLE_GAME` is never true on the results screen (root is `RESULTS`) and
`game == null` sends the player home. The puzzle should restart.

### 🟡 1.5 Locked level shows generic "Something went wrong"
`MainViewModel.kt:341-344` — tapping a locked level toasts `error_unknown`; should say "locked".

### 🟡 1.6 Star bookkeeping mismatch on replay
`MainViewModel.kt:553-560` — remote keeps best stars but the local `campaignProgress` list is
overwritten with the *new* (possibly lower) stars. Also `onCampaignLevelCompleted` shows a
hardcoded English toast ("Level X cleared! …★").

---

## 2. Online / room mode

### 🔴 2.1 Nobody can advance a stalled online turn — the game freezes mid-match
`MainViewModel.kt:715-723` (`skipCurrentTurn` returns early unless the *current* player's seat
is owned by this device). The README states "The host advances timed-out online turns", but:
- the host's timer only auto-skips owned seats (`MainViewModel.kt:852-855`),
- the host's Skip button also refuses non-owned turns.

If the remote opponent loses connection mid-turn, no one can skip → the room is stuck forever.

### 🟠 2.2 Turn timer is purely local and never synced
`MainViewModel.kt:830-861` — the joiner's device starts each turn at a fresh 45 s regardless of
how long the owner already used; timers drift and sounds fire on devices that don't own the turn.

### 🟠 2.3 Assignment screen seat choice is ignored
`MainViewModel.kt:262-264` — `continueAssignment()` only uses the *count* of online seats
(`assignmentPlayerCount - onlineSlots.size`); the exact seats toggled online in the UI are
discarded. The room always makes the first `localCount` seats local. The UI implies per-seat
choice.

### 🔴 2.4 Friend "Invite" and game invites are non-functional
- `MainViewModel.kt:974-977` (`inviteFriend`) just opens a quick room + a toast; the friend is
  never notified and no room code is sent.
- `MainViewModel.kt:1088-1104` (`acceptGameInvite`) is an explicit placeholder — it never joins
  the room.
- `RequestRepository.sendGameInvite()` (`app/.../RequestRepository.kt:107`) is never called from
  anywhere in the app.

### 🔴 2.5 LIFE request gives the life to the wrong person
`supabase/schema.sql` / `supabase/campaign.sql` — trigger `handle_life_request_accepted`:
```sql
update profiles set lives_current = least(lives_max, lives_current + 1) where id = new.receiver_id;
update profiles set coins = coins + 10 where id = new.sender_id;
```
The requester is the **sender** ("Ask a friend for life" → `sendRequest(LIFE, uid, friendId)`).
So the friend who accepts gets the +1 life and the person who needed it gets +10 coins —
exactly backwards.

### 🟠 2.6 "Ask a friend for life" always targets the first friend (or silently does nothing)
`app/.../ui/navigation/NavGraph.kt:190-192` — no picker; with no friends the sheet just closes.

### 🟡 2.7 Presence isn't restarted after reconnection
`MainViewModel.kt:151-169` — `resubscribeRealtime()` re-subscribes rooms/games but never calls
`startPresence()`, so online badges on the friends list stay stale after a reconnect.

---

## 3. Sound / win handling

### 🟠 3.1 Defeat sound never plays; victory always plays
`MainViewModel.kt:882`:
```kotlin
val didWin = game.players.any { ... } || game.status == GameStatus.FINISHED
```
`|| game.status == FINISHED` makes `didWin` always true when the game is over. The Results
screen uses the correct `MainUiState.didWinCurrentGame`, so you see "you lost" but hear a
victory fanfare.

### 🟡 3.2 Theme ducking is dead code
`SoundManager.setThemeVolume()` / `THEME_DUCKED_VOLUME` (`app/.../audio/SoundManager.kt:89,122`)
are never called — the battle theme plays at full volume over the fanfare.

---

## 4. Identity / first login

### 🟠 4.1 New users cannot set a display name on first login (10-day cooldown fires immediately)
The Postgres trigger `enforce_profile_identity_rules()` sets `display_name_updated_at = now()`
on INSERT (`supabase/update_username_cooldown.sql:60-64`). Both the client
(`ProfileRules.canChangeDisplayName`) and the trigger then block the very first display-name
save. `IdentityScreen.kt:58` locks the name field (`displayNameLocked`) on first login.
README says the first login asks for a display name — it effectively doesn't.

---

## 5. Minor / polish

- 🟡 `GameRepository` timer job keeps running after a game finishes (silent failed `skipTurn`,
  `turnSecondsRemaining` updates on the results screen) — cancel the timer when status changes.
- 🟡 `enterLocalGame` has a pointless ternary (`MainViewModel.kt:601`).
- 🟡 `ComputerAI.chooseMove` is O(cells × 26 × segments) — ~2–3 M operations per AI turn
  mid-game; may stutter on old phones (`app/.../data/game/ComputerAI.kt:23-44`).
- 🟡 Many screens hardcode English strings instead of using `strings.xml`
  (Friends, MessageThread, PuzzleGame, Profile "Campaign Progress", Results toasts, LivesBottomSheet…)
  — breaks the English/Bengali promise.
- 🟡 `buyLife` shows generic "something went wrong" for "not enough coins".

---

## What is solid (no issues found)
- Offline Computer + pass-and-play (2–4P): game creation, scoring, ranking, used-word
  de-dup, cross-axis scoring, blocked cells — well covered by unit tests.
- Word placement / scoring engine (`WordEngine`, `GameRepository`).
- Room create/join/ready flow and the PGRST102 bulk-insert fix (documented + regression test).
- Connectivity observer, offline dialog, reconnect banner and resubscribe logic.
- Language switching mechanism and English/Bengali string parity (235/235, placeholders match).
- Dictionary loading with fallback; release signing workflow.

---

## Fix log (this branch)

Implemented in this working tree on top of the report above:

| # | Issue | Fix |
| --- | --- | --- |
| 1.1 | Puzzle wrong-guess fired on incomplete lines | `PuzzleEngine.isWrongGuess` now only flags a guess when the whole segment between blockers is filled and the finished word is invalid. Regression tests added (`incomplete run is never a wrong guess`, `completed invalid run still costs a life`). |
| 1.2 | `turnTimeSeconds = null` ran a 45 s timer | Campaign timer uses `null` → unlimited (no countdown). |
| 1.3 | 45 s timer ran on AI turns; human could skip the AI | Timer skips computer turns entirely; `skipCurrentTurn` refuses to skip a COMPUTER turn; a skipped campaign turn now consumes one of the player's allowed turns. |
| 1.4 | Puzzle "Play Again" went Home | `playAgain` restarts the puzzle from the results screen. |
| 1.6 | Replay stars overwritten / hardcoded toast | `onCampaignLevelCompleted` keeps the best stars and uses the localized `toast_level_cleared`. |
| 2.1 | Stalled online turn could never be advanced | Host device can now skip any MIXED_ONLINE turn (button + auto-advance after the host's clock runs out). |
| 2.3 | Assignment seat choice ignored | `toggleOnlineSlot` keeps the selection as a contiguous tail so the UI always matches the room configuration. |
| 2.4 | Friend invite / game invite dead | `inviteFriend` creates a 1v1 room and sends a real GAME_INVITE (code + passcode); `acceptGameInvite` parses the payload and joins the lobby. |
| 2.5 | LIFE request gave the life to the wrong person | SQL triggers in `schema.sql` + `campaign.sql` now grant the +1 life to the sender (requester) and +10 coins to the accepting receiver. |
| 2.6 | "Ask a friend" always used the first friend | `LivesBottomSheet` lists friends; tapping one sends the request. |
| 2.7 | Presence not restarted after reconnect | `resubscribeRealtime` also calls `startPresence()`. |
| 3.1 | Defeat sound never played | Removed the always-true `|| game.status == FINISHED` from `playEndOfGameSound`; LEVEL_FAILED now also plays the end sound. |
| 4.1 | First-login display name blocked by cooldown | Trigger (both SQL files) sets `display_name_updated_at = null` on INSERT + a backfill clears it for rows that never edited their name. |
| 5 | Locked-level toast, buy-life error, timer-after-finish | `error_level_locked` message; `buyLife` pre-checks coins; `startTurnTimer` returns early when the match is over. |

### Still open (not changed here)
- Localized strings for the remaining hardcoded English screens (Friends/MessageThread/Puzzle/Results) — parity test still passes, translations exist only for string resources.
- `ComputerAI` performance (O(cells × 26 × segments)) — works, just heavy on old phones.
- README security note: server-side turn validation is a follow-up for public launch.
- SQL changes require re-running `supabase/update_username_cooldown.sql` and `supabase/campaign.sql` (or `schema.sql` on a fresh project) against the Supabase project.

## Round 2 — fixes added after re-review

| # | Issue | Fix |
| --- | --- | --- |
| 1 | **Campaign: the AI reaching the target finished the level and "won" for the human.** `GameRepository.placeLetter` assigned ranks / ended the match whenever ANY player crossed `targetScore`, so a strong bot could complete the player's level with stars the player never earned. | Campaign now counts **only the human's score** toward finishing (rank assignment, auto-last-rank, and the success check all look at the human player). The bot stays a word-blocking sparring partner. Regression tests added (`computer reaching the campaign target does not finish the level`, `human reaching the campaign target finishes the level with stars`). |
| 2 | **`skipCurrentTurn` campaign failure check used the AI's score.** After a skip, `currentTurnPlayerId` is the next (bot) player, so the "target reached?" check read the bot's score. | The check now reads the human player's score. |
| 3 | No end-of-match sound for a won campaign level or a solved puzzle. | Victory fanfare plays on campaign win and puzzle solve. |
| 4 | **Puzzle results screen was empty** — puzzles keep no `GameState`, so the standings card rendered with zero rows and the win flag was always false. | New `CampaignResult` in `MainUiState` carries level/stars/time/turns to the results screen; a summary card is shown for puzzles and the win banner/confetti now appears. |
| 5 | Unlimited campaign levels (1–2) showed a red "0s" timer chip. | Timer chip shows "∞" when there is no countdown. |
| 6 | **Ghost lobbies**: leaving Room Setup / Lobby never cleaned up the room — a joiner's seat stayed filled (host stuck waiting for Ready forever) and a host's room lingered as a lobby with a working code. | `RoomRepository.leaveLobby()` — host deletes the room (cascade), joiner releases only their own seat. Wired into `goBack`. Requires the new RLS policy `Player can release own slot` (added to `schema.sql` + `room_creation_fix.sql`; DELETE grant already present). |
| 7 | Host abandoning an in-progress online match left the room `in_progress` forever. | `goHome` now closes the room (best-effort `finishRoom`) when the host leaves mid-match. |
| 8 | Online badges on the friends list stayed stale after a reconnect. | `startPresence()` now cancels the old subscription and rebuilds the presence channel. |
