-- WORD BATTLE — Apply all latest fixes (idempotent)
-- =============================================================
-- Run THIS WHOLE FILE once in the Supabase SQL Editor (Dashboard → SQL Editor → New query).
-- Safe to re-run: every statement is idempotent (drop-if-exists / create-or-replace).
--
-- It applies the three fixes from the current code:
--   1. LIFE requests: the SENDER (the one who asked) gets the +1 life; the friend who
--      accepted gets +10 coins. (Previously the rewards were swapped.)
--   2. Display-name cooldown: a brand-new profile is NOT put on the 10-day cooldown at
--      signup; the cooldown starts on the first real display-name edit. Existing rows that
--      never edited their name get their cooldown cleared too.
--   3. Lobby cleanup: a joiner may release their own room slot when leaving the lobby, so
--      the host is never stuck waiting for a Ready that will never come (the app calls this
--      on "back" from the lobby/room-setup screens).
-- =============================================================

-- ---------------------------------------------------------------------------
-- 1. LIFE request reward direction
-- ---------------------------------------------------------------------------

create or replace function public.handle_life_request_accepted()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.type = 'LIFE' and old.status = 'pending' and new.status = 'accepted' then
    -- The SENDER asked for the life ("Ask a friend for life"), so the sender gets the +1 life
    -- (and a fresh regen anchor) and the friend who accepted gets +10 coins for helping out.
    update public.profiles
    set lives_current = least(lives_max, lives_current + 1),
        last_life_regen_at = case
          when lives_current + 1 >= lives_max then now()
          else coalesce(last_life_regen_at, now())
        end
    where id = new.sender_id;

    -- Receiver gets +10 coins atomically – prevents self-minting via client write
    update public.profiles
    set coins = coins + 10
    where id = new.receiver_id;
  end if;
  return new;
end;
$$;

drop trigger if exists requests_life_reward on public.requests;
create trigger requests_life_reward
after update on public.requests
for each row execute function public.handle_life_request_accepted();

-- ---------------------------------------------------------------------------
-- 2. Display-name cooldown starts on the FIRST real name edit, not at signup
-- ---------------------------------------------------------------------------

create or replace function public.enforce_profile_identity_rules()
returns trigger
language plpgsql
as $$
declare
  cooldown_days constant int := 10;
  next_allowed timestamptz;
begin
  -- Always store a trimmed display name and a trimmed, lowercase username.
  if new.display_name is not null then
    new.display_name := btrim(new.display_name);
  end if;
  if new.username is not null then
    new.username := lower(btrim(new.username));
    if new.username = '' then
      new.username := null;
    end if;
  end if;

  if tg_op = 'INSERT' then
    -- A brand-new profile has no display-name change history yet: the 10-day cooldown must
    -- start on the FIRST real display-name edit, not on account creation (otherwise a new
    -- user cannot pick their own name on the first-login identity screen).
    new.display_name_updated_at := null;
    return new;
  end if;

  -- UPDATE: nothing to police unless the display name actually changes.
  if new.display_name is not distinct from old.display_name then
    -- Keep the original timestamp; a username-only change must not restart the cooldown.
    new.display_name_updated_at := old.display_name_updated_at;
    return new;
  end if;

  if old.display_name_updated_at is not null then
    next_allowed := old.display_name_updated_at + make_interval(days => cooldown_days);
    if now() < next_allowed then
      raise exception
        'display_name_change_cooldown: display name can be changed again in % day(s)',
        greatest(1, ceil(extract(epoch from (next_allowed - now())) / 86400)::int)
        using errcode = 'check_violation';
    end if;
  end if;

  new.display_name_updated_at := now();
  return new;
end;
$$;

drop trigger if exists profiles_identity_rules on public.profiles;

-- Backfill: clear the cooldown for rows created before this fix that never actually changed
-- their display name (the timestamp still equals the row's creation time). Runs WITHOUT the
-- trigger so the null is not overwritten back.
update public.profiles
set display_name_updated_at = null
where display_name_updated_at is not null
  and display_name_updated_at = created_at;

create trigger profiles_identity_rules
  before insert or update on public.profiles
  for each row execute function public.enforce_profile_identity_rules();

-- ---------------------------------------------------------------------------
-- 3. Joiner may release their own lobby seat (ghost-lobby fix)
-- ---------------------------------------------------------------------------

drop policy if exists "Player can release own slot" on public.room_slots;
create policy "Player can release own slot" on public.room_slots
  for delete to authenticated
  using (filled_by = auth.uid());

-- The DELETE privilege on room_slots is required for the policy above. The app only ever uses
-- the publishable (anon) key; it is already granted by the full schema, but re-asserting it
-- here makes this file self-contained for any older project.
grant delete on public.room_slots to authenticated;

-- ---------------------------------------------------------------------------
-- Optional sanity check
-- ---------------------------------------------------------------------------
-- Should return one row with polname = 'Player can release own slot'.
select polname
from pg_policy
where polrelid = 'public.room_slots'::regclass
  and polname = 'Player can release own slot';
