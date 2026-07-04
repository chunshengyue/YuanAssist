from __future__ import annotations

import cgi
import cv2
import json
import mimetypes
import os
import re
import shutil
import sys
import uuid
from dataclasses import asdict
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse


ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
RUNS_DIR = ROOT / "web_runs"
HOST = "127.0.0.1"
PORT = int(os.environ.get("TABLE_OCR_PORT", "17865"))
CELL_CROP_PATH_RE = re.compile(r"^([^/]+)/([^/]+)/cells/r(\d+)_c(\d+)\.png$")

if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from table_ocr.models import PipelineConfig  # noqa: E402
from table_ocr.ocr import ocr_full_image_raw_text  # noqa: E402
from table_ocr.pipeline import run_pipeline  # noqa: E402
from table_ocr.preprocess import load_image  # noqa: E402

PROFILE_PRESETS = [
    {
        "key": "full",
        "label": "完整流程",
        "description": "表格区域提取 + 高斯模糊 + 自适应二值化 + 多行处理",
        "options": {},
    },
    {
        "key": "light_preprocess",
        "label": "简化预处理",
        "description": "关闭高斯模糊，改用 Otsu 二值化",
        "options": {
            "enable_blur": False,
            "enable_adaptive_threshold": False,
        },
    },
    {
        "key": "no_multiline",
        "label": "关闭多行处理",
        "description": "不对复杂单元格做分行识别",
        "options": {
            "enable_multiline_split": False,
        },
    },
]

RAW_TEXT_COMPARISON_PROFILE = {
    "key": "raw_full_image_ocr",
    "label": "整图直接 OCR",
    "description": "不做表格定位、预处理、切格，直接把整张原图送进 OCR。",
}

DEBUG_IMAGE_LABELS = {
    "source_region_overlay.png": {
        "label": "原图表格定位",
        "description": "在整张截图上标出程序认为的表格主区域。",
        "step": "table_region",
        "order": 40,
    },
    "source_binary.png": {
        "label": "原图二值化",
        "description": "先在原图尺度上做一次二值化，方便后续定位表格轮廓。",
        "step": "table_region",
        "order": 20,
    },
    "source_merged.png": {
        "label": "表格轮廓合并",
        "description": "把相邻区域合并成更完整的候选轮廓，用来选择最终表格框。",
        "step": "table_region",
        "order": 30,
    },
    "table_region.png": {
        "label": "裁剪后的纯表格图",
        "description": "这是去掉立绘和大部分背景后的纯表格区域，后续所有步骤都只处理它。",
        "step": "table_region",
        "order": 50,
    },
    "gray.png": {
        "label": "表格灰度图",
        "description": "把表格区域转成灰度图，减少颜色干扰。",
        "step": "preprocess",
        "order": 10,
    },
    "threshold_input.png": {
        "label": "阈值输入图",
        "description": "这是进入阈值分割前的图像版本，用来观察模糊和降噪效果。",
        "step": "preprocess",
        "order": 20,
    },
    "binary.png": {
        "label": "表格二值化",
        "description": "把线条和文字提成高对比前景，方便识别网格结构。",
        "step": "preprocess",
        "order": 30,
    },
    "grid_overlay.png": {
        "label": "切格结果",
        "description": "在整表上画出恢复出来的行列网格和单元格编号。",
        "step": "grid",
        "order": 10,
    },
}


