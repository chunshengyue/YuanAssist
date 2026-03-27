import requests
import auth

# 百度 API 地址常量
URL_TABLE = "https://aip.baidubce.com/rest/2.0/ocr/v1/table"
URL_ACCURATE = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate"
URL_BASIC = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic"


def call_ocr(mode, account, image_base64):
    """
    实际调用百度 API
    mode: 'table' | 'accurate' | 'basic'
    account: {"ak":..., "sk":...}
    """
    token = auth.get_access_token(account["ak"], account["sk"])
    if not token:
        return {"error_code": -1, "error_msg": "Failed to get access token"}

    url = ""
    data = {"image": image_base64}

    if mode == "table":
        url = URL_TABLE
        data["cell_contents"] = "false"  # 你示例里是 false
        data["return_excel"] = "false"
    elif mode == "accurate":
        url = URL_ACCURATE
        data["detect_direction"] = "false"
        data["vertexes_location"] = "false"  # 你示例里是 false
        data["paragraph"] = "false"
    elif mode == "basic":
        url = URL_BASIC
        data["detect_direction"] = "false"

    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "application/json"
    }
    params = {"access_token": token}

    try:
        # 注意：百度 OCR 推荐用 application/x-www-form-urlencoded
        # requests 的 data 参数会自动处理
        resp = requests.post(url, params=params, data=data, headers=headers, timeout=10)
        return resp.json()
    except Exception as e:
        return {"error_code": -2, "error_msg": str(e)}
