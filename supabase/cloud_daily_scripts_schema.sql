create table if not exists public.cloud_daily_scripts (
  id uuid primary key default gen_random_uuid(),
  object_id text not null unique,
  author_id uuid references public."User"(id) on delete set null,
  title text not null,
  description text not null default '',
  tags text not null default '',
  guide_images text not null default '[]',
  bundle_path text not null,
  bundle_size bigint not null default 0,
  task_count integer not null default 0,
  download_count integer not null default 0,
  status text not null default 'published',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_cloud_daily_scripts_status_created
  on public.cloud_daily_scripts(status, created_at desc);

create index if not exists idx_cloud_daily_scripts_status_download
  on public.cloud_daily_scripts(status, download_count desc);

insert into storage.buckets (id, name, public)
values ('daily-script-bundles', 'daily-script-bundles', false)
on conflict (id) do nothing;
