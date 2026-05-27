update public.cloud_daily_scripts
set
  title = '突发情况：调查按钮 OCR 修正',
  tags = '突发情况 OCR',
  updated_at = now()
where object_id = 'official-override-tu-fa-qing-kuang-diaocha-ocr'
returning object_id, title, tags, override_asset_script, updated_at;
