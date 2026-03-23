import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";

type DedupeRow = {
  candidate_id: string;
  alias_text: string;
  status: "voting" | "approved" | "rejected";
  similarity_score: number;
  levenshtein_distance: number;
  bucket: "exact" | "high" | "gray" | "low";
  support_count: number;
  oppose_count: number;
};

type ExistingCandidate = {
  candidateId: string;
  aliasText: string;
  status: string;
  similarity: number;
  bucket: string;
  supportCount: number;
  opposeCount: number;
};

type LLMResult = {
  is_duplicate: boolean;
  confidence: number;
  matched_alias?: string;
  reason?: string;
};

type SubmitRequest = {
  songIdentifier?: string;
  aliasText?: string;
  deviceLocalDate?: string; // YYYY-MM-DD
  tzOffsetMinutes?: number;
};

type SubmitResponse = {
  status:
    | "created"
    | "rejected_duplicate"
    | "quota_exceeded"
    | "unauthenticated"
    | "invalid_request"
    | "error";
  message: string;
  candidate?: {
    id: string;
    songIdentifier: string;
    aliasText: string;
    status: string;
    createdAt: string;
  };
  existingCandidates?: ExistingCandidate[];
  similarAliases?: string[];
  quotaRemaining?: number;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function jsonResponse(payload: SubmitResponse, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

function normalizeAlias(input: string): string {
  return input
    .normalize("NFKC")
    .trim()
    .toLowerCase()
    .replace(/[\s]+/gu, "")
    .replace(/[\p{P}\p{S}，。！？、；：·・•（）【】《》〈〉「」『』“”‘’—～＿－…￥]+/gu, "");
}

function parseJSONFromText(text: string): LLMResult | null {
  if (!text) return null;

  const trimmed = text.trim();
  const direct = tryParse(trimmed);
  if (direct) return direct;

  const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)\s*```/i);
  if (fenced?.[1]) {
    const parsed = tryParse(fenced[1].trim());
    if (parsed) return parsed;
  }

  const objectLike = trimmed.match(/\{[\s\S]*\}/);
  if (objectLike?.[0]) {
    const parsed = tryParse(objectLike[0]);
    if (parsed) return parsed;
  }

  return null;
}

function tryParse(text: string): LLMResult | null {
  try {
    const parsed = JSON.parse(text) as Partial<LLMResult>;
    if (typeof parsed.is_duplicate !== "boolean") return null;
    const confidence = Number(parsed.confidence ?? 0);
    return {
      is_duplicate: parsed.is_duplicate,
      confidence: Number.isFinite(confidence) ? confidence : 0,
      matched_alias: parsed.matched_alias,
      reason: parsed.reason,
    };
  } catch {
    return null;
  }
}

async function callLLMForDedupe(inputAlias: string, candidates: DedupeRow[]): Promise<LLMResult | null> {
  const baseURL = Deno.env.get("THIRD_PARTY_LLM_BASE_URL");
  const apiKey = Deno.env.get("THIRD_PARTY_LLM_API_KEY");
  const model = Deno.env.get("THIRD_PARTY_LLM_MODEL");

  if (!baseURL || !apiKey || !model || candidates.length === 0) {
    return null;
  }

  const candidateLines = candidates
    .slice(0, 8)
    .map((row, index) => `${index + 1}. alias=${row.alias_text}, similarity=${row.similarity_score.toFixed(3)}, lev=${row.levenshtein_distance}`)
    .join("\n");

  const systemPrompt = [
    "You are a strict Chinese rhythm-game alias dedupe judge.",
    "Return JSON only with keys: is_duplicate(boolean), confidence(number 0-1), matched_alias(string), reason(string).",
    "Mark duplicate only when alias meaning and likely usage are effectively the same in community context.",
    "Prefer false when uncertain.",
  ].join(" ");

  const userPrompt = [
    `Input alias: ${inputAlias}`,
    "Candidate aliases:",
    candidateLines,
    "Answer in JSON only.",
  ].join("\n");

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 4500);

  try {
    const endpoint = `${baseURL.replace(/\/$/, "")}/chat/completions`;
    const response = await fetch(endpoint, {
      method: "POST",
      signal: controller.signal,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model,
        temperature: 0,
        max_tokens: 200,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: userPrompt },
        ],
      }),
    });

    if (!response.ok) {
      return null;
    }

    const payload = await response.json();
    const content = payload?.choices?.[0]?.message?.content;
    if (typeof content !== "string") {
      return null;
    }

    return parseJSONFromText(content);
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ status: "invalid_request", message: "Only POST is supported." }, 405);
  }

  const supabaseURL = Deno.env.get("SUPABASE_URL");
  const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const supabaseKey = supabaseServiceRoleKey ?? supabaseAnonKey;

  if (!supabaseURL || !supabaseKey) {
    return jsonResponse({ status: "error", message: "Supabase env is not configured in Edge Function." }, 500);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const supabase = createClient(supabaseURL, supabaseKey, {
    global: { headers: { Authorization: authHeader } },
  });

  const {
    data: { user },
    error: userError,
  } = await supabase.auth.getUser();

  if (userError || !user) {
    return jsonResponse({ status: "unauthenticated", message: "Please sign in before submitting aliases." }, 401);
  }

  let body: SubmitRequest;
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ status: "invalid_request", message: "Invalid JSON body." }, 400);
  }

  const songIdentifier = (body.songIdentifier ?? "").trim();
  const aliasText = (body.aliasText ?? "").trim();
  const deviceLocalDate = (body.deviceLocalDate ?? "").trim();
  const tzOffsetMinutes = Number(body.tzOffsetMinutes ?? 0);

  if (!songIdentifier || !aliasText || aliasText.length > 64 || !/^\d{4}-\d{2}-\d{2}$/.test(deviceLocalDate)) {
    return jsonResponse({
      status: "invalid_request",
      message: "songIdentifier, aliasText and deviceLocalDate are required. aliasText must be 1..64 chars.",
    }, 400);
  }

  const normalized = normalizeAlias(aliasText);
  if (!normalized || normalized.length < 1) {
    return jsonResponse({ status: "invalid_request", message: "Alias is empty after normalization." }, 400);
  }

  const { data: dedupeData, error: dedupeError } = await supabase.rpc("community_alias_find_candidates_for_dedupe", {
    p_song_identifier: songIdentifier,
    p_alias_text: aliasText,
    p_limit: 20,
  });

  if (dedupeError) {
    return jsonResponse({ status: "error", message: `Failed to evaluate dedupe candidates: ${dedupeError.message}` }, 500);
  }

  const dedupeRows = (dedupeData ?? []) as DedupeRow[];
  const hardMatches = dedupeRows.filter((row) => row.bucket === "exact" || row.bucket === "high");
  const grayMatches = dedupeRows.filter((row) => row.bucket === "gray");

  if (hardMatches.length > 0) {
    const existingCandidates: ExistingCandidate[] = hardMatches.slice(0, 6).map((row) => ({
      candidateId: row.candidate_id,
      aliasText: row.alias_text,
      status: row.status,
      similarity: row.similarity_score,
      bucket: row.bucket,
      supportCount: row.support_count,
      opposeCount: row.oppose_count,
    }));

    return jsonResponse({
      status: "rejected_duplicate",
      message: "A same/similar alias already exists for this song.",
      existingCandidates,
      similarAliases: existingCandidates.map((x) => x.aliasText),
    });
  }

  if (grayMatches.length > 0) {
    const llm = await callLLMForDedupe(aliasText, grayMatches);
    const confidence = Number(llm?.confidence ?? 0);
    if (llm?.is_duplicate === true && confidence >= 0.85) {
      const matched = grayMatches.find((x) => x.alias_text === llm.matched_alias) ?? grayMatches[0];
      const existingCandidates: ExistingCandidate[] = [matched].map((row) => ({
        candidateId: row.candidate_id,
        aliasText: row.alias_text,
        status: row.status,
        similarity: row.similarity_score,
        bucket: row.bucket,
        supportCount: row.support_count,
        opposeCount: row.oppose_count,
      }));

      return jsonResponse({
        status: "rejected_duplicate",
        message: llm.reason ?? "A highly similar alias already exists.",
        existingCandidates,
        similarAliases: existingCandidates.map((x) => x.aliasText),
      });
    }
  }

  const { data: dailyCountRaw, error: dailyCountError } = await supabase.rpc("community_alias_count_daily_creations", {
    p_local_date: deviceLocalDate,
  });

  if (dailyCountError) {
    return jsonResponse({ status: "error", message: `Failed to check daily quota: ${dailyCountError.message}` }, 500);
  }

  const dailyCount = Number(dailyCountRaw ?? 0);
  const quotaLimit = 5;
  if (Number.isFinite(dailyCount) && dailyCount >= quotaLimit) {
    return jsonResponse({
      status: "quota_exceeded",
      message: "Daily alias submission quota reached.",
      quotaRemaining: 0,
    });
  }

  const nowIso = new Date().toISOString();
  const { data: cycleEndRaw, error: cycleEndError } = await supabase.rpc("community_alias_cycle_end", {
    p_ts: nowIso,
  });
  if (cycleEndError || !cycleEndRaw) {
    return jsonResponse({
      status: "error",
      message: `Failed to evaluate current cycle end: ${cycleEndError?.message ?? "empty result"}`,
    }, 500);
  }

  const voteCloseAt = typeof cycleEndRaw === "string" ? cycleEndRaw : String(cycleEndRaw);

  const { data: inserted, error: insertError } = await supabase
    .from("community_alias_candidates")
    .insert({
      song_identifier: songIdentifier,
      alias_text: aliasText,
      alias_norm: normalized,
      submitter_id: user.id,
      status: "voting",
      vote_open_at: nowIso,
      vote_close_at: voteCloseAt,
      submitted_local_date: deviceLocalDate,
      submitted_tz_offset_min: Number.isFinite(tzOffsetMinutes) ? Math.trunc(tzOffsetMinutes) : 0,
    })
    .select("id, song_identifier, alias_text, status, created_at")
    .single();

  if (insertError) {
    // Handle race where another write won the unique index.
    if (insertError.code === "23505") {
      const { data: rerunRows } = await supabase.rpc("community_alias_find_candidates_for_dedupe", {
        p_song_identifier: songIdentifier,
        p_alias_text: aliasText,
        p_limit: 10,
      });

      const rows = ((rerunRows ?? []) as DedupeRow[])
        .filter((row) => row.bucket === "exact" || row.bucket === "high")
        .slice(0, 6)
        .map((row) => ({
          candidateId: row.candidate_id,
          aliasText: row.alias_text,
          status: row.status,
          similarity: row.similarity_score,
          bucket: row.bucket,
          supportCount: row.support_count,
          opposeCount: row.oppose_count,
        }));

      return jsonResponse({
        status: "rejected_duplicate",
        message: "A same/similar alias was submitted moments ago.",
        existingCandidates: rows,
        similarAliases: rows.map((x) => x.aliasText),
      });
    }

    return jsonResponse({ status: "error", message: `Failed to create alias candidate: ${insertError.message}` }, 500);
  }

  const quotaRemaining = Math.max(0, quotaLimit - (dailyCount + 1));
  return jsonResponse({
    status: "created",
    message: "Alias submitted and is now public in the current voting cycle.",
    candidate: {
      id: inserted.id,
      songIdentifier: inserted.song_identifier,
      aliasText: inserted.alias_text,
      status: inserted.status,
      createdAt: inserted.created_at,
    },
    quotaRemaining,
  });
});
