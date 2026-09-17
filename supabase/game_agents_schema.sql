-- Cloud-only character records. App-side reading is intentionally not enabled yet.
create table if not exists public.game_agents (
  id uuid primary key default gen_random_uuid(),
  game_version smallint not null check (game_version in (0, 1)),
  name text not null check (btrim(name) <> ''),
  aliases text[] not null default '{}'::text[],
  avatar_path text not null check (btrim(avatar_path) <> ''),
  rarity smallint null check (rarity between 1 and 6),
  element text null,
  profession text null,
  sub_profession text null,
  skills jsonb not null default '{}'::jsonb check (jsonb_typeof(skills) = 'object'),
  fate_discs jsonb not null default '[]'::jsonb check (jsonb_typeof(fate_discs) = 'array'),
  extra jsonb not null default '{}'::jsonb check (jsonb_typeof(extra) = 'object'),
  release_version text null,
  released_at date null,
  source_url text null,
  is_active boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (game_version, name)
);

comment on table public.game_agents is
  '游戏机角色云端补充字典；头像存储在 game-agent-avatars bucket，表内仅保存相对路径。';
comment on column public.game_agents.game_version is '1=如鸢，0=代号鸢。';
comment on column public.game_agents.skills is
  '技能对象，例如 normal_attack、skill、captain_skill、passives。';
comment on column public.game_agents.fate_discs is
  '命盘数组；每项含 position、rarity、name、short_name、description。';
comment on column public.game_agents.extra is
  '不适合固定字段的角色资料，保留后续游戏版本扩展。';

create index if not exists idx_game_agents_active_sort
  on public.game_agents (game_version, is_active, sort_order, name);

create or replace function public.set_game_agents_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_game_agents_updated_at on public.game_agents;
create trigger trg_game_agents_updated_at
before update on public.game_agents
for each row execute function public.set_game_agents_updated_at();

alter table public.game_agents enable row level security;
revoke all on table public.game_agents from anon, authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'game-agent-avatars',
  'game-agent-avatars',
  false,
  5242880,
  array['image/png', 'image/jpeg', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- No storage.objects policy is created: the private bucket is writable only by server-side maintenance.
