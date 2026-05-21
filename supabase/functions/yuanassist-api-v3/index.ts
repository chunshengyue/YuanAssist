import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import {
  assertFeedbackAdminDevice,
  normalizeAdminFeedbackReply,
} from "./feedbackAdmin.ts";

type JsonRecord = Record<string, unknown>;

type UserRow = {
  id: string;
  object_id: string | null;
  avatar_url: string | null;
  nickname: string | null;
  username: string | null;
  created_at: string | null;
  updated_at?: string | null;
  device_id: string | null;
};

type StrategyRow = {
  id: string;
  objectId: string | null;
  agentType: number | null;
  scriptContent: string | null;
  title: string | null;
  updatedAt: string | null;
  createdAt: string | null;
  visible: number | null;
  viewCount: number | null;
  agentTextDesc: string | null;
  agentText: string | null;
  instructions: string | null;
  favoriteCount: number | null;
  ruyuan: number | null;
  agents: string | null;
  agentImageUrl: string | null;
  content: string | null;
  coverUrl: string | null;
  config: string | null;
  agentSelection: string | null;
  originalPostUrl: string | null;
  strategyImage: string | null;
  author_id: string | null;
};

type FavoriteRow = {
  id: string;
  objectId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  uniqueKey: string | null;
  user_id: string | null;
  strategy_id: string | null;
};

type CommentRow = {
  id: string;
  objectId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  content: string | null;
  replyToUserName: string | null;
  user_id: string | null;
  strategy_id: string | null;
  reply_to_comment_id: string | null;
  reply_to_user_id: string | null;
};

type MessageRow = {
  id: string;
  objectId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  isRead: boolean | null;
  type: number | null;
  contentSnapshot: string | null;
  recipient_id: string | null;
  sender_id: string | null;
  strategy_id: string | null;
  comment_id: string | null;
};

type FeedbackRow = {
  id: string;
  objectId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  reply: string | null;
  deviceId: string | null;
  logContent: string | null;
  status: number | null;
  imageUrls: string | null;
  description: string | null;
  user_id: string | null;
};

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

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const db = createClient(supabaseUrl, serviceRoleKey, {
  auth: { persistSession: false, autoRefreshToken: false },
});

const DAILY_SCRIPT_BUCKET = "daily-script-bundles";
const DAILY_SCRIPT_SELECT =
  "id, object_id, author_id, title, description, tags, guide_images, bundle_path, bundle_size, task_count, download_count, status, created_at, updated_at";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
    status: 200,
  });
}

function fail(message: string, status = 400) {
  return new Response(JSON.stringify({ success: false, message }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
    status,
  });
}

function requireString(value: unknown, fieldName: string): string {
  const normalized = String(value ?? "").trim();
  if (!normalized) {
    throw new Error(`${fieldName} 不能为空`);
  }
  return normalized;
}

function normalizeTimestamp(value: string | null | undefined): string | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toISOString();
}

function randomObjectId(length = 10): string {
  return crypto.randomUUID().replace(/-/g, "").slice(0, length);
}

function uniqueNonEmpty(values: Array<string | null | undefined>): string[] {
  return Array.from(new Set(values.map((item) => item?.trim()).filter((item): item is string => Boolean(item))));
}

async function ensureUserByDeviceId(deviceId: string): Promise<UserRow> {
  const { data, error } = await db
    .from("User")
    .select("id, object_id, avatar_url, nickname, username, created_at, device_id")
    .or(`device_id.eq.${deviceId},username.eq.${deviceId}`)
    .limit(1)
    .maybeSingle<UserRow>();

  if (error) throw error;

  if (data) {
    if (!data.device_id) {
      const { data: refreshed, error: updateError } = await db
        .from("User")
        .update({ device_id: deviceId })
        .eq("id", data.id)
        .select("id, object_id, avatar_url, nickname, username, created_at, device_id")
        .single<UserRow>();
      if (updateError) throw updateError;
      return refreshed;
    }
    return data;
  }

  const payload = {
    id: crypto.randomUUID(),
    object_id: randomObjectId(),
    username: deviceId,
    device_id: deviceId,
    nickname: `玩家_${deviceId}`,
    avatar_url: "",
  };
  const { data: inserted, error: insertError } = await db
    .from("User")
    .insert(payload)
    .select("id, object_id, avatar_url, nickname, username, created_at, device_id")
    .single<UserRow>();
  if (insertError) throw insertError;
  return inserted;
}

