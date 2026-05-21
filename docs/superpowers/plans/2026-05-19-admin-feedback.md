# 管理员反馈管理模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `Mine` 页新增仅管理员设备可见的“反馈管理”入口，并提供一个独立的手机端管理页来查看 `issue_feedback` 全表、查看图片与描述、写入单条官方回复。

**Architecture:** Android 侧通过一个轻量管理员设备判断 helper 控制 `Mine` 页入口显隐，并新增 `FeedbackAdminActivity` 作为独立管理页；数据层在 `SupabaseRepository` 增加管理员列表与回复接口。Supabase Edge Function 继续沿用现有 `action` 路由模式，新增两个管理员 action，并在服务端再次校验设备 ID，避免仅靠前端隐藏入口。

**Tech Stack:** Kotlin + Jetpack Compose + OkHttp/Gson + Android Activity/Manifest + Supabase Edge Functions (Deno/TypeScript)

---

## File Structure

**Create**
- `app/src/main/java/com/example/yuanassist/utils/AdminAccess.kt`
- `app/src/main/java/com/example/yuanassist/ui/FeedbackAdminActivity.kt`
- `app/src/test/java/com/example/yuanassist/utils/AdminAccessTest.kt`
- `supabase/functions/yuanassist-api/feedbackAdmin.ts`
- `supabase/functions/yuanassist-api/feedbackAdmin_test.ts`

**Modify**
- `app/src/main/java/com/example/yuanassist/ui/main/MainShellModels.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/MainShellActions.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/MineTabScreen.kt`
- `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`
- `app/src/main/java/com/example/yuanassist/network/SupabaseRepository.kt`
- `app/src/main/AndroidManifest.xml`
- `supabase/functions/yuanassist-api/index.ts`
- `docs/project_context.md`

## Task 1: Add Shared Admin Access Helpers

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/utils/AdminAccess.kt`
- Create: `app/src/test/java/com/example/yuanassist/utils/AdminAccessTest.kt`
- Create: `supabase/functions/yuanassist-api/feedbackAdmin.ts`
- Create: `supabase/functions/yuanassist-api/feedbackAdmin_test.ts`

- [ ] **Step 1: Write the failing Android helper test**

```kotlin
package com.example.yuanassist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAccessTest {

    @Test
    fun `admin device id should pass feedback admin gate`() {
        assertTrue(isFeedbackAdminDevice("815e9c7c33fa662e"))
    }

    @Test
    fun `non admin device id should fail feedback admin gate`() {
        assertFalse(isFeedbackAdminDevice("other-device"))
    }

    @Test
    fun `first feedback image url should trim and pick first non blank value`() {
        assertEquals(
            "https://a.example/1.webp",
            firstFeedbackImageUrl(" https://a.example/1.webp, https://a.example/2.webp ")
        )
    }
}
```

- [ ] **Step 2: Run the Android helper test to verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.AdminAccessTest"
```

Expected: FAIL with unresolved references such as `isFeedbackAdminDevice` / `firstFeedbackImageUrl`.

- [ ] **Step 3: Write the Android helper implementation**

```kotlin
package com.example.yuanassist.utils

const val FEEDBACK_ADMIN_DEVICE_ID = "815e9c7c33fa662e"

fun isFeedbackAdminDevice(deviceId: String?): Boolean {
    return deviceId?.trim() == FEEDBACK_ADMIN_DEVICE_ID
}

fun firstFeedbackImageUrl(imageUrls: String): String {
    return imageUrls
        .split(',')
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
}
```

- [ ] **Step 4: Re-run the Android helper test**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.AdminAccessTest"
```

Expected: PASS.

- [ ] **Step 5: Write the failing Deno helper test**

```ts
import {
  assertFeedbackAdminDevice,
  isFeedbackAdminDeviceId,
  normalizeAdminFeedbackReply,
} from "./feedbackAdmin.ts";
import { assertEquals, assertThrows } from "https://deno.land/std/assert/mod.ts";

