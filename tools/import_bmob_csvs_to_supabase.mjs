#!/usr/bin/env node

const SUPABASE_URL = "https://ftryfykwzsadgiayquvz.supabase.co";
const PROJECT_ROOT = process.cwd();

const TABLE_CONFIGS = [
  {
    filePattern: /_announcement_\d+\.csv$/i,
    tableName: "announcement",
    mode: "upsert",
    columns: [
      { csv: "content", db: "content", type: "text" },
      { csv: "version", db: "version", type: "int" },
      { csv: "objectId", db: "objectId", type: "text" },
      { csv: "updatedAt", db: "updatedAt", type: "timestamptz" },
      { csv: "createdAt", db: "createdAt", type: "timestamptz" },
      { csv: "title", db: "title", type: "text" },
    ],
  },
  {
    filePattern: /_issue_feedback_\d+\.csv$/i,
    tableName: "issue_feedback",
    mode: "upsert",
    columns: [
      { csv: "reply", db: "reply", type: "text" },
      { csv: "deviceId", db: "deviceId", type: "text" },
      { csv: "objectId", db: "objectId", type: "text" },
      { csv: "updatedAt", db: "updatedAt", type: "timestamptz" },
      { csv: "createdAt", db: "createdAt", type: "timestamptz" },
      { csv: "logContent", db: "logContent", type: "text" },
      { csv: "status", db: "status", type: "int" },
      { csv: "imageUrls", db: "imageUrls", type: "text" },
      { csv: "description", db: "description", type: "text" },
    ],
  },
  {
    filePattern: /_OcrConfig_\d+\.csv$/i,
    tableName: "OcrConfig",
    mode: "upsert",
    columns: [
      { csv: "objectId", db: "objectId", type: "text" },
      { csv: "updatedAt", db: "updatedAt", type: "timestamptz" },
      { csv: "createdAt", db: "createdAt", type: "timestamptz" },
      { csv: "enabled", db: "enabled", type: "int" },
      { csv: "key", db: "key", type: "text" },
    ],
  },
  {
    filePattern: /_OcrUsage_\d+\.csv$/i,
    tableName: "OcrUsage",
    mode: "upsert",
    columns: [
      { csv: "objectId", db: "objectId", type: "text" },
      { csv: "updatedAt", db: "updatedAt", type: "timestamptz" },
      { csv: "createdAt", db: "createdAt", type: "timestamptz" },
      { csv: "deviceId", db: "deviceId", type: "text" },
      { csv: "usageCount", db: "usageCount", type: "int" },
      { csv: "date", db: "date", type: "text" },
    ],
  },
  {
    filePattern: /_strategy_comment_\d+\.csv$/i,
    tableName: "strategy_comment",
    mode: "upsert",
    columns: [
      { csv: "updatedAt", db: "updatedAt", type: "timestamptz" },
      { csv: "createdAt", db: "createdAt", type: "timestamptz" },
      { csv: "content", db: "content", type: "text" },
      { csv: "replyToUserName", db: "replyToUserName", type: "text" },
      { csv: "objectId", db: "objectId", type: "text" },
    ],
  },
  {
    filePattern: /_strategy_detail_\d+\.csv$/i,
    tableName: "strategy_detail",
    mode: "upsert",
    columns: [
      { csv: "agentType", db: "agentType", type: "int" },
      { csv: "scriptContent", db: "scriptContent", type: "text" },
      { csv: "title", db: "title", type: "text" },
      { csv: "objectId", db: "objectId", type: "text" },
      { csv: "updatedAt", db: "updatedAt", type: "timestamptz" },
      { csv: "createdAt", db: "createdAt", type: "timestamptz" },
      { csv: "visible", db: "visible", type: "int" },
      { csv: "viewCount", db: "viewCount", type: "int" },
      { csv: "agentTextDesc", db: "agentTextDesc", type: "text" },
      { csv: "agentText", db: "agentText", type: "text" },
      { csv: "instructions", db: "instructions", type: "text" },
      { csv: "favoriteCount", db: "favoriteCount", type: "int" },
      { csv: "ruyuan", db: "ruyuan", type: "int" },
      { csv: "agents", db: "agents", type: "text" },
      { csv: "agentImageUrl", db: "agentImageUrl", type: "text" },
      { csv: "content", db: "content", type: "text" },
      { csv: "coverUrl", db: "coverUrl", type: "text" },
      { csv: "config", db: "config", type: "text" },
      { csv: "agentSelection", db: "agentSelection", type: "text" },
      { csv: "originalPostUrl", db: "originalPostUrl", type: "text" },
      { csv: "strategyImage", db: "strategyImage", type: "text" },
    ],
  },
  {
    filePattern: /_strategy_favorite_\d+\.csv$/i,
    tableName: "strategy_favorite",
    mode: "upsert",
    columns: [
      { csv: "objectId", db: "objectId", type: "text" },
      { csv: "updatedAt", db: "updatedAt", type: "timestamptz" },
      { csv: "createdAt", db: "createdAt", type: "timestamptz" },
      { csv: "uniqueKey", db: "uniqueKey", type: "text" },
    ],
  },
  {
    filePattern: /_strategy_message_\d+\.csv$/i,
    tableName: "strategy_message",
    mode: "upsert",
    columns: [
      { csv: "isRead", db: "isRead", type: "bool" },
      { csv: "type", db: "type", type: "int" },
      { csv: "contentSnapshot", db: "contentSnapshot", type: "text" },
      { csv: "objectId", db: "objectId", type: "text" },
      { csv: "updatedAt", db: "updatedAt", type: "timestamptz" },
      { csv: "createdAt", db: "createdAt", type: "timestamptz" },
    ],
  },
  {
    filePattern: /_update_\d+\.csv$/i,
    tableName: "update",
    mode: "replace",
    replaceFilter: "versionCode=not.is.null",
    columns: [
      { csv: "versionCode", db: "versionCode", type: "text" },
      { csv: "releaseNotes", db: "releaseNotes", type: "text" },
      { csv: "versionName", db: "versionName", type: "text" },
      { csv: "apkUrl", db: "apkUrl", type: "text" },
    ],
  },
];

