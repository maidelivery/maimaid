"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DownloadIcon, ExternalLinkIcon, Loader2Icon, Music2Icon, SmartphoneIcon } from "lucide-react";
import Image from "next/image";
import { useTranslation } from "react-i18next";
import { SongDetailDialog } from "@/components/songs/SongDetailDialog";
import type { Alias, Sheet, Song } from "@/components/songs/types";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Empty, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { CHART_TYPE_ORDER, COVER_BASE_URL } from "@/lib/app-helpers";
import { normalizeDifficulty, normalizeSheetType } from "@/lib/song-index";
import {
	CollectionShareError,
	resolveSharedCollection,
	type CollectionShareErrorCode,
	type SharedSongCollection,
	type SharedSongCollectionEntry,
} from "@/lib/song-collection-codec";
import { loadStaticCatalog, type StaticCatalog } from "@/lib/static-catalog";
import { cn } from "@/lib/utils";

const PROJECT_DOWNLOAD_URL = "https://github.com/rhythmeta/maimaid/releases/latest";
const IOS_DOWNLOAD_URL = process.env.NEXT_PUBLIC_IOS_DOWNLOAD_URL?.trim() || PROJECT_DOWNLOAD_URL;
const ANDROID_DOWNLOAD_URL = process.env.NEXT_PUBLIC_ANDROID_DOWNLOAD_URL?.trim() || PROJECT_DOWNLOAD_URL;

type ReadyState = {
	collection: SharedSongCollection;
	catalog: StaticCatalog;
};

type ResolvedEntry = {
	entry: SharedSongCollectionEntry;
	song: Song | null;
	sheet: Sheet | null;
};

type PublicCollectionPageProps = {
	segment: string;
};

function formatChartType(value?: string | null) {
	const normalized = normalizeSheetType(value);
	if (normalized === "std") return "STD";
	if (normalized === "dx") return "DX";
	if (normalized === "utage") return "UTAGE";
	return normalized ? normalized.toUpperCase() : "-";
}

function formatDifficulty(value?: string | null) {
	const normalized = normalizeDifficulty(value);
	if (normalized === "remaster") return "Re:MASTER";
	return normalized ? normalized.toUpperCase() : "-";
}

function formatDateToYmd(value?: string | null) {
	if (!value) return null;
	const direct = /^(\d{4}-\d{2}-\d{2})/u.exec(value.trim());
	if (direct?.[1]) return direct[1];
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value.trim() : parsed.toISOString().slice(0, 10);
}

function resolveEntries(collection: SharedSongCollection, catalog: StaticCatalog): ResolvedEntry[] {
	const songsByIdentifier = new Map(catalog.songs.map((song) => [song.songIdentifier, song]));
	const songsByNumericId = new Map(catalog.songs.filter((song) => song.songId > 0).map((song) => [song.songId, song]));

	return collection.entries.map((entry) => {
		const numericSongId = Number(entry.songId);
		const song =
			songsByIdentifier.get(entry.songId) ??
			(Number.isFinite(numericSongId) ? songsByNumericId.get(Math.trunc(numericSongId)) : undefined) ??
			null;
		const sheet = song
			? (catalog.sheets.find(
					(candidate) =>
						candidate.songIdentifier === song.songIdentifier &&
						normalizeSheetType(candidate.chartType) === normalizeSheetType(entry.chartType) &&
						normalizeDifficulty(candidate.difficulty) === normalizeDifficulty(entry.difficulty),
				) ?? null)
			: null;
		return { entry, song, sheet };
	});
}

function difficultyClass(difficulty: string) {
	switch (normalizeDifficulty(difficulty)) {
		case "basic":
			return "border-diff-basic/50 bg-diff-basic/10 text-diff-basic";
		case "advanced":
			return "border-diff-advanced/50 bg-diff-advanced/10 text-diff-advanced";
		case "expert":
			return "border-diff-expert/50 bg-diff-expert/10 text-diff-expert";
		case "master":
			return "border-diff-master/50 bg-diff-master/10 text-diff-master";
		case "remaster":
			return "border-diff-remaster/50 bg-diff-remaster/10 text-diff-remaster";
		default:
			return "";
	}
}