Deno.test("admin device id passes validation", () => {
  assertEquals(isFeedbackAdminDeviceId("815e9c7c33fa662e"), true);
});

Deno.test("empty reply is rejected", () => {
  assertThrows(() => normalizeAdminFeedbackReply("   "), Error, "reply 不能为空");
});

Deno.test("non admin device is rejected", () => {
  assertThrows(() => assertFeedbackAdminDevice("other-device"), Error, "当前设备无反馈管理权限");
});
```

- [ ] **Step 6: Run the Deno helper test to verify it fails**

Run:

```powershell
deno test .\supabase\functions\yuanassist-api\feedbackAdmin_test.ts
```

Expected: FAIL because `feedbackAdmin.ts` does not exist yet.

- [ ] **Step 7: Write the Deno helper implementation**

```ts
export const FEEDBACK_ADMIN_DEVICE_ID = "815e9c7c33fa662e";

export function isFeedbackAdminDeviceId(deviceId: string): boolean {
  return deviceId.trim() === FEEDBACK_ADMIN_DEVICE_ID;
}

export function assertFeedbackAdminDevice(deviceId: string): void {
  if (!isFeedbackAdminDeviceId(deviceId)) {
    throw new Error("当前设备无反馈管理权限");
  }
}

export function normalizeAdminFeedbackReply(value: unknown): string {
  const reply = String(value ?? "").trim();
  if (!reply) {
    throw new Error("reply 不能为空");
  }
  return reply;
}
```

- [ ] **Step 8: Re-run the Deno helper test**

Run:

```powershell
deno test .\supabase\functions\yuanassist-api\feedbackAdmin_test.ts
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/example/yuanassist/utils/AdminAccess.kt app/src/test/java/com/example/yuanassist/utils/AdminAccessTest.kt supabase/functions/yuanassist-api/feedbackAdmin.ts supabase/functions/yuanassist-api/feedbackAdmin_test.ts
git commit -m "test: add feedback admin access helpers"
```

## Task 2: Wire the Mine Tab Entry and Navigation Gate

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/main/MainShellModels.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/main/MainShellActions.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/main/MineTabScreen.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`

- [ ] **Step 1: Extend the Mine profile state with an admin flag**

```kotlin
data class MineProfileState(
    val isLoggedIn: Boolean = false,
    val nickname: String = "未登录",
    val detail: String = "点击资料卡进入资料中心",
    val avatarFallback: String = "我",
    val avatarUrl: String? = null,
    val isFeedbackAdmin: Boolean = false,
)
```

- [ ] **Step 2: Add a dedicated Mine action for the admin feedback page**

```kotlin
data class MineTabActions(
    val onPrimaryAction: () -> Unit,
    val onEditNickname: () -> Unit,
    val onOpenProfileCenter: () -> Unit,
    val onSyncProfile: (() -> Unit)?,
    val onOpenPublished: () -> Unit,
    val onOpenStone: () -> Unit,
    val onOpenFavorite: () -> Unit,
    val onOpenMessage: () -> Unit,
    val onOpenFeedbackAdmin: () -> Unit,
    val onOpenOfficialSite: () -> Unit,
)
```

- [ ] **Step 3: Show the feedback admin entry only for the admin device**

```kotlin
val mineEntries = buildList {
    add(MineEntryItem("我的发布", R.drawable.item_achan, actions.onOpenPublished))
    add(MineEntryItem("我的星石", R.drawable.item_zhangliao, actions.onOpenStone))
    add(MineEntryItem("我的消息", R.drawable.item_xiahoudun, actions.onOpenMessage))
    add(MineEntryItem("我的收藏", R.drawable.item_xiahouyuan, actions.onOpenFavorite))
    if (state.isFeedbackAdmin) {
        add(MineEntryItem("反馈管理", R.drawable.item_zhenmi, actions.onOpenFeedbackAdmin))
    }
}
```

- [ ] **Step 4: Populate the admin flag and wire the new navigation in MainActivity**

