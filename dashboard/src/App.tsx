import { useCallback, useEffect, useMemo, useState } from "react";
import {
	BarChart3Icon,
	DatabaseIcon,
	DownloadIcon,
	MenuIcon,
	Music2Icon,
	Settings2Icon,
	ShieldIcon,
	TagsIcon,
	UserIcon,
	UsersIcon,
} from "lucide-react";
import { useShallow } from "zustand/react/shallow";
import { Alert as UiAlert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Toaster } from "@/components/ui/sonner";
import { toast } from "sonner";
import { AuthScreen } from "@/components/auth/AuthScreen";
import { AppSidebar } from "@/components/layout/AppSidebar";
import { TabPanelContent } from "@/components/layout/TabPanelContent";
import { ScoreEditDialog } from "@/components/scores/ScoreEditDialog";
import { useAuthFlow } from "@/hooks/use-auth-flow";
import { useConfirmDialog } from "@/hooks/use-confirm-dialog";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useDashboardActions } from "@/hooks/use-dashboard-actions";
import {
	BACKEND_URL,
	CHART_TYPE_ORDER,
	COVER_BASE_URL,
	DEFAULT_SONG_FILTERS,
	SONG_FAVORITE_STORAGE_KEY,
	SONG_HIDE_DELETED_STORAGE_KEY,
	SONG_SORT_ASC_STORAGE_KEY,
	SONG_SORT_OPTION_STORAGE_KEY,
	localizedStandardContains,
	normalizeSearchText,
	parseBundleLxnsAliases,
	parseBundleSheets,
	parseBundleSongs,
	parseCatalogVersionItems,
	parseSongIdItems,
	songSnapshotMatchesSearch,
} from "@/lib/app-helpers";
import type {
	AdminCandidate,
	AdminDashboardStats,
	AdminUserRow,
	ApprovedAliasSyncRow,
	BackupCodeStatus,
	CatalogVersionItem,
	CommunityCandidate,
	MfaStatus,
	NavigationTabItem,
	MyCommunityCandidate,
	PasskeyCredential,
	PlayRecordRow,
	Profile,
	ScoreRow,
	StaticBundle,
	StaticBundleSchedule,
	StaticSource,
	SongIdItem,
	ToastSeverity,
} from "@/lib/app-types";
import { buildSongCatalogIndex, normalizeDifficulty, normalizeSheetType } from "@/lib/song-index";
import { SongDetailDialog } from "./components/songs/SongDetailDialog";
import type { Alias, Sheet as SongSheet, Song } from "./components/songs/types";
import { requestJson } from "./lib/backend-client";
import {
	clearStoredSessionArtifacts,
	persistRefreshToken,
	readStoredRefreshToken,
	toSession,
	type LoginResponse,
} from "./lib/session";
import { useDashboardStore } from "./store/dashboard-store";
import { useTranslation } from "react-i18next";

const VIEW_PROFILE_STORAGE_KEY_PREFIX = "dashboard.viewProfileId";

function getViewProfileStorageKey(userId: string) {
	return `${VIEW_PROFILE_STORAGE_KEY_PREFIX}.${userId}`;
}

function getSessionStorageItem(key: string): string | null {
	if (typeof window === "undefined") {
		return null;
	}
	try {
		return window.sessionStorage.getItem(key);
	} catch {
		return null;
	}
}

function setSessionStorageItem(key: string, value: string) {
	if (typeof window === "undefined") {
		return;
	}
	try {
		window.sessionStorage.setItem(key, value);
	} catch {
		// noop
	}
}

function removeSessionStorageItem(key: string) {
	if (typeof window === "undefined") {
		return;
	}
	try {
		window.sessionStorage.removeItem(key);
	} catch {
		// noop
	}
}

