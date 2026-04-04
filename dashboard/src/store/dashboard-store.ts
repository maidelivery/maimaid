import type { SetStateAction } from "react";
import { create } from "zustand";
import type { Alias, Sheet as SongSheet, Song, SongFilterSettings, SongSortOption } from "@/components/songs/types";
import {
	DEFAULT_SONG_FILTERS,
	getLocalStorageItem,
	SONG_FAVORITE_STORAGE_KEY,
	SONG_HIDE_DELETED_STORAGE_KEY,
	SONG_SORT_ASC_STORAGE_KEY,
	SONG_SORT_OPTION_STORAGE_KEY,
} from "@/lib/app-helpers";
import type {
	AdminCandidate,
	AdminDashboardStats,
	AdminUserRow,
	ApprovedAliasSyncRow,
	BackupCodeStatus,
	CatalogVersionItem,
	CommunityCandidate,
	MfaSetup,
	MfaStatus,
	MyCommunityCandidate,
	PasskeyCredential,
	PlayRecordRow,
	Profile,
	ScoreRow,
	SongIdItem,
	StaticBundle,
	StaticBundleSchedule,
	StaticSource,
} from "@/lib/app-types";
import type { Session } from "@/lib/session";

type StateUpdater<T> = SetStateAction<T>;

function resolveStateUpdate<T>(next: StateUpdater<T>, previous: T): T {
	if (typeof next === "function") {
		return (next as (previousState: T) => T)(previous);
	}
	return next;
}

function readInitialSongFilters(): SongFilterSettings {
	const hideDeletedRaw = getLocalStorageItem(SONG_HIDE_DELETED_STORAGE_KEY);
	const hideDeletedSongs = hideDeletedRaw === "true";
	return {
		...DEFAULT_SONG_FILTERS,
		hideDeletedSongs,
	};
}

function readInitialSongSortOption(): SongSortOption {
	const raw = getLocalStorageItem(SONG_SORT_OPTION_STORAGE_KEY);
	if (raw === "default" || raw === "versionDate" || raw === "difficulty") {
		return raw;
	}
	return "default";
}

function readInitialSongSortAscending(): boolean {
	const raw = getLocalStorageItem(SONG_SORT_ASC_STORAGE_KEY);
	return raw === null ? true : raw === "true";
}

function readInitialSongFavorites(): Set<string> {
	try {
		const raw = getLocalStorageItem(SONG_FAVORITE_STORAGE_KEY);
		if (!raw) return new Set<string>();
		const parsed = JSON.parse(raw) as unknown;
		if (!Array.isArray(parsed)) return new Set<string>();
		return new Set(parsed.filter((item): item is string => typeof item === "string"));
	} catch {
		return new Set<string>();
	}
}