```kotlin
import com.example.yuanassist.utils.isFeedbackAdminDevice

private fun refreshMineProfileState() {
    val currentDeviceId = SupabaseRepository.currentDeviceId(this)
    val isFeedbackAdmin = isFeedbackAdminDevice(currentDeviceId)
    val currentUser = SupabaseRepository.getCurrentUser(this)
    if (currentUser == null) {
        mineProfileState = MineProfileState(
            isLoggedIn = false,
            nickname = "未登录",
            detail = "点击下方按钮绑定当前设备",
            avatarFallback = "我",
            avatarUrl = null,
            isFeedbackAdmin = isFeedbackAdmin,
        )
        return
    }
    mineProfileState = MineProfileState(
        isLoggedIn = true,
        nickname = nickname,
        detail = detail,
        avatarFallback = nickname.firstOrNull()?.toString() ?: "我",
        avatarUrl = avatarUrl,
        isFeedbackAdmin = isFeedbackAdmin,
    )
}
```

```kotlin
mineActions = MineTabActions(
    onPrimaryAction = { performOneClickLogin() },
    onEditNickname = ::showEditNicknameDialog,
    onOpenProfileCenter = ::showEditAvatarDialog,
    onSyncProfile = if (mineProfileState.isLoggedIn) ::syncDataFromServer else null,
    onOpenPublished = { startActivity(Intent(this, MyPublishedActivity::class.java)) },
    onOpenStone = { startActivity(Intent(this, MyStoneActivity::class.java)) },
    onOpenFavorite = { startActivity(Intent(this, MyFavoriteActivity::class.java)) },
    onOpenMessage = ::openMyMessage,
    onOpenFeedbackAdmin = { startActivity(Intent(this, FeedbackAdminActivity::class.java)) },
    onOpenOfficialSite = ::openOfficialSite,
)
```

- [ ] **Step 5: Run the Android helper test again after the gate wiring**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.AdminAccessTest"
```

Expected: PASS. This is the regression guard for the visibility gate.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/yuanassist/ui/main/MainShellModels.kt app/src/main/java/com/example/yuanassist/ui/main/MainShellActions.kt app/src/main/java/com/example/yuanassist/ui/main/MineTabScreen.kt app/src/main/java/com/example/yuanassist/ui/MainActivity.kt
git commit -m "feat: add mine entry for feedback admin"
```

## Task 3: Extend the Edge Function and Android Repository

**Files:**
- Modify: `supabase/functions/yuanassist-api/index.ts`
- Modify: `app/src/main/java/com/example/yuanassist/network/SupabaseRepository.kt`

- [ ] **Step 1: Add the admin list handler to the edge function**

```ts
import {
  assertFeedbackAdminDevice,
  normalizeAdminFeedbackReply,
} from "./feedbackAdmin.ts";

async function listFeedbackAdmin(deviceId: string) {
  assertFeedbackAdminDevice(deviceId);
  await ensureUserByDeviceId(deviceId);
  const { data, error } = await db
    .from("issue_feedback")
    .select("id, objectId, createdAt, updatedAt, reply, deviceId, logContent, status, imageUrls, description, user_id")
    .order("createdAt", { ascending: false })
    .limit(200);
  if (error) throw error;
  const rows = (data as FeedbackRow[]) ?? [];
  const users = await loadUsersByIds(rows.map((item) => item.user_id));
  return rows.map((item) => mapFeedback(item, item.user_id ? users.get(item.user_id) : null));
}
```

- [ ] **Step 2: Add the admin reply handler to the edge function**

```ts
async function replyFeedbackAdmin(body: JsonRecord) {
  const deviceId = requireString(body.deviceId, "deviceId");
  const feedbackObjectId = requireString(body.feedbackObjectId, "feedbackObjectId");
  const reply = normalizeAdminFeedbackReply(body.reply);
  assertFeedbackAdminDevice(deviceId);
  await ensureUserByDeviceId(deviceId);

  const { data, error } = await db
    .from("issue_feedback")
    .update({
      reply,
      status: 1,
    })
    .eq("objectId", feedbackObjectId)
    .select("id, objectId, createdAt, updatedAt, reply, deviceId, logContent, status, imageUrls, description, user_id")
    .single<FeedbackRow>();

  if (error) throw error;
  const users = await loadUsersByIds(data.user_id ? [data.user_id] : []);
  return mapFeedback(data, data.user_id ? users.get(data.user_id) : null);
}
```