INDEX_HTML = """<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>攻略表格 OCR 工作台</title>
  <style>
    :root {
      --ink: #241d16;
      --muted: #6d6259;
      --paper: #f4efe5;
      --panel: rgba(255, 250, 240, .92);
      --line: #b89962;
      --accent: #b6462f;
      --green: #236c59;
      --shadow: 0 22px 70px rgba(63, 42, 20, .18);
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: var(--ink);
      font-family: "Microsoft YaHei UI", "Noto Sans CJK SC", sans-serif;
      background:
        linear-gradient(90deg, rgba(120, 82, 39, .08) 1px, transparent 1px),
        linear-gradient(0deg, rgba(120, 82, 39, .08) 1px, transparent 1px),
        radial-gradient(circle at 20% 15%, rgba(182, 70, 47, .12), transparent 28rem),
        #efe7d6;
      background-size: 42px 42px, 42px 42px, auto, auto;
    }
    .shell { width: min(1180px, calc(100vw - 36px)); margin: 34px auto; }
    header { display: grid; grid-template-columns: 1.1fr .9fr; gap: 24px; align-items: end; }
    h1 {
      margin: 0;
      font-family: Georgia, "Microsoft YaHei UI", serif;
      font-size: clamp(34px, 5vw, 68px);
      line-height: .98;
      letter-spacing: 0;
    }
    .lede { color: var(--muted); font-size: 15px; line-height: 1.8; margin: 0 0 8px; }
    .board {
      margin-top: 28px;
      display: grid;
      grid-template-columns: 360px 1fr;
      gap: 22px;
      align-items: start;
    }
    .panel {
      background: var(--panel);
      border: 1px solid rgba(184, 153, 98, .72);
      box-shadow: var(--shadow);
      border-radius: 8px;
    }
    .upload { padding: 22px; position: sticky; top: 24px; }
    .drop {
      position: relative;
      display: grid;
      place-items: center;
      min-height: 270px;
      border: 1.5px dashed var(--line);
      border-radius: 8px;
      background: rgba(255, 255, 255, .38);
      cursor: pointer;
      overflow: hidden;
      text-align: center;
    }
    .drop input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .drop img {
      width: 100%;
      height: 270px;
      object-fit: contain;
      display: none;
      background: #fffaf0;
    }
    .drop.has-image .hint { display: none; }
    .drop.has-image img { display: block; }
    .hint strong { display: block; font-size: 19px; margin-bottom: 10px; }
    .hint span { color: var(--muted); font-size: 13px; }
    button {
      width: 100%;
      height: 48px;
      margin-top: 16px;
      border: 0;
      border-radius: 6px;
      color: #fff9ee;
      background: linear-gradient(135deg, #2d705f, #123f35);
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 12px 24px rgba(35, 108, 89, .25);
    }
    button:disabled { opacity: .55; cursor: wait; }
    .status { margin-top: 14px; min-height: 22px; color: var(--muted); font-size: 13px; }
    .result { min-height: 560px; padding: 22px; }
    .empty {
      min-height: 512px;
      display: grid;
      place-items: center;
      color: var(--muted);
      text-align: center;
      border: 1px solid rgba(184, 153, 98, .35);
      border-radius: 8px;
      background: rgba(255, 255, 255, .26);
    }
    .toolbar { display: flex; gap: 10px; align-items: center; justify-content: space-between; margin-bottom: 14px; }
    .toolbar h2 { margin: 0; font-family: Georgia, "Microsoft YaHei UI", serif; font-size: 24px; }
    .toolbar a { color: var(--accent); font-weight: 700; text-decoration: none; }
    .summary { display: flex; flex-wrap: wrap; gap: 10px; margin: 0 0 16px; }
    .badge {
      padding: 8px 12px;
      border: 1px solid rgba(184, 153, 98, .45);
      border-radius: 999px;
      background: rgba(255, 248, 235, .86);
      color: var(--muted);
      font-size: 13px;
    }
    table { width: 100%; border-collapse: collapse; background: #fffaf0; border: 1px solid rgba(184, 153, 98, .55); }
    th, td { border-bottom: 1px solid rgba(184, 153, 98, .35); padding: 10px 12px; text-align: center; }
    th { background: #eadcc2; font-size: 13px; }
    td:first-child, th:first-child { color: var(--accent); font-weight: 700; }
    tr:last-child td { border-bottom: 0; }
    .section-title { margin: 24px 0 12px; font-family: Georgia, "Microsoft YaHei UI", serif; font-size: 22px; }
    .process-shell {
      border: 1px solid rgba(184,153,98,.45);
      border-radius: 8px;
      background: rgba(255,250,240,.94);
      overflow: hidden;
    }
    .stage-tab-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      padding: 14px;
      border-bottom: 1px solid rgba(184,153,98,.35);
      background: rgba(234, 220, 194, .44);
    }
    .stage-tab {
      width: auto;
      height: auto;
      margin: 0;
      padding: 10px 14px;
      border: 1px solid rgba(184,153,98,.45);
      border-radius: 999px;
      color: var(--muted);
      background: rgba(255, 250, 240, .92);
      box-shadow: none;
      font-size: 14px;
      font-weight: 700;
    }
    .stage-tab.active {
      color: #fff9ee;
      background: linear-gradient(135deg, #b6462f, #7f2517);
      border-color: rgba(127, 37, 23, .75);
    }
    .stage-panel {
      display: none;
      padding: 18px;
    }
    .stage-panel.active { display: block; }
    .stage-header {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 14px;
    }
    .stage-header h3 { margin: 0; font-size: 24px; }
    .stage-header p {
      margin: 8px 0 0;
      color: var(--muted);
      font-size: 14px;
      line-height: 1.8;
      max-width: 820px;
    }
    .stage-note {
      margin: 0 0 14px;
      padding: 10px 12px;
      border: 1px solid rgba(184,153,98,.35);
      border-radius: 8px;
      background: rgba(255, 255, 255, .36);
      color: var(--muted);
      font-size: 13px;
      line-height: 1.8;
    }
    .stage-metrics {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin: 0 0 14px;
    }
    .sequence-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
    }
    .sequence-card {
      margin: 0;
      border: 1px solid rgba(184,153,98,.4);
      border-radius: 8px;
      overflow: hidden;
      background: #fffaf0;
    }
    .sequence-card.wide { grid-column: span 2; }
    .sequence-card img {
      width: 100%;
      display: block;
      max-height: 420px;
      object-fit: contain;
      background: #fff;
      cursor: zoom-in;
    }
    .sequence-card figcaption {
      padding: 10px 12px;
      color: var(--muted);
      font-size: 13px;
      line-height: 1.7;
    }
    .sequence-card strong {
      display: block;
      margin-bottom: 4px;
      color: var(--ink);
      font-size: 15px;
    }
    .grid-focus img { max-height: 620px; }
    .cell-rows { display: grid; gap: 16px; margin-top: 18px; }
    .cell-row {
      border: 1px solid rgba(184,153,98,.45);
      border-radius: 8px;
      background: rgba(255,250,240,.92);
      padding: 12px;
    }
    .cell-row h4 { margin: 0 0 10px; font-size: 16px; }
    .cell-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
    .cell-card {
      border: 1px solid rgba(184,153,98,.35);
      border-radius: 6px;
      background: #fffaf0;
      padding: 8px;
    }
    .cell-card img {
      width: 100%;
      height: 110px;
      object-fit: contain;
      display: block;
      background: #fff;
      border-radius: 4px;
      cursor: zoom-in;
    }
    .cell-card strong { display: block; margin-top: 8px; font-size: 13px; }
    .cell-card span { display: block; color: var(--muted); font-size: 12px; line-height: 1.6; }
    .comparison-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; margin-top: 18px; }
    .profile-card {
      border: 1px solid rgba(184,153,98,.45);
      border-radius: 8px;
      background: rgba(255, 250, 240, .9);
      padding: 14px;
    }
    .profile-card h3 { margin: 0 0 8px; font-size: 18px; }
    .profile-card p { margin: 0 0 10px; color: var(--muted); font-size: 13px; line-height: 1.7; }
    .profile-diff { color: var(--accent); font-size: 13px; margin: 0 0 10px; }
    .mini-table th, .mini-table td { padding: 7px 8px; font-size: 12px; }
    .raw-text-block {
      margin: 0;
      padding: 12px;
      min-height: 220px;
      max-height: 360px;
      overflow: auto;
      border: 1px solid rgba(184,153,98,.35);
      border-radius: 8px;
      background: #fffdf7;
      color: var(--ink);
      font-size: 12px;
      line-height: 1.8;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .diff-list { margin: 10px 0 0; padding-left: 18px; color: var(--muted); font-size: 12px; }
    .complex-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; margin-top: 18px; }
    .complex-card {
      border: 1px solid rgba(184,153,98,.45);
      border-radius: 8px;
      background: rgba(255, 250, 240, .9);
      padding: 12px;
    }
    .complex-card img {
      width: 100%;
      max-height: 260px;
      object-fit: contain;
      display: block;
      background: #fffaf0;
      border-radius: 6px;
      cursor: zoom-in;
    }
    .complex-lines { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-top: 8px; }
    .complex-lines img { max-height: 180px; }
    .complex-meta { margin-top: 8px; color: var(--muted); font-size: 12px; line-height: 1.7; }
    .warning-list {
      border: 1px solid rgba(184,153,98,.45);
      border-radius: 8px;
      background: rgba(255,250,240,.92);
      padding: 12px 16px;
      color: var(--muted);
      font-size: 13px;
      line-height: 1.8;
    }
    .warning-list ul { margin: 0; padding-left: 18px; }
    .correction-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 14px;
      margin-top: 18px;
    }
    .correction-card {
      border: 1px solid rgba(184,153,98,.45);
      border-radius: 8px;
      background: rgba(255, 250, 240, .94);
      padding: 12px;
    }
    .correction-card img {
      width: 100%;
      height: 118px;
      object-fit: contain;
      display: block;
      background: #fff;
      border-radius: 6px;
      cursor: zoom-in;
    }
    .correction-card h4 { margin: 10px 0 6px; font-size: 15px; }
    .correction-card p { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.65; }
    .correction-card .token {
      display: inline-flex;
      margin: 4px 4px 0 0;
      padding: 3px 7px;
      border: 1px solid rgba(184,153,98,.38);
      border-radius: 999px;
      background: #fffaf0;
      color: var(--ink);
      font-size: 12px;
      font-weight: 700;
    }
    .image-modal {
      position: fixed;
      inset: 0;
      display: none;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: rgba(23, 16, 10, .72);
      z-index: 999;
    }
    .image-modal.open { display: flex; }
    .modal-card {
      width: min(1200px, 100%);
      max-height: calc(100vh - 48px);
      overflow: auto;
      border: 1px solid rgba(184,153,98,.55);
      border-radius: 10px;
      background: rgba(255,250,240,.98);
      box-shadow: var(--shadow);
      padding: 16px;
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 12px;
    }
    .modal-close {
      width: auto;
      height: auto;
      margin: 0;
      padding: 8px 14px;
      border-radius: 999px;
      box-shadow: none;
      background: linear-gradient(135deg, #6e5b47, #3d3024);
      font-size: 14px;
    }
    .modal-card img {
      width: 100%;
      max-height: 70vh;
      object-fit: contain;
      display: block;
      background: #fff;
      border-radius: 8px;
    }
    .modal-meta { margin-top: 12px; color: var(--muted); line-height: 1.8; }
    .modal-meta strong { display: block; color: var(--ink); font-size: 18px; margin-bottom: 4px; }
    @media (max-width: 900px) {
      header, .board, .sequence-grid { grid-template-columns: 1fr; }
      .upload { position: static; }
      .sequence-card.wide { grid-column: span 1; }
      .complex-lines { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
  <main class="shell">
    <header>
      <h1>攻略表格 OCR 工作台</h1>
      <p class="lede">上传游戏攻略截图，服务会展示 OpenCV 预处理、表格定位、网格切分、多行拆分等中间过程，并对比简化流程的识别结果。</p>
    </header>
    <section class="board">
      <aside class="panel upload">
        <label class="drop" id="drop">
          <input id="file" type="file" accept="image/*">
          <img id="preview" alt="截图预览">
          <span class="hint"><strong>选择或拖入截图</strong><span>支持 jpg / png / jpeg</span></span>
        </label>
        <button id="run" disabled>开始识别</button>
        <div class="status" id="status">等待上传截图。</div>
      </aside>
      <section class="panel result" id="result">
        <div class="empty">识别结果会显示在这里。<br>中间过程会按阶段切换展示，图片支持放大查看。</div>
      </section>
    </section>
  </main>
  <div class="image-modal" id="imageModal" aria-hidden="true">
    <div class="modal-card">
      <div class="modal-actions">
        <button id="modalClose" class="modal-close" type="button">关闭大图</button>
      </div>
      <img id="modalImage" alt="中间过程大图">
      <div class="modal-meta">
        <strong id="modalTitle"></strong>
        <div id="modalDescription"></div>
      </div>
  </div>
  </div>
  <script>
    const INITIAL_CELL_ROW_LIMIT = 4;
    const CELL_ROW_BATCH_SIZE = 4;
    const fileInput = document.querySelector('#file');
    const preview = document.querySelector('#preview');
    const drop = document.querySelector('#drop');
    const run = document.querySelector('#run');
    const statusEl = document.querySelector('#status');
    const result = document.querySelector('#result');
    const imageModal = document.querySelector('#imageModal');
    const modalImage = document.querySelector('#modalImage');
    const modalTitle = document.querySelector('#modalTitle');
    const modalDescription = document.querySelector('#modalDescription');
    const modalClose = document.querySelector('#modalClose');
    let selectedFile = null;
    let latestPayload = null;
    let currentCellRowLimit = INITIAL_CELL_ROW_LIMIT;
    let activeStageKey = 'table_region';

    fileInput.addEventListener('change', () => setFile(fileInput.files[0]));
    drop.addEventListener('dragover', event => { event.preventDefault(); drop.style.borderColor = '#b6462f'; });
    drop.addEventListener('dragleave', () => { drop.style.borderColor = ''; });
    drop.addEventListener('drop', event => {
      event.preventDefault();
      drop.style.borderColor = '';
      setFile(event.dataTransfer.files[0]);
    });

    function setFile(file) {
      if (!file) return;
      selectedFile = file;
      preview.src = URL.createObjectURL(file);
      drop.classList.add('has-image');
      run.disabled = false;
      statusEl.textContent = `已选择：${file.name}`;
    }

    run.addEventListener('click', async () => {
      if (!selectedFile) return;
      run.disabled = true;
      statusEl.textContent = '识别中，PaddleOCR 首次加载模型可能需要稍等...';
      const form = new FormData();
      form.append('image', selectedFile);
      try {
        const response = await fetch('/api/recognize', { method: 'POST', body: form });
        const payload = await response.json();
        if (!response.ok) throw new Error(payload.error || '识别失败');
        renderResult(payload);
        statusEl.textContent = `识别完成：${payload.rows.length} 行`;
      } catch (error) {
        result.innerHTML = `<div class="empty">识别失败：${escapeHtml(error.message)}</div>`;
        statusEl.textContent = '识别失败，请查看终端错误。';
      } finally {
        run.disabled = false;
      }
    });

    result.addEventListener('click', event => {
      const stageTab = event.target.closest('[data-stage-target]');
      if (stageTab) {
        activateStage(stageTab.closest('.process-shell'), stageTab.dataset.stageTarget);
        return;
      }
      const actionButton = event.target.closest('[data-action]');
      if (actionButton) {
        if (!latestPayload) return;
        if (actionButton.dataset.action === 'more-cells') {
          currentCellRowLimit = Math.min(
            buildCellRows(latestPayload.summary.cells).length,
            currentCellRowLimit + CELL_ROW_BATCH_SIZE,
          );
          renderResult(latestPayload, false);
        } else if (actionButton.dataset.action === 'all-cells') {
          currentCellRowLimit = buildCellRows(latestPayload.summary.cells).length;
          renderResult(latestPayload, false);
        }
        return;
      }
      const zoomTarget = event.target.closest('[data-zoom-src]');
      if (zoomTarget) {
        openImageModal(
          zoomTarget.dataset.zoomSrc,
          zoomTarget.dataset.zoomTitle || '',
          zoomTarget.dataset.zoomDescription || '',
        );
      }
    });

    modalClose.addEventListener('click', closeImageModal);
    imageModal.addEventListener('click', event => {
      if (event.target === imageModal) {
        closeImageModal();
      }
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape') {
        closeImageModal();
      }
    });

    function renderResult(payload, resetCellLimit = true) {
      latestPayload = payload;
      const cellRows = buildCellRows(payload.summary.cells);
      if (resetCellLimit) {
        currentCellRowLimit = Math.min(
          Math.max(cellRows.length, 1),
          INITIAL_CELL_ROW_LIMIT,
        );
      }
      const comparisonCards = payload.comparisons.map(profile => `
        <article class="profile-card">
          <h3>${escapeHtml(profile.label)}</h3>
          <p>${escapeHtml(profile.description)}</p>
          <div class="profile-diff">${escapeHtml(profile.diff.summary)}</div>
          ${profile.diff.examples.length ? `<ul class="diff-list">${profile.diff.examples.map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ul>` : ''}
          ${renderComparisonBody(profile)}
        </article>
      `).join('');
      result.innerHTML = `
        <div class="toolbar">
          <h2>完整流程结果</h2>
          <a href="${payload.csv_url}" download>下载 CSV</a>
        </div>
        <div class="summary">
          <span class="badge">识别行数 ${payload.rows.length}</span>
          <span class="badge">单元格 ${payload.summary.cell_count}</span>
          <span class="badge">多行格 ${payload.summary.multiline_cell_count}</span>
          <span class="badge">动作格 ${payload.summary.correction_cell_count || 0}</span>
          <span class="badge">识别告警 ${payload.summary.warning_cell_count}</span>
          <span class="badge">行线 ${payload.summary.row_lines.length}</span>
          <span class="badge">列线 ${payload.summary.col_lines.length}</span>
        </div>
        ${renderMainTable(payload.rows)}
        <h3 class="section-title">中间过程</h3>
        ${renderStageTabs(payload, cellRows)}
        <h3 class="section-title">对比实验</h3>
        <div class="comparison-grid">${comparisonCards}</div>
      `;
    }

    function renderMainTable(rows) {
      return `
        <table>
          <thead><tr><th>回合</th><th>列1</th><th>列2</th><th>列3</th><th>列4</th><th>列5</th></tr></thead>
          <tbody>${renderRows(rows)}</tbody>
        </table>
      `;
    }

    function renderMiniTable(rows) {
      return `
        <table class="mini-table">
          <thead><tr><th>回合</th><th>列1</th><th>列2</th><th>列3</th><th>列4</th><th>列5</th></tr></thead>
          <tbody>${renderRows(rows)}</tbody>
        </table>
      `;
    }

    function renderComparisonBody(profile) {
      if (profile.mode === 'raw_text') {
        return `<pre class="raw-text-block">${escapeHtml(profile.raw_text || '没有识别到文本')}</pre>`;
      }
      return renderMiniTable(profile.rows || []);
    }

    function renderRows(rows) {
      const html = rows.map(row => `
        <tr>
          <td>${escapeHtml(row.round_label)}</td>
          ${row.actions.map(action => `<td>${escapeHtml(action)}</td>`).join('')}
        </tr>
      `).join('');
      return html || '<tr><td colspan="6">没有识别到有效行</td></tr>';
    }

    function buildCellRows(cells) {
      const grouped = new Map();
      (cells || []).forEach(cell => {
        if (!grouped.has(cell.row)) grouped.set(cell.row, []);
        grouped.get(cell.row).push(cell);
      });
      return Array.from(grouped.entries())
        .sort((a, b) => a[0] - b[0])
        .map(([row, rowCells]) => ({
          row,
          cells: rowCells.sort((a, b) => a.col - b.col),
        }));
    }

    function renderStageTabs(payload, cellRows) {
      const imageMap = new Map((payload.debug_images || []).map(item => [item.name, item]));
      const stageMeta = new Map((payload.summary.step_notes || []).map(step => [step.key, step]));
      const stages = [
        {
          key: 'table_region',
          nav: '1. 定位',
          title: getStageTitle(stageMeta, 'table_region', '1. 原图与表格定位'),
          description: getStageDescription(
            stageMeta,
            'table_region',
            '先在整图里把表格从立绘和背景中扣出来，后续所有步骤都只处理裁出来的纯表格区域。'
          ),
          note: `顺序：原始截图 -> 原图二值化 -> 轮廓合并 -> 框出表格 -> 裁出纯表格图`,
          metrics: [
            `表格框 x=${payload.summary.table_rect?.[0] ?? 0}`,
            `y=${payload.summary.table_rect?.[1] ?? 0}`,
            `w=${payload.summary.table_rect?.[2] ?? 0}`,
            `h=${payload.summary.table_rect?.[3] ?? 0}`,
          ],
          body: renderImageGrid([
            {
              label: '原始截图',
              description: '整张输入图，只在这里展示一次，用来和后续纯表格图做对照。',
              url: payload.input_url,
              wide: true,
            },
            pickImage(imageMap, 'source_binary.png'),
            pickImage(imageMap, 'source_merged.png'),
            pickImage(imageMap, 'source_region_overlay.png'),
            wideImage(pickImage(imageMap, 'table_region.png')),
          ]),
        },
        {
          key: 'preprocess',
          nav: '2. 预处理',
          title: getStageTitle(stageMeta, 'preprocess', '2. 表格预处理'),
          description: getStageDescription(
            stageMeta,
            'preprocess',
            '这一步只处理纯表格图，顺序是灰度 -> 阈值输入 -> 二值图，不再回到原图。'
          ),
          note: '这里不会再出现原图。灰度图是起点，阈值输入图是进入分割前的版本，最后才得到切格用的表格二值图。',
          body: renderImageGrid([
            pickImage(imageMap, 'gray.png'),
            pickImage(imageMap, 'threshold_input.png'),
            wideImage(pickImage(imageMap, 'binary.png')),
          ]),
        },
        {
          key: 'grid',
          nav: '3. 切格',
          title: getStageTitle(stageMeta, 'grid', '3. 表格分割'),
          description: getStageDescription(
            stageMeta,
            'grid',
            '用二值图恢复行线和列线，再把结果重新画回纯表格图上。'
          ),
          note: '这一页只看切格结果本身。点击图片可以放大查看单元格边界和编号。',
          metrics: [
            `检测到 ${payload.summary.row_lines.length} 条行线`,
            `检测到 ${payload.summary.col_lines.length} 条列线`,
            `共切出 ${payload.summary.cell_count} 个格子`,
          ],
          body: renderImageGrid([
            wideImage(pickImage(imageMap, 'grid_overlay.png'), 'grid-focus'),
          ]),
        },
        {
          key: 'ocr',
          nav: '4. OCR',
          title: getStageTitle(stageMeta, 'ocr', '4. 单元格 OCR'),
          description: getStageDescription(
            stageMeta,
            'ocr',
            '逐格查看 OCR 的原始文本和规范化结果，方便定位是哪一格出了问题。'
          ),
          note: '这里展示的是切出来的单元格图和对应文本，不再重复放原图、灰度图或二值图。',
          body: `
            ${renderWarningList(payload.warning_cells)}
            ${renderCellPreviewSection(cellRows)}
          `,
        },
        {
          key: 'correction',
          nav: '5. 纠错',
          title: getStageTitle(stageMeta, 'correction', '5. 动作纠错与组件补救'),
          description: getStageDescription(
            stageMeta,
            'correction',
            '对齐 App 端的动作格处理：先看 OCR 原文，再看字符修复、token 解析、组件判形和最终动作串。'
          ),
          note: '这一页展示动作格的解析状态。accepted 表示直接接受；corrected_or_normalized 表示发生字符修复或规范化；parser_low_confidence 会保留 fragment；multiline_or_layout_complex 表示由布局分析触发复杂格处理。',
          metrics: [
            `动作格 ${payload.summary.correction_cell_count || 0}`,
            `告警 ${payload.summary.warning_cell_count || 0}`,
            `多行复杂格 ${payload.summary.multiline_cell_count || 0}`,
          ],
          body: renderCorrectionCards(payload.correction_cells || []),
        },
        {
          key: 'multiline',
          nav: '6. 复杂格',
          title: getStageTitle(stageMeta, 'multiline', '6. 多行与复杂格处理'),
          description: getStageDescription(
            stageMeta,
            'multiline',
            '当一个格子里有多行动作时，会额外展示整格图和拆分后的子行图。'
          ),
          note: payload.multiline_cells.length
            ? '先看整格，再看拆出来的各行小图。这里主要解释多行格为什么能被单独处理。'
            : '当前这张图没有触发多行复杂格拆分。',
          body: renderMultilineCards(payload.multiline_cells),
        },
      ];
      const stageKeys = stages.map(stage => stage.key);
      const currentStageKey = stageKeys.includes(activeStageKey) ? activeStageKey : stages[0].key;

      return `
        <section class="process-shell">
          <div class="stage-tab-bar" role="tablist" aria-label="中间过程阶段切换">
            ${stages.map((stage, index) => `
              <button
                class="stage-tab${stage.key === currentStageKey ? ' active' : ''}"
                type="button"
                data-stage-target="${stage.key}"
                aria-selected="${stage.key === currentStageKey ? 'true' : 'false'}"
              >${escapeHtml(stage.nav)}</button>
            `).join('')}
          </div>
            ${stages.map((stage, index) => `
            <section class="stage-panel${stage.key === currentStageKey ? ' active' : ''}" data-stage-panel="${stage.key}">
              <div class="stage-header">
                <div>
                  <h3>${escapeHtml(stage.title)}</h3>
                  <p>${escapeHtml(stage.description)}</p>
                </div>
              </div>
              ${stage.note ? `<div class="stage-note">${escapeHtml(stage.note)}</div>` : ''}
              ${stage.metrics?.length ? `<div class="stage-metrics">${stage.metrics.map(item => `<span class="badge">${escapeHtml(item)}</span>`).join('')}</div>` : ''}
              ${stage.body}
            </section>
          `).join('')}
        </section>
      `;
    }

    function renderImageGrid(items) {
      const validItems = items.filter(Boolean);
      if (!validItems.length) {
        return '<div class="stage-note">这一阶段当前没有可展示的图片。</div>';
      }
      return `
        <div class="sequence-grid">
          ${validItems.map(item => `
            <figure class="sequence-card${item.wide ? ' wide' : ''}${item.extraClass ? ` ${item.extraClass}` : ''}">
              <img
                src="${item.url}"
                alt="${escapeHtml(item.label)}"
                loading="lazy"
                data-zoom-src="${item.url}"
                data-zoom-title="${escapeHtml(item.label)}"
                data-zoom-description="${escapeHtml(item.description || '')}"
              >
              <figcaption>
                <strong>${escapeHtml(item.label)}</strong>
                ${escapeHtml(item.description || '')}
              </figcaption>
            </figure>
          `).join('')}
        </div>
      `;
    }

    function renderCellRows(rows, includeText) {
      if (!rows.length) {
        return '<div class="stage-note">没有可展示的单元格。</div>';
      }
      return rows.map(row => `
        <section class="cell-row">
          <h4>第 ${row.row + 1} 行</h4>
          <div class="cell-grid">
            ${row.cells.map(cell => `
              <article class="cell-card">
                <img
                  src="${cell.image_url}"
                  alt="R${cell.row + 1}C${cell.col + 1}"
                  loading="lazy"
                  data-zoom-src="${cell.image_url}"
                  data-zoom-title="R${cell.row + 1}C${cell.col + 1}"
                  data-zoom-description="${escapeHtml(includeText ? `原始：${cell.raw_text || '空'} | 规范化：${cell.normalized_text || '空'}` : `单元格位置：第 ${cell.row + 1} 行，第 ${cell.col + 1} 列`)}"
                >
                <strong>R${cell.row + 1}C${cell.col + 1}</strong>
                ${includeText ? `
                  <span>原始：${escapeHtml(cell.raw_text || '空')}</span>
                  <span>规范化：${escapeHtml(cell.normalized_text || '空')}</span>
                ` : '<span>点击查看大图</span>'}
              </article>
            `).join('')}
          </div>
        </section>
      `).join('');
    }

    function renderMultilineCards(items) {
      if (!items.length) {
        return '<div class="stage-note">当前没有检测到需要额外拆分的多行复杂格。</div>';
      }
      return `
        <div class="complex-grid">
          ${items.map(item => `
            <article class="complex-card">
              <img
                src="${item.image_url}"
                alt="复杂单元格 ${item.row}-${item.col}"
                loading="lazy"
                data-zoom-src="${item.image_url}"
                data-zoom-title="第 ${item.row + 1} 行第 ${item.col + 1} 列"
                data-zoom-description="${escapeHtml(`原始：${item.raw_text || '空'} | 规范化：${item.normalized_text || '空'} | 分段数：${item.segment_count}`)}"
              >
              <div class="complex-meta">
                单元格：第 ${item.row + 1} 行，第 ${item.col + 1} 列<br>
                原始：${escapeHtml(item.raw_text || '空')}<br>
                规范化：${escapeHtml(item.normalized_text || '空')}<br>
                分段数：${escapeHtml(item.segment_count)}
              </div>
              ${item.line_urls.length ? `
                <div class="complex-lines">
                  ${item.line_urls.map((url, idx) => `
                    <img
                      src="${url}"
                      alt="拆分行 ${idx + 1}"
                      loading="lazy"
                      data-zoom-src="${url}"
                      data-zoom-title="拆分行 ${idx + 1}"
                      data-zoom-description="这是从复杂单元格里拆出来的子行图。"
                    >
                  `).join('')}
                </div>
              ` : ''}
            </article>
          `).join('')}
        </div>
      `;
    }

    function renderCellPreviewSection(cellRows) {
      if (!cellRows.length) {
        return '<div class="stage-note">没有可展示的单元格。</div>';
      }
      const visibleRows = cellRows.slice(0, currentCellRowLimit);
      const hiddenCount = Math.max(0, cellRows.length - visibleRows.length);
      const controls = hiddenCount > 0 ? `
        <div class="stage-metrics">
          <span class="badge">当前展示 ${visibleRows.length} / ${cellRows.length} 行</span>
          <button class="stage-tab" type="button" data-action="more-cells">再看 ${Math.min(CELL_ROW_BATCH_SIZE, hiddenCount)} 行</button>
          <button class="stage-tab" type="button" data-action="all-cells">显示全部 ${cellRows.length} 行</button>
        </div>
      ` : `
        <div class="stage-metrics">
          <span class="badge">当前展示全部 ${cellRows.length} 行</span>
        </div>
      `;
      return `
        ${controls}
        <div class="cell-rows">${renderCellRows(visibleRows, true)}</div>
      `;
    }

    function renderWarningList(items) {
      if (!items.length) {
        return '';
      }
      return `
        <section class="warning-list">
          <ul>
            ${items.map(item => `
              <li>R${item.row + 1}C${item.col + 1}：原始 ${escapeHtml(item.raw_text || '空')}，规范化 ${escapeHtml(item.normalized_text || '空')}</li>
            `).join('')}
          </ul>
        </section>
      `;
    }

    function renderCorrectionCards(items) {
      if (!items.length) {
        return '<div class="stage-note">当前没有动作格纠错记录。</div>';
      }
      return `
        <div class="correction-grid">
          ${items.map(item => `
            <article class="correction-card">
              ${item.image_url ? `
                <img
                  src="${item.image_url}"
                  alt="动作格 R${item.row + 1}C${item.col + 1}"
                  loading="lazy"
                  data-zoom-src="${item.image_url}"
                  data-zoom-title="动作格 R${item.row + 1}C${item.col + 1}"
                  data-zoom-description="${escapeHtml(`原始：${item.raw_text || '空'} | 结果：${item.normalized_text || '空'} | ${item.note || ''}`)}"
                >
              ` : ''}
              <h4>R${item.row + 1}C${item.col + 1}</h4>
              <p>
                <span class="token">raw: ${escapeHtml(item.raw_text || '空')}</span>
                <span class="token">out: ${escapeHtml(item.normalized_text || '空')}</span><br>
                置信度：${escapeHtml(item.confidence || 'none')}，
                完整：${item.is_complete ? '是' : '否'}，
                修复：${item.was_fixed ? '是' : '否'}<br>
                fragment：${escapeHtml(item.fragment || '无')}<br>
                分段数：${escapeHtml(item.segment_count ?? 0)}，
                note：${escapeHtml(item.note || '')}
              </p>
            </article>
          `).join('')}
        </div>
      `;
    }

    function pickImage(imageMap, name) {
      return imageMap.get(name) || null;
    }

    function wideImage(item, extraClass = '') {
      if (!item) return null;
      return { ...item, wide: true, extraClass };
    }

    function getStageTitle(stageMeta, key, fallback) {
      return stageMeta.get(key)?.title || fallback;
    }

    function getStageDescription(stageMeta, key, fallback) {
      return stageMeta.get(key)?.description || fallback;
    }

    function activateStage(shell, key) {
      if (!shell) return;
      activeStageKey = key;
      shell.querySelectorAll('[data-stage-target]').forEach(tab => {
        const active = tab.dataset.stageTarget === key;
        tab.classList.toggle('active', active);
        tab.setAttribute('aria-selected', active ? 'true' : 'false');
      });
      shell.querySelectorAll('[data-stage-panel]').forEach(panel => {
        panel.classList.toggle('active', panel.dataset.stagePanel === key);
      });
    }

    function openImageModal(src, title, description) {
      if (!src) return;
      modalImage.src = src;
      modalTitle.textContent = title;
      modalDescription.textContent = description;
      imageModal.classList.add('open');
      imageModal.setAttribute('aria-hidden', 'false');
    }

    function closeImageModal() {
      imageModal.classList.remove('open');
      imageModal.setAttribute('aria-hidden', 'true');
      modalImage.removeAttribute('src');
    }

    function escapeHtml(value) {
      return String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[ch]));
    }
  </script>
</body>
</html>
"""