type DashboardStore = {
	session: Session | null;
	sessionBootstrapped: boolean;
	tab: string;
	mobileDrawerOpen: boolean;
	mfaStatus: MfaStatus | null;
	mfaSetup: MfaSetup | null;
	mfaSetupCode: string;
	passkeys: PasskeyCredential[];
	backupCodeStatus: BackupCodeStatus;
	profiles: Profile[];
	activeProfileId: string;
	songKeyword: string;
	songFilterExpanded: boolean;
	songCatalog: Song[];
	catalogSheets: SongSheet[];
	catalogAliases: Alias[];
	songIdItems: SongIdItem[];
	catalogVersionItems: CatalogVersionItem[];
	songFilters: SongFilterSettings;
	songSortOption: SongSortOption;
	songSortAscending: boolean;
	songFavorites: Set<string>;
	selectedSong: Song | null;
	songSheets: SongSheet[];
	songAliases: Alias[];
	songDetailLoading: boolean;
	songDetailChartType: string;
	scores: ScoreRow[];
	playRecords: PlayRecordRow[];
	scoreSearchKeyword: string;
	scoreSongName: string;
	scoreType: string;
	scoreDifficulty: string;
	scoreAchievements: string;
	dfQQ: string;
	dfImportToken: string;
	lxnsAuthCode: string;
	communityRows: CommunityCandidate[];
	communitySongName: string;
	communityAliasText: string;
	communityDailyCount: number;
	myCandidateSongName: string;
	myCommunityRows: MyCommunityCandidate[];
	approvedAliasRows: ApprovedAliasSyncRow[];
	adminCandidates: AdminCandidate[];
	adminCandidateStatus: string;
	adminStats: AdminDashboardStats | null;
	candidateVoteCloseDrafts: Record<string, string>;
	adminUsers: AdminUserRow[];
	newUserEmail: string;
	newUserPassword: string;
	staticSources: StaticSource[];
	staticBundles: StaticBundle[];
	staticBundleSchedule: StaticBundleSchedule | null;
	newStaticCategory: string;
	newStaticActiveUrl: string;
	newStaticFallbackUrls: string;

	setSession: (next: StateUpdater<Session | null>) => void;
	setSessionBootstrapped: (next: StateUpdater<boolean>) => void;
	setTab: (next: StateUpdater<string>) => void;
	setMobileDrawerOpen: (next: StateUpdater<boolean>) => void;
	setMfaStatus: (next: StateUpdater<MfaStatus | null>) => void;
	setMfaSetup: (next: StateUpdater<MfaSetup | null>) => void;
	setMfaSetupCode: (next: StateUpdater<string>) => void;
	setPasskeys: (next: StateUpdater<PasskeyCredential[]>) => void;
	setBackupCodeStatus: (next: StateUpdater<BackupCodeStatus>) => void;
	setProfiles: (next: StateUpdater<Profile[]>) => void;
	setActiveProfileId: (next: StateUpdater<string>) => void;
	setSongKeyword: (next: StateUpdater<string>) => void;
	setSongFilterExpanded: (next: StateUpdater<boolean>) => void;
	setSongCatalog: (next: StateUpdater<Song[]>) => void;
	setCatalogSheets: (next: StateUpdater<SongSheet[]>) => void;
	setCatalogAliases: (next: StateUpdater<Alias[]>) => void;
	setSongIdItems: (next: StateUpdater<SongIdItem[]>) => void;
	setCatalogVersionItems: (next: StateUpdater<CatalogVersionItem[]>) => void;
	setSongFilters: (next: StateUpdater<SongFilterSettings>) => void;
	setSongSortOption: (next: StateUpdater<SongSortOption>) => void;
	setSongSortAscending: (next: StateUpdater<boolean>) => void;
	setSongFavorites: (next: StateUpdater<Set<string>>) => void;
	setSelectedSong: (next: StateUpdater<Song | null>) => void;
	setSongSheets: (next: StateUpdater<SongSheet[]>) => void;
	setSongAliases: (next: StateUpdater<Alias[]>) => void;
	setSongDetailLoading: (next: StateUpdater<boolean>) => void;
	setSongDetailChartType: (next: StateUpdater<string>) => void;
	setScores: (next: StateUpdater<ScoreRow[]>) => void;
	setPlayRecords: (next: StateUpdater<PlayRecordRow[]>) => void;
	setScoreSearchKeyword: (next: StateUpdater<string>) => void;
	setScoreSongName: (next: StateUpdater<string>) => void;
	setScoreType: (next: StateUpdater<string>) => void;
	setScoreDifficulty: (next: StateUpdater<string>) => void;
	setScoreAchievements: (next: StateUpdater<string>) => void;
	setDfQQ: (next: StateUpdater<string>) => void;
	setDfImportToken: (next: StateUpdater<string>) => void;
	setLxnsAuthCode: (next: StateUpdater<string>) => void;
	setCommunityRows: (next: StateUpdater<CommunityCandidate[]>) => void;
	setCommunitySongName: (next: StateUpdater<string>) => void;
	setCommunityAliasText: (next: StateUpdater<string>) => void;
	setCommunityDailyCount: (next: StateUpdater<number>) => void;
	setMyCandidateSongName: (next: StateUpdater<string>) => void;
	setMyCommunityRows: (next: StateUpdater<MyCommunityCandidate[]>) => void;
	setApprovedAliasRows: (next: StateUpdater<ApprovedAliasSyncRow[]>) => void;
	setAdminCandidates: (next: StateUpdater<AdminCandidate[]>) => void;
	setAdminCandidateStatus: (next: StateUpdater<string>) => void;
	setAdminStats: (next: StateUpdater<AdminDashboardStats | null>) => void;
	setCandidateVoteCloseDrafts: (next: StateUpdater<Record<string, string>>) => void;
	setAdminUsers: (next: StateUpdater<AdminUserRow[]>) => void;
	setNewUserEmail: (next: StateUpdater<string>) => void;
	setNewUserPassword: (next: StateUpdater<string>) => void;
	setStaticSources: (next: StateUpdater<StaticSource[]>) => void;
	setStaticBundles: (next: StateUpdater<StaticBundle[]>) => void;
	setStaticBundleSchedule: (next: StateUpdater<StaticBundleSchedule | null>) => void;
	setNewStaticCategory: (next: StateUpdater<string>) => void;
	setNewStaticActiveUrl: (next: StateUpdater<string>) => void;
	setNewStaticFallbackUrls: (next: StateUpdater<string>) => void;
	resetForSignedOut: () => void;
};

