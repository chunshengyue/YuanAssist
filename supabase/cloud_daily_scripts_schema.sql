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
  override_asset_script text null,
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

create table if not exists public.cloud_daily_script_comment (
  id uuid primary key default gen_random_uuid(),
  "objectId" text not null unique,
  "createdAt" timestamptz not null default now(),
  "updatedAt" timestamptz not null default now(),
  content text not null default '',
  "replyToUserName" text not null default '',
  user_id uuid references public."User"(id) on delete set null,
  script_id uuid not null references public.cloud_daily_scripts(id) on delete cascade,
  reply_to_comment_id uuid references public.cloud_daily_script_comment(id) on delete set null,
  reply_to_user_id uuid references public."User"(id) on delete set null
);

create index if not exists idx_cloud_daily_script_comment_script_created
  on public.cloud_daily_script_comment(script_id, "createdAt" asc);

create index if not exists idx_cloud_daily_script_comment_user
  on public.cloud_daily_script_comment(user_id);

alter table public.cloud_daily_script_comment enable row level security;

create table if not exists public.cloud_daily_script_message (
  id uuid primary key default gen_random_uuid(),
  "objectId" text not null unique,
  "createdAt" timestamptz not null default now(),
  "updatedAt" timestamptz not null default now(),
  "isRead" boolean not null default false,
  type integer not null,
  "contentSnapshot" text not null default '',
  recipient_id uuid references public."User"(id) on delete cascade,
  sender_id uuid references public."User"(id) on delete set null,
  script_id uuid references public.cloud_daily_scripts(id) on delete cascade,
  comment_id uuid references public.cloud_daily_script_comment(id) on delete cascade
);

create index if not exists idx_cloud_daily_script_message_recipient_created
  on public.cloud_daily_script_message(recipient_id, "createdAt" desc);

create index if not exists idx_cloud_daily_script_message_recipient_unread
  on public.cloud_daily_script_message(recipient_id, "isRead");

alter table public.cloud_daily_script_message enable row level security;
