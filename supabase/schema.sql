-- WORD BATTLE — Supabase schema
-- Run this file manually in the Supabase SQL Editor. The Android app never uses a service_role key.
--
-- A brand new project only needs this file. Existing projects can apply the same fixes piecemeal:
--   * supabase/room_creation_fix.sql        — room/room_slots/games policies + grants + realtime
--   * supabase/update_username_cooldown.sql — unique username + 10 day display name cooldown

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  -- Globally unique, lowercase handle. Friend search matches on this column.
  username text,
  photo_url text,
  coins integer not null default 0 check (coins >= 0),
  gems integer not null default 0 check (gems >= 0),
  level integer not null default 1 check (level >= 1),
  games_played integer not null default 0 check (games_played >= 0),
  wins integer not null default 0 check (wins >= 0),
  weekly_score integer not null default 0 check (weekly_score >= 0),
  -- Last display name change; the trigger below enforces a 10 day cooldown on top of it.
  display_name_updated_at timestamptz,
  created_at timestamptz not null default now()
);

alter table public.profiles add column if not exists username text;
alter table public.profiles add column if not exists display_name_updated_at timestamptz;

create table if not exists public.friends (
  user_id uuid references public.profiles(id) on delete cascade,
  friend_id uuid references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  primary key (user_id, friend_id),
  check (user_id <> friend_id)
);

create table if not exists public.rooms (
  id uuid primary key default gen_random_uuid(),
  room_code text unique not null,
  passcode text not null,
  host_id uuid not null references public.profiles(id),
  total_slots int not null check (total_slots between 2 and 4),
  local_slots int not null check (local_slots >= 1),
  online_slots int not null check (online_slots >= 1),
  status text not null default 'lobby' check (status in ('lobby', 'in_progress', 'finished')),
  game_id uuid,
  created_at timestamptz not null default now(),
  check (local_slots + online_slots = total_slots)
);

create table if not exists public.room_slots (
  -- The app inserts slots WITHOUT an id column (NewRoomSlotDto), so this default supplies it.
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  slot_index int not null,
  filled_by uuid references public.profiles(id),
  filled_by_name text,
  is_ready boolean not null default false,
  unique(room_id, slot_index)
);

alter table public.room_slots alter column id set default gen_random_uuid();

create table if not exists public.games (
  id uuid primary key default gen_random_uuid(),
  room_id uuid references public.rooms(id),
  mode text not null check (mode in ('computer', 'local', 'mixed_online')),
  target_score int not null default 100 check (target_score > 0),
  board jsonb not null,
  players jsonb not null,
  used_words jsonb not null default '[]'::jsonb,
  current_turn_player_id text,
  status text not null default 'in_progress' check (status in ('lobby', 'in_progress', 'finished')),
  rankings jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.rooms
  drop constraint if exists rooms_game_id_fkey;
alter table public.rooms
  add constraint rooms_game_id_fkey foreign key (game_id) references public.games(id);

-- ---------------------------------------------------------------------------
-- Profile identity rules — unique lowercase username + 10 day display name cooldown.
-- Mirrors com.wordbattle.com.data.game.ProfileRules on the client.
-- ---------------------------------------------------------------------------

update public.profiles
set username = 'player_' || substr(replace(id::text, '-', ''), 1, 12)
where username is null or btrim(username) = '';

update public.profiles
set username = lower(btrim(username))
where username <> lower(btrim(username));

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

-- The unique violation message mentions "username", which the app maps to USERNAME_TAKEN.
create unique index if not exists profiles_username_key
  on public.profiles (username)
  where username is not null;

create index if not exists profiles_username_search_idx
  on public.profiles (username text_pattern_ops);

create or replace function public.enforce_profile_identity_rules()
returns trigger
language plpgsql
as $$
declare
  cooldown_days constant int := 10;
  next_allowed timestamptz;
begin
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

  if new.display_name is not distinct from old.display_name then
    -- A username-only change must not restart the display name cooldown.
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

create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists games_touch_updated_at on public.games;
create trigger games_touch_updated_at before update on public.games
for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------------------
-- Row level security. The app only ever uses the publishable (anon) key, so every
-- write it performs must be expressible as a policy below.
-- ---------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.friends enable row level security;
alter table public.rooms enable row level security;
alter table public.room_slots enable row level security;
alter table public.games enable row level security;

-- Helper predicates. SECURITY DEFINER so a room_slots policy cannot recurse into room_slots.
create or replace function public.is_room_host(target_room uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.rooms r where r.id = target_room and r.host_id = auth.uid()
  );
$$;

create or replace function public.is_room_participant(target_room uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.rooms r where r.id = target_room and r.host_id = auth.uid()
  ) or exists (
    select 1 from public.room_slots s where s.room_id = target_room and s.filled_by = auth.uid()
  );
$$;

create or replace function public.is_online_slot(target_room uuid, target_index int)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.rooms r
    where r.id = target_room
      and target_index >= r.local_slots
      and target_index < r.total_slots
  );
