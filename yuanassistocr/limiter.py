import time
from datetime import datetime
import requests
import config

# ================= 配置區域 =================
DAILY_DEVICE_LIMIT = 20  # 每個設備每天最多 20 次 (取消全域限額)
BMOB_CLASS_NAME = "OcrUsage" # Bmob 上的表名，存放使用紀錄
BMOB_BASE_URL = "https://api.bmobcloud.com/1/classes" # 使用更通用、解析度更高的 Bmob API 域名
# ==========================================

# 降級方案用的記憶體紀錄 (當 Bmob 網路異常時兜底)
from collections import defaultdict
_fallback_requests = defaultdict(list)

def _get_headers():
    return {
        "X-Bmob-Application-Id": config.BMOB_APP_ID,
        "X-Bmob-REST-API-Key": config.BMOB_REST_API_KEY,
        "Content-Type": "application/json"
    }

def can_request(device_id: str) -> bool:
    """ 第一步：檢查是否有調用資格（只查詢，不增加次數） """
    if not device_id:
        return True

    if config.BMOB_APP_ID and config.BMOB_REST_API_KEY:
        try:
            today = datetime.now().strftime("%Y-%m-%d")

            # 使用 Bmob 的 where 查詢條件，查找這個 deviceId 今天是否有紀錄
            import urllib.parse
            import json
            where = {
                "deviceId": device_id,
                "date": today
            }
            query_str = urllib.parse.quote(json.dumps(where))
            url = f"{BMOB_BASE_URL}/{BMOB_CLASS_NAME}?where={query_str}&limit=1"

            response = requests.get(url, headers=_get_headers(), timeout=5)
            if response.status_code == 200:
                data = response.json()
                results = data.get("results", [])

                if results:
                    usage = results[0].get("usageCount", 0)
                    if usage >= DAILY_DEVICE_LIMIT:
                        print(f"🚫 設備 {device_id} 今日已達上限 ({usage}/{DAILY_DEVICE_LIMIT})")
                        return False
                return True
            else:
                print(f"Bmob 查詢失敗: HTTP {response.status_code} - {response.text}")
        except Exception as e:
            print(f"Bmob 請求異常: {e}")
            pass

    # 降級防連擊：如果 Bmob 沒設定或是掛了，只防 1 分鐘內的惡意請求
    now = time.time()
    valid_history = [t for t in _fallback_requests[device_id] if t > now - 60]
    if len(valid_history) >= 10:
        return False
    return True


def record_success(device_id: str):
    """ 第二步：只有在 OCR 成功後才呼叫此函數，實際扣除額度 (增加紀錄) """
    if not device_id:
        return

    if config.BMOB_APP_ID and config.BMOB_REST_API_KEY:
        try:
            today = datetime.now().strftime("%Y-%m-%d")
            import urllib.parse
            import json

            # 1. 先查有沒有今天的紀錄
            where = {"deviceId": device_id, "date": today}
            query_str = urllib.parse.quote(json.dumps(where))
            url = f"{BMOB_BASE_URL}/{BMOB_CLASS_NAME}?where={query_str}&limit=1"

            response = requests.get(url, headers=_get_headers(), timeout=5)
            if response.status_code == 200:
                data = response.json()
                results = data.get("results", [])

                if results:
                    # 已經有這天的紀錄了，用 PUT 更新次數 (原子操作增加)
                    record_id = results[0]["objectId"]
                    update_url = f"{BMOB_BASE_URL}/{BMOB_CLASS_NAME}/{record_id}"
                    payload = {
                        "usageCount": {"__op": "Increment", "amount": 1}
                    }
                    requests.put(update_url, headers=_get_headers(), json=payload, timeout=5)
                    print(f"✅ Bmob 額度更新成功 (ID: {record_id})")
                else:
                    # 今天還沒有紀錄，用 POST 新增一條
                    create_url = f"{BMOB_BASE_URL}/{BMOB_CLASS_NAME}"
                    payload = {
                        "deviceId": device_id,
                        "date": today,
                        "usageCount": 1
                    }
                    requests.post(create_url, headers=_get_headers(), json=payload, timeout=5)
                    print(f"✅ Bmob 額度創建成功 (Device: {device_id})")
            else:
                 print(f"Bmob 查詢扣款失敗: HTTP {response.status_code}")
        except Exception as e:
            print(f"Bmob 扣除額度異常: {e}")
    else:
        now = time.time()
        _fallback_requests[device_id].append(now)