- [ ] **Step 3: Register the two new actions in `routeAction`**

```ts
case "list-feedback-admin":
  return await listFeedbackAdmin(requireString(body.deviceId, "deviceId"));
case "reply-feedback-admin":
  return await replyFeedbackAdmin(body);
```

- [ ] **Step 4: Add the matching Android repository methods**

```kotlin
fun listFeedbackForAdmin(
    context: Context,
    onSuccess: (List<issue_feedback>) -> Unit,
    onError: (String) -> Unit,
) {
    request<List<issue_feedback>>(
        action = "list-feedback-admin",
        payload = mapOf("deviceId" to currentDeviceId(context)),
        type = object : TypeToken<List<issue_feedback>>() {}.type,
        onSuccess = onSuccess,
        onError = onError,
    )
}

fun replyFeedbackAsAdmin(
    context: Context,
    feedbackObjectId: String,
    reply: String,
    onSuccess: (issue_feedback) -> Unit,
    onError: (String) -> Unit,
) {
    request<issue_feedback>(
        action = "reply-feedback-admin",
        payload = mapOf(
            "deviceId" to currentDeviceId(context),
            "feedbackObjectId" to feedbackObjectId,
            "reply" to reply,
        ),
        type = issue_feedback::class.java,
        onSuccess = onSuccess,
        onError = onError,
    )
}
```

- [ ] **Step 5: Run the Deno helper test and a backend type check**

Run:

```powershell
deno test .\supabase\functions\yuanassist-api\feedbackAdmin_test.ts
deno check .\supabase\functions\yuanassist-api\index.ts
```

Expected: PASS for the test, and `Check` success for `index.ts`.

- [ ] **Step 6: Commit**

```powershell
git add supabase/functions/yuanassist-api/index.ts app/src/main/java/com/example/yuanassist/network/SupabaseRepository.kt
git commit -m "feat: add admin feedback API actions"
```

## Task 4: Build the Feedback Admin Activity

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/ui/FeedbackAdminActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create the activity shell and deny access early for non-admin devices**

```kotlin
class FeedbackAdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = SupabaseRepository.currentDeviceId(this)
        if (!isFeedbackAdminDevice(deviceId)) {
            Toast.makeText(this, "当前设备无反馈管理权限", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            FeedbackAdminScreen(
                deviceId = deviceId,
                onBack = ::finish,
                onLoadRecords = { onLoaded, onError ->
                    SupabaseRepository.listFeedbackForAdmin(
                        context = this,
                        onSuccess = onLoaded,
                        onError = onError,
                    )
                },
                onReply = { feedbackObjectId, reply, onSuccess, onError ->
                    SupabaseRepository.replyFeedbackAsAdmin(
                        context = this,
                        feedbackObjectId = feedbackObjectId,
                        reply = reply,
                        onSuccess = { onSuccess(it) },
                        onError = onError,
                    )
                },
            )
        }
    }
}
```

- [ ] **Step 2: Implement a screen with list loading, inline reply drafts, and card updates**

