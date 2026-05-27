with admin_user as (
  select id
  from public."User"
  where device_id = '815e9c7c33fa662e'
     or username = '815e9c7c33fa662e'
  order by created_at desc nulls last
  limit 1
),
payload as (
  select
    'official-override-tu-fa-qing-kuang-diaocha-ocr'::text as object_id,
    (select id from admin_user) as author_id,
    '突发情况官方修正：调查按钮 OCR'::text as title,
    '将突发情况脚本中的调查按钮识别从模板匹配改为 OCR「前往调查」，命中 2 字即可点击。'::text as description,
    '官方修正 突发情况 OCR'::text as tags,
    '[]'::text as guide_images,
    'official-overrides/tu_fa_qing_kuang_diaocha_ocr.zip'::text as bundle_path,
    1981::bigint as bundle_size,
    73::integer as task_count,
    'published'::text as status,
    'tu_fa_qing_kuang.json'::text as override_asset_script
)
insert into public.cloud_daily_scripts (
  id,
  object_id,
  author_id,
  title,
  description,
  tags,
  guide_images,
  bundle_path,
  bundle_size,
  task_count,
  download_count,
  status,
  override_asset_script,
  created_at,
  updated_at
)
select
  gen_random_uuid(),
  object_id,
  author_id,
  title,
  description,
  tags,
  guide_images,
  bundle_path,
  bundle_size,
  task_count,
  0,
  status,
  override_asset_script,
  now(),
  now()
from payload
on conflict (object_id) do update set
  author_id = excluded.author_id,
  title = excluded.title,
  description = excluded.description,
  tags = excluded.tags,
  guide_images = excluded.guide_images,
  bundle_path = excluded.bundle_path,
  bundle_size = excluded.bundle_size,
  task_count = excluded.task_count,
  status = excluded.status,
  override_asset_script = excluded.override_asset_script,
  updated_at = now()
returning object_id, title, bundle_path, bundle_size, task_count, status, override_asset_script;