function App() {
	const { t } = useTranslation();
	const {
		session,
		setSession,
		sessionBootstrapped,
		setSessionBootstrapped,
		resetForSignedOut,
		tab,
		setTab,
		mobileDrawerOpen,
		setMobileDrawerOpen,
		mfaStatus,
		setMfaStatus,
		mfaSetup,
		setMfaSetup,
		mfaSetupCode,
		setMfaSetupCode,
		passkeys,
		setPasskeys,
		backupCodeStatus,
		setBackupCodeStatus,
		profiles,
		setProfiles,
		activeProfileId,
		setActiveProfileId,
		songKeyword,
		setSongKeyword,
		songFilterExpanded,
		setSongFilterExpanded,
		songCatalog,
		setSongCatalog,
		catalogSheets,
		setCatalogSheets,
		catalogAliases,
		setCatalogAliases,
		songIdItems,
		setSongIdItems,
		catalogVersionItems,
		setCatalogVersionItems,
		songFilters,
		setSongFilters,
		songSortOption,
		setSongSortOption,
		songSortAscending,
		setSongSortAscending,
		songFavorites,
		setSongFavorites,
		selectedSong,
		setSelectedSong,
		songSheets,
		setSongSheets,
		songAliases,
		setSongAliases,
		songDetailLoading,
		setSongDetailLoading,
		songDetailChartType,
		setSongDetailChartType,
		scores,
		setScores,
		playRecords,
		setPlayRecords,
		scoreSearchKeyword,
		setScoreSearchKeyword,
		scoreSongName,
		setScoreSongName,
		scoreType,
		setScoreType,
		scoreDifficulty,
		setScoreDifficulty,
		scoreAchievements,
		setScoreAchievements,
		dfQQ,
		setDfQQ,
		dfImportToken,
		setDfImportToken,
		lxnsAuthCode,
		setLxnsAuthCode,
		communityRows,
		setCommunityRows,
		communitySongName,
		setCommunitySongName,
		communityAliasText,
		setCommunityAliasText,
		communityDailyCount,
		setCommunityDailyCount,
		myCandidateSongName,
		setMyCommunityRows,
		setApprovedAliasRows,
		adminCandidates,
		setAdminCandidates,
		adminCandidateStatus,
		adminStats,
		setAdminStats,
		candidateVoteCloseDrafts,
		setCandidateVoteCloseDrafts,
		adminUsers,
		setAdminUsers,
		newUserEmail,
		setNewUserEmail,
		newUserPassword,
		setNewUserPassword,
		staticSources,
		setStaticSources,
		staticBundles,
		setStaticBundles,
		staticBundleSchedule,
		setStaticBundleSchedule,
		newStaticCategory,
		setNewStaticCategory,
		newStaticActiveUrl,
		setNewStaticActiveUrl,
		newStaticFallbackUrls,
		setNewStaticFallbackUrls,
	} = useDashboardStore(
		useShallow((state) => ({
			session: state.session,
			setSession: state.setSession,
			sessionBootstrapped: state.sessionBootstrapped,
			setSessionBootstrapped: state.setSessionBootstrapped,
			resetForSignedOut: state.resetForSignedOut,
			tab: state.tab,
			setTab: state.setTab,
			mobileDrawerOpen: state.mobileDrawerOpen,
			setMobileDrawerOpen: state.setMobileDrawerOpen,
			mfaStatus: state.mfaStatus,
			setMfaStatus: state.setMfaStatus,
			mfaSetup: state.mfaSetup,
			setMfaSetup: state.setMfaSetup,
			mfaSetupCode: state.mfaSetupCode,
			setMfaSetupCode: state.setMfaSetupCode,
			passkeys: state.passkeys,
			setPasskeys: state.setPasskeys,
			backupCodeStatus: state.backupCodeStatus,
			setBackupCodeStatus: state.setBackupCodeStatus,
			profiles: state.profiles,
			setProfiles: state.setProfiles,
			activeProfileId: state.activeProfileId,
			setActiveProfileId: state.setActiveProfileId,
			songKeyword: state.songKeyword,
			setSongKeyword: state.setSongKeyword,
			songFilterExpanded: state.songFilterExpanded,
			setSongFilterExpanded: state.setSongFilterExpanded,
			songCatalog: state.songCatalog,
			setSongCatalog: state.setSongCatalog,
			catalogSheets: state.catalogSheets,
			setCatalogSheets: state.setCatalogSheets,
			catalogAliases: state.catalogAliases,
			setCatalogAliases: state.setCatalogAliases,
			songIdItems: state.songIdItems,
			setSongIdItems: state.setSongIdItems,
			catalogVersionItems: state.catalogVersionItems,
			setCatalogVersionItems: state.setCatalogVersionItems,
			songFilters: state.songFilters,
			setSongFilters: state.setSongFilters,
			songSortOption: state.songSortOption,
			setSongSortOption: state.setSongSortOption,
			songSortAscending: state.songSortAscending,
			setSongSortAscending: state.setSongSortAscending,
			songFavorites: state.songFavorites,
			setSongFavorites: state.setSongFavorites,
			selectedSong: state.selectedSong,
			setSelectedSong: state.setSelectedSong,
			songSheets: state.songSheets,
			setSongSheets: state.setSongSheets,
			songAliases: state.songAliases,
			setSongAliases: state.setSongAliases,
			songDetailLoading: state.songDetailLoading,
			setSongDetailLoading: state.setSongDetailLoading,
			songDetailChartType: state.songDetailChartType,
			setSongDetailChartType: state.setSongDetailChartType,
			scores: state.scores,
			setScores: state.setScores,
			playRecords: state.playRecords,
			setPlayRecords: state.setPlayRecords,
			scoreSearchKeyword: state.scoreSearchKeyword,
			setScoreSearchKeyword: state.setScoreSearchKeyword,
			scoreSongName: state.scoreSongName,
			setScoreSongName: state.setScoreSongName,
			scoreType: state.scoreType,
			setScoreType: state.setScoreType,
			scoreDifficulty: state.scoreDifficulty,
			setScoreDifficulty: state.setScoreDifficulty,
			scoreAchievements: state.scoreAchievements,
			setScoreAchievements: state.setScoreAchievements,
			dfQQ: state.dfQQ,
			setDfQQ: state.setDfQQ,
			dfImportToken: state.dfImportToken,
			setDfImportToken: state.setDfImportToken,
			lxnsAuthCode: state.lxnsAuthCode,
			setLxnsAuthCode: state.setLxnsAuthCode,
			communityRows: state.communityRows,
			setCommunityRows: state.setCommunityRows,
			communitySongName: state.communitySongName,
			setCommunitySongName: state.setCommunitySongName,
			communityAliasText: state.communityAliasText,
			setCommunityAliasText: state.setCommunityAliasText,
			communityDailyCount: state.communityDailyCount,
			setCommunityDailyCount: state.setCommunityDailyCount,
			myCandidateSongName: state.myCandidateSongName,
			setMyCommunityRows: state.setMyCommunityRows,
			setApprovedAliasRows: state.setApprovedAliasRows,
			adminCandidates: state.adminCandidates,
			setAdminCandidates: state.setAdminCandidates,
			adminCandidateStatus: state.adminCandidateStatus,
			adminStats: state.adminStats,
			setAdminStats: state.setAdminStats,
			candidateVoteCloseDrafts: state.candidateVoteCloseDrafts,
			setCandidateVoteCloseDrafts: state.setCandidateVoteCloseDrafts,
			adminUsers: state.adminUsers,
			setAdminUsers: state.setAdminUsers,
			newUserEmail: state.newUserEmail,
			setNewUserEmail: state.setNewUserEmail,
			newUserPassword: state.newUserPassword,
			setNewUserPassword: state.setNewUserPassword,
			staticSources: state.staticSources,
			setStaticSources: state.setStaticSources,
			staticBundles: state.staticBundles,
			setStaticBundles: state.setStaticBundles,
			staticBundleSchedule: state.staticBundleSchedule,
			setStaticBundleSchedule: state.setStaticBundleSchedule,
			newStaticCategory: state.newStaticCategory,
			setNewStaticCategory: state.setNewStaticCategory,
			newStaticActiveUrl: state.newStaticActiveUrl,
			setNewStaticActiveUrl: state.setNewStaticActiveUrl,
			newStaticFallbackUrls: state.newStaticFallbackUrls,
			setNewStaticFallbackUrls: state.setNewStaticFallbackUrls,
		})),
	);

	const debouncedSongKeyword = useDebouncedValue(songKeyword, 280);
	const debouncedScoreSearchKeyword = useDebouncedValue(scoreSearchKeyword, 280);
	const debouncedScoreSongName = useDebouncedValue(scoreSongName, 280);
	const debouncedCommunitySongName = useDebouncedValue(communitySongName, 280);

	const isAdmin = session?.user.isAdmin ?? false;
	const enabledProfile = useMemo(() => profiles.find((item) => item.isActive) ?? null, [profiles]);
	const selectedProfile = useMemo(
		() => profiles.find((item) => item.id === activeProfileId) ?? null,
		[activeProfileId, profiles],
	);
	const activeProfileAvatarUrl = selectedProfile?.avatarUrl ?? enabledProfile?.avatarUrl ?? null;
	const {
		songTitleByIdentifier,
		songCatalogIndexByIdentifier,
		songCatalogByIdentifier,
		songSnapshots,
		songSnapshotByIdentifier,
		allCategories,
	} = useMemo(
		() =>
			buildSongCatalogIndex({
				songs: songCatalog,
				sheets: catalogSheets,
				aliases: catalogAliases,
				songIdItems,
			}),
		[catalogAliases, catalogSheets, songCatalog, songIdItems],
	);

	const resolveSongTitle = useCallback(
		(songIdentifier: string) => songTitleByIdentifier.get(songIdentifier) ?? t("app:unknownSong"),
		[songTitleByIdentifier, t],
	);

	const buildCoverUrl = useCallback((imageName?: string | null) => {
		const clean = imageName?.trim();
		if (!clean) return null;
		return `${COVER_BASE_URL}/${encodeURIComponent(clean)}`;
	}, []);

	const resolveSongCoverUrl = useCallback(
		(songIdentifier: string) => {
			const song = songCatalogByIdentifier.get(songIdentifier);
			return buildCoverUrl(song?.imageName);
		},
		[buildCoverUrl, songCatalogByIdentifier],
	);

	const formatChartType = useCallback((value?: string | null) => {
		const normalized = normalizeSheetType(value);
		if (normalized === "std") return "STD";
		if (normalized === "dx") return "DX";
		if (normalized === "utage") return "UTAGE";
		return normalized ? normalized.toUpperCase() : "-";
	}, []);

	const formatDifficulty = useCallback((value?: string | null) => {
		const normalized = normalizeDifficulty(value);
		if (normalized === "remaster") return "Re:MASTER";
		return normalized ? normalized.toUpperCase() : "-";
	}, []);

	const formatDateToYmd = useCallback((value?: string | null) => {
		if (!value) return null;
		const trimmed = value.trim();
		const direct = /^(\d{4}-\d{2}-\d{2})/u.exec(trimmed);
		if (direct?.[1]) {
			return direct[1];
		}
		const parsed = new Date(trimmed);
		if (Number.isNaN(parsed.getTime())) {
			return trimmed;
		}
		return parsed.toISOString().slice(0, 10);
	}, []);

	const catalogVersionSequence = useMemo(() => catalogVersionItems.map((item) => item.version), [catalogVersionItems]);

	const versionSortOrder = useCallback(
		(version: string) => {
			const exactIndex = catalogVersionSequence.findIndex((item) => item === version);
			if (exactIndex >= 0) {
				return exactIndex;
			}

			const matches = catalogVersionSequence
				.map((item, index) => ({ item, index }))
				.filter(({ item }) => version.includes(item) || item.includes(version));
			if (matches.length === 0) {
				return 999;
			}
			return matches.sort((a, b) => b.item.length - a.item.length)[0]!.index;
		},
		[catalogVersionSequence],
	);

	const formatVersionDisplay = useCallback(
		(version?: string | null) => {
			if (!version) {
				return "-";
			}
			const exact = catalogVersionItems.find((item) => item.version === version);
			if (exact) {
				return exact.abbr;
			}

			const matches = catalogVersionItems.filter((item) => version.includes(item.version) || item.version.includes(version));
			if (matches.length === 0) {
				return version;
			}
			const best = matches.sort((a, b) => b.version.length - a.version.length)[0];
			return best?.abbr ?? version;
		},
		[catalogVersionItems],
	);

	const songDetailChartTypes = useMemo(() => {
		const types = Array.from(new Set(songSheets.map((sheet) => normalizeSheetType(sheet.chartType)).filter(Boolean)));
		return types.sort((a, b) => (CHART_TYPE_ORDER[a] ?? 99) - (CHART_TYPE_ORDER[b] ?? 99) || a.localeCompare(b));
	}, [songSheets]);

	const selectedSongRegionSummary = useMemo(() => {
		if (songSheets.length === 0) {
			return { jp: false, intl: false, cn: false };
		}
		return {
			jp: songSheets.some((sheet) => sheet.regionJp),
			intl: songSheets.some((sheet) => sheet.regionIntl),
			cn: songSheets.some((sheet) => sheet.regionCn),
		};
	}, [songSheets]);

	const allVersions = useMemo(
		() =>
			Array.from(new Set(songCatalog.map((song) => song.version).filter((item): item is string => Boolean(item)))).sort(
				(a, b) => {
					const orderDiff = versionSortOrder(a) - versionSortOrder(b);
					if (orderDiff !== 0) return orderDiff;
					return a.localeCompare(b);
				},
			),
		[songCatalog, versionSortOrder],
	);

	const resolveSongByName = useCallback(
		(songName: string) => {
			const query = songName.trim();
			if (!query) {
				return null;
			}

			const normalizedQuery = normalizeSearchText(query);
			const exactMatches = songSnapshots.filter((snapshot) => {
				if (normalizeSearchText(snapshot.song.title) === normalizedQuery) {
					return true;
				}
				if (normalizeSearchText(snapshot.song.songIdentifier) === normalizedQuery) {
					return true;
				}
				return snapshot.songIds.some((songId) => String(songId) === query);
			});
			if (exactMatches.length === 1) {
				return exactMatches[0]?.song ?? null;
			}

			const matched = songSnapshots.filter((snapshot) => songSnapshotMatchesSearch(snapshot, query));
			if (matched.length === 1) {
				return matched[0]?.song ?? null;
			}

			return null;
		},
		[songSnapshots],
	);

	const filteredScores = useMemo(() => {
		const searchText = debouncedScoreSearchKeyword.trim();
		if (!searchText) {
			return scores;
		}

		return scores.filter((row) => {
			const songIdentifier = row.sheet?.songIdentifier;
			if (!songIdentifier) {
				return false;
			}

			const snapshot = songSnapshotByIdentifier.get(songIdentifier);
			if (snapshot) {
				return songSnapshotMatchesSearch(snapshot, searchText);
			}

			const fallbackTitle = row.sheet?.song?.title ?? resolveSongTitle(songIdentifier);
			return localizedStandardContains(fallbackTitle, searchText) || localizedStandardContains(songIdentifier, searchText);
		});
	}, [debouncedScoreSearchKeyword, resolveSongTitle, scores, songSnapshotByIdentifier]);

	const filteredPlayRecords = useMemo(() => {
		const searchText = debouncedScoreSearchKeyword.trim();
		if (!searchText) {
			return playRecords;
		}

		return playRecords.filter((row) => {
			const songIdentifier = row.sheet?.songIdentifier;
			if (!songIdentifier) {
				return false;
			}

			const snapshot = songSnapshotByIdentifier.get(songIdentifier);
			if (snapshot) {
				return songSnapshotMatchesSearch(snapshot, searchText);
			}

			const fallbackTitle = row.sheet?.song?.title ?? resolveSongTitle(songIdentifier);
			return localizedStandardContains(fallbackTitle, searchText) || localizedStandardContains(songIdentifier, searchText);
		});
	}, [debouncedScoreSearchKeyword, playRecords, resolveSongTitle, songSnapshotByIdentifier]);

	const filteredSongs = useMemo(() => {
		const searchText = debouncedSongKeyword;
		const hasSearch = searchText.length > 0;
		const hasCategories = songFilters.selectedCategories.size > 0;
		const hasVersions = songFilters.selectedVersions.size > 0;
		const hasTypes = songFilters.selectedTypes.size > 0;
		const hasDifficulties = songFilters.selectedDifficulties.size > 0;

		const filteredSnapshots = songSnapshots.filter((snapshot) => {
			const { song, sheets } = snapshot;

			if (hasSearch && !songSnapshotMatchesSearch(snapshot, searchText)) {
				return false;
			}

			if (songFilters.showFavoritesOnly && !songFavorites.has(song.songIdentifier)) {
				return false;
			}

			if (hasCategories && !songFilters.selectedCategories.has(song.category ?? "")) {
				return false;
			}

			if (hasVersions) {
				const version = song.version ?? "";
				if (!version || !songFilters.selectedVersions.has(version)) {
					return false;
				}
			}

			if (hasTypes || hasDifficulties || songFilters.hideDeletedSongs) {
				let hasMatchingType = !hasTypes;
				let hasMatchingDifficulty = !hasDifficulties;
				let isPlayable = !songFilters.hideDeletedSongs;

				for (const sheet of sheets) {
					if (hasTypes && songFilters.selectedTypes.has(sheet.type)) {
						hasMatchingType = true;
					}

					if (hasDifficulties && songFilters.selectedDifficulties.has(sheet.difficulty)) {
						const level = sheet.internalLevelValue ?? sheet.levelValue ?? 0;
						if (level >= songFilters.minLevel && level <= songFilters.maxLevel) {
							hasMatchingDifficulty = true;
						}
					}

					if (songFilters.hideDeletedSongs && (sheet.regionJp || sheet.regionIntl || sheet.regionCn)) {
						isPlayable = true;
					}
				}

				if (!hasMatchingType || !hasMatchingDifficulty || !isPlayable) {
					return false;
				}
			}

			return true;
		});

		const sortedSnapshots = [...filteredSnapshots].sort((left, right) => {
			if (songSortOption === "versionDate") {
				const versionOrderLeft = versionSortOrder(left.song.version ?? "");
				const versionOrderRight = versionSortOrder(right.song.version ?? "");
				if (versionOrderLeft !== versionOrderRight) {
					return songSortAscending ? versionOrderLeft - versionOrderRight : versionOrderRight - versionOrderLeft;
				}

				const releaseDateLeft = left.song.releaseDate ?? "0000-00-00";
				const releaseDateRight = right.song.releaseDate ?? "0000-00-00";
				if (releaseDateLeft !== releaseDateRight) {
					return songSortAscending
						? releaseDateLeft.localeCompare(releaseDateRight)
						: releaseDateRight.localeCompare(releaseDateLeft);
				}
				const fallbackLeft = left.song.sortOrder ?? songCatalogIndexByIdentifier.get(left.song.songIdentifier) ?? 999999;
				const fallbackRight = right.song.sortOrder ?? songCatalogIndexByIdentifier.get(right.song.songIdentifier) ?? 999999;
				return fallbackLeft - fallbackRight;
			}

			if (songSortOption === "difficulty") {
				if (left.maxDifficulty !== right.maxDifficulty) {
					return songSortAscending ? left.maxDifficulty - right.maxDifficulty : right.maxDifficulty - left.maxDifficulty;
				}
				const fallbackLeft = left.song.sortOrder ?? songCatalogIndexByIdentifier.get(left.song.songIdentifier) ?? 999999;
				const fallbackRight = right.song.sortOrder ?? songCatalogIndexByIdentifier.get(right.song.songIdentifier) ?? 999999;
				return fallbackLeft - fallbackRight;
			}

			const sortOrderLeft = left.song.sortOrder ?? songCatalogIndexByIdentifier.get(left.song.songIdentifier) ?? 999999;
			const sortOrderRight = right.song.sortOrder ?? songCatalogIndexByIdentifier.get(right.song.songIdentifier) ?? 999999;
			return songSortAscending ? sortOrderLeft - sortOrderRight : sortOrderRight - sortOrderLeft;
		});

		return sortedSnapshots.map((snapshot) => snapshot.song);
	}, [
		debouncedSongKeyword,
		songCatalogIndexByIdentifier,
		songFavorites,
		songFilters,
		songSnapshots,
		songSortAscending,
		songSortOption,
		versionSortOrder,
	]);

	const scoreSongSuggestions = useMemo(() => {
		const searchText = debouncedScoreSongName.trim();
		if (!searchText) {
			return [];
		}

		return songSnapshots
			.filter((snapshot) => songSnapshotMatchesSearch(snapshot, searchText))
			.slice(0, 8)
			.map((snapshot) => ({
				songIdentifier: snapshot.song.songIdentifier,
				title: snapshot.song.title,
				artist: snapshot.song.artist,
			}));
	}, [debouncedScoreSongName, songSnapshots]);

	const communitySongSuggestions = useMemo(() => {
		const searchText = debouncedCommunitySongName.trim();
		if (!searchText) {
			return [];
		}

		return songSnapshots
			.filter((snapshot) => songSnapshotMatchesSearch(snapshot, searchText))
			.slice(0, 8)
			.map((snapshot) => ({
				songIdentifier: snapshot.song.songIdentifier,
				title: snapshot.song.title,
				artist: snapshot.song.artist,
			}));
	}, [debouncedCommunitySongName, songSnapshots]);

	const showToast = useCallback((message: string, severity: ToastSeverity = "info") => {
		if (severity === "success") {
			toast.success(message);
			return;
		}
		if (severity === "warning") {
			toast.warning(message);
			return;
		}
		if (severity === "error") {
			toast.error(message);
			return;
		}
		toast.info(message);
	}, []);
	const { confirm, confirmDialogNode } = useConfirmDialog();
	const [isMobileViewport, setIsMobileViewport] = useState(false);
	const toasterPosition = isMobileViewport ? "bottom-center" : "top-right";

	useEffect(() => {
		if (typeof window === "undefined") {
			return;
		}
		const mediaQuery = window.matchMedia("(max-width: 767px)");
		const syncViewport = () => {
			setIsMobileViewport(mediaQuery.matches);
		};
		syncViewport();
		mediaQuery.addEventListener("change", syncViewport);
		return () => {
			mediaQuery.removeEventListener("change", syncViewport);
		};
	}, []);

	const clearSession = useCallback(() => {
		setSession(null);
		clearStoredSessionArtifacts();
		resetForSignedOut();
	}, [resetForSignedOut, setSession]);

	const request = useCallback(
		async <T,>(
			path: string,
			options?: {
				method?: "GET" | "POST" | "PATCH" | "DELETE";
				body?: unknown;
				auth?: boolean;
				retry?: boolean;
				accessToken?: string;
			},
		): Promise<T> => requestJson<T>(BACKEND_URL, path, session, setSession, clearSession, options),
		[clearSession, session, setSession],
	);

	useEffect(() => {
		if (sessionBootstrapped) {
			return;
		}

		const storedRefreshToken = readStoredRefreshToken();
		if (!storedRefreshToken) {
			setSessionBootstrapped(true);
			return;
		}

		let cancelled = false;
		void (async () => {
			try {
				const payload = await requestJson<LoginResponse>(
					BACKEND_URL,
					"v1/auth/refresh",
					null,
					() => {
						// no-op in bootstrap path
					},
					() => {
						// no-op in bootstrap path
					},
					{
						method: "POST",
						auth: false,
						retry: false,
						body: { refreshToken: storedRefreshToken },
					},
				);
				if (cancelled) {
					return;
				}
				setSession(toSession(payload));
			} catch {
				if (cancelled) {
					return;
				}
				clearStoredSessionArtifacts();
				setSession(null);
			} finally {
				if (!cancelled) {
					setSessionBootstrapped(true);
				}
			}
		})();

		return () => {
			cancelled = true;
		};
	}, [sessionBootstrapped, setSession, setSessionBootstrapped]);

	const loadProfiles = useCallback(async () => {
		if (!session) return;
		const payload = await request<{ profiles: Profile[] }>("v1/profiles");
		setProfiles(payload.profiles);
		const storageKey = getViewProfileStorageKey(session.user.id);
		const storedProfileId = getSessionStorageItem(storageKey)?.trim() ?? "";
		setActiveProfileId((previous) => {
			const previousProfileId = previous.trim();
			if (previousProfileId && payload.profiles.some((item) => item.id === previousProfileId)) {
				return previousProfileId;
			}
			if (storedProfileId && payload.profiles.some((item) => item.id === storedProfileId)) {
				return storedProfileId;
			}
			const active = payload.profiles.find((item) => item.isActive) ?? payload.profiles[0] ?? null;
			return active?.id ?? "";
		});
	}, [request, session, setActiveProfileId, setProfiles]);

	const loadSongCatalog = useCallback(async () => {
		try {
			const [songsPayload, sheetsPayload, aliasesPayload, versionsPayload, songIdItemsPayload] = await Promise.all([
				request<{ songs: Song[] }>("v1/catalog/songs", { auth: false }),
				request<{ sheets: SongSheet[] }>("v1/catalog/sheets", { auth: false }),
				request<{ aliases: Alias[] }>("v1/catalog/aliases", { auth: false }),
				request<{ versions: CatalogVersionItem[] }>("v1/catalog/versions", { auth: false }),
				request<{ items: SongIdItem[] }>("v1/static/songid-items", { auth: false }),
			]);

			const nextSongs = songsPayload.songs;
			const nextSheets = sheetsPayload.sheets.map((sheet) => ({
				...sheet,
				id: String(sheet.id),
			}));
			if (nextSongs.length > 0 && nextSheets.length > 0) {
				const nextAliases = aliasesPayload.aliases.map((alias) => ({
					id: String(alias.id),
					songIdentifier: alias.songIdentifier,
					aliasText: alias.aliasText,
					source: alias.source,
				}));
				const nextSongIdItems = parseSongIdItems(songIdItemsPayload.items);
				const nextVersionItems = parseCatalogVersionItems(versionsPayload.versions);

				setSongCatalog(nextSongs);
				setCatalogSheets(nextSheets);
				setCatalogAliases(nextAliases);
				setSongIdItems(nextSongIdItems);
				setCatalogVersionItems(nextVersionItems);
				return;
			}
		} catch (error) {
			console.warn("[catalog] catalog API load failed, falling back to static bundle payload.", error);
		}

		const bundlePayload = await request<{ payload?: { resources?: Record<string, unknown> } }>("v1/static/bundle/latest", {
			auth: false,
		});
		const resources = bundlePayload.payload?.resources;
		const dataJsonResource = resources?.data_json;
		const songidResource = resources?.songid_json;
		const lxnsAliasesResource = resources?.lxns_aliases;

		const nextSongs = parseBundleSongs(dataJsonResource);
		const nextSheets = parseBundleSheets(dataJsonResource);
		if (nextSongs.length === 0 || nextSheets.length === 0) {
			throw new Error(t("app:backendErrDefault"));
		}

		const nextSongIdItems = parseSongIdItems(songidResource);
		const nextAliases = parseBundleLxnsAliases(lxnsAliasesResource, {
			songs: nextSongs,
			songIdItems: nextSongIdItems,
		});

		setSongCatalog(nextSongs);
		setCatalogSheets(nextSheets);
		setCatalogAliases(nextAliases);
		setSongIdItems(nextSongIdItems);
		setCatalogVersionItems(parseCatalogVersionItems(dataJsonResource));
	}, [request, setCatalogAliases, setCatalogSheets, setCatalogVersionItems, setSongCatalog, setSongIdItems, t]);

	const loadSongDetail = useCallback(
		async (songIdentifier: string) => {
			setSongDetailLoading(true);
			try {
				const nextSheets = catalogSheets.filter((sheet) => sheet.songIdentifier === songIdentifier);
				const nextAliases = catalogAliases.filter((alias) => alias.songIdentifier === songIdentifier);
				setSongSheets(nextSheets);
				setSongAliases(nextAliases);

				const types = Array.from(new Set(nextSheets.map((sheet) => normalizeSheetType(sheet.chartType)).filter(Boolean))).sort(
					(a, b) => (CHART_TYPE_ORDER[a] ?? 99) - (CHART_TYPE_ORDER[b] ?? 99) || a.localeCompare(b),
				);
				if (types.includes("dx")) {
					setSongDetailChartType("dx");
				} else if (types.includes("std")) {
					setSongDetailChartType("std");
				} else {
					setSongDetailChartType(types[0] ?? "");
				}
			} finally {
				setSongDetailLoading(false);
			}
		},
		[catalogAliases, catalogSheets, setSongAliases, setSongDetailChartType, setSongDetailLoading, setSongSheets],
	);

	const closeSongDetail = useCallback(() => {
		setSelectedSong(null);
		setSongSheets([]);
		setSongAliases([]);
		setSongDetailChartType("");
		setSongDetailLoading(false);
	}, [setSelectedSong, setSongAliases, setSongDetailChartType, setSongDetailLoading, setSongSheets]);

	const handleOpenSongDetail = useCallback(
		async (song: Song) => {
			setSelectedSong(song);
			try {
				await loadSongDetail(song.songIdentifier);
			} catch (error) {
				showToast((error as Error).message, "error");
			}
		},
		[loadSongDetail, setSelectedSong, showToast],
	);

	const toggleSongFilterSet = useCallback(
		(key: "selectedCategories" | "selectedVersions" | "selectedDifficulties" | "selectedTypes", value: string) => {
			setSongFilters((previous) => {
				const nextSet = new Set(previous[key]);
				if (nextSet.has(value)) {
					nextSet.delete(value);
				} else {
					nextSet.add(value);
				}
				return { ...previous, [key]: nextSet };
			});
		},
		[setSongFilters],
	);

	const toggleSongFavorite = useCallback(
		(songIdentifier: string) => {
			setSongFavorites((previous) => {
				const next = new Set(previous);
				if (next.has(songIdentifier)) {
					next.delete(songIdentifier);
				} else {
					next.add(songIdentifier);
				}
				return next;
			});
		},
		[setSongFavorites],
	);

	const resetSongFilters = useCallback(() => {
		setSongFilters((previous) => ({
			...DEFAULT_SONG_FILTERS,
			hideDeletedSongs: previous.hideDeletedSongs,
		}));
	}, [setSongFilters]);

	const loadScores = useCallback(async () => {
		if (!activeProfileId || !session) {
			setScores([]);
			setPlayRecords([]);
			return;
		}
		const [scorePayload, recordPayload] = await Promise.all([
			request<{ scores: ScoreRow[] }>(`v1/scores?profileId=${encodeURIComponent(activeProfileId)}`),
			request<{ records: PlayRecordRow[] }>(`v1/play-records?profileId=${encodeURIComponent(activeProfileId)}&limit=300`),
		]);
		setScores(scorePayload.scores);
		setPlayRecords(recordPayload.records);
	}, [activeProfileId, request, session, setPlayRecords, setScores]);

	const loadCommunity = useCallback(async () => {
		const boardPromise = request<{ rows: CommunityCandidate[] }>("v1/community/candidates:votingBoard?limit=120&offset=0", {
			auth: Boolean(session),
		});
		const approvedPromise = request<{ rows: ApprovedAliasSyncRow[] }>("v1/community/aliases:sync?limit=80", {
			auth: false,
		});
		const dailyPromise = session
			? request<{ count: number }>("v1/community/candidates:dailyCount")
			: Promise.resolve<{ count: number }>({ count: 0 });
		const [boardPayload, approvedPayload, dailyPayload] = await Promise.all([boardPromise, approvedPromise, dailyPromise]);
		setCommunityRows(boardPayload.rows);
		setApprovedAliasRows(approvedPayload.rows);
		setCommunityDailyCount(dailyPayload.count);
	}, [request, session, setApprovedAliasRows, setCommunityDailyCount, setCommunityRows]);

	const loadMyCommunity = useCallback(async () => {
		if (!session) {
			setMyCommunityRows([]);
			return;
		}
		const songName = myCandidateSongName.trim();
		const matchedSong = songName ? resolveSongByName(songName) : null;
		if (songName && !matchedSong) {
			showToast(t("app:errNoMatches"), "warning");
			return;
		}
		const suffix = matchedSong ? `?songIdentifier=${encodeURIComponent(matchedSong.songIdentifier)}&limit=30` : "?limit=30";
		const payload = await request<{ rows: MyCommunityCandidate[] }>(`v1/community/candidates:my${suffix}`);
		setMyCommunityRows(payload.rows);
	}, [myCandidateSongName, request, resolveSongByName, session, setMyCommunityRows, showToast, t]);

	const loadAdminCandidates = useCallback(async () => {
		if (!isAdmin) return;
		const payload = await request<{ rows: AdminCandidate[] }>(
			`v1/admin/candidates?status=${encodeURIComponent(adminCandidateStatus)}&limit=80&offset=0`,
		);
		setAdminCandidates(payload.rows);
	}, [adminCandidateStatus, isAdmin, request, setAdminCandidates]);

	const loadAdminStats = useCallback(async () => {
		if (!isAdmin) return;
		const payload = await request<AdminDashboardStats>("v1/admin/dashboard");
		setAdminStats(payload);
	}, [isAdmin, request, setAdminStats]);

	const loadAdminUsers = useCallback(async () => {
		if (!isAdmin) return;
		const payload = await request<{ rows: AdminUserRow[] }>("v1/admin/users?limit=120&offset=0");
		setAdminUsers(payload.rows);
	}, [isAdmin, request, setAdminUsers]);

	const loadStaticAdmin = useCallback(async () => {
		if (!isAdmin) return;
		const [sourcePayload, bundlePayload, schedulePayload] = await Promise.all([
			request<{ sources: StaticSource[] }>("v1/admin/static-sources"),
			request<{ bundles: StaticBundle[] }>("v1/admin/static-bundles"),
			request<{ schedule: StaticBundleSchedule }>("v1/admin/static-bundle-schedule"),
		]);
		setStaticSources(sourcePayload.sources);
		setStaticBundles(bundlePayload.bundles);
		setStaticBundleSchedule(schedulePayload.schedule);
	}, [isAdmin, request, setStaticBundleSchedule, setStaticBundles, setStaticSources]);

	const loadMfaStatus = useCallback(async () => {
		if (!session) {
			setMfaStatus(null);
			return;
		}
		const payload = await request<MfaStatus>("v1/auth/mfa/status");
		setMfaStatus(payload);
	}, [request, session, setMfaStatus]);

	const loadPasskeys = useCallback(async () => {
		if (!session) {
			setPasskeys([]);
			return;
		}
		const payload = await request<{ passkeys: PasskeyCredential[] }>("v1/auth/mfa/passkeys");
		setPasskeys(payload.passkeys);
	}, [request, session, setPasskeys]);

	const loadBackupCodeStatus = useCallback(async () => {
		if (!session) {
			setBackupCodeStatus({
				activeCount: 0,
				latestGeneratedAt: null,
			});
			return;
		}
		const payload = await request<BackupCodeStatus>("v1/auth/mfa/backup-codes");
		setBackupCodeStatus(payload);
	}, [request, session, setBackupCodeStatus]);

	useEffect(() => {
		window.localStorage.setItem(SONG_FAVORITE_STORAGE_KEY, JSON.stringify(Array.from(songFavorites)));
	}, [songFavorites]);

	useEffect(() => {
		window.localStorage.setItem(SONG_HIDE_DELETED_STORAGE_KEY, songFilters.hideDeletedSongs ? "true" : "false");
	}, [songFilters.hideDeletedSongs]);

	useEffect(() => {
		window.localStorage.setItem(SONG_SORT_OPTION_STORAGE_KEY, songSortOption);
	}, [songSortOption]);

	useEffect(() => {
		window.localStorage.setItem(SONG_SORT_ASC_STORAGE_KEY, songSortAscending ? "true" : "false");
	}, [songSortAscending]);

	useEffect(() => {
		if (!session?.refreshToken) {
			clearStoredSessionArtifacts();
			return;
		}
		persistRefreshToken(session.refreshToken);
	}, [session]);

	useEffect(() => {
		if (!session) return;
		const storageKey = getViewProfileStorageKey(session.user.id);
		if (!activeProfileId) {
			removeSessionStorageItem(storageKey);
			return;
		}
		setSessionStorageItem(storageKey, activeProfileId);
	}, [activeProfileId, session]);

	useEffect(() => {
		if (!sessionBootstrapped) {
			return;
		}

		if (!session) {
			resetForSignedOut();
			return;
		}

		void (async () => {
			try {
				await Promise.all([
					loadProfiles(),
					loadCommunity(),
					loadSongCatalog(),
					loadMfaStatus(),
					loadPasskeys(),
					loadBackupCodeStatus(),
				]);
				if (isAdmin) {
					await Promise.all([loadAdminCandidates(), loadAdminStats(), loadAdminUsers(), loadStaticAdmin()]);
				}
			} catch (error) {
				showToast((error as Error).message, "error");
			}
		})();
	}, [
		isAdmin,
		loadAdminCandidates,
		loadAdminStats,
		loadAdminUsers,
		loadCommunity,
		loadMfaStatus,
		loadBackupCodeStatus,
		loadPasskeys,
		loadProfiles,
		loadSongCatalog,
		loadStaticAdmin,
		resetForSignedOut,
		session,
		sessionBootstrapped,
		showToast,
	]);

	useEffect(() => {
		if (session) {
			void loadScores();
		}
	}, [loadScores, session]);

	const authFlow = useAuthFlow({
		session,
		request,
		setSession,
		showToast,
	});
	const {
		authMode,
		setAuthMode,
		loginStep,
		loginEmail,
		setLoginEmail,
		loginPassword,
		setLoginPassword,
		registerEmail,
		setRegisterEmail,
		registerUsername,
		setRegisterUsername,
		registerPassword,
		setRegisterPassword,
		registerConfirmPassword,
		setRegisterConfirmPassword,
		forgotEmail,
		setForgotEmail,
		forgotResultMessage,
		setForgotResultMessage,
		verificationEmail,
		verificationEmailSent,
		verificationResult,
		setVerificationResult,
		resetEmail,
		setResetEmail,
		resetPassword,
		setResetPassword,
		resetConfirmPassword,
		setResetConfirmPassword,
		resetResultMessage,
		setResetResultMessage,
		setResetToken,
		loading,
		mfaMethods,
		mfaTotpCode,
		setMfaTotpCode,
		mfaBackupCode,
		setMfaBackupCode,
		isAppAuthFlow,
		appAuthRequestedMode,
		completeAppLoginWithExistingSession,
		resetLoginFlow,
		handleLoginContinue,
		handleLoginWithPassword,
		handleTotpChallenge,
		handlePasskeyChallenge,
		handleBackupCodeChallenge,
		handleDirectPasskeyLogin,
		handleRegister,
		handleResendVerification,
		handleForgotPassword,
		handleResetPassword,
	} = authFlow;

	const handleLogout = async () => {
		try {
			if (session?.refreshToken) {
				await request<{ success: boolean }>("v1/auth/logout", {
					method: "POST",
					body: { refreshToken: session.refreshToken },
				});
			}
		} catch {
			// noop
		}
		clearSession();
	};

	const dashboardActions = useDashboardActions({
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
		confirmAction: confirm,
	});
	const {
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
	} = dashboardActions;

	const tabs = useMemo<NavigationTabItem[]>(() => {
		const baseTabs = [
			{ value: "songs", label: t("app:tabSongs"), icon: Music2Icon },
			{ value: "scores", label: t("app:tabScores"), icon: BarChart3Icon },
			{ value: "import", label: t("app:tabImport"), icon: DownloadIcon },
			{ value: "aliases", label: t("app:tabAliases"), icon: TagsIcon },
			{ value: "settings", label: t("app:tabSettings"), icon: Settings2Icon },
		];
		if (!isAdmin) {
			return baseTabs;
		}
		return [
			...baseTabs,
			{ value: "admin-users", label: t("app:tabAdminUsers"), icon: UsersIcon },
			{ value: "admin-static", label: t("app:tabAdminStatic"), icon: DatabaseIcon },
		];
	}, [isAdmin, t]);

	if (!BACKEND_URL) {
		return (
			<div className="min-h-screen bg-background text-foreground">
				<main className="mx-auto flex min-h-screen max-w-2xl items-center px-4 py-10">
					<UiAlert>
						<AlertTitle>{t("app:missingEnvTitle")}</AlertTitle>
						<AlertDescription>{t("app:missingEnvDesc")}</AlertDescription>
					</UiAlert>
					<Toaster position={toasterPosition} richColors />
				</main>
			</div>
		);
	}

	if (!sessionBootstrapped) {
		return (
			<div className="min-h-screen bg-background text-foreground">
				<main className="mx-auto flex min-h-screen max-w-2xl items-center px-4 py-10">
					<UiAlert>
						<AlertTitle>{t("app:restoringTitle")}</AlertTitle>
						<AlertDescription>{t("app:restoringDesc")}</AlertDescription>
					</UiAlert>
					<Toaster position={toasterPosition} richColors />
				</main>
			</div>
		);
	}

	if (!session || (isAppAuthFlow && appAuthRequestedMode !== "login")) {
		return (
			<AuthScreen
				authMode={authMode}
				setAuthMode={setAuthMode}
				loginStep={loginStep}
				loginEmail={loginEmail}
				setLoginEmail={setLoginEmail}
				loginPassword={loginPassword}
				setLoginPassword={setLoginPassword}
				registerEmail={registerEmail}
				setRegisterEmail={setRegisterEmail}
				registerUsername={registerUsername}
				setRegisterUsername={setRegisterUsername}
				registerPassword={registerPassword}
				setRegisterPassword={setRegisterPassword}
				registerConfirmPassword={registerConfirmPassword}
				setRegisterConfirmPassword={setRegisterConfirmPassword}
				forgotEmail={forgotEmail}
				setForgotEmail={setForgotEmail}
				forgotResultMessage={forgotResultMessage}
				setForgotResultMessage={setForgotResultMessage}
				verificationEmail={verificationEmail}
				verificationEmailSent={verificationEmailSent}
				verificationResult={verificationResult}
				setVerificationResult={setVerificationResult}
				resetEmail={resetEmail}
				setResetEmail={setResetEmail}
				resetPassword={resetPassword}
				setResetPassword={setResetPassword}
				resetConfirmPassword={resetConfirmPassword}
				setResetConfirmPassword={setResetConfirmPassword}
				resetResultMessage={resetResultMessage}
				setResetResultMessage={setResetResultMessage}
				setResetToken={setResetToken}
				loading={loading}
				mfaMethods={mfaMethods}
				mfaTotpCode={mfaTotpCode}
				setMfaTotpCode={setMfaTotpCode}
				mfaBackupCode={mfaBackupCode}
				setMfaBackupCode={setMfaBackupCode}
				resetLoginFlow={resetLoginFlow}
				handleLoginContinue={handleLoginContinue}
				handleLoginWithPassword={handleLoginWithPassword}
				handleTotpChallenge={handleTotpChallenge}
				handlePasskeyChallenge={handlePasskeyChallenge}
				handleBackupCodeChallenge={handleBackupCodeChallenge}
				handleDirectPasskeyLogin={handleDirectPasskeyLogin}
				handleRegister={handleRegister}
				handleResendVerification={handleResendVerification}
				handleForgotPassword={handleForgotPassword}
				handleResetPassword={handleResetPassword}
			/>
		);
	}

	if (isAppAuthFlow && appAuthRequestedMode === "login") {
		return (
			<div className="min-h-screen bg-background text-foreground">
				<main className="mx-auto flex min-h-screen w-full max-w-md flex-col items-center justify-center gap-4 px-4 py-10">
					<UiAlert>
						<AlertTitle>{t("app:continueAppTitle")}</AlertTitle>
						<AlertDescription className="space-y-1">
							<p>{t("app:continueAppSignedInAs", { handle: session.user.handle })}</p>
							<p className="text-muted-foreground">{session.user.email}</p>
							<p>{t("app:continueAppConfirmHint")}</p>
						</AlertDescription>
					</UiAlert>

					<Button
						className="w-full"
						onClick={() => {
							void completeAppLoginWithExistingSession();
						}}
						disabled={loading}
					>
						{loading ? t("app:btnProcessing") : t("app:btnConfirmReturn")}
					</Button>

					<Button
						className="w-full"
						variant="outline"
						onClick={() => {
							clearSession();
							setAuthMode("login");
							resetLoginFlow();
						}}
						disabled={loading}
					>
						{t("app:btnUseOther")}
					</Button>
				</main>

				<Toaster position={toasterPosition} richColors />
			</div>
		);
	}

	const currentTabLabel = tabs.find((item) => item.value === tab)?.label ?? t("app:fallbackTab");
	const RoleIcon = isAdmin ? ShieldIcon : UserIcon;
	const roleLabel = isAdmin ? t("app:roleAdmin") : t("app:roleUser");
	const workspaceTabs = tabs.filter((item) => item.value !== "settings" && !item.value.startsWith("admin-"));
	const managementTabs = tabs.filter((item) => item.value === "settings" || item.value.startsWith("admin-"));
	const selectedProfileName = selectedProfile?.name?.trim() || enabledProfile?.name?.trim() || t("app:unnamedProfile");

	const sidebarContent = (
		<AppSidebar
			workspaceTabs={workspaceTabs}
			managementTabs={managementTabs}
			tab={tab}
			setTab={setTab}
			setMobileDrawerOpen={setMobileDrawerOpen}
			enabledProfileName={selectedProfileName}
			sessionHandle={session.user.handle}
			sessionEmail={session.user.email}
			roleLabel={roleLabel}
			RoleIcon={RoleIcon}
			activeProfileAvatarUrl={activeProfileAvatarUrl}
			onLogout={() => {
				void handleLogout();
			}}
		/>
	);

	const tabPanelContent = (
		<TabPanelContent
			tab={tab}
			isAdmin={isAdmin}
			sessionUser={session.user}
			enabledProfile={enabledProfile}
			selectedProfile={selectedProfile}
			activeProfileAvatarUrl={activeProfileAvatarUrl}
			mfaStatus={mfaStatus}
			mfaSetup={mfaSetup}
			mfaSetupCode={mfaSetupCode}
			passkeys={passkeys}
			backupCodeStatus={backupCodeStatus}
			setMfaSetupCode={setMfaSetupCode}
			songKeyword={songKeyword}
			filteredSongs={filteredSongs}
			songFilterExpanded={songFilterExpanded}
			songFilters={songFilters}
			allCategories={allCategories}
			allVersions={allVersions}
			songSortOption={songSortOption}
			songSortAscending={songSortAscending}
			setSongFilterExpanded={setSongFilterExpanded}
			setSongKeyword={setSongKeyword}
			setSongFilters={setSongFilters}
			toggleSongFilterSet={toggleSongFilterSet}
			resetSongFilters={resetSongFilters}
			setSongSortOption={setSongSortOption}
			setSongSortAscending={setSongSortAscending}
			formatVersionDisplay={formatVersionDisplay}
			songFavorites={songFavorites}
			buildCoverUrl={buildCoverUrl}
			toggleSongFavorite={toggleSongFavorite}
			handleOpenSongDetail={handleOpenSongDetail}
			activeProfileId={activeProfileId}
			profiles={profiles}
			scoreSongName={scoreSongName}
			scoreSongSuggestions={scoreSongSuggestions}
			scoreSearchKeyword={scoreSearchKeyword}
			scoreType={scoreType}
			scoreDifficulty={scoreDifficulty}
			scoreAchievements={scoreAchievements}
			filteredScores={filteredScores}
			filteredPlayRecords={filteredPlayRecords}
			resolveSongTitle={resolveSongTitle}
			resolveSongCoverUrl={resolveSongCoverUrl}
			formatChartType={formatChartType}
			formatDifficulty={formatDifficulty}
			setActiveProfileId={setActiveProfileId}
			loadScores={loadScores}
			setScoreSongName={setScoreSongName}
			setScoreSearchKeyword={setScoreSearchKeyword}
			setScoreType={setScoreType}
			setScoreDifficulty={setScoreDifficulty}
			setScoreAchievements={setScoreAchievements}
			handleSubmitScore={handleSubmitScore}
			openScoreEditDialog={openScoreEditDialog}
			handleDeleteScore={handleDeleteScore}
			handleDeletePlayRecord={handleDeletePlayRecord}
			dfQQ={dfQQ}
			dfImportToken={dfImportToken}
			lxnsAuthCode={lxnsAuthCode}
			setDfQQ={setDfQQ}
			setDfImportToken={setDfImportToken}
			setLxnsAuthCode={setLxnsAuthCode}
			handleImportDf={handleImportDf}
			handleImportLxns={handleImportLxns}
			communityDailyCount={communityDailyCount}
			communityRows={communityRows}
			communitySongName={communitySongName}
			communitySongSuggestions={communitySongSuggestions}
			communityAliasText={communityAliasText}
			adminStats={adminStats}
			adminCandidates={adminCandidates}
			candidateVoteCloseDrafts={candidateVoteCloseDrafts}
			setCommunitySongName={setCommunitySongName}
			setCommunityAliasText={setCommunityAliasText}
			handleCommunitySubmit={handleCommunitySubmit}
			handleCommunityVote={handleCommunityVote}
			loadAdminCandidates={loadAdminCandidates}
			handleAdminRollCycle={handleAdminRollCycle}
			setCandidateVoteCloseDrafts={setCandidateVoteCloseDrafts}
			handleAdminVoteWindowUpdate={handleAdminVoteWindowUpdate}
			handleAdminCandidateStatus={handleAdminCandidateStatus}
			loadProfiles={loadProfiles}
			handleStartTotpSetup={handleStartTotpSetup}
			handleDisableTotp={handleDisableTotp}
			handleConfirmTotpSetup={handleConfirmTotpSetup}
			handleUpdateUsername={handleUpdateUsername}
			handleRegisterPasskey={handleRegisterPasskey}
			handleRenamePasskey={handleRenamePasskey}
			handleDeletePasskey={handleDeletePasskey}
			handleRegenerateBackupCodes={handleRegenerateBackupCodes}
			newUserEmail={newUserEmail}
			newUserPassword={newUserPassword}
			adminUsers={adminUsers}
			setNewUserEmail={setNewUserEmail}
			setNewUserPassword={setNewUserPassword}
			handleCreateUser={handleCreateUser}
			loadAdminUsers={loadAdminUsers}
			handleDeleteUser={handleDeleteUser}
			staticSources={staticSources}
			staticBundles={staticBundles}
			staticBundleSchedule={staticBundleSchedule}
			handleBuildBundle={handleBuildBundle}
			handleUpdateStaticBundleSchedule={handleUpdateStaticBundleSchedule}
			loadStaticAdmin={loadStaticAdmin}
			handleToggleSource={handleToggleSource}
			handleEditSourceUrl={handleEditSourceUrl}
		/>
	);

	return (
		<div className="min-h-screen bg-background text-foreground">
			<header className="sticky top-0 z-40 border-b border-border/60 bg-background/95 backdrop-blur md:hidden">
				<div className="mx-auto flex h-14 max-w-[1800px] items-center gap-2 px-4">
					<Button variant="ghost" size="icon-sm" onClick={() => setMobileDrawerOpen(true)} aria-label="打开导航菜单">
						<MenuIcon />
					</Button>
					<p className="flex-1 truncate text-sm font-medium">{currentTabLabel}</p>
				</div>
			</header>

			<Sheet open={mobileDrawerOpen} onOpenChange={setMobileDrawerOpen}>
				<SheetContent side="left" className="w-[86vw] max-w-[300px] border-border/60 bg-card/90 p-0 backdrop-blur-xl">
					<SheetHeader className="sr-only">
						<SheetTitle>导航菜单</SheetTitle>
					</SheetHeader>
					{sidebarContent}
				</SheetContent>
			</Sheet>

			<div className="min-h-screen">
				<aside className="fixed inset-y-0 left-0 z-20 hidden w-[272px] border-r border-border/60 bg-card/30 md:flex md:flex-col">
					{sidebarContent}
				</aside>

				<main className="flex min-w-0 flex-col md:pl-[272px]">
					<header className="sticky top-0 z-30 hidden h-14 items-center border-b border-border/60 bg-background/90 px-6 backdrop-blur md:flex">
						<p className="text-lg">{currentTabLabel}</p>
					</header>

					<div className="flex min-w-0 flex-col gap-4 p-4 md:p-6">{tabPanelContent}</div>
				</main>
			</div>

			<SongDetailDialog
				song={selectedSong}
				songAliases={songAliases}
				songSheets={songSheets}
				songDetailChartTypes={songDetailChartTypes}
				songDetailChartType={songDetailChartType}
				songDetailLoading={songDetailLoading}
				selectedSongRegionSummary={selectedSongRegionSummary}
				buildCoverUrl={buildCoverUrl}
				formatVersionDisplay={formatVersionDisplay}
				formatDateToYmd={formatDateToYmd}
				formatChartType={formatChartType}
				formatDifficulty={formatDifficulty}
				normalizeSheetType={normalizeSheetType}
				onClose={closeSongDetail}
				onChangeChartType={setSongDetailChartType}
			/>

			<ScoreEditDialog
				open={scoreEditOpen}
				setOpen={setScoreEditOpen}
				scoreEdit={scoreEdit}
				setScoreEdit={setScoreEdit}
				onSave={() => {
					void handleSaveScoreEdit();
				}}
			/>

			{confirmDialogNode}
			<Toaster position={toasterPosition} richColors />
		</div>
	);
}

export default App;
