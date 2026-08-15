-- WORD BATTLE — unique username + display name cooldown
--
-- Run this file in the Supabase SQL Editor on an existing project. It is idempotent.
-- New projects get the same objects from `supabase/schema.sql`.
--
-- It adds:
--   * `profiles.username`            — globally unique, lowercase handle used by friend search.
--   * `profiles.display_name_updated_at` — when the display name was last changed.
--   * a BEFORE UPDATE trigger enforcing a 10 day cooldown on display name changes, mirroring
--     `com.wordbattle.com.data.game.ProfileRules` on the client.
--
-- Client mirror (keep the two in sync):
--   display name : 3..20 characters after trimming
--   username     : 3..20 characters, ^[a-z0-9_]+$, stored lowercase
--   cooldown     : ProfileRules.DISPLAY_NAME_COOLDOWN_DAYS = 10

begin;

-- ---------------------------------------------------------------------------
-- 1. Columns
-- ---------------------------------------------------------------------------

alter table public.profiles
  add column if not exists username text;
alter table public.profiles
  add column if not exists display_name_updated_at timestamptz;

-- Backfill a deterministic handle for rows created before usernames existed, so the unique index
-- below can be created without manual clean-up. Users can change it once from the identity screen.
update public.profiles
set username = 'player_' || substr(replace(id::text, '-', ''), 1, 12)
where username is null or btrim(username) = '';

-- Existing handles are normalised to lowercase; the app always sends lowercase.
update public.profiles
set username = lower(btrim(username))
where username <> lower(btrim(username));

-- ---------------------------------------------------------------------------
-- 2. Constraints — shape + global uniqueness
-- ---------------------------------------------------------------------------

alter table public.profiles
  drop constraint if exists profiles_username_format_check;
alter table public.profiles
  add constraint profiles_username_format_check
  check (username is null or username ~ '^[a-z0-9_]{3,20}$');

alter table public.profiles
  drop constraint if exists profiles_display_name_length_check;
alter table public.profiles
  add constraint profiles_display_name_length_check
  check (char_length(btrim(display_name)) between 3 and 20);

-- The error raised by this index contains the word "username", which the app's error classifier
-- maps onto AppErrorCode.USERNAME_TAKEN ("This username is already taken").
drop index if exists public.profiles_username_key;
create unique index if not exists profiles_username_key
  on public.profiles (username)
  where username is not null;

create index if not exists profiles_username_search_idx
  on public.profiles (username text_pattern_ops);

-- ---------------------------------------------------------------------------
-- 3. Normalisation + 10 day display name cooldown
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
    if new.display_name_updated_at is null then
      new.display_name_updated_at := now();
    end if;
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
create trigger profiles_identity_rules
  before insert or update on public.profiles
  for each row execute function public.enforce_profile_identity_rules();

-- ---------------------------------------------------------------------------
-- 4. Grants — the app reads/writes profiles with the publishable (anon) key only.
-- ---------------------------------------------------------------------------

grant select on public.profiles to anon, authenticated;
grant insert, update on public.profiles to authenticated;
revoke insert, update, delete on public.profiles from anon;

commit;
