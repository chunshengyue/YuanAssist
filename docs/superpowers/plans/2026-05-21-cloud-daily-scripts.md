# Cloud Daily Scripts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a cloud sharing flow for recorded daily scripts, using Supabase Storage for script bundles and image-bed URLs for the "图片指引" guide module.

**Architecture:** Add a Supabase-backed `cloud_daily_scripts` resource exposed through the existing `yuanassist-api-v3` Edge Function. Android packages `UserDailyScriptStore` bundles as zip files, uploads them through signed Storage URLs, lists/details scripts through `SupabaseRepository`, and reuses the existing local daily script import path after download and unzip.

**Tech Stack:** Android Kotlin, View-based Activities matching JobStation style, OkHttp, Gson, Supabase Edge Functions, Supabase Storage, JUnit.

---

## File Map

- Modify `supabase/functions/yuanassist-api-v3/index.ts`: add cloud daily script row types, mappers, Storage signed upload/download helpers, and six new actions.
- Create `supabase/cloud_daily_scripts_schema.sql`: SQL for manual Supabase setup of table, indexes, and Storage bucket.
- Modify `app/src/main/java/com/example/yuanassist/model/StrategyModel.kt`: add `cloud_daily_script` and small response models.
- Modify `app/src/main/java/com/example/yuanassist/network/SupabaseRepository.kt`: add cloud daily script API methods and raw upload/download helpers.
- Create `app/src/main/java/com/example/yuanassist/utils/DailyScriptBundleZipStore.kt`: package and unpack `UserDailyScriptBundle` zip files safely.
- Test `app/src/test/java/com/example/yuanassist/utils/DailyScriptBundleZipStoreTest.kt`: zip round trip and zip-slip rejection.
- Create `app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptListActivity.kt`: list/search/sort/upload entry.
- Create `app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptListAdapter.kt`: RecyclerView cards.
- Create `app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptActivity.kt`: detail page with guide images and download/import buttons.
- Create `app/src/main/java/com/example/yuanassist/ui/UploadCloudDailyScriptActivity.kt`: upload form with local bundle picker and guide image picker.
- Create layouts: `activity_cloud_daily_script_list.xml`, `item_cloud_daily_script.xml`, `activity_cloud_daily_script.xml`, `activity_upload_cloud_daily_script.xml`.
- Modify `app/src/main/AndroidManifest.xml`: register new activities.
- Modify `app/src/main/java/com/example/yuanassist/ui/main/MainShellActions.kt`, `HomeTabScreen.kt`, and `MainActivity.kt`: add the homepage entry.
- Modify `docs/project_context.md`: record the cloud daily scripts module.

## Task 1: Supabase Schema And Edge Actions

**Files:**
- Create: `supabase/cloud_daily_scripts_schema.sql`
- Modify: `supabase/functions/yuanassist-api-v3/index.ts`

- [ ] **Step 1: Create schema SQL**

Create `supabase/cloud_daily_scripts_schema.sql` with:

```sql
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
```

- [ ] **Step 2: Add Edge Function row types and constants**

In `supabase/functions/yuanassist-api-v3/index.ts`, after `FeedbackRow`, add:

```ts
type CloudDailyScriptRow = {
  id: string;
  object_id: string | null;
  author_id: string | null;
  title: string | null;
  description: string | null;
  tags: string | null;
  guide_images: string | null;
  bundle_path: string | null;
  bundle_size: number | null;
  task_count: number | null;
  download_count: number | null;
  status: string | null;
  created_at: string | null;
  updated_at: string | null;
};

const DAILY_SCRIPT_BUCKET = "daily-script-bundles";
const DAILY_SCRIPT_SELECT =
  "id, object_id, author_id, title, description, tags, guide_images, bundle_path, bundle_size, task_count, download_count, status, created_at, updated_at";
```

- [ ] **Step 3: Add Edge Function helper functions**

After `mapOcrConfig`, add:

```ts
function normalizeJsonArrayString(value: unknown): string {
  if (Array.isArray(value)) {
    return JSON.stringify(value.map((item) => String(item)).filter((item) => item.trim()));
  }
  const text = String(value ?? "").trim();
  if (!text) return "[]";
  try {
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? JSON.stringify(parsed.map((item) => String(item)).filter((item) => item.trim())) : "[]";
  } catch {
    return "[]";
  }
}

function mapCloudDailyScript(row: CloudDailyScriptRow, author: UserRow | null | undefined) {
  return {
    objectId: row.object_id,
    title: row.title ?? "",
    description: row.description ?? "",
    tags: row.tags ?? "",
    guideImages: row.guide_images ?? "[]",
    bundlePath: row.bundle_path ?? "",
    bundleSize: row.bundle_size ?? 0,
    taskCount: row.task_count ?? 0,
    downloadCount: row.download_count ?? 0,
    status: row.status ?? "published",
    createdAt: normalizeTimestamp(row.created_at),
    updatedAt: normalizeTimestamp(row.updated_at),
    author: mapUser(author),
  };
}

async function getCloudDailyScriptByObjectId(objectId: string): Promise<CloudDailyScriptRow | null> {
  const { data, error } = await db
    .from("cloud_daily_scripts")
    .select(DAILY_SCRIPT_SELECT)
    .eq("object_id", objectId)
    .limit(1)
    .maybeSingle<CloudDailyScriptRow>();
  if (error) throw error;
  return data;
}
```

- [ ] **Step 4: Add Edge Function action implementations**

After `getOcrConfig`, add:

```ts
async function createDailyScriptUpload(body: JsonRecord) {
  const deviceId = requireString(body.deviceId, "deviceId");
  const title = requireString(body.title, "title");
  const bundleSize = Number(body.bundleSize ?? 0);
  if (bundleSize <= 0) throw new Error("脚本包不能为空");
  const user = await ensureUserByDeviceId(deviceId);
  const scriptObjectId = randomObjectId(12);
  const authorObjectId = user.object_id ?? user.id;
  const bundlePath = `daily/${authorObjectId}/${scriptObjectId}.zip`;
  const { data, error } = await db.storage
    .from(DAILY_SCRIPT_BUCKET)
    .createSignedUploadUrl(bundlePath);
  if (error) throw error;
  return {
    scriptObjectId,
    bundlePath,
    uploadUrl: data.signedUrl,
    token: data.token,
    title,
  };
}

async function publishDailyScript(body: JsonRecord) {
  const deviceId = requireString(body.deviceId, "deviceId");
  const user = await ensureUserByDeviceId(deviceId);
  const scriptObjectId = requireString(body.scriptObjectId, "scriptObjectId");
  const title = requireString(body.title, "title");
  const bundlePath = requireString(body.bundlePath, "bundlePath");
  const nowIso = new Date().toISOString();
  const payload = {
    object_id: scriptObjectId,
    author_id: user.id,
    title,
    description: String(body.description ?? ""),
    tags: String(body.tags ?? ""),
    guide_images: normalizeJsonArrayString(body.guideImages),
    bundle_path: bundlePath,
    bundle_size: Number(body.bundleSize ?? 0),
    task_count: Number(body.taskCount ?? 0),
    status: "published",
    updated_at: nowIso,
  };
  const existing = await getCloudDailyScriptByObjectId(scriptObjectId);
  const query = existing
    ? db.from("cloud_daily_scripts").update(payload).eq("id", existing.id)
    : db.from("cloud_daily_scripts").insert({ id: crypto.randomUUID(), created_at: nowIso, download_count: 0, ...payload });
  const { data, error } = await query.select(DAILY_SCRIPT_SELECT).single<CloudDailyScriptRow>();
  if (error) throw error;
  return mapCloudDailyScript(data, user);
}

async function listDailyScripts(sortMode: string, keyword: string, limit: number) {
  const orderColumn = sortMode === "hot" ? "download_count" : "created_at";
  let query = db
    .from("cloud_daily_scripts")
    .select(DAILY_SCRIPT_SELECT)
    .eq("status", "published")
    .order(orderColumn, { ascending: false })
    .limit(Number.isFinite(limit) ? Math.min(Math.max(limit, 1), 200) : 100);
  const normalizedKeyword = keyword.trim();
  if (normalizedKeyword) {
    query = query.or(`title.ilike.%${normalizedKeyword}%,description.ilike.%${normalizedKeyword}%,tags.ilike.%${normalizedKeyword}%`);
  }
  const { data, error } = await query;
  if (error) throw error;
  const rows = (data as CloudDailyScriptRow[]) ?? [];
  const authors = await loadUsersByIds(rows.map((item) => item.author_id));
  return rows.map((item) => mapCloudDailyScript(item, item.author_id ? authors.get(item.author_id) : null));
}

async function getDailyScriptDetail(scriptObjectId: string) {
  const row = await getCloudDailyScriptByObjectId(scriptObjectId);
  if (!row || row.status !== "published") throw new Error("未找到该云端脚本");
  const author = row.author_id ? (await loadUsersByIds([row.author_id])).get(row.author_id) : null;
  return mapCloudDailyScript(row, author);
}

async function createDailyScriptDownloadUrl(scriptObjectId: string) {
  const row = await getCloudDailyScriptByObjectId(scriptObjectId);
  if (!row || row.status !== "published" || !row.bundle_path) throw new Error("未找到该云端脚本");
  const { data, error } = await db.storage
    .from(DAILY_SCRIPT_BUCKET)
    .createSignedUrl(row.bundle_path, 60 * 10);
  if (error) throw error;
  return { downloadUrl: data.signedUrl, bundlePath: row.bundle_path };
}

async function incrementDailyScriptDownload(scriptObjectId: string) {
  const row = await getCloudDailyScriptByObjectId(scriptObjectId);
  if (!row) throw new Error("未找到该云端脚本");
  const nextCount = (row.download_count ?? 0) + 1;
  const { error } = await db
    .from("cloud_daily_scripts")
    .update({ download_count: nextCount, updated_at: new Date().toISOString() })
    .eq("id", row.id);
  if (error) throw error;
  return { downloadCount: nextCount };
}
```

- [ ] **Step 5: Register actions in `routeAction`**

In the switch, before `default`, add:

```ts
    case "create-daily-script-upload":
      return await createDailyScriptUpload(body);
    case "publish-daily-script":
      return await publishDailyScript(body);
    case "list-daily-scripts":
      return await listDailyScripts(
        String(body.sortMode ?? "newest"),
        String(body.keyword ?? ""),
        Number(body.limit ?? 100),
      );
    case "get-daily-script-detail":
      return await getDailyScriptDetail(requireString(body.scriptId, "scriptId"));
    case "create-daily-script-download-url":
      return await createDailyScriptDownloadUrl(requireString(body.scriptId, "scriptId"));
    case "increment-daily-script-download":
      return await incrementDailyScriptDownload(requireString(body.scriptId, "scriptId"));
```

- [ ] **Step 6: Verify TypeScript formatting by running local check**

Run: `npx supabase functions serve yuanassist-api-v3 --no-verify-jwt`

Expected: function starts or fails only because env/project secrets are unavailable. Syntax errors must be fixed.

- [ ] **Step 7: Commit**

```bash
git add supabase/cloud_daily_scripts_schema.sql supabase/functions/yuanassist-api-v3/index.ts
git commit -m "feat: add cloud daily script api"
```

## Task 2: Android Models And Repository APIs

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/model/StrategyModel.kt`
- Modify: `app/src/main/java/com/example/yuanassist/network/SupabaseRepository.kt`

- [ ] **Step 1: Add Android models**

In `StrategyModel.kt`, after `strategy_message`, add:

```kotlin
class cloud_daily_script : SupabaseRecord() {
    var title: String = ""
    var description: String = ""
    var tags: String = ""
    var guideImages: String = "[]"
    var bundlePath: String = ""
    var bundleSize: Long = 0L
    var taskCount: Int = 0
    var downloadCount: Int = 0
    var status: String = "published"
    var author: MyUser? = null
}
```

In `SupabaseRepository.kt`, after `StrategySavePayload`, add:

```kotlin
data class DailyScriptUploadTicket(
    val scriptObjectId: String = "",
    val bundlePath: String = "",
    val uploadUrl: String = "",
    val token: String = "",
)