async function getUserByObjectId(objectId: string): Promise<UserRow | null> {
  const { data, error } = await db
    .from("User")
    .select("id, object_id, avatar_url, nickname, username, created_at, device_id")
    .eq("object_id", objectId)
    .limit(1)
    .maybeSingle<UserRow>();
  if (error) throw error;
  return data;
}

async function getStrategyByObjectId(objectId: string): Promise<StrategyRow | null> {
  const { data, error } = await db
    .from("strategy_detail")
    .select("id, objectId, agentType, scriptContent, title, updatedAt, createdAt, visible, viewCount, agentTextDesc, agentText, instructions, favoriteCount, ruyuan, agents, agentImageUrl, content, coverUrl, config, agentSelection, originalPostUrl, strategyImage, author_id")
    .eq("objectId", objectId)
    .limit(1)
    .maybeSingle<StrategyRow>();
  if (error) throw error;
  return data;
}

async function getCommentByObjectId(objectId: string): Promise<CommentRow | null> {
  const { data, error } = await db
    .from("strategy_comment")
    .select("id, objectId, createdAt, updatedAt, content, replyToUserName, user_id, strategy_id, reply_to_comment_id, reply_to_user_id")
    .eq("objectId", objectId)
    .limit(1)
    .maybeSingle<CommentRow>();
  if (error) throw error;
  return data;
}

async function loadUsersByIds(userIds: string[]): Promise<Map<string, UserRow>> {
  const ids = uniqueNonEmpty(userIds);
  if (ids.length === 0) return new Map();
  const { data, error } = await db
    .from("User")
    .select("id, object_id, avatar_url, nickname, username, created_at, device_id")
    .in("id", ids);
  if (error) throw error;
  return new Map((data as UserRow[]).map((item) => [item.id, item]));
}

async function loadStrategiesByIds(strategyIds: string[]): Promise<Map<string, StrategyRow>> {
  const ids = uniqueNonEmpty(strategyIds);
  if (ids.length === 0) return new Map();
  const { data, error } = await db
    .from("strategy_detail")
    .select("id, objectId, agentType, scriptContent, title, updatedAt, createdAt, visible, viewCount, agentTextDesc, agentText, instructions, favoriteCount, ruyuan, agents, agentImageUrl, content, coverUrl, config, agentSelection, originalPostUrl, strategyImage, author_id")
    .in("id", ids);
  if (error) throw error;
  return new Map((data as StrategyRow[]).map((item) => [item.id, item]));
}

async function loadCommentsByIds(commentIds: string[]): Promise<Map<string, CommentRow>> {
  const ids = uniqueNonEmpty(commentIds);
  if (ids.length === 0) return new Map();
  const { data, error } = await db
    .from("strategy_comment")
    .select("id, objectId, createdAt, updatedAt, content, replyToUserName, user_id, strategy_id, reply_to_comment_id, reply_to_user_id")
    .in("id", ids);
  if (error) throw error;
  return new Map((data as CommentRow[]).map((item) => [item.id, item]));
}

function mapUser(row: UserRow | null | undefined) {
  if (!row) return null;
  return {
    objectId: row.object_id,
    username: row.username ?? "",
    nickname: row.nickname ?? "",
    avatarUrl: row.avatar_url ?? "",
    deviceId: row.device_id ?? row.username ?? "",
    createdAt: normalizeTimestamp(row.created_at),
    updatedAt: normalizeTimestamp(row.updated_at),
  };
}

function mapStrategy(row: StrategyRow, author: UserRow | null | undefined) {
  return {
    objectId: row.objectId,
    title: row.title ?? "",
    content: row.content ?? "",
    scriptContent: row.scriptContent ?? "",
    config: row.config ?? "",
    instructions: row.instructions ?? "",
    strategyImage: row.strategyImage ?? "",
    agents: row.agents ?? "",
    coverUrl: row.coverUrl ?? "",
    originalPostUrl: row.originalPostUrl ?? "",
    agentType: row.agentType ?? 0,
    agentSelection: row.agentSelection ?? "",
    agentImageUrl: row.agentImageUrl ?? "",
    agentTextDesc: row.agentTextDesc ?? "",
    visible: row.visible ?? 0,
    ruyuan: row.ruyuan ?? 0,
    viewCount: row.viewCount ?? 0,
    favoriteCount: row.favoriteCount ?? 0,
    createdAt: normalizeTimestamp(row.createdAt),
    updatedAt: normalizeTimestamp(row.updatedAt),
    author: mapUser(author),
  };
}

