-- Public catalog metadata for recruitment pools. User recruitment data stays in gacha_archives.
create table if not exists public.gacha_pools (
  id uuid primary key default gen_random_uuid(),
  pool_id text not null unique check (btrim(pool_id) <> ''),
  game_version smallint not null check (game_version in (0, 1)),
  name text not null check (btrim(name) <> ''),
  up_agents text[] not null default '{}'::text[],
  cover_url text not null check (btrim(cover_url) <> ''),
  sort_order integer not null default 0,
  status text not null default 'active' check (status in ('active', 'archived', 'invalid')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.gacha_pools is
  '招募记录的云端卡池目录；仅保存公共卡池资料，不保存用户招募数据。';
comment on column public.gacha_pools.game_version is '1=如鸢，0=代号鸢。';
comment on column public.gacha_pools.sort_order is '数值越大越靠前；绣衣天下固定为100000。';
comment on column public.gacha_pools.status is
  'active=目录展示，archived=不再展示但保留历史，invalid=维护样例或错误数据，不展示也不计入统计。';

create index if not exists idx_gacha_pools_active_order
  on public.gacha_pools (game_version, sort_order desc, pool_id)
  where status = 'active';

create or replace function public.set_gacha_pools_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_gacha_pools_updated_at on public.gacha_pools;
create trigger trg_gacha_pools_updated_at
before update on public.gacha_pools
for each row execute function public.set_gacha_pools_updated_at();

alter table public.gacha_pools enable row level security;
revoke all on table public.gacha_pools from anon, authenticated;

-- No client-facing policy is created. Future manual catalog refresh goes through an Edge Function.