function parseArgs(argv) {
  const args = {
    dir: PROJECT_ROOT,
    batchSize: 200,
  };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--dir") {
      args.dir = argv[i + 1];
      i += 1;
      continue;
    }
    if (arg === "--batch-size") {
      args.batchSize = Number(argv[i + 1]);
      i += 1;
      continue;
    }
    if (arg === "--help" || arg === "-h") {
      args.help = true;
      continue;
    }
    throw new Error(`Unknown argument: ${arg}`);
  }
  return args;
}

function printHelp() {
  console.log(
    [
      "Usage:",
      "  SUPABASE_SECRET_KEY=... node tools/import_bmob_csvs_to_supabase.mjs --dir D:/YuanAssist-master",
      "",
      "This script imports all root-level ce7c10b39d790e195d0f3e11*.csv files except the User export.",
    ].join("\n"),
  );
}

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let inQuotes = false;

  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === "\"") {
        if (text[i + 1] === "\"") {
          field += "\"";
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        field += ch;
      }
      continue;
    }

    if (ch === "\"") {
      inQuotes = true;
    } else if (ch === ",") {
      row.push(field);
      field = "";
    } else if (ch === "\n") {
      row.push(field.replace(/\r$/, ""));
      rows.push(row);
      row = [];
      field = "";
    } else {
      field += ch;
    }
  }

  if (field.length > 0 || row.length > 0) {
    row.push(field.replace(/\r$/, ""));
    rows.push(row);
  }

  return rows.filter((r) => !(r.length === 1 && r[0] === ""));
}

function normalizeTimestamp(value) {
  if (!value) {
    return null;
  }
  if (/[zZ]$|[+-]\d{2}:\d{2}$/.test(value)) {
    return value;
  }
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) {
    return `${value.replace(" ", "T")}+08:00`;
  }
  return value;
}

function normalizeInt(value) {
  if (value == null || value === "") {
    return null;
  }
  return Number.parseInt(value, 10);
}

function normalizeBool(value) {
  if (value == null || value === "") {
    return null;
  }
  if (value === "true" || value === "1") {
    return true;
  }
  if (value === "false" || value === "0") {
    return false;
  }
  throw new Error(`Unsupported boolean value: ${value}`);
}

function normalizeValue(type, value) {
  if (type === "timestamptz") {
    return normalizeTimestamp(value);
  }
  if (type === "int") {
    return normalizeInt(value);
  }
  if (type === "bool") {
    return normalizeBool(value);
  }
  return value == null || value === "" ? null : value;
}

function findTableConfig(fileName) {
  return TABLE_CONFIGS.find((config) => config.filePattern.test(fileName));
}

async function readMappedRows(filePath, config, fs) {
  const text = (await fs.readFile(filePath, "utf8")).replace(/^\uFEFF/, "");
  const rows = parseCsv(text);
  if (rows.length === 0) {
    return [];
  }

  const header = rows[0];
  return rows.slice(1).map((values) => {
    const rawRow = Object.fromEntries(header.map((name, index) => [name, values[index] ?? ""]));
    const row = {};
    for (const column of config.columns) {
      row[column.db] = normalizeValue(column.type, rawRow[column.csv]);
    }
    return row;
  });
}

