-- WORD BATTLE — Campaign Mode + Lives + Requests + Messaging
-- Run this file in Supabase SQL Editor on existing project. Idempotent.
-- This is also folded into schema.sql for new projects.
-- App uses only publishable (anon) key, so all writes must be RLS policies.

begin;

-- ---------------------------------------------------------------------------
-- 1. profiles – lives + campaign columns
-- ---------------------------------------------------------------------------

alter table public.profiles add column if not exists lives_current int not null default 3 check (lives_current >= 0);
alter table public.profiles add column if not exists lives_max int not null default 3 check (lives_max >= 1);
alter table public.profiles add column if not exists last_life_regen_at timestamptz;
alter table public.profiles add column if not exists campaign_level int not null default 1 check (campaign_level >= 1);
alter table public.profiles add column if not exists campaign_stars_total int not null default 0 check (campaign_stars_total >= 0);

-- Backfill for existing rows
update public.profiles set lives_current = 3 where lives_current is null;
update public.profiles set lives_max = 3 where lives_max is null;
update public.profiles set campaign_level = 1 where campaign_level is null;
update public.profiles set campaign_stars_total = 0 where campaign_stars_total is null;

-- ---------------------------------------------------------------------------
-- 2. campaign_progress
-- ---------------------------------------------------------------------------

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

-- ---------------------------------------------------------------------------
-- 3. requests (FRIEND, LIFE, GAME_INVITE)
-- ---------------------------------------------------------------------------

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
create index if not exists requests_type_idx on public.requests(type);

-- Daily LIFE request cap – server-side enforce, client cannot trust
create or replace function public.enforce_daily_life_request_cap()
returns trigger
language plpgsql
as $$
declare
  today_count int;
begin
  if new.type <> 'LIFE' then
    return new;
  end if;
  select count(*) into today_count
  from public.requests
  where sender_id = new.sender_id
    and type = 'LIFE'
    and created_at::date = now()::date
    and status <> 'declined';

  if today_count >= 5 then
    raise exception 'daily_life_request_limit: max 5 LIFE requests per day'
      using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

drop trigger if exists requests_daily_cap on public.requests;
create trigger requests_daily_cap
before insert on public.requests
for each row execute function public.enforce_daily_life_request_cap();

-- Atomic coins award + life reward when LIFE request accepted
create or replace function public.handle_life_request_accepted()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.type = 'LIFE' and old.status = 'pending' and new.status = 'accepted' then
    -- Receiver gets +1 life up to max
    update public.profiles
    set lives_current = least(lives_max, lives_current + 1),
        last_life_regen_at = case
          when lives_current + 1 >= lives_max then now()
          else coalesce(last_life_regen_at, now())
        end
    where id = new.receiver_id;

    -- Sender gets +10 coins atomically – prevents self-minting via client write
    update public.profiles
    set coins = coins + 10
    where id = new.sender_id;
  end if;
  return new;
end;
$$;

drop trigger if exists requests_life_reward on public.requests;
create trigger requests_life_reward
after update on public.requests
for each row execute function public.handle_life_request_accepted();

-- ---------------------------------------------------------------------------
-- 4. messages (plain text v1)
-- ---------------------------------------------------------------------------

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
create index if not exists messages_sender_idx on public.messages(sender_id);

-- ---------------------------------------------------------------------------
-- 5. RLS
-- ---------------------------------------------------------------------------

alter table public.campaign_progress enable row level security;
alter table public.requests enable row level security;
alter table public.messages enable row level security;

-- campaign_progress: owner only
drop policy if exists "Users can view own campaign progress" on public.campaign_progress;
create policy "Users can view own campaign progress" on public.campaign_progress
  for select using (auth.uid() = user_id);

drop policy if exists "Users can insert own campaign progress" on public.campaign_progress;
create policy "Users can insert own campaign progress" on public.campaign_progress
  for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "Users can update own campaign progress" on public.campaign_progress;
create policy "Users can update own campaign progress" on public.campaign_progress
  for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can delete own campaign progress" on public.campaign_progress;
create policy "Users can delete own campaign progress" on public.campaign_progress
  for delete to authenticated using (auth.uid() = user_id);

-- requests: sender or receiver can read, sender can insert, receiver can update status, both can delete
drop policy if exists "Participants can view requests" on public.requests;
create policy "Participants can view requests" on public.requests
  for select using (auth.uid() = sender_id or auth.uid() = receiver_id);

drop policy if exists "Users can create requests" on public.requests;
create policy "Users can create requests" on public.requests
  for insert to authenticated with check (auth.uid() = sender_id);

drop policy if exists "Receiver can update requests" on public.requests;
create policy "Receiver can update requests" on public.requests
  for update to authenticated
  using (auth.uid() = receiver_id or auth.uid() = sender_id)
  with check (auth.uid() = receiver_id or auth.uid() = sender_id);

drop policy if exists "Participants can delete requests" on public.requests;
create policy "Participants can delete requests" on public.requests
  for delete to authenticated using (auth.uid() = sender_id or auth.uid() = receiver_id);

-- messages: participants read, sender inserts, receiver marks read
drop policy if exists "Participants can view messages" on public.messages;
create policy "Participants can view messages" on public.messages
  for select using (auth.uid() = sender_id or auth.uid() = receiver_id);

drop policy if exists "Users can send messages" on public.messages;
create policy "Users can send messages" on public.messages
  for insert to authenticated with check (auth.uid() = sender_id);

drop policy if exists "Receiver can mark read" on public.messages;
create policy "Receiver can mark read" on public.messages
  for update to authenticated
  using (auth.uid() = receiver_id or auth.uid() = sender_id)
  with check (auth.uid() = receiver_id or auth.uid() = sender_id);

drop policy if exists "Participants can delete messages" on public.messages;
create policy "Participants can delete messages" on public.messages
  for delete to authenticated using (auth.uid() = sender_id or auth.uid() = receiver_id);

-- ---------------------------------------------------------------------------
-- 6. Grants
-- ---------------------------------------------------------------------------

grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on public.campaign_progress to authenticated;
grant select, insert, update, delete on public.requests to authenticated;
grant select, insert, update, delete on public.messages to authenticated;
revoke insert, update, delete on public.campaign_progress, public.requests, public.messages from anon;
grant select on public.campaign_progress, public.requests, public.messages to anon, authenticated;

grant execute on function public.enforce_daily_life_request_cap() to authenticated;
grant execute on function public.handle_life_request_accepted() to authenticated;

commit;

-- ---------------------------------------------------------------------------
-- 7. Realtime – outside transaction
-- ---------------------------------------------------------------------------

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

alter table public.campaign_progress replica identity full;
alter table public.requests replica identity full;
alter table public.messages replica identity full;
