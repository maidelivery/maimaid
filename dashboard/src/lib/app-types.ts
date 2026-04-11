import type { LucideIcon } from "lucide-react";
import type { Song } from "@/components/songs/types";

export type ToastSeverity = "success" | "info" | "warning" | "error";
export type AuthMode = "login" | "register" | "forgot" | "verify-email" | "reset-password";
export type LoginStep = "email" | "password" | "mfa";

export type Profile = {
	id: string;
	name: string;
	avatarUrl?: string | null;
	isActive: boolean;
};

export type ScoreRow = {
	id: string;
	profileId: string;
	achievements: number | string;
	rank: string;
	dxScore: number;
	fc?: string | null;
	fs?: string | null;
	sheet?: {
		songIdentifier: string;
		chartType: string;
		difficulty: string;
		song?: {
			title: string;
		};
	};
};

export type PlayRecordRow = {
	id: string;
	profileId: string;
	achievements: number | string;
	rank: string;
	dxScore: number;
	fc?: string | null;
	fs?: string | null;
	playTime: string;
	sheet?: {
		songIdentifier: string;
		chartType: string;
		difficulty: string;
		song?: {
			title: string;
		};
	};
};

export type CommunityCandidate = {
	candidateId: string;
	songIdentifier: string;
	aliasText: string;
	status?: string;
	supportCount: number;
	opposeCount: number;
	submitterId: string;
	submitterHandle: string;
	voteOpenAt: string | null;
	voteCloseAt: string | null;
	myVote?: number | null;
	createdAt?: string;
};

export type MyCommunityCandidate = {
	candidateId: string;
	songIdentifier: string;
	aliasText: string;
	status: string;
	supportCount: number;
	opposeCount: number;
	voteCloseAt: string | null;
	updatedAt: string;
};

export type ApprovedAliasSyncRow = {
	candidateId: string;
	songIdentifier: string;
	aliasText: string;
	updatedAt: string;
	approvedAt: string | null;
};

export type AdminCandidate = {
	candidateId: string;
	songIdentifier: string;
	aliasText: string;
	status: string;
	supportCount: number;
	opposeCount: number;
	submitterId: string;
	submitterHandle: string;
	submitterEmail: string | null;
	voteCloseAt: string | null;
	createdAt: string;
	updatedAt: string;
};

export type AdminDashboardStats = {
	totalCount: number;
	votingCount: number;
	approvedCount: number;
	rejectedCount: number;
	closingSoonCount: number;
	expiredVotingCount: number;
	todaySubmissions: number;
};

export type AdminUserRow = {
	id: string;
	email: string;
	handle: string;
	isAdmin: boolean;
	status: string;
	createdAt: string;
	mfa: {
		enabled: boolean;
		totpEnabled: boolean;
		passkeyCount: number;
	};
};

export type StaticSource = {
	id: string;
	category: string;
	activeUrl: string;
	fallbackUrls: string[];
	enabled: boolean;
};

export type StaticBundle = {
	id: string;
	version: string;
	md5: string;
	active: boolean;
	createdAt: string;
};

export type StaticBundleSchedule = {
	enabled: boolean;
	intervalHours: number;
	cronExpression: string;
};

export type MfaStatus = {
	mfaEnabled: boolean;
	totpEnabled: boolean;
	passkeyCount: number;
	backupCodeCount: number;
};

export type MfaSetup = {
	secretBase32: string;
	otpauthUrl: string;
};

export type PasskeyCredential = {
	credentialId: string;
	name: string | null;
	transports: string[];
	createdAt: string;
	updatedAt: string;
};

export type BackupCodeStatus = {
	activeCount: number;
	latestGeneratedAt: string | null;
};

export type RegisterResponse = {
	verificationEmailSent: boolean;
};

export type ForgotPasswordResponse = {
	success: boolean;
	resetEmailSent?: boolean;
};

export type PasskeyLoginStartResponse = {
	challengeToken: string;
	options: unknown;
};

export type ScoreEditState = {
	scoreId: string;
	achievements: string;
	rank: string;
	dxScore: string;
	fc: string;
	fs: string;
};

export type SongFilterSnapshot = {
	song: Song;
	aliases: string[];
	songIds: number[];
	sheets: Array<{
		type: string;
		difficulty: string;
		noteDesigner?: string | null;
		internalLevelValue?: number | null;
		levelValue?: number | null;
		regionJp?: boolean;
		regionIntl?: boolean;
		regionCn?: boolean;
	}>;
	maxDifficulty: number;
};

export type SongIdItem = {
	id: number;
	name: string;
};

export type CatalogVersionItem = {
	version: string;
	abbr: string;
	releaseDate?: string | null;
};

export type VerificationResult = {
	status: "success" | "error";
	code: string;
};

export type NavigationTabItem = {
	value: string;
	label: string;
	icon: LucideIcon;
};
