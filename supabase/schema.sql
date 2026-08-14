-- WORD BATTLE — Supabase schema
-- Run this file manually in the Supabase SQL Editor. The Android app never uses a service_role key.

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  photo_url text,
  coins integer not null default 0 check (coins >= 0),
  gems integer not null default 0 check (gems >= 0),
  level integer not null default 1 check (level >= 1),
  games_played integer not null default 0 check (games_played >= 0),
  wins integer not null default 0 check (wins >= 0),
  weekly_score integer not null default 0 check (weekly_score >= 0),
  created_at timestamptz not null default now()
);

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
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  slot_index int not null,
  filled_by uuid references public.profiles(id),
  filled_by_name text,
  is_ready boolean not null default false,
  unique(room_id, slot_index)
);

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

alter table public.profiles enable row level security;
alter table public.friends enable row level security;
alter table public.rooms enable row level security;
alter table public.room_slots enable row level security;
alter table public.games enable row level security;

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

drop policy if exists "Anyone can read rooms" on public.rooms;
create policy "Anyone can read rooms" on public.rooms for select using (true);
drop policy if exists "Authenticated users can create rooms" on public.rooms;
create policy "Authenticated users can create rooms" on public.rooms for insert with check (auth.uid() = host_id);
drop policy if exists "Host can update own room" on public.rooms;
create policy "Host can update own room" on public.rooms for update using (auth.uid() = host_id) with check (auth.uid() = host_id);

drop policy if exists "Anyone can read slots" on public.room_slots;
create policy "Anyone can read slots" on public.room_slots for select using (true);
-- Required for the host's createRoom() transaction; omitted from the original prompt's policies.
drop policy if exists "Host can create room slots" on public.room_slots;
create policy "Host can create room slots" on public.room_slots for insert with check (
  exists (select 1 from public.rooms r where r.id = room_id and r.host_id = auth.uid())
);
drop policy if exists "Users can claim/update their own slot" on public.room_slots;
create policy "Users can claim/update their own slot" on public.room_slots for update
  using (auth.uid() = filled_by or filled_by is null)
  with check (auth.uid() = filled_by or auth.uid() = (select host_id from public.rooms where id = room_id));

drop policy if exists "Anyone involved can read games" on public.games;
create policy "Anyone involved can read games" on public.games for select using (true);
drop policy if exists "Authenticated users can create games" on public.games;
create policy "Authenticated users can create games" on public.games for insert with check (
  auth.uid() = (select host_id from public.rooms where id = room_id)
);
drop policy if exists "Anyone involved can update games" on public.games;
create policy "Anyone involved can update games" on public.games for update using (auth.uid() is not null) with check (auth.uid() is not null);

-- Explicit API grants. RLS still decides which rows each role can access.
grant usage on schema public to anon, authenticated;
grant select on public.profiles, public.rooms, public.room_slots, public.games to anon, authenticated;
grant select, insert, update on public.friends to authenticated;
grant insert, update on public.profiles, public.rooms, public.room_slots, public.games to authenticated;
revoke insert, update, delete on public.profiles, public.friends, public.rooms, public.room_slots, public.games from anon;

create index if not exists rooms_code_passcode_idx on public.rooms (room_code, passcode);
create index if not exists room_slots_room_idx on public.room_slots (room_id, slot_index);
create index if not exists games_room_idx on public.games (room_id);
create index if not exists profiles_weekly_score_idx on public.profiles (weekly_score desc);
create index if not exists profiles_wins_idx on public.profiles (wins desc);

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
