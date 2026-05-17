#!/usr/bin/env node

import fs from "node:fs/promises";

const SUPABASE_URL = "https://ftryfykwzsadgiayquvz.supabase.co";

function parseArgs(argv) {
  const args = {};
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--from-csv") {
      args.fromCsv = argv[i + 1];
      i += 1;
      continue;
    }
    if (arg === "--version-code") {
      args.versionCode = argv[i + 1];
      i += 1;
      continue;
    }
    if (arg === "--version-name") {
      args.versionName = argv[i + 1];
      i += 1;
      continue;
    }
    if (arg === "--apk-url") {
      args.apkUrl = argv[i + 1];
      i += 1;
      continue;
    }
    if (arg === "--release-notes") {
      args.releaseNotes = argv[i + 1];
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
      "  SUPABASE_SECRET_KEY=... node tools/update_supabase_update_record.mjs --from-csv path/to/update.csv",
      "  SUPABASE_SECRET_KEY=... node tools/update_supabase_update_record.mjs --version-code 39 --version-name 1.1.5.3a --apk-url https://...",
      "",
      "Options:",
      "  --from-csv       Read versionCode/releaseNotes/versionName/apkUrl from an update CSV",
      "  --version-code   Override versionCode",
      "  --version-name   Override versionName",
      "  --apk-url        Override apkUrl",
      "  --release-notes  Override releaseNotes",
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

  return rows.filter((item) => !(item.length === 1 && item[0] === ""));
}

async function readUpdateCsv(csvPath) {
  const text = await fs.readFile(csvPath, "utf8");
  const rows = parseCsv(text.replace(/^\uFEFF/, ""));
  if (rows.length < 2) {
    throw new Error(`CSV has no data rows: ${csvPath}`);
  }

  const headers = rows[0];
  const values = rows[1];
  const record = {};
  for (let i = 0; i < headers.length; i += 1) {
    record[headers[i]] = values[i] ?? "";
  }

  return {
    versionCode: record.versionCode?.trim() ?? "",
    versionName: record.versionName?.trim() ?? "",
    apkUrl: record.apkUrl?.trim() ?? "",
    releaseNotes: record.releaseNotes ?? "",
  };
}

function mergePayload(args, csvRecord) {
  const payload = {};

  const versionCode = args.versionCode ?? csvRecord?.versionCode;
  const versionName = args.versionName ?? csvRecord?.versionName;
  const apkUrl = args.apkUrl ?? csvRecord?.apkUrl;
  const releaseNotes = args.releaseNotes ?? csvRecord?.releaseNotes;

  if (versionCode) payload.versionCode = String(versionCode);
  if (versionName) payload.versionName = versionName;
  if (apkUrl) payload.apkUrl = apkUrl;
  if (releaseNotes != null && releaseNotes !== "") payload.releaseNotes = releaseNotes;

  if (!payload.versionCode && !payload.versionName && !payload.apkUrl && !payload.releaseNotes) {
    throw new Error("No update fields provided");
  }

  return payload;
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  const bodyText = await response.text();
  let body;
  try {
    body = bodyText ? JSON.parse(bodyText) : null;
  } catch {
    body = bodyText;
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${typeof body === "string" ? body : JSON.stringify(body)}`);
  }
  return body;
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

  const csvRecord = args.fromCsv ? await readUpdateCsv(args.fromCsv) : null;
  const payload = mergePayload(args, csvRecord);
  const headers = {
    apikey: secretKey,
    Authorization: `Bearer ${secretKey}`,
    "Content-Type": "application/json",
    Prefer: "return=representation",
  };

  const existingRows = await requestJson(`${SUPABASE_URL}/rest/v1/update?select=versionCode&limit=1`, {
    headers: {
      apikey: secretKey,
      Authorization: `Bearer ${secretKey}`,
    },
  });

  let result;
  if (Array.isArray(existingRows) && existingRows.length > 0) {
    result = await requestJson(`${SUPABASE_URL}/rest/v1/update?versionCode=not.is.null`, {
      method: "PATCH",
      headers,
      body: JSON.stringify(payload),
    });
  } else {
    result = await requestJson(`${SUPABASE_URL}/rest/v1/update`, {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
    });
  }

  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