$$;

grant execute on function public.is_room_host(uuid) to anon, authenticated;
grant execute on function public.is_room_participant(uuid) to anon, authenticated;
grant execute on function public.is_online_slot(uuid, int) to anon, authenticated;

drop policy if exists "Anyone can view profiles" on public.profiles;
create policy "Anyone can view profiles" on public.profiles for select using (true);
drop policy if exists "Users can update own profile" on public.profiles;
create policy "Users can update own profile" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);
drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can insert own profile" on public.profiles for insert with check (auth.uid() = id);

drop policy if exists "Users can view their friendships" on public.friends;
create policy "Users can view their friendships" on public.friends for select using (auth.uid() = user_id or auth.uid() = friend_id);
drop policy if exists "Users can create friend requests" on public.friends;
create policy "Users can create friend requests" on public.friends for insert with check (auth.uid() = user_id);
drop policy if exists "Users can update friendships they're part of" on public.friends;
create policy "Users can update friendships they're part of" on public.friends for update
  using (auth.uid() = user_id or auth.uid() = friend_id)
  with check (auth.uid() = user_id or auth.uid() = friend_id);

-- rooms: the host owns the row for its whole lifetime, including the rollback delete.
drop policy if exists "Anyone can read rooms" on public.rooms;
create policy "Anyone can read rooms" on public.rooms for select using (true);
drop policy if exists "Authenticated users can create rooms" on public.rooms;
create policy "Authenticated users can create rooms" on public.rooms for insert to authenticated
  with check (
    auth.uid() = host_id
    and exists (select 1 from public.profiles p where p.id = auth.uid())
  );
drop policy if exists "Host can update own room" on public.rooms;
create policy "Host can update own room" on public.rooms for update to authenticated
  using (auth.uid() = host_id) with check (auth.uid() = host_id);
-- Required by deleteIncompleteRoom(): cleans up a room whose slots could not be inserted.
drop policy if exists "Host can delete own room" on public.rooms;
create policy "Host can delete own room" on public.rooms for delete to authenticated
  using (auth.uid() = host_id);

-- room_slots: the host creates every seat; a joiner may only claim an EMPTY ONLINE seat.
drop policy if exists "Anyone can read slots" on public.room_slots;
create policy "Anyone can read slots" on public.room_slots for select using (true);
drop policy if exists "Host can create room slots" on public.room_slots;
create policy "Host can create room slots" on public.room_slots for insert to authenticated
  with check (public.is_room_host(room_id));
drop policy if exists "Host can manage room slots" on public.room_slots;
create policy "Host can manage room slots" on public.room_slots for update to authenticated
  using (public.is_room_host(room_id)) with check (public.is_room_host(room_id));
drop policy if exists "Users can claim/update their own slot" on public.room_slots;
drop policy if exists "Player can claim an empty online slot" on public.room_slots;
create policy "Player can claim an empty online slot" on public.room_slots for update to authenticated
  using (
    filled_by is null
    and public.is_online_slot(room_id, slot_index)
    and exists (select 1 from public.rooms r where r.id = room_id and r.status = 'lobby')
  )
  with check (filled_by = auth.uid());
drop policy if exists "Player can update own slot" on public.room_slots;
create policy "Player can update own slot" on public.room_slots for update to authenticated
  using (filled_by = auth.uid()) with check (filled_by = auth.uid());
drop policy if exists "Host can delete room slots" on public.room_slots;
create policy "Host can delete room slots" on public.room_slots for delete to authenticated
  using (public.is_room_host(room_id));

-- games: created by the host, updated only by the people sitting in that room.
drop policy if exists "Anyone involved can read games" on public.games;
create policy "Anyone involved can read games" on public.games for select using (true);
drop policy if exists "Authenticated users can create games" on public.games;
create policy "Authenticated users can create games" on public.games for insert to authenticated
  with check (public.is_room_host(room_id));
