from fastapi import FastAPI, Header, HTTPException, Form
from pydantic import BaseModel
import sys
import os
import re

# 將當前檔案所在的目錄加入到 Python 的搜尋路徑中
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import strategy
import limiter
import config

app = FastAPI()


class OcrRequest(BaseModel):
    image: str


# ==========================================
# 💡 核心清洗邏輯
# ==========================================
def clean_action_text(text):
    """清洗格子內的文字，只保留 0-9, A, ↑, ↓, 圈"""
    if not text:
        return "-"
    # 替換掉所有不在允許列表內的字元
    cleaned = re.sub(r'[^0-9A↑↓圈]', '', text)
    # 如果清洗完後變成空字串，就回傳占位符 "-"
    return cleaned if cleaned else "-"


def parse_baidu_table(json_data):
    if "tables_result" not in json_data or not json_data["tables_result"]: return None
    table = json_data["tables_result"][0]
    body = table.get("body", [])
    rows_map = {}

    for cell in body:
        r, c, w = cell["row_start"], cell["col_start"], cell["words"]
        # 先做基礎去頭尾空格與換行
        w = w.replace("\n", "").strip()
        if r not in rows_map: rows_map[r] = {}
        rows_map[r][c] = w

    output_lines = []
    for r_idx in sorted(rows_map.keys()):
        row_data = rows_map[r_idx]
        col0 = row_data.get(0, "")

        # 提取回合數 (必須包含數字)
        match_col0 = re.search(r'\d+', col0)
        if not match_col0:
            continue
        turn_num = match_col0.group()

        # 處理第 1 到第 5 列的動作
        actions = []
        for c in range(1, 6):
            raw_text = row_data.get(c, "")
            # 💡 呼叫清洗函數：去雜字 + 補占位符
            cleaned_text = clean_action_text(raw_text)
            actions.append(cleaned_text)

        # 💡 空行剔除檢查：如果 5 個動作全都是 "-"，代表這行沒有操作，直接跳過
        if all(a == "-" for a in actions):
            continue

        # 拼接成最終文字，列與列之間用空格隔開
        output_lines.append(f"{turn_num} {' '.join(actions)}")

    return "\n".join(output_lines)


def parse_baidu_general(json_data):
    if "words_result" not in json_data: return None
    words_list = json_data["words_result"]
    if not words_list: return ""

    # 模式 1: 高精度含位置 (利用座標分行)
    if "location" in words_list[0]:
        total_height = sum([w['location']['height'] for w in words_list])
        row_threshold = (total_height / len(words_list)) * 0.6
        sorted_words = sorted(words_list, key=lambda x: x["location"]["top"])

        rows = []
        current_row = []
        current_row_top = 0
        for item in sorted_words:
            top = item["location"]["top"]
            if not current_row:
                current_row.append(item)
                current_row_top = top
            else:
                if abs(top - current_row_top) < row_threshold:
                    current_row.append(item)
                else:
                    rows.append(current_row)
                    current_row = [item]
                    current_row_top = top
        if current_row: rows.append(current_row)

        output_lines = []
        for row in rows:
            row.sort(key=lambda x: x["location"]["left"])
            texts = [x["words"].replace("\n", "").strip() for x in row]

            if not texts: continue

            # 判斷第一列是否為回合數
            match_col0 = re.search(r'\d+', texts[0])
            if not match_col0:
                continue
            turn_num = match_col0.group()

            # 處理後面的動作列
            raw_actions = texts[1:]
            actions = []
            for idx in range(5):
                if idx < len(raw_actions):
                    actions.append(clean_action_text(raw_actions[idx]))
                else:
                    actions.append("-")  # 不夠的列用 "-" 補齊

            # 💡 空行剔除檢查
            if all(a == "-" for a in actions):
                continue

            output_lines.append(f"{turn_num} {' '.join(actions)}")

        return "\n".join(output_lines)

    # 模式2 (無位置版) 兜底：簡單用空格拼接
    texts = [w["words"].strip() for w in words_list]
    return " ".join(texts)


# ==========================================
# API 路由區
# ==========================================

@app.get("/")
@app.get("/release")
@app.get("/release/")
def home():
    return {"status": "running", "service": "OCR-Backend"}


@app.post("/ocr")
@app.post("/release/ocr")
@app.post("/release/ocr/")
def ocr_endpoint(
        image: str = Form(...),
        force_mode: int = Form(None),
        response_mode: str = Form("parsed"),
        x_device_id: str = Header(None, alias="X-Device-ID"),
        x_api_secret: str = Header(None, alias="X-Api-Secret")
):
    # 1. 簡單鑒權
    if config.API_SECRET and x_api_secret != config.API_SECRET:
        raise HTTPException(status_code=403, detail="Invalid API Secret")

    # 2. 💡 限流檢查：只查不扣 (超過回傳自訂錯誤，不拋 429 Exception，讓安卓能彈出友善提示)
    if not limiter.can_request(x_device_id):
        return {
            "error": True,
            "error_code": 429,
            "error_msg": "Too Many Requests or Quota Exceeded",
            "suggestion": "今日免費識別額度已用完，請明天再來嘗試"
        }

    if not image:
        raise HTTPException(status_code=400, detail="Image is required")

    # 3. 執行策略 (呼叫百度 OCR)
    result = strategy.execute_strategy(image, force_mode)

    # 💡 檢查是否發生了錯誤 (例如並發受限、圖片過大超時)
    if result.get("error"):
        return result

    # 4. 💡 萬事大吉！只有百度真實返回了成功數據，才去 Redis 裡把次數 +1
    limiter.record_success(x_device_id)

    if response_mode == "raw":
        raw_payload = {
            "status": "success",
            "_strategy_used": result.get("_strategy_used")
        }
        if "tables_result" in result:
            raw_payload["tables_result"] = result.get("tables_result", [])
        if "words_result" in result:
            raw_payload["words_result"] = result.get("words_result", [])
        if "words_result_num" in result:
            raw_payload["words_result_num"] = result.get("words_result_num", 0)
        if "log_id" in result:
            raw_payload["log_id"] = result.get("log_id")
        return raw_payload

    # 5. 執行數據清洗
    parsed_str = ""
    if "tables_result" in result:
        parsed_str = parse_baidu_table(result)
    elif "words_result" in result:
        parsed_str = parse_baidu_general(result)

    # 6. 返回標準化成功格式給安卓端
    return {
        "status": "success",
        "_strategy_used": result.get("_strategy_used"),
        "parsed_text": parsed_str
    }
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=9000)