def _collect_debug_images(run_id: str, profile_key: str, debug_dir: Path) -> list[dict[str, str]]:
    images: list[dict[str, str]] = []
    for name, meta in sorted(DEBUG_IMAGE_LABELS.items(), key=lambda item: item[1].get("order", 0)):
        path = debug_dir / name
        if path.is_file():
            images.append(
                {
                    "name": name,
                    "label": meta["label"],
                    "description": meta["description"],
                    "step": meta["step"],
                    "order": meta.get("order", 0),
                    "url": f"/runs/{run_id}/{profile_key}/debug/{name}",
                }
            )
    return images


def _build_dynamic_cell_crop_url(run_id: str, profile_key: str, row: int, col: int) -> str:
    return f"/runs/{run_id}/{profile_key}/cells/r{row}_c{col}.png"


def _load_summary(run_id: str, profile_key: str, debug_dir: Path) -> dict[str, object]:
    path = debug_dir / "summary.json"
    if not path.is_file():
        return {}
    def _with_urls(items: list[dict[str, object]]) -> list[dict[str, object]]:
        enriched: list[dict[str, object]] = []
        for item in items:
            enriched.append(
                {
                    **item,
                    "image_url": f"/runs/{run_id}/{profile_key}/debug/{item['image']}" if item.get("image") else "",
                    "line_urls": [
                        f"/runs/{run_id}/{profile_key}/debug/{name}"
                        for name in item.get("lines", [])
                    ],
                }
            )
        return enriched
    summary = json.loads(path.read_text(encoding="utf-8"))
    for cell in summary.get("cells", []):
        cell["image_url"] = _build_dynamic_cell_crop_url(
            run_id,
            profile_key,
            int(cell.get("row", 0)),
            int(cell.get("col", 0)),
        )
    for cell in summary.get("correction_cells", []):
        cell["image_url"] = _build_dynamic_cell_crop_url(
            run_id,
            profile_key,
            int(cell.get("row", 0)),
            int(cell.get("col", 0)),
        )
    summary["multiline_cells"] = _with_urls(summary.get("multiline_cells", []))
    summary["warning_cells"] = _with_urls(summary.get("warning_cells", []))
    return summary