data class DailyScriptDownloadTicket(
    val downloadUrl: String = "",
    val bundlePath: String = "",
)

data class CloudDailyScriptPublishPayload(
    val scriptObjectId: String,
    val title: String,
    val description: String,
    val tags: String,
    val guideImages: List<String>,
    val bundlePath: String,
    val bundleSize: Long,
    val taskCount: Int,
)
```

- [ ] **Step 2: Import model and OkHttp body helpers**

At the top of `SupabaseRepository.kt`, add:

```kotlin
import com.example.yuanassist.model.cloud_daily_script
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
```

- [ ] **Step 3: Add cloud API methods**

Inside `SupabaseRepository`, after `saveStrategy`, add:

```kotlin
    fun createDailyScriptUpload(
        context: Context,
        title: String,
        bundleSize: Long,
        onSuccess: (DailyScriptUploadTicket) -> Unit,
        onError: (String) -> Unit,
    ) {
        ensureUser(
            context = context,
            onSuccess = {
                request<DailyScriptUploadTicket>(
                    action = "create-daily-script-upload",
                    payload = mapOf(
                        "deviceId" to currentDeviceId(context),
                        "title" to title,
                        "bundleSize" to bundleSize,
                    ),
                    type = DailyScriptUploadTicket::class.java,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    fun publishDailyScript(
        context: Context,
        payload: CloudDailyScriptPublishPayload,
        onSuccess: (cloud_daily_script) -> Unit,
        onError: (String) -> Unit,
    ) {
        ensureUser(
            context = context,
            onSuccess = {
                request<cloud_daily_script>(
                    action = "publish-daily-script",
                    payload = mapOf(
                        "deviceId" to currentDeviceId(context),
                        "scriptObjectId" to payload.scriptObjectId,
                        "title" to payload.title,
                        "description" to payload.description,
                        "tags" to payload.tags,
                        "guideImages" to payload.guideImages,
                        "bundlePath" to payload.bundlePath,
                        "bundleSize" to payload.bundleSize,
                        "taskCount" to payload.taskCount,
                    ),
                    type = cloud_daily_script::class.java,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    fun listDailyScripts(
        sortMode: String = "newest",
        keyword: String = "",
        limit: Int = 100,
        onSuccess: (List<cloud_daily_script>) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<List<cloud_daily_script>>(
            action = "list-daily-scripts",
            payload = mapOf(
                "sortMode" to if (sortMode == "hot") "hot" else "newest",
                "keyword" to keyword,
                "limit" to limit,
            ),
            type = object : TypeToken<List<cloud_daily_script>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun getDailyScriptDetail(
        scriptId: String,
        onSuccess: (cloud_daily_script) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<cloud_daily_script>(
            action = "get-daily-script-detail",
            payload = mapOf("scriptId" to scriptId),
            type = cloud_daily_script::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun createDailyScriptDownloadUrl(
        scriptId: String,
        onSuccess: (DailyScriptDownloadTicket) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<DailyScriptDownloadTicket>(
            action = "create-daily-script-download-url",
            payload = mapOf("scriptId" to scriptId),
            type = DailyScriptDownloadTicket::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun incrementDailyScriptDownload(scriptId: String, onError: ((String) -> Unit)? = null) {
        request<JsonObject>(
            action = "increment-daily-script-download",
            payload = mapOf("scriptId" to scriptId),
            type = JsonObject::class.java,
            onSuccess = {},
            onError = { message -> onError?.invoke(message) },
        )
    }
```

- [ ] **Step 4: Add raw upload/download helpers**

Still inside `SupabaseRepository`, before `cacheCurrentUser`, add:

```kotlin
    fun uploadDailyScriptBundle(
        uploadUrl: String,
        zipFile: File,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val request = Request.Builder()
            .url(uploadUrl)
            .post(zipFile.asRequestBody("application/zip".toMediaTypeOrNull()))
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                dispatchError(onError, "脚本包上传失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) {
                        dispatchSuccess(onSuccess, Unit)
                    } else {
                        dispatchError(onError, "脚本包上传失败: HTTP ${it.code}")
                    }
                }
            }
        })
    }

    fun downloadDailyScriptBundle(
        downloadUrl: String,
        targetFile: File,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val request = Request.Builder().url(downloadUrl).get().build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                dispatchError(onError, "脚本包下载失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        dispatchError(onError, "脚本包下载失败: HTTP ${it.code}")
                        return
                    }
                    runCatching {
                        targetFile.parentFile?.mkdirs()
                        val body = it.body ?: error("响应为空")
                        targetFile.outputStream().use { output -> body.byteStream().use { input -> input.copyTo(output) } }
                        targetFile
                    }.onSuccess { file ->
                        dispatchSuccess(onSuccess, file)
                    }.onFailure { error ->
                        dispatchError(onError, error.message ?: "脚本包保存失败")
                    }
                }
            }
        })
    }
```

- [ ] **Step 5: Run unit compile**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.yuanassist.utils.SupabaseTimeFormatterTest`

Expected: existing test passes and Kotlin compilation succeeds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/yuanassist/model/StrategyModel.kt app/src/main/java/com/example/yuanassist/network/SupabaseRepository.kt
git commit -m "feat: add cloud daily script repository"
```

## Task 3: Bundle Zip Utility With Tests

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/utils/DailyScriptBundleZipStore.kt`
- Create: `app/src/test/java/com/example/yuanassist/utils/DailyScriptBundleZipStoreTest.kt`

- [ ] **Step 1: Write failing tests**

Create `DailyScriptBundleZipStoreTest.kt`:

```kotlin
package com.example.yuanassist.utils

import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskParams
import com.example.yuanassist.model.DailyTaskPlan
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DailyScriptBundleZipStoreTest {
    @Test
    fun `packBundle writes script and templates then count tasks`() {
        val root = createTempDir(prefix = "daily_zip_pack")
        val bundleRoot = File(root, "source").apply { mkdirs() }
        val templates = File(bundleRoot, "templates").apply { mkdirs() }
        val bundle = UserDailyScriptBundle("source", bundleRoot, File(bundleRoot, "script.json"), templates)
        val plan = DailyTaskPlan(
            title = "云端测试",
            tasks = listOf(
                DailyTask(id = 1, action = "CLICK", params = DailyTaskParams(x = 1, y = 2)),
                DailyTask(id = 2, action = "BACK"),
            ),
        )
        UserDailyScriptStore.savePlan(bundle, plan, Gson())
        File(templates, "button.png").writeText("image-bytes", Charsets.UTF_8)

        val zip = DailyScriptBundleZipStore.packBundle(root, bundle, Gson())

        assertTrue(zip.file.exists())
        assertEquals(2, zip.taskCount)
        assertTrue(zip.sizeBytes > 0)
        root.deleteRecursively()
    }

    @Test
    fun `unpackToBundle restores script and templates`() {
        val root = createTempDir(prefix = "daily_zip_unpack")
        val sourceRoot = File(root, "source").apply { mkdirs() }
        val sourceTemplates = File(sourceRoot, "templates").apply { mkdirs() }
        val source = UserDailyScriptBundle("source", sourceRoot, File(sourceRoot, "script.json"), sourceTemplates)
        UserDailyScriptStore.savePlan(source, DailyTaskPlan(title = "下载脚本", tasks = listOf(DailyTask(id = 1, action = "BACK"))), Gson())
        File(sourceTemplates, "a.png").writeText("template", Charsets.UTF_8)
        val zip = DailyScriptBundleZipStore.packBundle(root, source, Gson()).file
        val targetRoot = File(root, "target").apply { mkdirs() }
        val target = UserDailyScriptBundle("target", targetRoot, File(targetRoot, "script.json"), File(targetRoot, "templates"))

        DailyScriptBundleZipStore.unpackToBundle(zip, target, Gson())

        assertTrue(target.scriptFile.exists())
        assertEquals("下载脚本", UserDailyScriptStore.loadPlan(target, Gson()).title)
        assertEquals("template", File(target.templatesDir, "a.png").readText(Charsets.UTF_8))
        root.deleteRecursively()
    }

    @Test
    fun `unpackToBundle rejects zip slip entries`() {
        val root = createTempDir(prefix = "daily_zip_slip")
        val zip = File(root, "bad.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("../escape.txt"))
            output.write("bad".toByteArray())
            output.closeEntry()
        }
        val targetRoot = File(root, "target").apply { mkdirs() }
        val target = UserDailyScriptBundle("target", targetRoot, File(targetRoot, "script.json"), File(targetRoot, "templates"))

        try {
            DailyScriptBundleZipStore.unpackToBundle(zip, target, Gson())
            fail("Expected zip slip rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("非法压缩包路径"))
        }
        root.deleteRecursively()
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.yuanassist.utils.DailyScriptBundleZipStoreTest`

Expected: FAIL because `DailyScriptBundleZipStore` does not exist.

- [ ] **Step 3: Implement zip utility**

Create `DailyScriptBundleZipStore.kt`:

```kotlin
package com.example.yuanassist.utils

import com.example.yuanassist.model.DailyTaskPlan
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class PackedDailyScriptBundle(
    val file: File,
    val taskCount: Int,
    val sizeBytes: Long,
)

object DailyScriptBundleZipStore {
    private const val SCRIPT_FILE_NAME = "script.json"
    private const val TEMPLATES_DIR_NAME = "templates"

    fun packBundle(cacheDir: File, bundle: UserDailyScriptBundle, gson: Gson = Gson()): PackedDailyScriptBundle {
        val plan = UserDailyScriptStore.loadPlan(bundle, gson)
        val target = File(cacheDir, "daily_script_${bundle.scriptId}_${System.currentTimeMillis()}.zip")
        if (target.exists()) target.delete()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            addFile(zip, bundle.scriptFile, SCRIPT_FILE_NAME)
            bundle.templatesDir
                .listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { templateFile ->
                    addFile(zip, templateFile, "$TEMPLATES_DIR_NAME/${templateFile.name}")
                }
        }
        return PackedDailyScriptBundle(
            file = target,
            taskCount = plan.tasks.size,
            sizeBytes = target.length(),
        )
    }

    fun unpackToBundle(zipFile: File, bundle: UserDailyScriptBundle, gson: Gson = Gson()): DailyTaskPlan {
        bundle.rootDir.mkdirs()
        bundle.templatesDir.mkdirs()
        val rootPath = bundle.rootDir.canonicalFile.toPath()
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val target = File(bundle.rootDir, entry.name).canonicalFile
                if (!target.toPath().startsWith(rootPath)) {
                    throw IllegalArgumentException("非法压缩包路径：${entry.name}")
                }
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> zip.copyTo(output) }
                zip.closeEntry()
            }
        }
        if (!bundle.scriptFile.exists()) {
            throw IllegalArgumentException("压缩包缺少 script.json")
        }
        return UserDailyScriptStore.loadPlan(bundle, gson)
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists() || !file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.yuanassist.utils.DailyScriptBundleZipStoreTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/yuanassist/utils/DailyScriptBundleZipStore.kt app/src/test/java/com/example/yuanassist/utils/DailyScriptBundleZipStoreTest.kt
git commit -m "feat: add daily script bundle zip store"
```

## Task 4: Cloud List Page

**Files:**
- Create: `app/src/main/res/layout/activity_cloud_daily_script_list.xml`
- Create: `app/src/main/res/layout/item_cloud_daily_script.xml`
- Create: `app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptListAdapter.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptListActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create list layout by adapting JobStation list**

Create `activity_cloud_daily_script_list.xml` with the same structure as `activity_job_station_list.xml`, using these ID/name changes:

```xml
android:id="@+id/btn_cloud_daily_script_list_back"
android:id="@+id/et_cloud_daily_script_search_keyword"
android:hint="搜索标题/说明/标签"
android:id="@+id/btn_cloud_daily_script_search"
android:id="@+id/chip_cloud_sort_hot"
android:id="@+id/chip_cloud_sort_newest"
android:id="@+id/swipe_cloud_daily_script_list"
android:id="@+id/rv_cloud_daily_script_list"
android:id="@+id/tv_cloud_daily_script_list_empty"
android:text="暂无云端脚本"
android:id="@+id/fab_upload_cloud_daily_script"
android:contentDescription="上传云端脚本"
```

Keep only two chips: `最热` and `最新`. Reuse `@drawable/bg_job_station_card`, `bg_job_station_chip`, and `bg_job_station_icon_button`.

- [ ] **Step 2: Create item layout**

Create `item_cloud_daily_script.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    android:background="@drawable/bg_job_station_card"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tv_cloud_script_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:fontFamily="serif"
        android:maxLines="2"
        android:textColor="#2A2216"
        android:textSize="17sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tv_cloud_script_desc"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:ellipsize="end"
        android:maxLines="2"
        android:textColor="#6F6048"
        android:textSize="13sp" />

    <TextView
        android:id="@+id/tv_cloud_script_meta"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:textColor="#9A7A3A"
        android:textSize="12sp" />
</LinearLayout>
```

- [ ] **Step 3: Implement adapter**

Create `CloudDailyScriptListAdapter.kt`:

```kotlin
package com.example.yuanassist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.cloud_daily_script
import com.example.yuanassist.utils.SupabaseTimeFormatter

class CloudDailyScriptListAdapter(
    private var items: List<cloud_daily_script>,
    private val onClick: (cloud_daily_script) -> Unit,
) : RecyclerView.Adapter<CloudDailyScriptListAdapter.ViewHolder>() {
    fun submitList(next: List<cloud_daily_script>) {
        items = next
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cloud_daily_script, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val onClick: (cloud_daily_script) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_cloud_script_title)
        private val desc: TextView = itemView.findViewById(R.id.tv_cloud_script_desc)
        private val meta: TextView = itemView.findViewById(R.id.tv_cloud_script_meta)

        fun bind(item: cloud_daily_script) {
            title.text = item.title.ifBlank { "未命名脚本" }
            desc.text = item.description.ifBlank { "暂无说明" }
            val authorName = item.author?.nickname?.takeIf { it.isNotBlank() } ?: "匿名用户"
            val time = SupabaseTimeFormatter.formatToBeijing(item.updatedAt ?: item.createdAt)
            meta.text = "$authorName · ${item.taskCount} 节点 · 下载 ${item.downloadCount} · $time"
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
```

- [ ] **Step 4: Implement Activity**

Create `CloudDailyScriptListActivity.kt`:

```kotlin
package com.example.yuanassist.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.yuanassist.R
import com.example.yuanassist.network.SupabaseRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CloudDailyScriptListActivity : AppCompatActivity() {
    private enum class SortMode { HOT, NEWEST }

    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchInput: EditText
    private lateinit var chipHot: TextView
    private lateinit var chipNewest: TextView
    private lateinit var adapter: CloudDailyScriptListAdapter
    private var currentSortMode = SortMode.NEWEST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_daily_script_list)
        applyStatusBarInsets()
        findViewById<ImageView>(R.id.btn_cloud_daily_script_list_back).setOnClickListener { finish() }
        emptyView = findViewById(R.id.tv_cloud_daily_script_list_empty)
        recyclerView = findViewById(R.id.rv_cloud_daily_script_list)
        swipeRefreshLayout = findViewById(R.id.swipe_cloud_daily_script_list)
        searchInput = findViewById(R.id.et_cloud_daily_script_search_keyword)
        chipHot = findViewById(R.id.chip_cloud_sort_hot)
        chipNewest = findViewById(R.id.chip_cloud_sort_newest)
        adapter = CloudDailyScriptListAdapter(emptyList()) { item ->
            startActivity(Intent(this, CloudDailyScriptActivity::class.java).putExtra(CloudDailyScriptActivity.EXTRA_SCRIPT_ID, item.objectId))
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#C88A2C"), Color.parseColor("#8F6A2B"))
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#FFF8EB"))
        swipeRefreshLayout.setOnRefreshListener { loadScripts() }
        findViewById<Button>(R.id.btn_cloud_daily_script_search).setOnClickListener { loadScripts() }
        findViewById<FloatingActionButton>(R.id.fab_upload_cloud_daily_script).setOnClickListener {
            startActivity(Intent(this, UploadCloudDailyScriptActivity::class.java))
        }
        chipHot.setOnClickListener { currentSortMode = SortMode.HOT; bindChips(); loadScripts() }
        chipNewest.setOnClickListener { currentSortMode = SortMode.NEWEST; bindChips(); loadScripts() }
        bindChips()
        loadScripts()
    }

    override fun onResume() {
        super.onResume()
        loadScripts()
    }

    private fun loadScripts() {
        swipeRefreshLayout.isRefreshing = true
        SupabaseRepository.listDailyScripts(
            sortMode = if (currentSortMode == SortMode.HOT) "hot" else "newest",
            keyword = searchInput.text?.toString().orEmpty().trim(),
            onSuccess = { items ->
                swipeRefreshLayout.isRefreshing = false
                adapter.submitList(items)
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            },
            onError = { message ->
                swipeRefreshLayout.isRefreshing = false
                emptyView.visibility = View.VISIBLE
                Toast.makeText(this, "云端脚本加载失败：$message", Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun bindChips() {
        chipHot.background = AppCompatResources.getDrawable(this, if (currentSortMode == SortMode.HOT) R.drawable.bg_job_station_chip else R.drawable.bg_job_station_card)
        chipNewest.background = AppCompatResources.getDrawable(this, if (currentSortMode == SortMode.NEWEST) R.drawable.bg_job_station_chip else R.drawable.bg_job_station_card)
    }

    private fun applyStatusBarInsets() {
        val spacer = findViewById<View>(R.id.view_cloud_daily_script_list_status_bar_spacer)
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            view.layoutParams = view.layoutParams.apply {
                height = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            }
            insets
        }
    }
}
```

Make sure the list layout includes `view_cloud_daily_script_list_status_bar_spacer`.

- [ ] **Step 5: Register Activity**

Add to `AndroidManifest.xml`:

```xml
        <activity
            android:name=".ui.CloudDailyScriptListActivity"
            android:exported="false"
            android:screenOrientation="portrait" />
```

- [ ] **Step 6: Compile**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.yuanassist.utils.SupabaseTimeFormatterTest`

Expected: Kotlin and resource compilation succeed.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/activity_cloud_daily_script_list.xml app/src/main/res/layout/item_cloud_daily_script.xml app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptListAdapter.kt app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptListActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add cloud daily script list"
```

## Task 5: Detail Page Download And Import

**Files:**
- Create: `app/src/main/res/layout/activity_cloud_daily_script.xml`
- Create: `app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create detail layout**

Create `activity_cloud_daily_script.xml` using `activity_job_station.xml` style with:

```xml
TextView ids:
tv_cloud_detail_title
tv_cloud_detail_meta
tv_cloud_detail_description
tv_cloud_detail_tags
tv_cloud_detail_guide_title

LinearLayout ids:
ll_cloud_detail_guide_images

Button ids:
btn_cloud_save_local
btn_cloud_import_window

ImageView id:
btn_cloud_detail_back
```

Use a bottom horizontal bar with the two buttons, matching JobStation bottom action styling.

- [ ] **Step 2: Implement detail Activity**

Create `CloudDailyScriptActivity.kt`:

```kotlin
package com.example.yuanassist.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.Coil
import coil.request.ImageRequest
import com.example.yuanassist.R
import com.example.yuanassist.core.DailyPlanSelection
import com.example.yuanassist.core.DailyScriptLibraryBridge
import com.example.yuanassist.model.cloud_daily_script
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.DailyScriptBundleZipStore
import com.example.yuanassist.utils.SupabaseTimeFormatter
import com.example.yuanassist.utils.UserDailyScriptBundle
import com.example.yuanassist.utils.UserDailyScriptStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CloudDailyScriptActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SCRIPT_ID = "extra_script_id"
    }

    private val gson = Gson()
    private var detail: cloud_daily_script? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_daily_script)
        findViewById<ImageView>(R.id.btn_cloud_detail_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_cloud_save_local).setOnClickListener { downloadAndSave(importAfterSave = false) }
        findViewById<Button>(R.id.btn_cloud_import_window).setOnClickListener { downloadAndSave(importAfterSave = true) }
        loadDetail()
    }

    private fun loadDetail() {
        val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID).orEmpty()
        if (scriptId.isBlank()) {
            Toast.makeText(this, "缺少脚本ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        SupabaseRepository.getDailyScriptDetail(
            scriptId = scriptId,
            onSuccess = { item -> detail = item; bindDetail(item) },
            onError = { message -> Toast.makeText(this, "加载失败：$message", Toast.LENGTH_LONG).show(); finish() },
        )
    }

    private fun bindDetail(item: cloud_daily_script) {
        findViewById<TextView>(R.id.tv_cloud_detail_title).text = item.title.ifBlank { "未命名脚本" }
        val authorName = item.author?.nickname?.takeIf { it.isNotBlank() } ?: "匿名用户"
        val time = SupabaseTimeFormatter.formatToBeijing(item.updatedAt ?: item.createdAt)
        findViewById<TextView>(R.id.tv_cloud_detail_meta).text = "$authorName · ${item.taskCount} 节点 · 下载 ${item.downloadCount} · $time"
        findViewById<TextView>(R.id.tv_cloud_detail_description).text = item.description.ifBlank { "暂无说明" }
        findViewById<TextView>(R.id.tv_cloud_detail_tags).text = item.tags.ifBlank { "无标签" }
        bindGuideImages(parseGuideImages(item.guideImages))
    }

    private fun bindGuideImages(urls: List<String>) {
        val title = findViewById<TextView>(R.id.tv_cloud_detail_guide_title)
        val container = findViewById<LinearLayout>(R.id.ll_cloud_detail_guide_images)
        container.removeAllViews()
        title.visibility = if (urls.isEmpty()) View.GONE else View.VISIBLE
        container.visibility = if (urls.isEmpty()) View.GONE else View.VISIBLE
        urls.forEach { url ->
            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            container.addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp()
            })
            Coil.imageLoader(this).enqueue(ImageRequest.Builder(this).data(url).target(image).build())
        }
    }

    private fun downloadAndSave(importAfterSave: Boolean) {
        val item = detail ?: return
        val scriptId = item.objectId.orEmpty()
        Toast.makeText(this, "正在获取下载地址...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.createDailyScriptDownloadUrl(
            scriptId = scriptId,
            onSuccess = { ticket ->
                val zip = File(cacheDir, "cloud_daily_${scriptId}.zip")
                SupabaseRepository.downloadDailyScriptBundle(
                    downloadUrl = ticket.downloadUrl,
                    targetFile = zip,
                    onSuccess = { file -> saveDownloadedBundle(item, file, importAfterSave) },
                    onError = { message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() },
                )
            },
            onError = { message -> Toast.makeText(this, "下载地址获取失败：$message", Toast.LENGTH_LONG).show() },
        )
    }

    private fun saveDownloadedBundle(item: cloud_daily_script, zipFile: File, importAfterSave: Boolean) {
        runCatching {
            val bundle = UserDailyScriptStore.createBundle(this, item.title)
            val plan = DailyScriptBundleZipStore.unpackToBundle(zipFile, bundle, gson)
            SupabaseRepository.incrementDailyScriptDownload(item.objectId.orEmpty())
            if (importAfterSave) importBundle(bundle) else Toast.makeText(this, "已保存到本地脚本库", Toast.LENGTH_LONG).show()
            plan
        }.onFailure { error ->
            Toast.makeText(this, "脚本导入失败：${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importBundle(bundle: UserDailyScriptBundle) {
        val json = bundle.scriptFile.readText(Charsets.UTF_8)
        DailyScriptLibraryBridge.onDailyPlanSelected?.invoke(
            DailyPlanSelection(
                fileName = bundle.scriptId,
                jsonContent = json,
                templateDirPath = bundle.templatesDir.absolutePath,
            ),
        )
        Toast.makeText(this, "已导入日常悬浮窗", Toast.LENGTH_LONG).show()
    }

    private fun parseGuideImages(raw: String): List<String> {
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type)
        }.getOrNull().orEmpty().filter { it.isNotBlank() }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
```

- [ ] **Step 3: Register Activity**

Add to `AndroidManifest.xml`:

```xml
        <activity
            android:name=".ui.CloudDailyScriptActivity"
            android:exported="false"
            android:screenOrientation="portrait" />
```

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.yuanassist.utils.DailyScriptBundleZipStoreTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_cloud_daily_script.xml app/src/main/java/com/example/yuanassist/ui/CloudDailyScriptActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add cloud daily script detail"
```

## Task 6: Upload Page With 图片指引

**Files:**
- Create: `app/src/main/res/layout/activity_upload_cloud_daily_script.xml`
- Create: `app/src/main/java/com/example/yuanassist/ui/UploadCloudDailyScriptActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create upload layout**

Create a vertical ScrollView layout with:

```xml
ImageView id: btn_upload_cloud_daily_back
TextView id: tv_upload_selected_script
Button id: btn_pick_upload_daily_script
EditText ids: et_upload_cloud_title, et_upload_cloud_description, et_upload_cloud_tags
Button id: btn_pick_cloud_guide_images
LinearLayout id: ll_upload_cloud_guide_images
Button id: btn_publish_cloud_daily_script
ProgressBar id: progress_upload_cloud_daily_script
```

Use warm paper background and `bg_job_station_card`/`bg_job_station_chip`.

- [ ] **Step 2: Implement upload Activity**

Create `UploadCloudDailyScriptActivity.kt`:

```kotlin
package com.example.yuanassist.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.Coil
import coil.request.ImageRequest
import com.example.yuanassist.R
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.network.CloudDailyScriptPublishPayload
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.DailyScriptBundleZipStore
import com.example.yuanassist.utils.UserDailyScriptBundle
import com.example.yuanassist.utils.UserDailyScriptStore
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

class UploadCloudDailyScriptActivity : AppCompatActivity() {
    private var selectedBundle: UserDailyScriptBundle? = null
    private var selectedGuideUris: List<Uri> = emptyList()
    private lateinit var progress: ProgressBar

    private val guidePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedGuideUris = uris
        bindGuidePreview()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_cloud_daily_script)
        progress = findViewById(R.id.progress_upload_cloud_daily_script)
        findViewById<ImageView>(R.id.btn_upload_cloud_daily_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_pick_upload_daily_script).setOnClickListener { showBundlePicker() }
        findViewById<Button>(R.id.btn_pick_cloud_guide_images).setOnClickListener { guidePicker.launch("image/*") }
        findViewById<Button>(R.id.btn_publish_cloud_daily_script).setOnClickListener { publish() }
    }

    private fun showBundlePicker() {
        val bundles = UserDailyScriptStore.listBundles(this)
        if (bundles.isEmpty()) {
            Toast.makeText(this, "本地录制脚本为空", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = bundles.map { it.scriptId }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择要上传的日常脚本")
            .setItems(labels) { _, which ->
                selectedBundle = bundles[which]
                findViewById<TextView>(R.id.tv_upload_selected_script).text = labels[which]
                val titleInput = findViewById<EditText>(R.id.et_upload_cloud_title)
                if (titleInput.text.isNullOrBlank()) titleInput.setText(labels[which])
            }
            .show()
    }

    private fun publish() {
        val bundle = selectedBundle
        val title = findViewById<EditText>(R.id.et_upload_cloud_title).text?.toString().orEmpty().trim()
        val description = findViewById<EditText>(R.id.et_upload_cloud_description).text?.toString().orEmpty().trim()
        val tags = findViewById<EditText>(R.id.et_upload_cloud_tags).text?.toString().orEmpty().trim()
        if (bundle == null) {
            Toast.makeText(this, "请先选择本地录制脚本", Toast.LENGTH_SHORT).show()
            return
        }
        if (title.isBlank()) {
            Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        setPublishing(true)
        Toast.makeText(this, "正在打包脚本...", Toast.LENGTH_SHORT).show()
        val packed = runCatching { DailyScriptBundleZipStore.packBundle(cacheDir, bundle) }.getOrElse {
            setPublishing(false)
            Toast.makeText(this, "打包失败：${it.message}", Toast.LENGTH_LONG).show()
            return
        }
        ensureUserLoggedIn(
            onSuccess = { uploadGuideImagesThenBundle(title, description, tags, packed) },
            onError = { message -> setPublishing(false); Toast.makeText(this, "登录失败：$message", Toast.LENGTH_LONG).show() },
        )
    }

    private fun uploadGuideImagesThenBundle(title: String, description: String, tags: String, packed: com.example.yuanassist.utils.PackedDailyScriptBundle) {
        if (selectedGuideUris.isEmpty()) {
            uploadBundleAndPublish(title, description, tags, emptyList(), packed)
            return
        }
        Toast.makeText(this, "正在上传图片指引...", Toast.LENGTH_SHORT).show()
        val urls = mutableListOf<String>()
        val files = selectedGuideUris.mapNotNull { uriToCacheFile(it, "cloud_guide") }
        if (files.size != selectedGuideUris.size) {
            setPublishing(false)
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_LONG).show()
            return
        }
        uploadImageFilesSequentially(files, urls, 0) {
            uploadBundleAndPublish(title, description, tags, urls, packed)
            files.forEach { it.delete() }
        }
    }

    private fun uploadBundleAndPublish(title: String, description: String, tags: String, guideUrls: List<String>, packed: com.example.yuanassist.utils.PackedDailyScriptBundle) {
        Toast.makeText(this, "正在获取脚本上传地址...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.createDailyScriptUpload(
            context = this,
            title = title,
            bundleSize = packed.sizeBytes,
            onSuccess = { ticket ->
                Toast.makeText(this, "正在上传脚本包...", Toast.LENGTH_SHORT).show()
                SupabaseRepository.uploadDailyScriptBundle(
                    uploadUrl = ticket.uploadUrl,
                    zipFile = packed.file,
                    onSuccess = {
                        SupabaseRepository.publishDailyScript(
                            context = this,
                            payload = CloudDailyScriptPublishPayload(
                                scriptObjectId = ticket.scriptObjectId,
                                title = title,
                                description = description,
                                tags = tags,
                                guideImages = guideUrls,
                                bundlePath = ticket.bundlePath,
                                bundleSize = packed.sizeBytes,
                                taskCount = packed.taskCount,
                            ),
                            onSuccess = { setPublishing(false); Toast.makeText(this, "云端脚本发布成功", Toast.LENGTH_LONG).show(); finish() },
                            onError = { message -> setPublishing(false); Toast.makeText(this, "发布失败：$message", Toast.LENGTH_LONG).show() },
                        )
                    },
                    onError = { message -> setPublishing(false); Toast.makeText(this, message, Toast.LENGTH_LONG).show() },
                )
            },
            onError = { message -> setPublishing(false); Toast.makeText(this, "上传地址获取失败：$message", Toast.LENGTH_LONG).show() },
        )
    }

    private fun uploadImageFilesSequentially(files: List<File>, urls: MutableList<String>, index: Int, onDone: () -> Unit) {
        if (index >= files.size) {
            onDone()
            return
        }
        uploadImageToImageBed(
            file = files[index],
            onSuccess = { url -> runOnUiThread { urls += url; uploadImageFilesSequentially(files, urls, index + 1, onDone) } },
            onError = { message -> runOnUiThread { setPublishing(false); Toast.makeText(this, message, Toast.LENGTH_LONG).show(); files.forEach { it.delete() } } },
        )
    }

    private fun ensureUserLoggedIn(onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
        SupabaseRepository.getCurrentUser(this)?.let(onSuccess) ?: SupabaseRepository.loginWithDevice(this, onSuccess, onError)
    }

    private fun uriToCacheFile(uri: Uri, prefix: String): File? = runCatching {
        val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.png")
        contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        file.takeIf { it.exists() && it.length() > 0 }
    }.getOrNull()

    private fun uploadImageToImageBed(file: File, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val client = okhttp3.OkHttpClient()
        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
            .addFormDataPart("outputFormat", "jpeg")
            .build()
        val request = okhttp3.Request.Builder().url("https://img.scdn.io/api/v1.php").post(requestBody).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = onError("图床网络请求失败: ${e.message}")
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val raw = response.body?.string()
                if (!response.isSuccessful || raw == null) {
                    onError("图床服务器错误: HTTP ${response.code}")
                    return
                }
                runCatching {
                    val json = org.json.JSONObject(raw)
                    if (json.optBoolean("success")) json.optString("url") else error(json.optString("message", "上传被图床拒绝"))
                }.onSuccess(onSuccess).onFailure { onError("图床JSON解析失败: ${it.message}") }
            }
        })
    }

    private fun bindGuidePreview() {
        val container = findViewById<LinearLayout>(R.id.ll_upload_cloud_guide_images)
        container.removeAllViews()
        selectedGuideUris.forEach { uri ->
            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            container.addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 180.dp()).apply { bottomMargin = 10.dp() })
            Coil.imageLoader(this).enqueue(ImageRequest.Builder(this).data(uri).target(image).build())
        }
    }

    private fun setPublishing(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btn_publish_cloud_daily_script).isEnabled = !value
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
```

- [ ] **Step 3: Register Activity**

Add to `AndroidManifest.xml`:

```xml
        <activity
            android:name=".ui.UploadCloudDailyScriptActivity"
            android:exported="false"
            android:screenOrientation="portrait" />
```

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.yuanassist.utils.DailyScriptBundleZipStoreTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_upload_cloud_daily_script.xml app/src/main/java/com/example/yuanassist/ui/UploadCloudDailyScriptActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add cloud daily script upload"
```

## Task 7: Homepage Entry And Project Context

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/main/MainShellActions.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/main/HomeTabScreen.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`
- Modify: `docs/project_context.md`

- [ ] **Step 1: Add action**

In `HomeTabActions`, add after `onOpenScriptLibrary`:

```kotlin
    val onOpenCloudScripts: () -> Unit,
```

- [ ] **Step 2: Add button after 脚本库**

In `HomeTabScreen.kt`, in the "常用入口" list, add immediately after `脚本库`:

```kotlin
                HomeEntryButton("云端脚本", R.drawable.item_zhangzhao, R.drawable.decor_butterfly, onClick = actions.onOpenCloudScripts),
```

- [ ] **Step 3: Bind action in MainActivity**

Where `HomeTabActions` is constructed, add:

```kotlin
                    onOpenCloudScripts = {
                        startActivity(Intent(this, CloudDailyScriptListActivity::class.java))
                    },
```

Add import:

```kotlin
import com.example.yuanassist.ui.CloudDailyScriptListActivity
```

- [ ] **Step 4: Update project context**

In `docs/project_context.md`, add a short bullet under "日常脚本系统":

```markdown
  - 云端脚本共享入口位于首页「常用入口」的「脚本库」后面；只共享日常录制脚本 bundle，战斗脚本仍归 JobStation。脚本整包通过 Supabase Storage 保存为 zip，元数据由 `SupabaseRepository` / `yuanassist-api-v3` 管理；详情页的「图片指引」使用图床 URL。
```

- [ ] **Step 5: Compile**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.yuanassist.utils.DailyScriptBundleZipStoreTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/yuanassist/ui/main/MainShellActions.kt app/src/main/java/com/example/yuanassist/ui/main/HomeTabScreen.kt app/src/main/java/com/example/yuanassist/ui/MainActivity.kt docs/project_context.md
git commit -m "feat: add cloud scripts homepage entry"
```

## Task 8: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused unit tests**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.yuanassist.utils.DailyScriptBundleZipStoreTest --tests com.example.yuanassist.utils.SupabaseTimeFormatterTest`

Expected: PASS.

- [ ] **Step 2: Build debug APK if user requested verification**

Only run this if the user explicitly asks for build verification:

```bash
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual QA checklist**

On device/emulator:

1. Open homepage and confirm「云端脚本」appears after「脚本库」.
2. Open cloud list and confirm loading/empty/error states do not crash.
3. Upload a recorded daily script with one guide image.
4. Open the detail page and confirm「图片指引」renders.
5. Save to local script library and confirm it appears in `ScriptLibraryActivity`.
6. Use "导入日常悬浮窗" and confirm the selected bundle has a non-empty `templateDirPath`.

- [ ] **Step 4: Commit final fixes if any**

If verification produces fixes, stage only the files changed by those fixes and commit them with:

```bash
git commit -m "fix: polish cloud daily scripts"
```
