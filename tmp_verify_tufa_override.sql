select
  object_id,
  title,
  bundle_path,
  bundle_size,
  task_count,
  status,
  override_asset_script,
  updated_at
from public.cloud_daily_scripts
where object_id = 'official-override-tu-fa-qing-kuang-diaocha-ocr';