drop policy if exists "Anyone involved can update games" on public.games;
drop policy if exists "Room players can update games" on public.games;
create policy "Room players can update games" on public.games for update to authenticated
  using (room_id is not null and public.is_room_participant(room_id))
  with check (room_id is not null and public.is_room_participant(room_id));

-- Explicit API grants. RLS still decides which rows each role can access.
grant usage on schema public to anon, authenticated;
grant select on public.profiles, public.rooms, public.room_slots, public.games to anon, authenticated;
grant select, insert, update on public.friends to authenticated;
grant insert, update on public.profiles, public.games to authenticated;
grant insert, update, delete on public.rooms, public.room_slots to authenticated;
revoke insert, update, delete on public.profiles, public.friends, public.rooms, public.room_slots, public.games from anon;

create index if not exists rooms_code_passcode_idx on public.rooms (room_code, passcode);
create index if not exists room_slots_room_idx on public.room_slots (room_id, slot_index);
create index if not exists room_slots_filled_by_idx on public.room_slots (filled_by);
create index if not exists games_room_idx on public.games (room_id);
create index if not exists profiles_weekly_score_idx on public.profiles (weekly_score desc);
create index if not exists profiles_wins_idx on public.profiles (wins desc);

-- ---------------------------------------------------------------------------
-- Campaign + Lives + Requests + Messaging (also in supabase/campaign.sql)
-- ---------------------------------------------------------------------------

alter table public.profiles add column if not exists lives_current int not null default 3 check (lives_current >= 0);
alter table public.profiles add column if not exists lives_max int not null default 3 check (lives_max >= 1);
alter table public.profiles add column if not exists last_life_regen_at timestamptz;
alter table public.profiles add column if not exists campaign_level int not null default 1 check (campaign_level >= 1);
alter table public.profiles add column if not exists campaign_stars_total int not null default 0 check (campaign_stars_total >= 0);

-- games mode + status now include campaign
alter table public.games drop constraint if exists games_mode_check;
alter table public.games drop constraint if exists games_status_check;
-- Recreate with broader check (Postgres auto names may differ, so also try generic drop via script)
do $$ begin
  alter table public.games add constraint games_mode_check check (mode in ('computer','local','mixed_online','campaign_score','campaign_puzzle'));
exception when duplicate_object then null;
end $$;
do $$ begin
  alter table public.games add constraint games_status_check check (status in ('lobby','in_progress','finished','level_failed'));
exception when duplicate_object then null;
end $$;

create table if not exists public.campaign_progress (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  level_number int not null check (level_number between 1 and 500),
  stars int not null check (stars between 0 and 3),
  best_time_seconds int check (best_time_seconds is null or best_time_seconds >= 0),
  best_turns int check (best_turns is null or best_turns >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, level_number)
);

create or replace function public.touch_campaign_progress()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists campaign_progress_touch on public.campaign_progress;
create trigger campaign_progress_touch
before update on public.campaign_progress
for each row execute function public.touch_campaign_progress();

create table if not exists public.requests (
  id uuid primary key default gen_random_uuid(),
  type text not null check (type in ('FRIEND','LIFE','GAME_INVITE')),
  sender_id uuid not null references public.profiles(id) on delete cascade,
  receiver_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted','declined','fulfilled')),
  payload jsonb,
  created_at timestamptz not null default now(),
  check (sender_id <> receiver_id)
);

create index if not exists requests_sender_idx on public.requests(sender_id, created_at desc);
create index if not exists requests_receiver_idx on public.requests(receiver_id, created_at desc);

create or replace function public.enforce_daily_life_request_cap()
returns trigger language plpgsql as $$
declare today_count int;
begin
  if new.type <> 'LIFE' then return new; end if;
  select count(*) into today_count from public.requests
  where sender_id = new.sender_id and type = 'LIFE' and created_at::date = now()::date and status <> 'declined';
  if today_count >= 5 then
    raise exception 'daily_life_request_limit: max 5 LIFE requests per day' using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

drop trigger if exists requests_daily_cap on public.requests;
create trigger requests_daily_cap before insert on public.requests for each row execute function public.enforce_daily_life_request_cap();

create or replace function public.handle_life_request_accepted()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.type = 'LIFE' and old.status = 'pending' and new.status = 'accepted' then
    update public.profiles set lives_current = least(lives_max, lives_current + 1) where id = new.receiver_id;
    update public.profiles set coins = coins + 10 where id = new.sender_id;
  end if;
  return new;