export const useDashboardStore = create<DashboardStore>((set) => ({
	session: null,
	sessionBootstrapped: false,
	tab: "songs",
	mobileDrawerOpen: false,
	mfaStatus: null,
	mfaSetup: null,
	mfaSetupCode: "",
	passkeys: [],
	backupCodeStatus: {
		activeCount: 0,
		latestGeneratedAt: null,
	},
	profiles: [],
	activeProfileId: "",
	songKeyword: "",
	songFilterExpanded: false,
	songCatalog: [],
	catalogSheets: [],
	catalogAliases: [],
	songIdItems: [],
	catalogVersionItems: [],
	songFilters: readInitialSongFilters(),
	songSortOption: readInitialSongSortOption(),
	songSortAscending: readInitialSongSortAscending(),
	songFavorites: readInitialSongFavorites(),
	selectedSong: null,
	songSheets: [],
	songAliases: [],
	songDetailLoading: false,
	songDetailChartType: "",
	scores: [],
	playRecords: [],
	scoreSearchKeyword: "",
	scoreSongName: "",
	scoreType: "standard",
	scoreDifficulty: "expert",
	scoreAchievements: "100.0000",
	dfQQ: "",
	dfImportToken: "",
	lxnsAuthCode: "",
	communityRows: [],
	communitySongName: "",
	communityAliasText: "",
	communityDailyCount: 0,
	myCandidateSongName: "",
	myCommunityRows: [],
	approvedAliasRows: [],
	adminCandidates: [],
	adminCandidateStatus: "voting",
	adminStats: null,
	candidateVoteCloseDrafts: {},
	adminUsers: [],
	newUserEmail: "",
	newUserPassword: "",
	staticSources: [],
	staticBundles: [],
	staticBundleSchedule: null,
	newStaticCategory: "",
	newStaticActiveUrl: "",
	newStaticFallbackUrls: "",

	setSession: (next) => set((state) => ({ session: resolveStateUpdate(next, state.session) })),
	setSessionBootstrapped: (next) =>
		set((state) => ({ sessionBootstrapped: resolveStateUpdate(next, state.sessionBootstrapped) })),
	setTab: (next) => set((state) => ({ tab: resolveStateUpdate(next, state.tab) })),
	setMobileDrawerOpen: (next) => set((state) => ({ mobileDrawerOpen: resolveStateUpdate(next, state.mobileDrawerOpen) })),
	setMfaStatus: (next) => set((state) => ({ mfaStatus: resolveStateUpdate(next, state.mfaStatus) })),
	setMfaSetup: (next) => set((state) => ({ mfaSetup: resolveStateUpdate(next, state.mfaSetup) })),
	setMfaSetupCode: (next) => set((state) => ({ mfaSetupCode: resolveStateUpdate(next, state.mfaSetupCode) })),
	setPasskeys: (next) => set((state) => ({ passkeys: resolveStateUpdate(next, state.passkeys) })),
	setBackupCodeStatus: (next) => set((state) => ({ backupCodeStatus: resolveStateUpdate(next, state.backupCodeStatus) })),
	setProfiles: (next) => set((state) => ({ profiles: resolveStateUpdate(next, state.profiles) })),
	setActiveProfileId: (next) => set((state) => ({ activeProfileId: resolveStateUpdate(next, state.activeProfileId) })),
	setSongKeyword: (next) => set((state) => ({ songKeyword: resolveStateUpdate(next, state.songKeyword) })),
	setSongFilterExpanded: (next) => set((state) => ({ songFilterExpanded: resolveStateUpdate(next, state.songFilterExpanded) })),
	setSongCatalog: (next) => set((state) => ({ songCatalog: resolveStateUpdate(next, state.songCatalog) })),
	setCatalogSheets: (next) => set((state) => ({ catalogSheets: resolveStateUpdate(next, state.catalogSheets) })),
	setCatalogAliases: (next) => set((state) => ({ catalogAliases: resolveStateUpdate(next, state.catalogAliases) })),
	setSongIdItems: (next) => set((state) => ({ songIdItems: resolveStateUpdate(next, state.songIdItems) })),
	setCatalogVersionItems: (next) =>
		set((state) => ({ catalogVersionItems: resolveStateUpdate(next, state.catalogVersionItems) })),
	setSongFilters: (next) => set((state) => ({ songFilters: resolveStateUpdate(next, state.songFilters) })),
	setSongSortOption: (next) => set((state) => ({ songSortOption: resolveStateUpdate(next, state.songSortOption) })),
	setSongSortAscending: (next) => set((state) => ({ songSortAscending: resolveStateUpdate(next, state.songSortAscending) })),
	setSongFavorites: (next) => set((state) => ({ songFavorites: resolveStateUpdate(next, state.songFavorites) })),
	setSelectedSong: (next) => set((state) => ({ selectedSong: resolveStateUpdate(next, state.selectedSong) })),
	setSongSheets: (next) => set((state) => ({ songSheets: resolveStateUpdate(next, state.songSheets) })),
	setSongAliases: (next) => set((state) => ({ songAliases: resolveStateUpdate(next, state.songAliases) })),
	setSongDetailLoading: (next) => set((state) => ({ songDetailLoading: resolveStateUpdate(next, state.songDetailLoading) })),
	setSongDetailChartType: (next) =>
		set((state) => ({ songDetailChartType: resolveStateUpdate(next, state.songDetailChartType) })),
	setScores: (next) => set((state) => ({ scores: resolveStateUpdate(next, state.scores) })),
	setPlayRecords: (next) => set((state) => ({ playRecords: resolveStateUpdate(next, state.playRecords) })),
	setScoreSearchKeyword: (next) => set((state) => ({ scoreSearchKeyword: resolveStateUpdate(next, state.scoreSearchKeyword) })),
	setScoreSongName: (next) => set((state) => ({ scoreSongName: resolveStateUpdate(next, state.scoreSongName) })),
	setScoreType: (next) => set((state) => ({ scoreType: resolveStateUpdate(next, state.scoreType) })),
	setScoreDifficulty: (next) => set((state) => ({ scoreDifficulty: resolveStateUpdate(next, state.scoreDifficulty) })),
	setScoreAchievements: (next) => set((state) => ({ scoreAchievements: resolveStateUpdate(next, state.scoreAchievements) })),
	setDfQQ: (next) => set((state) => ({ dfQQ: resolveStateUpdate(next, state.dfQQ) })),
	setDfImportToken: (next) => set((state) => ({ dfImportToken: resolveStateUpdate(next, state.dfImportToken) })),
	setLxnsAuthCode: (next) => set((state) => ({ lxnsAuthCode: resolveStateUpdate(next, state.lxnsAuthCode) })),
	setCommunityRows: (next) => set((state) => ({ communityRows: resolveStateUpdate(next, state.communityRows) })),
	setCommunitySongName: (next) => set((state) => ({ communitySongName: resolveStateUpdate(next, state.communitySongName) })),
	setCommunityAliasText: (next) => set((state) => ({ communityAliasText: resolveStateUpdate(next, state.communityAliasText) })),
	setCommunityDailyCount: (next) =>
		set((state) => ({ communityDailyCount: resolveStateUpdate(next, state.communityDailyCount) })),
	setMyCandidateSongName: (next) =>
		set((state) => ({ myCandidateSongName: resolveStateUpdate(next, state.myCandidateSongName) })),
	setMyCommunityRows: (next) => set((state) => ({ myCommunityRows: resolveStateUpdate(next, state.myCommunityRows) })),
	setApprovedAliasRows: (next) => set((state) => ({ approvedAliasRows: resolveStateUpdate(next, state.approvedAliasRows) })),
	setAdminCandidates: (next) => set((state) => ({ adminCandidates: resolveStateUpdate(next, state.adminCandidates) })),
	setAdminCandidateStatus: (next) =>
		set((state) => ({ adminCandidateStatus: resolveStateUpdate(next, state.adminCandidateStatus) })),
	setAdminStats: (next) => set((state) => ({ adminStats: resolveStateUpdate(next, state.adminStats) })),
	setCandidateVoteCloseDrafts: (next) =>
		set((state) => ({ candidateVoteCloseDrafts: resolveStateUpdate(next, state.candidateVoteCloseDrafts) })),
	setAdminUsers: (next) => set((state) => ({ adminUsers: resolveStateUpdate(next, state.adminUsers) })),
	setNewUserEmail: (next) => set((state) => ({ newUserEmail: resolveStateUpdate(next, state.newUserEmail) })),
	setNewUserPassword: (next) => set((state) => ({ newUserPassword: resolveStateUpdate(next, state.newUserPassword) })),
	setStaticSources: (next) => set((state) => ({ staticSources: resolveStateUpdate(next, state.staticSources) })),
	setStaticBundles: (next) => set((state) => ({ staticBundles: resolveStateUpdate(next, state.staticBundles) })),
	setStaticBundleSchedule: (next) =>
		set((state) => ({ staticBundleSchedule: resolveStateUpdate(next, state.staticBundleSchedule) })),
	setNewStaticCategory: (next) => set((state) => ({ newStaticCategory: resolveStateUpdate(next, state.newStaticCategory) })),
	setNewStaticActiveUrl: (next) => set((state) => ({ newStaticActiveUrl: resolveStateUpdate(next, state.newStaticActiveUrl) })),
	setNewStaticFallbackUrls: (next) =>
		set((state) => ({ newStaticFallbackUrls: resolveStateUpdate(next, state.newStaticFallbackUrls) })),
	resetForSignedOut: () =>
		set(() => ({
			profiles: [],
			activeProfileId: "",
			scores: [],
			playRecords: [],
			adminCandidates: [],
			adminStats: null,
			adminUsers: [],
			staticSources: [],
			staticBundles: [],
			staticBundleSchedule: null,
			mfaStatus: null,
			passkeys: [],
			backupCodeStatus: {
				activeCount: 0,
				latestGeneratedAt: null,
			},
			myCommunityRows: [],
			communityDailyCount: 0,
		})),
}));