function mapComment(
  row: CommentRow,
  users: Map<string, UserRow>,
  strategies: Map<string, StrategyRow>,
  comments: Map<string, CommentRow>,
) {
  const strategy = row.strategy_id ? strategies.get(row.strategy_id) : null;
  return {
    objectId: row.objectId,
    createdAt: normalizeTimestamp(row.createdAt),
    updatedAt: normalizeTimestamp(row.updatedAt),
    content: row.content ?? "",
    replyToUserName: row.replyToUserName ?? "",
    user: row.user_id ? mapUser(users.get(row.user_id)) : null,
    strategy: strategy ? mapStrategy(strategy, strategy.author_id ? users.get(strategy.author_id) : null) : null,
    replyToComment: row.reply_to_comment_id ? mapCommentShallow(comments.get(row.reply_to_comment_id), users) : null,
    replyToUser: row.reply_to_user_id ? mapUser(users.get(row.reply_to_user_id)) : null,
  };
}

function mapCommentShallow(row: CommentRow | undefined, users: Map<string, UserRow>) {
  if (!row) return null;
  return {
    objectId: row.objectId,
    createdAt: normalizeTimestamp(row.createdAt),
    updatedAt: normalizeTimestamp(row.updatedAt),
    content: row.content ?? "",
    replyToUserName: row.replyToUserName ?? "",
    user: row.user_id ? mapUser(users.get(row.user_id)) : null,
    strategy: null,
    replyToComment: null,
    replyToUser: row.reply_to_user_id ? mapUser(users.get(row.reply_to_user_id)) : null,
  };
}

function mapMessage(
  row: MessageRow,
  users: Map<string, UserRow>,
  strategies: Map<string, StrategyRow>,
  comments: Map<string, CommentRow>,
) {
  const strategy = row.strategy_id ? strategies.get(row.strategy_id) : null;
  return {
    objectId: row.objectId,
    createdAt: normalizeTimestamp(row.createdAt),
    updatedAt: normalizeTimestamp(row.updatedAt),
    type: row.type ?? 0,
    contentSnapshot: row.contentSnapshot ?? "",
    isRead: Boolean(row.isRead),
    recipient: row.recipient_id ? mapUser(users.get(row.recipient_id)) : null,
    sender: row.sender_id ? mapUser(users.get(row.sender_id)) : null,
    strategy: strategy ? mapStrategy(strategy, strategy.author_id ? users.get(strategy.author_id) : null) : null,
    comment: row.comment_id ? mapCommentShallow(comments.get(row.comment_id), users) : null,
  };
}

function mapFeedback(row: FeedbackRow, user: UserRow | null | undefined) {
  return {
    objectId: row.objectId,
    createdAt: normalizeTimestamp(row.createdAt),
    updatedAt: normalizeTimestamp(row.updatedAt),
    deviceId: row.deviceId ?? "",
    user: mapUser(user),
    description: row.description ?? "",
    logContent: row.logContent ?? "",
    imageUrls: row.imageUrls ?? "",
    reply: row.reply ?? "",
    status: row.status ?? 0,
  };
}

function mapUpdate(row: JsonRecord) {
  return {
    versionCode: Number.parseInt(String(row.versionCode ?? "0"), 10) || 0,
    versionName: String(row.versionName ?? ""),
    apkUrl: String(row.apkUrl ?? ""),
    releaseNotes: String(row.releaseNotes ?? ""),
  };
}

function mapAnnouncement(row: JsonRecord) {
  return {
    objectId: String(row.objectId ?? ""),
    title: String(row.title ?? ""),
    content: String(row.content ?? ""),
    version: Number(row.version ?? 0),
    createdAt: normalizeTimestamp((row.createdAt as string | null | undefined) ?? null),
    updatedAt: normalizeTimestamp((row.updatedAt as string | null | undefined) ?? null),
  };
}

function mapOcrConfig(row: JsonRecord) {
  return {
    objectId: String(row.objectId ?? ""),
    key: String(row.key ?? ""),
    enabled: Number(row.enabled ?? 0),
    createdAt: normalizeTimestamp((row.createdAt as string | null | undefined) ?? null),
    updatedAt: normalizeTimestamp((row.updatedAt as string | null | undefined) ?? null),
  };
}

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

