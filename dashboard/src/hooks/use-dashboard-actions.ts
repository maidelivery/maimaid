import { useTranslation } from "react-i18next";
import { startRegistration } from "@simplewebauthn/browser";
import { useState, type Dispatch, type SetStateAction } from "react";
import type { Song } from "@/components/songs/types";
import type { ConfirmDialogOptions } from "@/hooks/use-confirm-dialog";
import {
	isPasswordComplexEnough,
	isValidEmailAddress,
	isValidUsername,
	normalizeUsername,
	PASSWORD_COMPLEXITY_HINT,
	USERNAME_HINT,
} from "@/lib/app-helpers";
import type {
	BackupCodeStatus,
	MfaSetup,
	OpaqueRegistrationStartResponse,
	ScoreEditState,
	ScoreRow,
	StaticSource,
	StaticBundleSchedule,
	ToastSeverity,
} from "@/lib/app-types";
import { finishOpaqueRegistration, startOpaqueRegistration } from "@/lib/opaque-password";
import type { Session } from "@/lib/session";

type RequestOptions = {
	method?: "GET" | "POST" | "PATCH" | "DELETE";
	body?: unknown;
	auth?: boolean;
	retry?: boolean;
};

type RequestFn = <T>(path: string, options?: RequestOptions) => Promise<T>;

type UseDashboardActionsInput = {
	request: RequestFn;
	showToast: (message: string, severity?: ToastSeverity) => void;
	session: Session | null;
	setSession: Dispatch<SetStateAction<Session | null>>;
	activeProfileId: string;
	scoreSongName: string;
	scoreType: string;
	scoreDifficulty: string;
	scoreAchievements: string;
	resolveSongByName: (songName: string) => Song | null;
	loadScores: () => Promise<void>;
	dfQQ: string;
	dfImportToken: string;
	lxnsAuthCode: string;
	communitySongName: string;
	communityAliasText: string;
	setCommunityAliasText: (value: string) => void;
	setCommunitySongName: (value: string) => void;
	loadCommunity: () => Promise<void>;
	loadMyCommunity: () => Promise<void>;
	candidateVoteCloseDrafts: Record<string, string>;
	loadAdminCandidates: () => Promise<void>;
	loadAdminStats: () => Promise<void>;
	newUserEmail: string;
	newUserPassword: string;
	setNewUserEmail: (value: string) => void;
	setNewUserPassword: (value: string) => void;
	loadAdminUsers: () => Promise<void>;
	newStaticCategory: string;
	newStaticActiveUrl: string;
	newStaticFallbackUrls: string;
	setNewStaticCategory: (value: string) => void;
	setNewStaticActiveUrl: (value: string) => void;
	setNewStaticFallbackUrls: (value: string) => void;
	loadStaticAdmin: () => Promise<void>;
	setMfaSetup: (value: MfaSetup | null) => void;
	mfaSetupCode: string;
	setMfaSetupCode: (value: string) => void;
	loadMfaStatus: () => Promise<void>;
	loadPasskeys: () => Promise<void>;
	loadBackupCodeStatus: () => Promise<void>;
	confirmAction: (options: ConfirmDialogOptions) => Promise<boolean>;
};

