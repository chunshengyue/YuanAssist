#!/usr/bin/env node

const SUPABASE_URL = "https://ftryfykwzsadgiayquvz.supabase.co";
const TABLE_NAME = "User";
const BATCH_SIZE = 200;

function parseArgs(argv) {
  const args = { csvPath: "User_import_ready.csv" };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--csv") {
      args.csvPath = argv[i + 1];
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
      "  SUPABASE_SECRET_KEY=... node tools/import_user_csv_to_supabase.mjs --csv path/to/file.csv",
      "",
      "Supported CSV formats:",
      "  1. Original export headers: objectId, avatarUrl, nickname, username, createdAt",
      "  2. Prepared headers: object_id, avatar_url, nickname, username, created_at",
    ].join("\n"),
  );
}

function parseCsvLine(line) {
  const values = [];
  let current = "";
  let inQuotes = false;

  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];

    if (inQuotes) {
      if (ch === "\"") {
        if (line[i + 1] === "\"") {
          current += "\"";
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        current += ch;
      }
      continue;
    }

    if (ch === "\"") {
      inQuotes = true;
    } else if (ch === ",") {
      values.push(current);
      current = "";
    } else {
      current += ch;
    }
  }

  values.push(current);
  return values;
}

function readCsvRows(text) {
  const lines = text.replace(/^\uFEFF/, "").split(/\r?\n/).filter(Boolean);
  if (lines.length < 2) {
    return [];
  }

  const headers = parseCsvLine(lines[0]);
  return lines.slice(1).map((line) => {
    const values = parseCsvLine(line);
    const row = {};
    headers.forEach((header, index) => {
      row[header] = values[index] ?? "";
    });
    return row;
  });
}

function normalizeCreatedAt(value) {
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

function normalizeNullable(value) {
  return value == null || value === "" ? null : value;
}

function mapRow(row) {
  if ("object_id" in row) {
    return {
      object_id: row.object_id,
      avatar_url: normalizeNullable(row.avatar_url),
      nickname: normalizeNullable(row.nickname),
      username: row.username,
      created_at: normalizeCreatedAt(row.created_at),
    };
  }

  return {
    object_id: row.objectId,
    avatar_url: normalizeNullable(row.avatarUrl),
    nickname: normalizeNullable(row.nickname),
    username: row.username,
    created_at: normalizeCreatedAt(row.createdAt),
  };
}

function validateRows(rows) {
  const seenObjectIds = new Set();
  const seenUsernames = new Set();

  rows.forEach((row, index) => {
    const rowNo = index + 2;
    if (!row.object_id) {
      throw new Error(`Row ${rowNo} is missing object_id`);
    }
    if (!row.username) {
      throw new Error(`Row ${rowNo} is missing username`);
    }
    if (!row.created_at) {
      throw new Error(`Row ${rowNo} is missing created_at`);
    }
    if (seenObjectIds.has(row.object_id)) {
      throw new Error(`Duplicate object_id in CSV: ${row.object_id}`);
    }
    if (seenUsernames.has(row.username)) {
      throw new Error(`Duplicate username in CSV: ${row.username}`);
    }
    seenObjectIds.add(row.object_id);
    seenUsernames.add(row.username);
  });
}

async function fetchExistingUsers(secretKey) {
  const pageSize = 1000;
  const rows = [];
  for (let offset = 0; ; offset += pageSize) {
    const response = await fetch(
      `${SUPABASE_URL}/rest/v1/${encodeURIComponent(TABLE_NAME)}?select=object_id,username&limit=${pageSize}&offset=${offset}`,
      {
        method: "GET",
        headers: {
          apikey: secretKey,
          Authorization: `Bearer ${secretKey}`,
          "Accept-Profile": "public",
        },
      },
    );

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Failed to fetch existing users: ${response.status} ${body}`);
    }

    const batch = await response.json();
    rows.push(...batch);
    if (batch.length < pageSize) {
      break;
    }
  }
  return rows;
}

function reconcileRows(rows, existingUsers) {
  const byObjectId = new Map(existingUsers.map((row) => [row.object_id, row]));
  const byUsername = new Map(existingUsers.map((row) => [row.username, row]));
  const prepared = [];
  let remappedByUsername = 0;
  let skippedCrossConflicts = 0;

  for (const row of rows) {
    const existingByObjectId = byObjectId.get(row.object_id);
    const existingByUsername = byUsername.get(row.username);

    if (
      existingByObjectId &&
      existingByUsername &&
      existingByObjectId.object_id !== existingByUsername.object_id
    ) {
      skippedCrossConflicts += 1;
      continue;
    }

    if (!existingByObjectId && existingByUsername) {
      prepared.push({
        ...row,
        object_id: existingByUsername.object_id,
      });
      remappedByUsername += 1;
      continue;
    }

    prepared.push(row);
  }

  return {
    rows: prepared,
    remappedByUsername,
    skippedCrossConflicts,
  };
}

async function uploadBatch(secretKey, batch, batchNo, batchCount) {
  const response = await fetch(
    `${SUPABASE_URL}/rest/v1/${encodeURIComponent(TABLE_NAME)}?on_conflict=object_id`,
    {
      method: "POST",
      headers: {
        apikey: secretKey,
        Authorization: `Bearer ${secretKey}`,
        "Content-Type": "application/json",
        Prefer: "resolution=merge-duplicates,return=minimal",
        "Content-Profile": "public",
      },
      body: JSON.stringify(batch),
    },
  );

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Batch ${batchNo}/${batchCount} failed: ${response.status} ${body}`);
  }

  console.log(`Uploaded batch ${batchNo}/${batchCount} (${batch.length} rows)`);
}

async function fetchRemoteCount(secretKey) {
  const response = await fetch(
    `${SUPABASE_URL}/rest/v1/${encodeURIComponent(TABLE_NAME)}?select=id`,
    {
      method: "HEAD",
      headers: {
        apikey: secretKey,
        Authorization: `Bearer ${secretKey}`,
        Prefer: "count=exact",
        "Accept-Profile": "public",
      },
    },
  );

  if (!response.ok) {
    return null;
  }

  const contentRange = response.headers.get("content-range");
  if (!contentRange) {
    return null;
  }

  const match = contentRange.match(/\/(\d+)$/);
  return match ? Number(match[1]) : null;
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

  const fs = await import("node:fs/promises");
  const rawText = await fs.readFile(args.csvPath, "utf8");
  const parsedRows = readCsvRows(rawText).map(mapRow);
  validateRows(parsedRows);
  const existingUsers = await fetchExistingUsers(secretKey);
  const reconciled = reconcileRows(parsedRows, existingUsers);
  const rows = reconciled.rows;

  const batchCount = Math.ceil(rows.length / BATCH_SIZE);
  for (let start = 0, batchNo = 1; start < rows.length; start += BATCH_SIZE, batchNo += 1) {
    const batch = rows.slice(start, start + BATCH_SIZE);
    await uploadBatch(secretKey, batch, batchNo, batchCount);
  }

  const remoteCount = await fetchRemoteCount(secretKey);
  if (remoteCount == null) {
    console.log(`Done. Uploaded ${rows.length} rows.`);
  } else {
    console.log(
      `Done. Uploaded ${rows.length} rows. Remote table count: ${remoteCount}. Remapped by username: ${reconciled.remappedByUsername}. Skipped cross-conflicts: ${reconciled.skippedCrossConflicts}.`,
    );
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