async function listPublicStrategies(sortMode: string, limit: number) {
  const orderColumn = sortMode === "hot" ? "viewCount" : "createdAt";
  const { data, error } = await db
    .from("strategy_detail")
    .select("id, objectId, agentType, scriptContent, title, updatedAt, createdAt, visible, viewCount, agentTextDesc, agentText, instructions, favoriteCount, ruyuan, agents, agentImageUrl, content, coverUrl, config, agentSelection, originalPostUrl, strategyImage, author_id")
    .eq("visible", 1)
    .order(orderColumn, { ascending: false })
    .limit(limit);
  if (error) throw error;
  const rows = (data as StrategyRow[]) ?? [];
  const users = await loadUsersByIds(rows.map((item) => item.author_id));
  return rows.map((item) => mapStrategy(item, item.author_id ? users.get(item.author_id) : null));
}

async function getStrategyDetailForClient(strategyObjectId: string, requesterDeviceId?: string) {
  const strategy = await getStrategyByObjectId(strategyObjectId);
  if (!strategy) throw new Error("未找到该攻略");
  const author = strategy.author_id ? (await loadUsersByIds([strategy.author_id])).get(strategy.author_id) : null;
  const requester = requesterDeviceId ? await ensureUserByDeviceId(requesterDeviceId) : null;
  const isOwner = Boolean(requester && author && requester.id === author.id);
  if ((strategy.visible ?? 0) !== 1 && !isOwner) {
    throw new Error("该攻略当前不可见");
  }
  return mapStrategy(strategy, author);
}

async function getFavoriteState(deviceId: string, strategyObjectId: string) {
  const user = await ensureUserByDeviceId(deviceId);
  const uniqueKey = `${user.object_id ?? "guest"}_${strategyObjectId}`;
  const strategy = await getStrategyByObjectId(strategyObjectId);
  if (!strategy) throw new Error("未找到该攻略");
  const { data, error } = await db
    .from("strategy_favorite")
    .select("objectId")
    .eq("uniqueKey", uniqueKey)
    .limit(1)
    .maybeSingle<{ objectId: string | null }>();
  if (error) throw error;
  return {
    favorited: Boolean(data?.objectId),
    favoriteObjectId: data?.objectId ?? "",
    favoriteCount: strategy.favoriteCount ?? 0,
  };
}

async function setFavorite(deviceId: string, strategyObjectId: string, favorited: boolean) {
  const user = await ensureUserByDeviceId(deviceId);
  const strategy = await getStrategyByObjectId(strategyObjectId);
  if (!strategy) throw new Error("未找到该攻略");

  const uniqueKey = `${user.object_id ?? "guest"}_${strategyObjectId}`;
  const { data: existing, error: existingError } = await db
    .from("strategy_favorite")
    .select("id, objectId")
    .eq("uniqueKey", uniqueKey)
    .limit(1)
    .maybeSingle<{ id: string; objectId: string | null }>();
  if (existingError) throw existingError;

  let nextCount = strategy.favoriteCount ?? 0;
  let favoriteObjectId = existing?.objectId ?? "";

  if (favorited && !existing) {
    const insertPayload = {
      id: crypto.randomUUID(),
      objectId: randomObjectId(),
      uniqueKey,
      user_id: user.id,
      strategy_id: strategy.id,
    };
    const { data: inserted, error: insertError } = await db
      .from("strategy_favorite")
      .insert(insertPayload)
      .select("objectId")
      .single<{ objectId: string | null }>();
    if (insertError) throw insertError;
    nextCount += 1;
    favoriteObjectId = inserted.objectId ?? "";
  }

  if (!favorited && existing) {
    const { error: deleteError } = await db
      .from("strategy_favorite")
      .delete()
      .eq("id", existing.id);
    if (deleteError) throw deleteError;
    nextCount = Math.max(0, nextCount - 1);
    favoriteObjectId = "";
  }

  if ((strategy.favoriteCount ?? 0) !== nextCount) {
    const { error: updateError } = await db
      .from("strategy_detail")
      .update({ favoriteCount: nextCount })
      .eq("id", strategy.id);
    if (updateError) throw updateError;
  }

  return {
    favorited,
    favoriteObjectId,
    favoriteCount: nextCount,
  };
}

async function incrementStrategyView(strategyObjectId: string) {
  const strategy = await getStrategyByObjectId(strategyObjectId);
  if (!strategy) throw new Error("未找到该攻略");
  const nextCount = (strategy.viewCount ?? 0) + 1;
  const { error } = await db
    .from("strategy_detail")
    .update({ viewCount: nextCount })
    .eq("id", strategy.id);
  if (error) throw error;
  return { viewCount: nextCount };
}