export function useDashboardActions(input: UseDashboardActionsInput) {
	const { t } = useTranslation("app");
	const {
		request,
		showToast,
		session,
		setSession,
		activeProfileId,
		scoreSongName,
		scoreType,
		scoreDifficulty,
		scoreAchievements,
		resolveSongByName,
		loadScores,
		dfQQ,
		dfImportToken,
		lxnsAuthCode,
		communitySongName,
		communityAliasText,
		setCommunityAliasText,
		setCommunitySongName,
		loadCommunity,
		loadMyCommunity,
		candidateVoteCloseDrafts,
		loadAdminCandidates,
		loadAdminStats,
		newUserEmail,
		newUserPassword,
		setNewUserEmail,
		setNewUserPassword,
		loadAdminUsers,
		newStaticCategory,
		newStaticActiveUrl,
		newStaticFallbackUrls,
		setNewStaticCategory,
		setNewStaticActiveUrl,
		setNewStaticFallbackUrls,
		loadStaticAdmin,
		setMfaSetup,
		mfaSetupCode,
		setMfaSetupCode,
		loadMfaStatus,
		loadPasskeys,
		loadBackupCodeStatus,
		confirmAction,
	} = input;

	const [scoreEdit, setScoreEdit] = useState<ScoreEditState | null>(null);
	const [scoreEditOpen, setScoreEditOpen] = useState(false);

	type CommunitySubmitResponse = {
		status: "created" | "rejected_duplicate" | "quota_exceeded";
		message?: string;
		duplicateReason?: "lxns_existing" | "community_existing" | "admin_rejected_locked";
		quotaRemaining?: number;
	};

	const handleSubmitScore = async () => {
		if (!activeProfileId || !scoreSongName.trim()) {
			showToast(t("actionReqProfileSong"), "warning");
			return;
		}

		const achievements = Number(scoreAchievements);
		if (!Number.isFinite(achievements)) {
			showToast(t("actionAchieveFormat"), "warning");
			return;
		}

		const matchedSong = resolveSongByName(scoreSongName);
		if (!matchedSong) {
			showToast(t("actionSongMatchFail"), "warning");
			return;
		}

		try {
			await request("v1/scores:batchUpsert", {
				method: "POST",
				body: {
					profileId: activeProfileId,
					scores: [
						{
							songIdentifier: matchedSong.songIdentifier,
							title: matchedSong.title,
							type: scoreType,
							difficulty: scoreDifficulty,
							achievements,
						},
					],
				},
			});
			await loadScores();
			showToast(t("actionScoreSubmit"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const openScoreEditDialog = (row: ScoreRow) => {
		setScoreEdit({
			scoreId: row.id,
			achievements: String(row.achievements),
			rank: row.rank ?? "",
			dxScore: String(row.dxScore ?? 0),
			fc: row.fc ?? "",
			fs: row.fs ?? "",
		});
		setScoreEditOpen(true);
	};

	const handleSaveScoreEdit = async () => {
		if (!scoreEdit) {
			return;
		}
		const achievements = Number(scoreEdit.achievements);
		const dxScore = Number(scoreEdit.dxScore);
		if (!Number.isFinite(achievements) || !Number.isFinite(dxScore)) {
			showToast(t("actionScoreMissData"), "warning");
			return;
		}
		try {
			await request(`v1/scores/${encodeURIComponent(scoreEdit.scoreId)}`, {
				method: "PATCH",
				body: {
					achievements,
					rank: scoreEdit.rank.trim() || undefined,
					dxScore,
					fc: scoreEdit.fc.trim() || null,
					fs: scoreEdit.fs.trim() || null,
				},
			});
			setScoreEditOpen(false);
			setScoreEdit(null);
			await loadScores();
			showToast(t("actionScoreUpdate"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleDeleteScore = async (scoreId: string) => {
		const confirmed = await confirmAction({
			title: t("actionScoreDelTitle"),
			description: t("actionScoreDelDesc"),
			confirmText: t("actionScoreDelConfirm"),
			tone: "destructive",
		});
		if (!confirmed) {
			return;
		}
		try {
			await request(`v1/scores/${encodeURIComponent(scoreId)}`, {
				method: "DELETE",
			});
			await loadScores();
			showToast(t("actionScoreDeleted"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleDeletePlayRecord = async (recordId: string) => {
		const confirmed = await confirmAction({
			title: t("actionRecDelTitle"),
			description: t("actionRecDelDesc"),
			confirmText: t("actionScoreDelConfirm"),
			tone: "destructive",
		});
		if (!confirmed) {
			return;
		}
		try {
			await request(`v1/play-records/${encodeURIComponent(recordId)}`, {
				method: "DELETE",
			});
			await loadScores();
			showToast(t("actionRecDeleted"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleImportDf = async () => {
		if (!activeProfileId || !dfQQ.trim() || !dfImportToken.trim()) {
			showToast(t("actionDfReq"), "warning");
			return;
		}
		try {
			const payload = await request<{ upsertedCount: number }>("v1/imports:importDf", {
				method: "POST",
				body: {
					profileId: activeProfileId,
					qq: dfQQ.trim() || undefined,
					importToken: dfImportToken.trim(),
				},
			});
			await loadScores();
			showToast(t("actionDfSuccess", { count: payload.upsertedCount }), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleImportLxns = async (input: { codeVerifier: string }) => {
		if (!activeProfileId || !lxnsAuthCode.trim() || !input.codeVerifier.trim()) {
			showToast(t("actionLxnsReq"), "warning");
			return;
		}
		try {
			const oauthPayload = await request<{ accessToken: string; refreshToken: string }>("v1/imports:exchangeLxnsToken", {
				method: "POST",
				body: {
					code: lxnsAuthCode.trim(),
					codeVerifier: input.codeVerifier.trim(),
				},
			});

			const payload = await request<{ upsertedCount: number }>("v1/imports:importLxns", {
				method: "POST",
				body: {
					profileId: activeProfileId,
					accessToken: oauthPayload.accessToken,
				},
			});

			await loadScores();
			showToast(t("actionLxnsSuccess", { count: payload.upsertedCount }), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleCommunitySubmit = async () => {
		if (!communitySongName.trim() || !communityAliasText.trim()) {
			showToast(t("actionAliasReq"), "warning");
			return;
		}
		const matchedSong = resolveSongByName(communitySongName);
		if (!matchedSong) {
			showToast(t("actionSongMatchFail"), "warning");
			return;
		}
		try {
			const payload = await request<CommunitySubmitResponse>("v1/community/candidates", {
				method: "POST",
				body: {
					songIdentifier: matchedSong.songIdentifier,
					aliasText: communityAliasText.trim(),
				},
			});

			if (payload.status === "created") {
				setCommunityAliasText("");
				setCommunitySongName("");
				await loadCommunity();
				await loadMyCommunity();
				showToast(t("actionAliasSubmitSuccess"), "success");
				return;
			}

			if (payload.status === "quota_exceeded") {
				showToast(payload.message || t("actionAliasQuota"), "warning");
				return;
			}

			if (payload.status === "rejected_duplicate") {
				if (payload.duplicateReason === "lxns_existing") {
					showToast(payload.message || t("actionAliasLxnsDup"), "warning");
					return;
				}
				if (payload.duplicateReason === "admin_rejected_locked") {
					showToast(payload.message || t("actionAliasAdminDup"), "warning");
					return;
				}
				showToast(payload.message || t("actionAliasComDup"), "warning");
				return;
			}

			showToast(payload.message || t("actionAliasFail"), "warning");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleCommunityVote = async (candidateId: string, vote: -1 | 1) => {
		try {
			await request(`v1/community/candidates/${encodeURIComponent(candidateId)}:vote`, {
				method: "POST",
				body: { vote },
			});
			await loadCommunity();
			await loadMyCommunity();
			showToast(t("actionVoteSuccess"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleAdminCandidateStatus = async (candidateId: string, status: "voting" | "approved" | "rejected") => {
		const confirmed = await confirmAction({
			title: t("actionCandStatusTitle"),
			description: t("actionCandStatusDesc", { status: status }),
			confirmText: t("actionCandStatusConfirm"),
			tone: status === "rejected" ? "destructive" : "default",
		});
		if (!confirmed) {
			return;
		}
		try {
			await request(`v1/admin/candidates/${encodeURIComponent(candidateId)}:setStatus`, {
				method: "POST",
				body: { status },
			});
			await Promise.all([loadAdminCandidates(), loadAdminStats(), loadCommunity(), loadMyCommunity()]);
			showToast(t("actionCandStatusSuccess"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleAdminRollCycle = async () => {
		const confirmed = await confirmAction({
			title: t("actionRollTitle"),
			description: t("actionRollDesc"),
			confirmText: t("actionRollConfirm"),
			tone: "destructive",
		});
		if (!confirmed) {
			return;
		}
		try {
			await request("v1/admin:rollCycle", {
				method: "POST",
			});
			await Promise.all([loadAdminCandidates(), loadAdminStats(), loadCommunity(), loadMyCommunity()]);
			showToast(t("actionRollSuccess"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleAdminVoteWindowUpdate = async (candidateId: string) => {
		const value = candidateVoteCloseDrafts[candidateId];
		if (!value) {
			showToast(t("actionVoteTimeReq"), "warning");
			return;
		}
		const date = new Date(value);
		if (Number.isNaN(date.getTime())) {
			showToast(t("actionVoteTimeFormat"), "warning");
			return;
		}
		try {
			await request(`v1/admin/candidates/${encodeURIComponent(candidateId)}:updateVoteWindow`, {
				method: "POST",
				body: {
					voteCloseAt: date.toISOString(),
				},
			});
			await Promise.all([loadAdminCandidates(), loadAdminStats(), loadCommunity(), loadMyCommunity()]);
			showToast(t("actionVoteTimeSuccess"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleCreateUser = async () => {
		const normalizedEmail = newUserEmail.trim().toLowerCase();
		const password = newUserPassword;

		if (!isValidEmailAddress(normalizedEmail) || !password.trim()) {
			showToast(t("actionUserReq"), "warning");
			return;
		}
		if (!isPasswordComplexEnough(password)) {
			showToast(PASSWORD_COMPLEXITY_HINT, "warning");
			return;
		}
		const confirmed = await confirmAction({
			title: t("actionUserCreateTitle"),
			description: t("actionUserCreateDesc", { email: normalizedEmail }),
			confirmText: t("actionUserCreateConfirm"),
		});
		if (!confirmed) {
			return;
		}
		try {
			const registrationState = await startOpaqueRegistration(password);
			const startPayload = await request<OpaqueRegistrationStartResponse>("v1/admin/users:start", {
				method: "POST",
				body: {
					email: normalizedEmail,
					registrationRequest: registrationState.registrationRequest,
				},
			});
			const finishPayload = await finishOpaqueRegistration({
				password,
				clientRegistrationState: registrationState.clientRegistrationState,
				registrationResponse: startPayload.registrationResponse,
			});
			await request("v1/admin/users:finish", {
				method: "POST",
				body: {
					email: normalizedEmail,
					registrationRecord: finishPayload.registrationRecord,
					passwordFingerprint: finishPayload.passwordFingerprint,
				},
			});
			setNewUserEmail("");
			setNewUserPassword("");
			await loadAdminUsers();
			showToast(t("actionUserCreateSuccess"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleUpdateUsername = async (username: string) => {
		const normalizedUsername = normalizeUsername(username);
		if (!isValidUsername(normalizedUsername)) {
			showToast(t("actionHandleInvalid") || USERNAME_HINT, "warning");
			return false;
		}

		try {
			const user = await request<Session["user"]>("v1/auth/me", {
				method: "PATCH",
				body: {
					username: normalizedUsername,
				},
			});

			setSession((current) => {
				if (!current) {
					return current;
				}
				return {
					...current,
					user,
				};
			});

			const followUpLoads = [loadCommunity()];
			if (session?.user.isAdmin) {
				followUpLoads.push(loadAdminCandidates(), loadAdminUsers());
			}
			await Promise.allSettled(followUpLoads);
			showToast(t("actionHandleUpdated"), "success");
			return true;
		} catch (error) {
			showToast((error as Error).message, "error");
			return false;
		}
	};

	const handleDeleteUser = async (userId: string) => {
		const confirmed = await confirmAction({
			title: t("actionUserDelTitle"),
			description: t("actionUserDelDesc"),
			confirmText: t("actionUserDelTitle"),
			tone: "destructive",
		});
		if (!confirmed) {
			return;
		}
		try {
			await request(`v1/admin/users/${encodeURIComponent(userId)}`, { method: "DELETE" });
			await loadAdminUsers();
			showToast(t("actionUserDeleted"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleToggleSource = async (source: StaticSource) => {
		try {
			await request(`v1/admin/static-sources/${encodeURIComponent(source.id)}`, {
				method: "PATCH",
				body: {
					enabled: !source.enabled,
				},
			});
			await loadStaticAdmin();
			showToast(t("actionSrcUpdate"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleCreateSource = async () => {
		if (!newStaticCategory.trim() || !newStaticActiveUrl.trim()) {
			showToast(t("actionSrcReq"), "warning");
			return;
		}
		try {
			const fallbackUrls = newStaticFallbackUrls
				.split(",")
				.map((item) => item.trim())
				.filter((item) => item.length > 0);
			await request("v1/admin/static-sources", {
				method: "POST",
				body: {
					category: newStaticCategory.trim(),
					activeUrl: newStaticActiveUrl.trim(),
					fallbackUrls,
					enabled: true,
				},
			});
			setNewStaticCategory("");
			setNewStaticActiveUrl("");
			setNewStaticFallbackUrls("");
			await loadStaticAdmin();
			showToast(t("actionSrcCreate"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleEditSourceUrl = async (source: StaticSource, nextUrl: string, nextExtraUrl?: string) => {
		const normalizedUrl = nextUrl.trim();
		if (!normalizedUrl) {
			showToast(t("actionSrcUrlReq"), "warning");
			return;
		}
		const normalizedExtraUrl = nextExtraUrl?.trim() ?? "";
		try {
			const body: Record<string, unknown> = {
				activeUrl: normalizedUrl,
			};
			if (source.category === "chart_fit") {
				body.fallbackUrls = normalizedExtraUrl ? [normalizedExtraUrl] : [];
			}
			await request(`v1/admin/static-sources/${encodeURIComponent(source.id)}`, {
				method: "PATCH",
				body,
			});
			await loadStaticAdmin();
			showToast(t("actionSrcUrlUpdate"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleBuildBundle = async (): Promise<boolean> => {
		try {
			await request("v1/admin/static-bundles:build", {
				method: "POST",
				body: {
					force: true,
				},
			});
			await loadStaticAdmin();
			showToast(t("actionSrcBundle"), "success");
			return true;
		} catch (error) {
			showToast((error as Error).message, "error");
			return false;
		}
	};

	const handleUpdateStaticBundleSchedule = async (input: { enabled: boolean; intervalHours: number }) => {
		try {
			const payload = await request<{ schedule: StaticBundleSchedule }>("v1/admin/static-bundle-schedule", {
				method: "PATCH",
				body: {
					enabled: input.enabled,
					intervalHours: input.intervalHours,
				},
			});
			await loadStaticAdmin();
			if (payload.schedule.enabled) {
				showToast(t("actionSrcAuto", { hours: payload.schedule.intervalHours }), "success");
			} else {
				showToast(t("actionSrcAutoOff"), "success");
			}
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleStartTotpSetup = async () => {
		try {
			const payload = await request<MfaSetup>("v1/auth/mfa/totp:startSetup", {
				method: "POST",
			});
			setMfaSetup(payload);
			showToast(t("actionTotpGen"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleConfirmTotpSetup = async () => {
		if (!mfaSetupCode.trim()) {
			showToast(t("actionTotpReq"), "warning");
			return;
		}
		try {
			await request("v1/auth/mfa/totp:confirmSetup", {
				method: "POST",
				body: { code: mfaSetupCode.trim() },
			});
			setMfaSetup(null);
			setMfaSetupCode("");
			await Promise.all([loadMfaStatus(), loadBackupCodeStatus()]);
			showToast(t("actionTotpSuccess"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleDisableTotp = async () => {
		try {
			await request("v1/auth/mfa/totp:disable", {
				method: "POST",
			});
			await Promise.all([loadMfaStatus(), loadBackupCodeStatus()]);
			showToast(t("actionTotpOff"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleRegisterPasskey = async (): Promise<string | null> => {
		try {
			const options = await request<unknown>("v1/auth/mfa/passkeys:startRegistration", {
				method: "POST",
			});
			const browserResponse = await startRegistration({
				optionsJSON: options as Parameters<typeof startRegistration>[0]["optionsJSON"],
			});
			const payload = await request<{ passkey?: { credentialId: string } }>("v1/auth/mfa/passkeys:finishRegistration", {
				method: "POST",
				body: {
					response: browserResponse,
				},
			});
			await Promise.all([loadMfaStatus(), loadPasskeys()]);
			showToast(t("actionPassSuccess"), "success");
			return payload.passkey?.credentialId ?? null;
		} catch (error) {
			showToast((error as Error).message, "error");
			return null;
		}
	};

	const handleRenamePasskey = async (credentialId: string, name: string) => {
		const trimmedName = name.trim();
		if (!trimmedName) {
			showToast(t("actionPassReq"), "warning");
			return false;
		}
		try {
			await request(`v1/auth/mfa/passkey/${encodeURIComponent(credentialId)}`, {
				method: "PATCH",
				body: {
					name: trimmedName,
				},
			});
			await loadPasskeys();
			showToast(t("actionPassUpdate"), "success");
			return true;
		} catch (error) {
			showToast((error as Error).message, "error");
			return false;
		}
	};

	const handleDeletePasskey = async (credentialId: string) => {
		const confirmed = await confirmAction({
			title: t("actionPassDelTitle"),
			description: t("actionPassDelDesc"),
			confirmText: t("actionScoreDelConfirm"),
			tone: "destructive",
		});
		if (!confirmed) {
			return;
		}
		try {
			await request(`v1/auth/mfa/passkey/${encodeURIComponent(credentialId)}`, {
				method: "DELETE",
			});
			await Promise.all([loadMfaStatus(), loadPasskeys()]);
			showToast(t("actionPassDeleted"), "success");
		} catch (error) {
			showToast((error as Error).message, "error");
		}
	};

	const handleRegenerateBackupCodes = async (): Promise<
		(BackupCodeStatus & { codes: string[]; generatedAt: string }) | null
	> => {
		const confirmed = await confirmAction({
			title: t("actionBkTitle"),
			description: t("actionBkDesc"),
			confirmText: t("actionBkConfirm"),
			tone: "destructive",
		});
		if (!confirmed) {
			return null;
		}
		try {
			const payload = await request<{ codes: string[]; activeCount: number; generatedAt: string }>(
				"v1/auth/mfa/backup-codes:regenerate",
				{
					method: "POST",
				},
			);
			await Promise.all([loadMfaStatus(), loadBackupCodeStatus()]);
			showToast(t("actionBkSuccess"), "success");
			return {
				codes: payload.codes,
				activeCount: payload.activeCount,
				latestGeneratedAt: payload.generatedAt,
				generatedAt: payload.generatedAt,
			};
		} catch (error) {
			showToast((error as Error).message, "error");
			return null;
		}
	};

	return {
		scoreEdit,
		setScoreEdit,
		scoreEditOpen,
		setScoreEditOpen,
		handleSubmitScore,
		openScoreEditDialog,
		handleSaveScoreEdit,
		handleDeleteScore,
		handleDeletePlayRecord,
		handleImportDf,
		handleImportLxns,
		handleCommunitySubmit,
		handleCommunityVote,
		handleAdminCandidateStatus,
		handleAdminRollCycle,
		handleAdminVoteWindowUpdate,
		handleCreateUser,
		handleUpdateUsername,
		handleDeleteUser,
		handleToggleSource,
		handleCreateSource,
		handleEditSourceUrl,
		handleBuildBundle,
		handleUpdateStaticBundleSchedule,
		handleStartTotpSetup,
		handleConfirmTotpSetup,
		handleDisableTotp,
		handleRegisterPasskey,
		handleRenamePasskey,
		handleDeletePasskey,
		handleRegenerateBackupCodes,
	};
}
