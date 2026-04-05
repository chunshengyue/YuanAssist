"""
飞书数据同步共享模块
包含所有通用的、可复用的代码，如 API 配置、token 管理、文件下载、JSON 写入等函数。
"""

import os, json, pathlib, requests, time, re
from urllib.parse import urlparse

# === 0. 基础配置（强烈建议用环境变量读取） ===
APP_ID     = os.getenv("FEISHU_APP_ID",     "")
APP_SECRET = os.getenv("FEISHU_APP_SECRET", "")
BASE_URL   = "https://open.feishu.cn"
SITE_DATA_DIR = pathlib.Path(__file__).parent.parent / "resource"
FEISHU_IMAGE_DIR = pathlib.Path(__file__).parent.parent / "public" / "images" / "feishu"

TABLES = {
    "levels":       dict(cn_name="关卡数据",   app="IquLbb1sVaV3ljsPhaPcxbmVnbb", tbl="tblywzqIAWTwZstE"),
    "operators":       dict(cn_name="密探数据",   app="IquLbb1sVaV3ljsPhaPcxbmVnbb", tbl="tblqJZBK1eaz7idg"),
}

# === 1. 飞书 API 封装 ===

def get_tenant_token() -> str:
    """获取 tenant_access_token"""
    url  = f"{BASE_URL}/open-apis/auth/v3/tenant_access_token/internal"
    body = {"app_id": APP_ID, "app_secret": APP_SECRET}
    r = requests.post(url, json=body, timeout=10)
    r.raise_for_status()
    return r.json()["tenant_access_token"]

def list_records(app_token: str, table_id: str, token: str, sort_field: str = None):
    """获取一个表的所有记录（会自动处理分页）"""
    all_records = []
    page_token = ""
    while True:
        params = {"page_size": 500}
        if page_token:
            params["page_token"] = page_token

        # 添加排序参数，按照记录创建时间或指定字段排序
        if sort_field:
            params["sort"] = f'[{{"field_name":"{sort_field}","desc":false}}]'

        url = f"{BASE_URL}/open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records"
        headers = {"Authorization": f"Bearer {token}"}
        r = requests.get(url, headers=headers, params=params, timeout=30)
        print(r.json())
        r.raise_for_status()
        resp_data = r.json()

        if resp_data.get("code", 0) != 0:
            print(f"Error from Feishu API: {resp_data.get('msg')} (code: {resp_data.get('code')})")
            # 打印一些上下文帮助调试
            print(f"  → Request URL: {r.request.url}")
            print(f"  → App Token used: {app_token}")
            print(f"  → Table ID used: {table_id}")
            break

        data = resp_data.get("data", {})
        items = data.get("items", [])
        if items:
            all_records.extend(items)

        if data.get("has_more"):
            page_token = data.get("page_token")
        else:
            break
        time.sleep(0.2) # 避免频率超限
    return all_records

def download_feishu_file(url: str, token: str, table_name: str) -> str | None:
    """
    从飞书下载文件并保存到本地。
    返回可公开访问的 URL 路径。
    """
    if not url:
        return None

    headers = {"Authorization": f"Bearer {token}"}

    try:
        with requests.get(url, headers=headers, stream=True, timeout=30) as r:
            r.raise_for_status()

            parsed_url = urlparse(url)
            file_token = parsed_url.path.split('/')[-2]

            content_disposition = r.headers.get('Content-Disposition', "")
            filename_match = re.search(r'filename="(.+)"', content_disposition)

            original_filename = ""
            if filename_match:
                original_filename = filename_match.group(1)
                # 确保文件名有扩展名
                if not pathlib.Path(original_filename).suffix:
                    content_type = r.headers.get('Content-Type', 'image/png')
                    extension = f".{content_type.split('/')[-1]}"
                    original_filename += extension
            else:
                content_type = r.headers.get('Content-Type', 'image/png')
                extension = f".{content_type.split('/')[-1]}"
                original_filename = f"download{extension}"

            # 清理文件名，防止路径问题
            safe_filename = re.sub(r'[\\/*?:"<>|]', "", original_filename)
            local_filename = f"{file_token}-{safe_filename}"

            table_image_dir = FEISHU_IMAGE_DIR / table_name
            table_image_dir.mkdir(exist_ok=True, parents=True)
            save_path = table_image_dir / local_filename

            with open(save_path, 'wb') as f:
                for chunk in r.iter_content(chunk_size=8192):
                    f.write(chunk)

                print(f"  → Downloaded {url} to {save_path}")

                return f"/images/feishu/{table_name}/{local_filename}"
    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 403:
             print(f"  → Error 403: Forbidden to download file from {url}. "
                   f"Please ensure the app has 'drive:file:readonly' permission and the file is shared correctly.")
        else:
            print(f"  → An HTTP error occurred: {e}")
        return None

# === 2. 辅助函数 ===

def write_json_file(data: any, filename: str):
    """将数据写入指定的 JSON 文件"""
    outfile = SITE_DATA_DIR / filename
    outfile.parent.mkdir(parents=True, exist_ok=True)
    outfile.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    count_info = ""
    if isinstance(data, list):
        count_info = f" ({len(data)} items)"
    print(f"✔ Generated {outfile}{count_info}")

def fetch_records(table_key: str, token: str, sort_field: str = None) -> list:
    """根据 table key 获取一个表的所有记录"""
    table_info = TABLES[table_key]
    print(f"Fetching {table_info['cn_name']}...")
    records = list_records(table_info["app"], table_info["tbl"], token, sort_field)
    print(f"  → Fetched {len(records)} records.")
    return records