async function listComments(strategyObjectId: string) {
  const strategy = await getStrategyByObjectId(strategyObjectId);
  if (!strategy) throw new Error("未找到该攻略");
  const { data, error } = await db
    .from("strategy_comment")
    .select("id, objectId, createdAt, updatedAt, content, replyToUserName, user_id, strategy_id, reply_to_comment_id, reply_to_user_id")
    .eq("strategy_id", strategy.id)
    .order("createdAt", { ascending: true });
  if (error) throw error;
  const rows = (data as CommentRow[]) ?? [];
  const users = await loadUsersByIds(rows.flatMap((item) => [item.user_id, item.reply_to_user_id]));
  const comments = new Map(rows.map((item) => [item.id, item]));
  const strategies = new Map([[strategy.id, strategy]]);
  return rows.map((item) => mapComment(item, users, strategies, comments));
}

async function createComment(body: JsonRecord) {
  const deviceId = requireString(body.deviceId, "deviceId");
  const strategyObjectId = requireString(body.strategyId, "strategyId");
  const content = requireString(body.content, "content");
  const user = await ensureUserByDeviceId(deviceId);
  const strategy = await getStrategyByObjectId(strategyObjectId);
  if (!strategy) throw new Error("未找到该攻略");

  let replyComment: CommentRow | null = null;
  let replyUser: UserRow | null = null;
  const replyToCommentObjectId = String(body.replyToCommentId ?? "").trim();
  if (replyToCommentObjectId) {
    replyComment = await getCommentByObjectId(replyToCommentObjectId);
    if (!replyComment) throw new Error("回复目标评论不存在");
  }

  const replyToUserObjectId = String(body.replyToUserId ?? "").trim();
  if (replyToUserObjectId) {
    replyUser = await getUserByObjectId(replyToUserObjectId);
  } else if (replyComment?.user_id) {
    replyUser = (await loadUsersByIds([replyComment.user_id])).get(replyComment.user_id) ?? null;
  }

  const replyToUserName = String(body.replyToUserName ?? "").trim();
  const insertPayload = {
    id: crypto.randomUUID(),
    objectId: randomObjectId(),
    strategy_id: strategy.id,
    user_id: user.id,
    content,
    reply_to_comment_id: replyComment?.id ?? null,
    reply_to_user_id: replyUser?.id ?? null,
    replyToUserName: replyToUserName || replyUser?.nickname || replyUser?.username || "",
  };

  const { data: inserted, error: insertError } = await db
    .from("strategy_comment")
    .insert(insertPayload)
    .select("id, objectId, createdAt, updatedAt, content, replyToUserName, user_id, strategy_id, reply_to_comment_id, reply_to_user_id")
    .single<CommentRow>();
  if (insertError) throw insertError;

  const recipientId = replyComment?.user_id ?? strategy.author_id;
  if (recipientId && recipientId !== user.id) {
    const { error: messageError } = await db.from("strategy_message").insert({
      id: crypto.randomUUID(),
      objectId: randomObjectId(),
      recipient_id: recipientId,
      sender_id: user.id,
      strategy_id: strategy.id,
      comment_id: inserted.id,
      type: replyComment ? 2 : 1,
      contentSnapshot: content,
      isRead: false,
    });
    if (messageError) {
      console.error("comment message insert failed", messageError);
    }
  }

  const users = await loadUsersByIds([inserted.user_id, inserted.reply_to_user_id]);
  const comments = new Map<string, CommentRow>();
  if (replyComment) comments.set(replyComment.id, replyComment);
  comments.set(inserted.id, inserted);
  const strategies = new Map([[strategy.id, strategy]]);
  return mapComment(inserted, users, strategies, comments);
}

async function deleteCommentByDevice(deviceId: string, commentObjectId: string) {
  const user = await ensureUserByDeviceId(deviceId);
  const comment = await getCommentByObjectId(commentObjectId);
  if (!comment) throw new Error("评论不存在");
  if (comment.user_id !== user.id) throw new Error("只能删除自己的评论");

  const { error: messageDeleteError } = await db
    .from("strategy_message")
    .delete()
    .eq("comment_id", comment.id);
  if (messageDeleteError) throw messageDeleteError;

  const { error } = await db
    .from("strategy_comment")
    .delete()
    .eq("id", comment.id);
  if (error) throw error;
  return { deleted: true };
}