function validateRows(rows, config, fileName) {
  if (config.mode === "replace") {
    return;
  }

  const idValues = [];
  for (const row of rows) {
    if (!row.objectId) {
      throw new Error(`${fileName}: missing objectId`);
    }
    idValues.push(row.objectId);
  }
  if (new Set(idValues).size !== idValues.length) {
    throw new Error(`${fileName}: duplicate objectId`);
  }

  if (config.tableName === "strategy_favorite") {
    const uniqueKeys = rows.map((row) => row.uniqueKey).filter(Boolean);
    if (new Set(uniqueKeys).size !== uniqueKeys.length) {
      throw new Error(`${fileName}: duplicate uniqueKey`);
    }
  }

  if (config.tableName === "OcrConfig") {
    const keys = rows.map((row) => row.key).filter(Boolean);
    if (new Set(keys).size !== keys.length) {
      throw new Error(`${fileName}: duplicate key`);
    }
  }
}

function buildTableUrl(tableName, conflictColumn) {
  return `${SUPABASE_URL}/rest/v1/${encodeURIComponent(tableName)}?on_conflict=${encodeURIComponent(conflictColumn)}`;
}

async function uploadRows(secretKey, tableName, rows, conflictColumn, batchSize) {
  const totalBatches = Math.ceil(rows.length / batchSize);
  for (let start = 0, batchNo = 1; start < rows.length; start += batchSize, batchNo += 1) {
    const batch = rows.slice(start, start + batchSize);
    const response = await fetch(buildTableUrl(tableName, conflictColumn), {
      method: "POST",
      headers: {
        apikey: secretKey,
        Authorization: `Bearer ${secretKey}`,
        "Content-Type": "application/json",
        Prefer: "resolution=merge-duplicates,return=minimal",
        "Content-Profile": "public",
      },
      body: JSON.stringify(batch),
    });

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`${tableName}: batch ${batchNo}/${totalBatches} failed: ${response.status} ${body}`);
    }

    console.log(`${tableName}: uploaded batch ${batchNo}/${totalBatches} (${batch.length} rows)`);
  }
}

async function replaceRows(secretKey, tableName, rows, replaceFilter) {
  const deleteResponse = await fetch(
    `${SUPABASE_URL}/rest/v1/${encodeURIComponent(tableName)}?${replaceFilter}`,
    {
      method: "DELETE",
      headers: {
        apikey: secretKey,
        Authorization: `Bearer ${secretKey}`,
        Prefer: "return=minimal",
        "Content-Profile": "public",
      },
    },
  );

  if (!deleteResponse.ok) {
    const body = await deleteResponse.text();
    throw new Error(`${tableName}: clear existing rows failed: ${deleteResponse.status} ${body}`);
  }

  if (rows.length === 0) {
    console.log(`${tableName}: cleared existing rows, no new rows to insert`);
    return;
  }

  const insertResponse = await fetch(
    `${SUPABASE_URL}/rest/v1/${encodeURIComponent(tableName)}`,
    {
      method: "POST",
      headers: {
        apikey: secretKey,
        Authorization: `Bearer ${secretKey}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
        "Content-Profile": "public",
      },
      body: JSON.stringify(rows),
    },
  );

  if (!insertResponse.ok) {
    const body = await insertResponse.text();
    throw new Error(`${tableName}: insert replacement rows failed: ${insertResponse.status} ${body}`);
  }

  console.log(`${tableName}: replaced with ${rows.length} rows`);
}

async function main() {
  const args = parseArgs(process.argv);
  if (args.help) {
    printHelp();
    return;
  }

  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!secretKey) {
    throw new Error("Missing SUPABASE_SECRET_KEY environment variable");
  }
  if (!Number.isInteger(args.batchSize) || args.batchSize <= 0) {
    throw new Error("batch-size must be a positive integer");
  }

  const fs = await import("node:fs/promises");
  const path = await import("node:path");
  const names = await fs.readdir(args.dir);
  const targetFiles = names
    .filter((name) => name.startsWith("ce7c10b39d790e195d0f3e11") && name.endsWith(".csv") && !name.includes("__User_"))
    .sort();

  for (const name of targetFiles) {
    const config = findTableConfig(name);
    if (!config) {
      console.log(`Skip unmatched file: ${name}`);
      continue;
    }
    const filePath = path.join(args.dir, name);
    const rows = await readMappedRows(filePath, config, fs);
    validateRows(rows, config, name);
    if (config.mode === "replace") {
      await replaceRows(secretKey, config.tableName, rows, config.replaceFilter);
      continue;
    }
    const conflictColumn = config.tableName === "strategy_favorite" ? "uniqueKey" : (config.tableName === "OcrConfig" ? "key" : "objectId");
    await uploadRows(secretKey, config.tableName, rows, conflictColumn, args.batchSize);
  }

  console.log("All matched CSV files imported.");
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