def _build_diff(reference_rows, candidate_rows) -> dict[str, object]:
    diffs: list[str] = []
    max_rows = max(len(reference_rows), len(candidate_rows))
    for row_index in range(max_rows):
        ref_row = reference_rows[row_index] if row_index < len(reference_rows) else None
        cur_row = candidate_rows[row_index] if row_index < len(candidate_rows) else None
        if ref_row is None or cur_row is None:
            diffs.append(f"第 {row_index + 1} 行存在与否发生变化")
            continue

        if ref_row.round_label != cur_row.round_label:
            diffs.append(
                f"第 {row_index + 1} 行回合: {ref_row.round_label or '空'} -> {cur_row.round_label or '空'}"
            )
        for col_index, (left, right) in enumerate(zip(ref_row.actions, cur_row.actions), start=1):
            if left != right:
                diffs.append(
                    f"第 {row_index + 1} 行列 {col_index}: {left or '空'} -> {right or '空'}"
                )

    if not diffs:
        return {
            "matches_full": True,
            "summary": "与完整流程输出一致",
            "examples": [],
        }
    return {
        "matches_full": False,
        "summary": f"与完整流程相比有 {len(diffs)} 处差异",
        "examples": diffs[:4],
    }


def _run_profile(run_id: str, run_dir: Path, input_path: Path, profile: dict[str, object]) -> dict[str, object]:
    profile_key = str(profile["key"])
    profile_dir = run_dir / profile_key
    debug_dir = profile_dir / "debug"
    output_path = profile_dir / "result.csv"
    profile_dir.mkdir(parents=True, exist_ok=True)

    rows = run_pipeline(
        PipelineConfig(
            input_path=str(input_path),
            output_path=str(output_path),
            tesseract_cmd=None,
            debug_dir=str(debug_dir),
            profile_name=profile_key,
            **dict(profile.get("options", {})),
        )
    )
    return {
        "key": profile_key,
        "label": profile["label"],
        "description": profile["description"],
        "rows": rows,
        "csv_url": f"/runs/{run_id}/{profile_key}/result.csv",
        "debug_images": _collect_debug_images(run_id, profile_key, debug_dir),
        "summary": _load_summary(run_id, profile_key, debug_dir),
    }