async function listMessages(deviceId: string) {
  const user = await ensureUserByDeviceId(deviceId);
  const { data, error } = await db
    .from("strategy_message")
    .select("id, objectId, createdAt, updatedAt, isRead, type, contentSnapshot, recipient_id, sender_id, strategy_id, comment_id")
    .eq("recipient_id", user.id)
    .order("createdAt", { ascending: false })
    .limit(100);
  if (error) throw error;
  const rows = (data as MessageRow[]) ?? [];
  const users = await loadUsersByIds(rows.flatMap((item) => [item.sender_id, item.recipient_id]));
  const strategies = await loadStrategiesByIds(rows.map((item) => item.strategy_id));
  const strategyAuthors = await loadUsersByIds(Array.from(strategies.values()).map((item) => item.author_id));
  strategyAuthors.forEach((value, key) => users.set(key, value));
  const comments = await loadCommentsByIds(rows.map((item) => item.comment_id));
  return rows.map((item) => mapMessage(item, users, strategies, comments));
}

async function markMessagesRead(deviceId: string, messageObjectIds: string[]) {
  const user = await ensureUserByDeviceId(deviceId);
  const ids = uniqueNonEmpty(messageObjectIds);
  if (ids.length === 0) return { updated: 0 };
  const { error } = await db
    .from("strategy_message")
    .update({ isRead: true })
    .eq("recipient_id", user.id)
    .in("objectId", ids);
  if (error) throw error;
  return { updated: ids.length };
}

async function listMyFavorites(deviceId: string) {
  const user = await ensureUserByDeviceId(deviceId);
  const { data, error } = await db
    .from("strategy_favorite")
    .select("id, objectId, createdAt, updatedAt, uniqueKey, user_id, strategy_id")
    .eq("user_id", user.id)
    .order("updatedAt", { ascending: false })
    .limit(100);
  if (error) throw error;
  const rows = (data as FavoriteRow[]) ?? [];
  const strategies = await loadStrategiesByIds(rows.map((item) => item.strategy_id));
  const authors = await loadUsersByIds(Array.from(strategies.values()).map((item) => item.author_id));
  return rows
    .map((item) => item.strategy_id ? strategies.get(item.strategy_id) : null)
    .filter((item): item is StrategyRow => Boolean(item && (item.visible ?? 0) === 1))
    .map((item) => mapStrategy(item, item.author_id ? authors.get(item.author_id) : null));
}

async function listMyPublished(deviceId: string) {
  const user = await ensureUserByDeviceId(deviceId);
  const { data, error } = await db
    .from("strategy_detail")
    .select("id, objectId, agentType, scriptContent, title, updatedAt, createdAt, visible, viewCount, agentTextDesc, agentText, instructions, favoriteCount, ruyuan, agents, agentImageUrl, content, coverUrl, config, agentSelection, originalPostUrl, strategyImage, author_id")
    .eq("author_id", user.id)
    .order("updatedAt", { ascending: false })
    .limit(100);
  if (error) throw error;
  const rows = (data as StrategyRow[]) ?? [];
  return rows.map((item) => mapStrategy(item, user));
}

async function deleteStrategyByDevice(deviceId: string, strategyObjectId: string) {
  const user = await ensureUserByDeviceId(deviceId);
  const strategy = await getStrategyByObjectId(strategyObjectId);
  if (!strategy) throw new Error("攻略不存在");
  if (strategy.author_id !== user.id) throw new Error("只能删除自己的攻略");

  const { data: commentRows, error: commentQueryError } = await db
    .from("strategy_comment")
    .select("id")
    .eq("strategy_id", strategy.id);
  if (commentQueryError) throw commentQueryError;
  const commentIds = (commentRows ?? []).map((item: { id: string }) => item.id);

  if (commentIds.length > 0) {
    const { error: deleteMessageByCommentError } = await db
      .from("strategy_message")
      .delete()
      .in("comment_id", commentIds);
    if (deleteMessageByCommentError) throw deleteMessageByCommentError;
  }

  const { error: deleteMessageByStrategyError } = await db
    .from("strategy_message")
    .delete()
    .eq("strategy_id", strategy.id);
  if (deleteMessageByStrategyError) throw deleteMessageByStrategyError;

  const { error: deleteFavoriteError } = await db
    .from("strategy_favorite")
    .delete()
    .eq("strategy_id", strategy.id);
  if (deleteFavoriteError) throw deleteFavoriteError;

  const { error: deleteCommentError } = await db
    .from("strategy_comment")
    .delete()
    .eq("strategy_id", strategy.id);
  if (deleteCommentError) throw deleteCommentError;

  const { error: deleteStrategyError } = await db
    .from("strategy_detail")
    .delete()
    .eq("id", strategy.id);
  if (deleteStrategyError) throw deleteStrategyError;

  return { deleted: true };
}

