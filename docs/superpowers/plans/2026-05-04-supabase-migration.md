# Supabase Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the app's Bmob-backed strategy, message, feedback, update, and OCR remote flows with Supabase-backed APIs.

**Architecture:** Add a thin Supabase Edge Functions layer as the only mobile-facing backend surface, then migrate Android data access to Retrofit/OkHttp callers that map JSON payloads into the app's existing UI-facing Kotlin models. Keep the current UI flow intact while removing Bmob-specific querying, pointer writes, and initialization.

**Tech Stack:** Supabase Edge Functions, Postgres tables/RLS, Kotlin, OkHttp, Retrofit, Gson, Android XML UI

---

### Task 1: Define migration boundaries

**Files:**
- Modify: `docs/superpowers/plans/2026-05-04-supabase-migration.md`
- Inspect: `app/src/main/java/com/example/yuanassist/ui/HomeFragment.kt`
- Inspect: `app/src/main/java/com/example/yuanassist/ui/JobStationActivity.kt`
- Inspect: `app/src/main/java/com/example/yuanassist/ui/UploadStrategyActivity.kt`
- Inspect: `app/src/main/java/com/example/yuanassist/ui/MyFavoriteActivity.kt`
- Inspect: `app/src/main/java/com/example/yuanassist/ui/MyMessageActivity.kt`
- Inspect: `app/src/main/java/com/example/yuanassist/ui/MyPublishedActivity.kt`
- Inspect: `app/src/main/java/com/example/yuanassist/ui/FeedbackCenterActivity.kt`

- [ ] Confirm all Bmob-backed user-facing flows that must move in this pass.
- [ ] Confirm Supabase tables already support those flows without further schema changes.

### Task 2: Add Supabase backend entrypoints

**Files:**
- Create: `supabase/functions/yuanassist-api/index.ts`
- Create: `supabase/functions/yuanassist-api/deno.json`

- [ ] Implement a single routed Edge Function for user bootstrap, strategies, favorites, comments, messages, feedback, updates, announcements, and OCR config.
- [ ] Keep request/response JSON shaped for the Android client rather than exposing raw table rows.
- [ ] Use only publishable-key-safe behavior from the mobile app; keep privileged writes in the function body.

### Task 3: Add database access rules for the new API path

**Files:**
- Modify: Supabase project policies and grants

- [ ] Add only the minimum RLS/policy/grant changes needed for the function-backed workflow.
- [ ] Remove or avoid public callable privileged helpers that are no longer needed by the app.

### Task 4: Add Android Supabase API client layer

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/network/SupabaseApiModels.kt`
- Create: `app/src/main/java/com/example/yuanassist/network/SupabaseApiService.kt`
- Create: `app/src/main/java/com/example/yuanassist/network/SupabaseApiClient.kt`
- Create: `app/src/main/java/com/example/yuanassist/network/SupabaseRepository.kt`

- [ ] Centralize project URL, publishable key, and function invocation headers.
- [ ] Add repository methods for every migrated flow so UI files stop knowing backend details.

### Task 5: Migrate strategy/user/message/feedback consumers

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/core/YuanAssistApp.kt`
- Modify: `app/src/main/java/com/example/yuanassist/utils/OcrRouteManager.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/HomeFragment.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/StrategyFragment.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/JobStationActivity.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/UploadStrategyActivity.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/MyFavoriteActivity.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/MyMessageActivity.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/MyPublishedActivity.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/FeedbackCenterActivity.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/MineFragment.kt`

- [ ] Replace Bmob reads/writes with repository calls.
- [ ] Preserve current device-based login behavior by making the backend bootstrap a user from Android ID.
- [ ] Preserve current image upload flow unless and until storage migration is explicitly needed.

### Task 6: Remove Bmob runtime wiring

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/yuanassist/model/StrategyModel.kt`
- Modify: `app/src/main/java/com/example/yuanassist/model/Feedback.kt`
- Modify: `app/src/main/java/com/example/yuanassist/model/AppUpdateModels.kt`

- [ ] Remove `Bmob.initialize`, the Bmob content provider, and the Bmob dependency.
- [ ] Replace `BmobObject`/`BmobUser`-shaped models where the migrated code path no longer needs them.

### Task 7: Static verification and cleanup

**Files:**
- Modify: touched files only

- [ ] Search for remaining `cn.bmob` imports and `Bmob` call sites.
- [ ] Search for app code still depending on Bmob pointer semantics.
- [ ] Document any intentionally deferred Bmob remnants that are outside this migration scope.