```kotlin
@Composable
private fun FeedbackAdminScreen(
    deviceId: String,
    onBack: () -> Unit,
    onLoadRecords: (
        onLoaded: (List<issue_feedback>) -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
    onReply: (
        feedbackObjectId: String,
        reply: String,
        onSuccess: (issue_feedback) -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
) {
    var loading by rememberSaveable { mutableStateOf(true) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<issue_feedback>>(emptyList()) }
    var replyDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var submittingIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun reload() {
        loading = true
        errorText = null
        onLoadRecords(
            {
                records = it
                loading = false
            },
            {
                errorText = it
                loading = false
            },
        )
    }

    LaunchedEffect(Unit) { reload() }

    SubpageScaffold(
        title = "反馈管理",
        subtitle = "全表阅览 · 官方回复",
        onBack = onBack,
        scrollable = false,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SubpageSectionCard(
                    title = "管理员设备",
                    subtitle = deviceId,
                ) {}
            }
            item {
                when {
                    loading -> { /* CircularProgressIndicator */ }
                    errorText != null -> { /* SubpageEmptyState */ }
                    else -> { /* records.forEach { FeedbackAdminRecordCard(...) } */ }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build each record card with image preview, current reply, and submit button**

```kotlin
@Composable
private fun FeedbackAdminRecordCard(
    record: issue_feedback,
    draft: String,
    submitting: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val imageUrl = firstFeedbackImageUrl(record.imageUrls)
    val feedbackObjectId = record.objectId.orEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = SupabaseTimeFormatter.formatToBeijing(record.createdAt),
                color = BodyInk,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
            SubpageBadge(if (record.reply.isNotBlank() || record.status == ISSUE_FEEDBACK_STATUS_REPLIED) "已回复" else "待回复")
        }
        Text(text = "设备ID：${record.deviceId}", color = BodyInk, fontSize = 12.sp, fontFamily = FontFamily.Serif)
        Text(text = record.description.ifBlank { "未填写问题描述" }, color = TitleInk, fontSize = 15.sp, fontFamily = FontFamily.Serif)
        if (imageUrl.isNotBlank()) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = "反馈图片",
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Text(text = "当前回复：${record.reply.ifBlank { "暂未回复" }}", color = BodyInk, fontSize = 13.sp, fontFamily = FontFamily.Serif)
        SubpageTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = "官方回复",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )
        StoneStyleButton(
            text = if (submitting) "提交中..." else "提交回复",
            onClick = onSubmit,
        )
    }
}
```

- [ ] **Step 4: Register the new activity in the manifest**

```xml
<activity
    android:name=".ui.FeedbackAdminActivity"
    android:exported="false"
    android:screenOrientation="portrait" />
```

- [ ] **Step 5: Run focused Android unit tests after adding the UI and activity**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.AdminAccessTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/yuanassist/ui/FeedbackAdminActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add feedback admin activity"
```

## Task 5: Refresh Project Context and Do Manual Verification

**Files:**
- Modify: `docs/project_context.md`

- [ ] **Step 1: Update the project context document with the new feedback admin module**

```md
## 功能结构
- 反馈系统
  - `FeedbackCenterActivity`：普通用户提交反馈、查看自己的历史反馈
  - `FeedbackAdminActivity`：管理员设备查看 `issue_feedback` 全表并写入单条官方回复

## 调试与开发时优先检查的入口
- 反馈管理行为不对
  - 先看 `FeedbackAdminActivity`
  - 再看 `SupabaseRepository`
  - 再看 `supabase/functions/yuanassist-api/index.ts`
```

- [ ] **Step 2: Do the manual verification checklist**

Run through this checklist on the admin device:

```text
1. 打开 Mine 页，确认“反馈管理”入口可见。
2. 切到普通设备或改用非管理员 deviceId，确认入口不可见。
3. 进入“反馈管理”页，确认能加载 issue_feedback 全表。
4. 检查每条记录是否展示时间、设备 ID、问题描述、首张图片、当前回复状态。
5. 对一条未回复记录填写单条官方回复并提交。
6. 重新进入页面或刷新，确认 reply 已写回且 status 为已回复。
7. 回到首页，确认原“问题反馈”入口和普通用户反馈页行为未变化。
```

- [ ] **Step 3: Capture final verification commands**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.AdminAccessTest"
deno test .\supabase\functions\yuanassist-api\feedbackAdmin_test.ts
deno check .\supabase\functions\yuanassist-api\index.ts
```

Expected: All commands succeed.

- [ ] **Step 4: Commit**

```powershell
git add docs/project_context.md
git commit -m "docs: record feedback admin module"
```