def _run_raw_text_comparison(input_path: Path) -> dict[str, object]:
    source_image = load_image(str(input_path))
    raw_text = ocr_full_image_raw_text(source_image)
    return {
        "key": RAW_TEXT_COMPARISON_PROFILE["key"],
        "label": RAW_TEXT_COMPARISON_PROFILE["label"],
        "description": RAW_TEXT_COMPARISON_PROFILE["description"],
        "mode": "raw_text",
        "raw_text": raw_text,
        "diff": {
            "matches_full": False,
            "summary": "不做任何预处理，直接对整张图做 OCR 原始文本识别",
            "examples": [],
        },
    }


class TableOcrHandler(SimpleHTTPRequestHandler):
    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/":
            self._send_bytes(INDEX_HTML.encode("utf-8"), "text/html; charset=utf-8")
            return
        if parsed.path.startswith("/runs/"):
            self._serve_run_file(parsed.path)
            return
        self.send_error(HTTPStatus.NOT_FOUND, "Not found")

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path != "/api/recognize":
            self.send_error(HTTPStatus.NOT_FOUND, "Not found")
            return
        try:
            payload = self._handle_recognize()
        except Exception as exc:  # pragma: no cover - user-facing diagnostics
            self._send_json({"error": str(exc)}, status=HTTPStatus.INTERNAL_SERVER_ERROR)
            return
        self._send_json(payload)

    def _handle_recognize(self) -> dict[str, object]:
        form = cgi.FieldStorage(
            fp=self.rfile,
            headers=self.headers,
            environ={
                "REQUEST_METHOD": "POST",
                "CONTENT_TYPE": self.headers.get("Content-Type", ""),
            },
        )
        field = form["image"] if "image" in form else None
        if field is None or not getattr(field, "filename", ""):
            raise ValueError("请上传截图文件")

        run_id = uuid.uuid4().hex[:12]
        run_dir = RUNS_DIR / run_id
        run_dir.mkdir(parents=True, exist_ok=True)

        suffix = Path(field.filename).suffix.lower()
        if suffix not in {".png", ".jpg", ".jpeg", ".bmp", ".webp"}:
            suffix = ".png"
        input_path = run_dir / f"input{suffix}"

        with input_path.open("wb") as handle:
            shutil.copyfileobj(field.file, handle)

        profile_results = [_run_profile(run_id, run_dir, input_path, profile) for profile in PROFILE_PRESETS]
        full_profile = next(item for item in profile_results if item["key"] == "full")
        full_rows = full_profile["rows"]

        comparisons = []
        for item in profile_results:
            rows = item["rows"]
            comparisons.append(
                {
                    "key": item["key"],
                    "label": item["label"],
                    "description": item["description"],
                    "mode": "table",
                    "rows": [asdict(row) for row in rows],
                    "csv_url": item["csv_url"],
                    "diff": (
                        {
                            "matches_full": True,
                            "summary": "当前基线输出",
                            "examples": [],
                        }
                        if item["key"] == "full"
                        else _build_diff(full_rows, rows)
                    ),
                }
            )
        comparisons.append(_run_raw_text_comparison(input_path))

        return {
            "run_id": run_id,
            "rows": [asdict(row) for row in full_rows],
            "csv_url": full_profile["csv_url"],
            "input_url": f"/runs/{run_id}/{input_path.name}",
            "debug_images": full_profile["debug_images"],
            "summary": full_profile["summary"],
            "multiline_cells": full_profile["summary"].get("multiline_cells", []),
            "warning_cells": full_profile["summary"].get("warning_cells", []),
            "correction_cells": full_profile["summary"].get("correction_cells", []),
            "comparisons": comparisons,
        }

    def _serve_run_file(self, request_path: str) -> None:
        relative = unquote(request_path.removeprefix("/runs/"))
        crop_match = CELL_CROP_PATH_RE.match(relative)
        if crop_match:
            self._serve_dynamic_cell_crop(
                run_id=crop_match.group(1),
                profile_key=crop_match.group(2),
                row=int(crop_match.group(3)),
                col=int(crop_match.group(4)),
            )
            return
        target = (RUNS_DIR / relative).resolve()
        if not str(target).startswith(str(RUNS_DIR.resolve())) or not target.is_file():
            self.send_error(HTTPStatus.NOT_FOUND, "Not found")
            return
        content_type = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        self._send_bytes(target.read_bytes(), content_type)

    def _serve_dynamic_cell_crop(
        self,
        run_id: str,
        profile_key: str,
        row: int,
        col: int,
    ) -> None:
        debug_dir = RUNS_DIR / run_id / profile_key / "debug"
        summary_path = debug_dir / "summary.json"
        table_region_path = debug_dir / "table_region.png"
        if not summary_path.is_file() or not table_region_path.is_file():
            self.send_error(HTTPStatus.NOT_FOUND, "Not found")
            return

        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        cell = next(
            (
                item
                for item in summary.get("cells", [])
                if int(item.get("row", -1)) == row and int(item.get("col", -1)) == col
            ),
            None,
        )
        if cell is None:
            self.send_error(HTTPStatus.NOT_FOUND, "Not found")
            return

        table_image = load_image(str(table_region_path))
        x = max(0, int(cell.get("x", 0)))
        y = max(0, int(cell.get("y", 0)))
        w = max(1, int(cell.get("w", 1)))
        h = max(1, int(cell.get("h", 1)))
        crop = table_image[y : y + h, x : x + w].copy()
        if crop.size == 0:
            self.send_error(HTTPStatus.NOT_FOUND, "Not found")
            return

        success, encoded = cv2.imencode(".png", crop)
        if not success:
            self.send_error(HTTPStatus.INTERNAL_SERVER_ERROR, "Failed to encode image")
            return
        self._send_bytes(encoded.tobytes(), "image/png")

    def _send_json(self, payload: dict[str, object], status: HTTPStatus = HTTPStatus.OK) -> None:
        self._send_bytes(
            json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            "application/json; charset=utf-8",
            status,
        )

    def _send_bytes(
        self,
        data: bytes,
        content_type: str,
        status: HTTPStatus = HTTPStatus.OK,
    ) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main() -> int:
    RUNS_DIR.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer((HOST, PORT), TableOcrHandler)
    print(f"攻略表格 OCR 工作台已启动: http://{HOST}:{PORT}")
    print("按 Ctrl+C 停止服务。")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n服务已停止。")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
