-- Route the periodic alias settlement through the application service. The
-- service removes legacy rejected duplicates before changing a candidate's
-- status, which keeps the candidate status unique constraint valid.
DO $migration$
DECLARE
    existing_job_id bigint;
BEGIN
    SELECT jobid
      INTO existing_job_id
      FROM cron.job
     WHERE jobname = 'maimaid-community-alias-roll'
     LIMIT 1;

    IF existing_job_id IS NOT NULL THEN
        PERFORM cron.unschedule(existing_job_id);
    END IF;

    PERFORM cron.schedule(
        'maimaid-community-alias-roll',
        '* * * * *',
        $command$select public.enqueue_job('community_alias_roll_cycle', '{}'::jsonb);$command$
    );
END;
$migration$;

CREATE OR REPLACE FUNCTION public.community_alias_cycle_end(p_ts timestamptz DEFAULT now())
RETURNS timestamptz
LANGUAGE sql
STABLE
AS $function$
SELECT coalesce(p_ts, now()) + interval '72 hours';
$function$;

-- Keep direct invocations safe as well. This function is retained for
-- operational use and removes conflicting rejected candidates before updating
-- an expired candidate.
CREATE OR REPLACE FUNCTION public.community_alias_roll_cycle(p_now timestamptz DEFAULT now())
RETURNS jsonb
LANGUAGE plpgsql
AS $function$
DECLARE
    v_now timestamptz := coalesce(p_now, now());
    v_settled_count integer := 0;
    v_candidate record;
    v_approved boolean;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('maimaid.community_alias_roll_cycle', 0));

    FOR v_candidate IN
        SELECT
            c."id",
            c."songIdentifier",
            c."aliasNorm",
            coalesce(sum(case when v."vote" = 1 then 1 else 0 end), 0)::integer AS support_count,
            coalesce(sum(case when v."vote" = -1 then 1 else 0 end), 0)::integer AS oppose_count
        FROM "community_alias_candidates" c
        LEFT JOIN "community_alias_votes" v ON v."candidateId" = c."id"
        WHERE c."status" = 'voting'
          AND c."voteCloseAt" IS NOT NULL
          AND c."voteCloseAt" <= v_now
        GROUP BY c."id"
        ORDER BY c."voteCloseAt" ASC, c."createdAt" ASC
    LOOP
        v_approved := v_candidate.support_count > v_candidate.oppose_count
            AND v_candidate.support_count >= 3;

        IF NOT v_approved THEN
            DELETE FROM "community_alias_candidates"
             WHERE "songIdentifier" = v_candidate."songIdentifier"
               AND "aliasNorm" = v_candidate."aliasNorm"
               AND "status" = 'rejected'
               AND "id" <> v_candidate."id";
        END IF;

        UPDATE "community_alias_candidates"
           SET "status" = CASE WHEN v_approved THEN 'approved' ELSE 'rejected' END::"CandidateStatus",
               "rejectionSource" = CASE WHEN v_approved THEN NULL ELSE 'community_vote' END::"CandidateRejectionSource",
               "approvedAt" = CASE WHEN v_approved THEN v_now ELSE NULL END,
               "rejectedAt" = CASE WHEN v_approved THEN NULL ELSE v_now END,
               "updatedAt" = v_now
         WHERE "id" = v_candidate."id";

        IF v_approved THEN
            INSERT INTO "aliases" ("songIdentifier", "aliasText", "aliasNorm", "source", "status")
            SELECT c."songIdentifier", c."aliasText", c."aliasNorm", 'community', 'approved'::"AliasStatus"
              FROM "community_alias_candidates" c
             WHERE c."id" = v_candidate."id"
            ON CONFLICT ("songIdentifier", "aliasNorm", "source") DO UPDATE
                SET "aliasText" = excluded."aliasText",
                    "status" = excluded."status",
                    "updatedAt" = now();
        END IF;

        v_settled_count := v_settled_count + 1;
    END LOOP;

    RETURN jsonb_build_object('now', v_now, 'settled_count', v_settled_count);
END;
$function$;