end;
$$;

drop trigger if exists requests_life_reward on public.requests;
create trigger requests_life_reward after update on public.requests for each row execute function public.handle_life_request_accepted();

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references public.profiles(id) on delete cascade,
  receiver_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now(),
  read_at timestamptz,
  check (sender_id <> receiver_id)
);

create index if not exists messages_conversation_idx on public.messages(least(sender_id, receiver_id), greatest(sender_id, receiver_id), created_at desc);
create index if not exists messages_receiver_idx on public.messages(receiver_id, read_at);

alter table public.campaign_progress enable row level security;
alter table public.requests enable row level security;
alter table public.messages enable row level security;

drop policy if exists "Users can view own campaign progress" on public.campaign_progress;
create policy "Users can view own campaign progress" on public.campaign_progress for select using (auth.uid() = user_id);
drop policy if exists "Users can insert own campaign progress" on public.campaign_progress;
create policy "Users can insert own campaign progress" on public.campaign_progress for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "Users can update own campaign progress" on public.campaign_progress;
create policy "Users can update own campaign progress" on public.campaign_progress for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
drop policy if exists "Users can delete own campaign progress" on public.campaign_progress;
create policy "Users can delete own campaign progress" on public.campaign_progress for delete to authenticated using (auth.uid() = user_id);

drop policy if exists "Participants can view requests" on public.requests;
create policy "Participants can view requests" on public.requests for select using (auth.uid() = sender_id or auth.uid() = receiver_id);
drop policy if exists "Users can create requests" on public.requests;
create policy "Users can create requests" on public.requests for insert to authenticated with check (auth.uid() = sender_id);
drop policy if exists "Receiver can update requests" on public.requests;
create policy "Receiver can update requests" on public.requests for update to authenticated using (auth.uid() = receiver_id or auth.uid() = sender_id) with check (auth.uid() = receiver_id or auth.uid() = sender_id);
drop policy if exists "Participants can delete requests" on public.requests;
create policy "Participants can delete requests" on public.requests for delete to authenticated using (auth.uid() = sender_id or auth.uid() = receiver_id);

drop policy if exists "Participants can view messages" on public.messages;
create policy "Participants can view messages" on public.messages for select using (auth.uid() = sender_id or auth.uid() = receiver_id);
drop policy if exists "Users can send messages" on public.messages;
create policy "Users can send messages" on public.messages for insert to authenticated with check (auth.uid() = sender_id);
drop policy if exists "Receiver can mark read" on public.messages;
create policy "Receiver can mark read" on public.messages for update to authenticated using (auth.uid() = receiver_id or auth.uid() = sender_id) with check (auth.uid() = receiver_id or auth.uid() = sender_id);
drop policy if exists "Participants can delete messages" on public.messages;
create policy "Participants can delete messages" on public.messages for delete to authenticated using (auth.uid() = sender_id or auth.uid() = receiver_id);

grant select, insert, update, delete on public.campaign_progress to authenticated;
grant select, insert, update, delete on public.requests to authenticated;
grant select, insert, update, delete on public.messages to authenticated;
revoke insert, update, delete on public.campaign_progress, public.requests, public.messages from anon;
grant select on public.campaign_progress, public.requests, public.messages to anon, authenticated;

-- Realtime powers leaderboard, lobby, and synchronized game updates.
do $$
begin
  alter publication supabase_realtime add table public.profiles;
exception when duplicate_object then null;
end $$;
do $$
begin
  alter publication supabase_realtime add table public.rooms;
exception when duplicate_object then null;
end $$;
do $$
begin
  alter publication supabase_realtime add table public.room_slots;
exception when duplicate_object then null;
end $$;
do $$
begin
  alter publication supabase_realtime add table public.games;
exception when duplicate_object then null;
end $$;
do $$
begin
  alter publication supabase_realtime add table public.campaign_progress;
exception when duplicate_object then null;
end $$;
do $$
begin
  alter publication supabase_realtime add table public.requests;
exception when duplicate_object then null;
end $$;
do $$
begin
  alter publication supabase_realtime add table public.messages;
exception when duplicate_object then null;
end $$;

alter table public.rooms replica identity full;
alter table public.room_slots replica identity full;
alter table public.games replica identity full;
alter table public.campaign_progress replica identity full;
alter table public.requests replica identity full;
alter table public.messages replica identity full;
