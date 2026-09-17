-- One row represents one user-owned recruitment archive.
-- Pool progress and secret-agent records are stored in pool_records JSONB.
create table if not exists public.gacha_archives (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public."User"(id) on delete cascade,
  archive_id text not null check (btrim(archive_id) <> ''),
  archive_name text not null check (btrim(archive_name) <> '' and char_length(archive_name) <= 20),
  game_version smallint not null check (game_version in (0, 1)),
  pool_records jsonb not null default '[]'::jsonb check (jsonb_typeof(pool_records) = 'array'),
  sync_version integer not null default 1 check (sync_version > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz null,
  unique (user_id, archive_id)
);

comment on table public.gacha_archives is
  '用户招募记录存档；一行保存一个存档的所有卡池进度和绝密记录。';
comment on column public.gacha_archives.game_version is '1=如鸢，0=代号鸢。';
comment on column public.gacha_archives.pool_records is
  '数组项包含 pool_id、pool_name、remaining_pity 与 secret_records；secret_records 项包含 record_id、agent_name、pulls_since_last_secret、is_up、drawn_at、source。';
comment on column public.gacha_archives.sync_version is
  '客户端同步的乐观锁版本号；更新时应递增。';
comment on column public.gacha_archives.deleted_at is
  '软删除时间；非空记录不应展示给用户。';

create index if not exists idx_gacha_archives_user_updated
  on public.gacha_archives (user_id, updated_at desc)
  where deleted_at is null;

create or replace function public.set_gacha_archives_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_gacha_archives_updated_at on public.gacha_archives;
create trigger trg_gacha_archives_updated_at
before update on public.gacha_archives
for each row execute function public.set_gacha_archives_updated_at();

alter table public.gacha_archives enable row level security;
revoke all on table public.gacha_archives from anon, authenticated;

-- No client-facing policy is created. Future access must go through an Edge Function.