async function saveStrategy(body: JsonRecord) {
  const deviceId = requireString(body.deviceId, "deviceId");
  const user = await ensureUserByDeviceId(deviceId);
  const strategyObjectId = String(body.strategyId ?? "").trim();
  const nowIso = new Date().toISOString();
  const payload = {
    title: String(body.title ?? "").trim(),
    content: String(body.content ?? ""),
    scriptContent: String(body.scriptContent ?? ""),
    config: String(body.config ?? ""),
    instructions: String(body.instructions ?? ""),
    strategyImage: String(body.strategyImage ?? ""),
    agents: String(body.agents ?? ""),
    coverUrl: String(body.coverUrl ?? ""),
    originalPostUrl: String(body.originalPostUrl ?? ""),
    agentType: Number(body.agentType ?? 0),
    agentSelection: String(body.agentSelection ?? ""),
    agentImageUrl: String(body.agentImageUrl ?? ""),
    agentTextDesc: String(body.agentTextDesc ?? ""),
    ruyuan: body.ruyuan == null ? null : Number(body.ruyuan),
  };

  if (!payload.title) {
    throw new Error("标题不能为空");
  }

  if (strategyObjectId) {
    const existing = await getStrategyByObjectId(strategyObjectId);
    if (!existing) throw new Error("攻略不存在");
    if (existing.author_id !== user.id) throw new Error("只能编辑自己的攻略");
    const { data, error } = await db
      .from("strategy_detail")
      .update({
        ...payload,
        updatedAt: nowIso,
      })
      .eq("id", existing.id)
      .select("id, objectId, agentType, scriptContent, title, updatedAt, createdAt, visible, viewCount, agentTextDesc, agentText, instructions, favoriteCount, ruyuan, agents, agentImageUrl, content, coverUrl, config, agentSelection, originalPostUrl, strategyImage, author_id")
      .single<StrategyRow>();
    if (error) throw error;
    return mapStrategy(data, user);
  }

  const insertPayload = {
    id: crypto.randomUUID(),
    objectId: randomObjectId(),
    author_id: user.id,
    createdAt: nowIso,
    updatedAt: nowIso,
    visible: 1,
    favoriteCount: 0,
    viewCount: 0,
    ...payload,
  };
  const { data, error } = await db
    .from("strategy_detail")
    .insert(insertPayload)
    .select("id, objectId, agentType, scriptContent, title, updatedAt, createdAt, visible, viewCount, agentTextDesc, agentText, instructions, favoriteCount, ruyuan, agents, agentImageUrl, content, coverUrl, config, agentSelection, originalPostUrl, strategyImage, author_id")
    .single<StrategyRow>();
  if (error) throw error;
  return mapStrategy(data, user);
}

async function listFeedback(deviceId: string) {
  const user = await ensureUserByDeviceId(deviceId);
  const { data, error } = await db
    .from("issue_feedback")
    .select("id, objectId, createdAt, updatedAt, reply, deviceId, logContent, status, imageUrls, description, user_id")
    .eq("deviceId", deviceId)
    .order("createdAt", { ascending: false })
    .limit(100);
  if (error) throw error;
  const rows = (data as FeedbackRow[]) ?? [];
  return rows.map((item) => mapFeedback(item, item.user_id === user.id ? user : null));
}

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

async function createFeedback(body: JsonRecord) {
  const deviceId = requireString(body.deviceId, "deviceId");
  const description = requireString(body.description, "description");
  const user = await ensureUserByDeviceId(deviceId);
  const payload = {
    id: crypto.randomUUID(),
    objectId: randomObjectId(),
    deviceId,
    user_id: user.id,
    description,
    logContent: String(body.logContent ?? ""),
    imageUrls: String(body.imageUrls ?? ""),
    reply: "",
    status: Number(body.status ?? 0),
  };
  const { data, error } = await db
    .from("issue_feedback")
    .insert(payload)
    .select("id, objectId, createdAt, updatedAt, reply, deviceId, logContent, status, imageUrls, description, user_id")
    .single<FeedbackRow>();
  if (error) throw error;
  return mapFeedback(data, user);
}

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

async function getLatestUpdate() {
  const { data, error } = await db.from("update").select("*");
  if (error) throw error;
  const rows = (data as JsonRecord[]) ?? [];
  if (rows.length === 0) throw new Error("暂无更新信息");
  const latest = rows
    .map((item) => mapUpdate(item))
    .sort((a, b) => b.versionCode - a.versionCode)[0];
  return latest;
}

