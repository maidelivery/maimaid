-- CreateEnum
CREATE TYPE "CandidateRejectionSource" AS ENUM ('admin_manual', 'community_vote');

-- AlterTable
ALTER TABLE "community_alias_candidates"
ADD COLUMN "rejectionSource" "CandidateRejectionSource";