export function PublicCollectionPage({ segment }: PublicCollectionPageProps) {
	const { t } = useTranslation("collection");
	const [ready, setReady] = useState<ReadyState | null>(null);
	const [errorCode, setErrorCode] = useState<CollectionShareErrorCode | "catalog" | null>(null);
	const [selectedSong, setSelectedSong] = useState<Song | null>(null);
	const [selectedChartType, setSelectedChartType] = useState("");
	const [showDownloadGuidance, setShowDownloadGuidance] = useState(false);

	useEffect(() => {
		let cancelled = false;
		void Promise.all([resolveSharedCollection(segment), loadStaticCatalog()])
			.then(([collection, catalog]) => {
				if (!cancelled) setReady({ collection, catalog });
			})
			.catch((error: unknown) => {
				if (cancelled) return;
				setErrorCode(error instanceof CollectionShareError ? error.code : "catalog");
			});
		return () => {
			cancelled = true;
		};
	}, [segment]);

	const resolvedEntries = useMemo(() => (ready ? resolveEntries(ready.collection, ready.catalog) : []), [ready]);
	const selectedSheets = useMemo(
		() =>
			selectedSong && ready ? ready.catalog.sheets.filter((sheet) => sheet.songIdentifier === selectedSong.songIdentifier) : [],
		[ready, selectedSong],
	);
	const selectedAliases = useMemo<Alias[]>(
		() =>
			selectedSong && ready
				? ready.catalog.aliases.filter((alias) => alias.songIdentifier === selectedSong.songIdentifier)
				: [],
		[ready, selectedSong],
	);
	const selectedChartTypes = useMemo(
		() =>
			Array.from(new Set(selectedSheets.map((sheet) => normalizeSheetType(sheet.chartType)).filter(Boolean))).sort(
				(left, right) => (CHART_TYPE_ORDER[left] ?? 99) - (CHART_TYPE_ORDER[right] ?? 99),
			),
		[selectedSheets],
	);
	const selectedRegions = useMemo(
		() => ({
			jp: selectedSheets.some((sheet) => sheet.regionJp),
			intl: selectedSheets.some((sheet) => sheet.regionIntl),
			cn: selectedSheets.some((sheet) => sheet.regionCn),
		}),
		[selectedSheets],
	);

	const openSong = useCallback((row: ResolvedEntry) => {
		if (!row.song) return;
		setSelectedChartType(normalizeSheetType(row.entry.chartType));
		setSelectedSong(row.song);
	}, []);

	const openInApp = useCallback(() => {
		let pageWasHidden = false;
		const onVisibilityChange = () => {
			if (document.visibilityState === "hidden") pageWasHidden = true;
		};
		document.addEventListener("visibilitychange", onVisibilityChange);
		window.location.href = `maimaid://collection/${segment}`;
		window.setTimeout(() => {
			document.removeEventListener("visibilitychange", onVisibilityChange);
			if (!pageWasHidden && document.visibilityState === "visible") setShowDownloadGuidance(true);
		}, 1500);
	}, [segment]);

	const userAgent = typeof navigator === "undefined" ? "" : navigator.userAgent;
	const isIOS = /iPad|iPhone|iPod/u.test(userAgent);
	const isAndroid = /Android/u.test(userAgent);
	const downloadURL = isIOS ? IOS_DOWNLOAD_URL : isAndroid ? ANDROID_DOWNLOAD_URL : PROJECT_DOWNLOAD_URL;

	if (errorCode) {
		const key =
			errorCode === "missing"
				? "missing"
				: errorCode === "catalog"
					? "catalogFailed"
					: errorCode === "network"
						? "networkFailed"
						: "invalid";
		return <CollectionPageState title={t(`${key}Title`)} description={t(`${key}Description`)} />;
	}

	if (!ready) {
		return <CollectionPageState loading title={t("loadingTitle")} description={t("loadingDescription")} />;
	}

	return (
		<div className="min-h-screen bg-background text-foreground">
			<header className="sticky top-0 z-30 border-b border-border/60 bg-background/88 backdrop-blur-xl">
				<div className="mx-auto flex h-16 max-w-5xl items-center gap-3 px-4 md:px-6">
					<Image src="/app-icon.png" alt="" width={36} height={36} className="size-9 rounded-md" />
					<div className="min-w-0 flex-1">
						<p className="truncate text-base font-semibold">maimaid</p>
						<p className="truncate text-xs text-muted-foreground">{t("sharedCollection")}</p>
					</div>
					<Button onClick={openInApp}>
						<SmartphoneIcon data-icon="inline-start" />
						{t("openInApp")}
					</Button>
				</div>
			</header>

			<main className="mx-auto w-full max-w-5xl px-4 py-8 md:px-6 md:py-12">
				<section className="mb-8 animate-in fade-in slide-in-from-bottom-2 duration-500">
					<div className="mb-3 flex items-center gap-2 text-sm text-muted-foreground">
						<Music2Icon className="size-4" />
						<span>{t("chartCount", { count: ready.collection.entries.length })}</span>
					</div>
					<h1 className="text-3xl font-bold tracking-normal md:text-4xl">{ready.collection.name || t("untitled")}</h1>
				</section>

				{resolvedEntries.length === 0 ? (
					<Empty className="border-y py-20">
						<EmptyHeader>
							<EmptyTitle>{t("empty")}</EmptyTitle>
						</EmptyHeader>
					</Empty>
				) : (
					<section className="divide-y border-y" aria-label={t("listLabel")}>
						{resolvedEntries.map((row, index) => (
							<Button
								key={`${index}:${row.entry.songId}:${row.entry.chartType}:${row.entry.difficulty}`}
								variant="ghost"
								disabled={!row.song}
								onClick={() => openSong(row)}
								className="group h-auto w-full justify-start rounded-none px-0 py-3 text-left transition-colors hover:bg-muted/55 disabled:opacity-70"
							>
								<span className="w-10 shrink-0 text-center text-sm tabular-nums text-muted-foreground">{index + 1}</span>
								{row.song?.imageName ? (
									<Image
										src={`${COVER_BASE_URL}/${encodeURIComponent(row.song.imageName)}`}
										alt=""
										width={64}
										height={64}
										unoptimized
										className="size-14 shrink-0 rounded-md object-cover md:size-16"
									/>
								) : (
									<span className="flex size-14 shrink-0 items-center justify-center rounded-md bg-muted md:size-16">
										<Music2Icon className="size-5 text-muted-foreground" />
									</span>
								)}
								<span className="min-w-0 flex-1 px-3">
									<span className="block truncate font-semibold">{row.song?.title ?? t("unavailableSong")}</span>
									<span className="mt-1 block truncate text-sm text-muted-foreground">
										{row.song?.artist || row.entry.songId}
									</span>
								</span>
								<span className="flex shrink-0 flex-col items-end gap-1 px-2 sm:flex-row sm:items-center">
									<Badge variant="secondary">{formatChartType(row.entry.chartType)}</Badge>
									<Badge variant="outline" className={cn(difficultyClass(row.entry.difficulty))}>
										{formatDifficulty(row.entry.difficulty)} {row.sheet?.level ?? ""}
									</Badge>
								</span>
							</Button>
						))}
					</section>
				)}
			</main>

			<SongDetailDialog
				song={selectedSong}
				songAliases={selectedAliases}
				songSheets={selectedSheets}
				songDetailChartTypes={selectedChartTypes}
				songDetailChartType={selectedChartType}
				songDetailLoading={false}
				selectedSongRegionSummary={selectedRegions}
				buildCoverUrl={(imageName) => (imageName ? `${COVER_BASE_URL}/${encodeURIComponent(imageName)}` : null)}
				formatVersionDisplay={(version) => version ?? "-"}
				formatDateToYmd={formatDateToYmd}
				formatChartType={formatChartType}
				formatDifficulty={formatDifficulty}
				normalizeSheetType={normalizeSheetType}
				onClose={() => setSelectedSong(null)}
				onChangeChartType={setSelectedChartType}
			/>

			<Dialog open={showDownloadGuidance} onOpenChange={setShowDownloadGuidance}>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>{t("downloadTitle")}</DialogTitle>
						<DialogDescription>{t("downloadDescription")}</DialogDescription>
					</DialogHeader>
					<Alert>
						<DownloadIcon />
						<AlertTitle>{t(isIOS ? "downloadIOS" : isAndroid ? "downloadAndroid" : "downloadDevice")}</AlertTitle>
						<AlertDescription>{t("downloadDescription")}</AlertDescription>
					</Alert>
					<DialogFooter>
						<Button variant="outline" onClick={() => setShowDownloadGuidance(false)}>
							{t("close")}
						</Button>
						<Button asChild>
							<a href={downloadURL} target="_blank" rel="noreferrer">
								<ExternalLinkIcon data-icon="inline-start" />
								{t("download")}
							</a>
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}

function CollectionPageState({
	title,
	description,
	loading = false,
}: {
	title: string;
	description: string;
	loading?: boolean;
}) {
	return (
		<main className="flex min-h-screen items-center justify-center bg-background px-6 text-foreground">
			<Empty className="max-w-lg">
				<EmptyHeader>
					{loading ? (
						<Loader2Icon className="size-8 animate-spin text-primary" aria-hidden />
					) : (
						<Music2Icon className="size-8 text-muted-foreground" aria-hidden />
					)}
					<EmptyTitle>{title}</EmptyTitle>
					<p className="text-sm text-muted-foreground">{description}</p>
				</EmptyHeader>
			</Empty>
		</main>
	);
}