async function getLatestAnnouncement() {
  const { data, error } = await db
    .from("announcement")
    .select("*")
    .order("version", { ascending: false })
    .limit(1)
    .maybeSingle<JsonRecord>();
  if (error) throw error;
  if (!data) throw new Error("暂无公告");
  return mapAnnouncement(data);
}

async function getOcrConfig(key: string) {
  const { data, error } = await db
    .from("OcrConfig")
    .select("*")
    .eq("key", key)
    .limit(1)
    .maybeSingle<JsonRecord>();
  if (error) throw error;
  if (!data) throw new Error("OCR 配置不存在");
  return mapOcrConfig(data);
}

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

async function routeAction(action: string, body: JsonRecord) {
  switch (action) {
    case "bootstrap-user":
      return mapUser(await ensureUserByDeviceId(requireString(body.deviceId, "deviceId")));
    case "update-user-profile": {
      const deviceId = requireString(body.deviceId, "deviceId");
      const user = await ensureUserByDeviceId(deviceId);
      const patch: JsonRecord = {};
      const nickname = String(body.nickname ?? "").trim();
      const avatarUrl = String(body.avatarUrl ?? "").trim();
      if (nickname) patch.nickname = nickname;
      if (body.avatarUrl !== undefined) patch.avatar_url = avatarUrl;
      if (Object.keys(patch).length === 0) {
        return mapUser(user);
      }
      const { data, error } = await db
        .from("User")
        .update(patch)
        .eq("id", user.id)
        .select("id, object_id, avatar_url, nickname, username, created_at, device_id")
        .single<UserRow>();
      if (error) throw error;
      return mapUser(data);
    }
    case "list-public-strategies":
      return await listPublicStrategies(String(body.sortMode ?? "newest"), Number(body.limit ?? 200));
    case "get-strategy-detail":
      return await getStrategyDetailForClient(
        requireString(body.strategyId, "strategyId"),
        String(body.deviceId ?? "").trim() || undefined,
      );
    case "increment-strategy-view":
      return await incrementStrategyView(requireString(body.strategyId, "strategyId"));
    case "get-favorite-state":
      return await getFavoriteState(
        requireString(body.deviceId, "deviceId"),
        requireString(body.strategyId, "strategyId"),
      );
    case "set-favorite":
      return await setFavorite(
        requireString(body.deviceId, "deviceId"),
        requireString(body.strategyId, "strategyId"),
        Boolean(body.favorited),
      );
    case "list-comments":
      return await listComments(requireString(body.strategyId, "strategyId"));
    case "create-comment":
      return await createComment(body);
    case "delete-comment":
      return await deleteCommentByDevice(
        requireString(body.deviceId, "deviceId"),
        requireString(body.commentId, "commentId"),
      );
    case "list-messages":
      return await listMessages(requireString(body.deviceId, "deviceId"));
    case "mark-messages-read":
      return await markMessagesRead(
        requireString(body.deviceId, "deviceId"),
        Array.isArray(body.messageIds) ? body.messageIds.map((item) => String(item)) : [],
      );
    case "list-my-favorites":
      return await listMyFavorites(requireString(body.deviceId, "deviceId"));
    case "list-my-published":
      return await listMyPublished(requireString(body.deviceId, "deviceId"));
    case "delete-strategy":
      return await deleteStrategyByDevice(
        requireString(body.deviceId, "deviceId"),
        requireString(body.strategyId, "strategyId"),
      );
    case "save-strategy":
      return await saveStrategy(body);
    case "list-feedback":
      return await listFeedback(requireString(body.deviceId, "deviceId"));
    case "list-feedback-admin":
      return await listFeedbackAdmin(requireString(body.deviceId, "deviceId"));
    case "create-feedback":
      return await createFeedback(body);
    case "reply-feedback-admin":
      return await replyFeedbackAdmin(body);
    case "get-latest-update":
      return await getLatestUpdate();
    case "get-latest-announcement":
      return await getLatestAnnouncement();
    case "get-ocr-config":
      return await getOcrConfig(requireString(body.key, "key"));
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
    default:
      throw new Error(`未知 action: ${action}`);
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return fail("只支持 POST", 405);
  }

  try {
    const body = (await req.json()) as JsonRecord;
    const action = requireString(body.action, "action");
    const data = await routeAction(action, body);
    return ok(data);
  } catch (error) {
    console.error(error);
    const message = error instanceof Error ? error.message : "服务异常";
    return fail(message, 400);
  }
});
