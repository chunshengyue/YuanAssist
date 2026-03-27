import time
import requests

# 内存缓存 Token: { "ak": {"token": "xxx", "expire_at": 171...} }
_token_cache = {}


def get_access_token(ak, sk):
    global _token_cache
    now = time.time()

    # 1. 检查缓存
    if ak in _token_cache:
        data = _token_cache[ak]
        if data["expire_at"] > now + 600:  # 提前 10 分钟刷新
            return data["token"]

    # 2. 请求百度获取新 Token
    url = "https://aip.baidubce.com/oauth/2.0/token"
    params = {
        "grant_type": "client_credentials",
        "client_id": ak,
        "client_secret": sk
    }

    try:
        resp = requests.post(url, params=params, timeout=5)
        if resp.status_code == 200:
            data = resp.json()
            if "access_token" in data:
                token = data["access_token"]
                expires_in = data.get("expires_in", 2592000)  # 默认30天

                # 更新缓存
                _token_cache[ak] = {
                    "token": token,
                    "expire_at": now + expires_in
                }
                return token
            else:
                print(f"[Auth] Failed to get token for {ak[:6]}...: {data}")
        else:
            print(f"[Auth] HTTP Error {resp.status_code}")
    except Exception as e:
        print(f"[Auth] Exception: {e}")

    return None
