import { useEffect, useRef, useState } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TablePagination } from "@/components/ui/table-pagination";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useTablePagination } from "@/lib/use-table-pagination";
import { cn } from "@/lib/utils";
import { RefreshCwIcon } from "lucide-react";
import { useTranslation } from "react-i18next";

type ScoreRow = {
	id: string;
	profileId: string;
	achievements: number | string;
	rank: string;
	dxScore: number;
	sheet?: {
		songIdentifier: string;
		chartType: string;
		difficulty: string;
		song?: {
			title: string;
		};
	};
};

type PlayRecordRow = {
	id: string;
	achievements: number | string;
	dxScore: number;
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

type ScoresPageProps = {
	scoreSongName: string;
	scoreSongSuggestions: Array<{ songIdentifier: string; title: string; artist: string }>;
	scoreSearchKeyword: string;
	scoreType: string;
	scoreDifficulty: string;
	scoreAchievements: string;
	scores: ScoreRow[];
	playRecords: PlayRecordRow[];
	resolveSongTitle: (songIdentifier: string) => string;
	resolveSongCoverUrl: (songIdentifier: string) => string | null;
	formatChartType: (value?: string | null) => string;
	formatDifficulty: (value?: string | null) => string;
	onReload: () => void | Promise<void>;
	onScoreSongNameChange: (value: string) => void;
	onScoreSearchKeywordChange: (value: string) => void;
	onScoreTypeChange: (value: string) => void;
	onScoreDifficultyChange: (value: string) => void;
	onScoreAchievementsChange: (value: string) => void;
	onSubmitScore: () => void | Promise<void>;
	onOpenScoreEdit: (row: ScoreRow) => void;
	onDeleteScore: (scoreId: string) => void | Promise<void>;
	onDeletePlayRecord: (recordId: string) => void | Promise<void>;
};

function formatDate(value?: string | null) {
	if (!value) {
		return "-";
	}
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return date.toISOString().slice(0, 10);
}

function resolveSongDisplayTitle(
	row: { sheet?: { songIdentifier: string; song?: { title: string } } },
	resolveSongTitle: (songIdentifier: string) => string,
	t: (key: string) => string,
) {
	const songIdentifier = row.sheet?.songIdentifier;
	if (!songIdentifier) {
		return t("app:unknownSong");
	}
	return row.sheet?.song?.title ?? resolveSongTitle(songIdentifier);
}

export function ScoresPage({
	scoreSongName,
	scoreSongSuggestions,
	scoreSearchKeyword,
	scoreType,
	scoreDifficulty,
	scoreAchievements,
	scores,
	playRecords,
	resolveSongTitle,
	resolveSongCoverUrl,
	formatChartType,
	formatDifficulty,
	onReload,
	onScoreSongNameChange,
	onScoreSearchKeywordChange,
	onScoreTypeChange,
	onScoreDifficultyChange,
	onScoreAchievementsChange,
	onSubmitScore,
	onOpenScoreEdit,
	onDeleteScore,
	onDeletePlayRecord,
}: ScoresPageProps) {
	const { t } = useTranslation();
	const scoresPagination = useTablePagination(scores);
	const playRecordsPagination = useTablePagination(playRecords);
	const [songSuggestionOpen, setSongSuggestionOpen] = useState(false);
	const [activeSongSuggestionIndex, setActiveSongSuggestionIndex] = useState(-1);
	const closeSuggestionTimerRef = useRef<number | null>(null);

	useEffect(() => {
		return () => {
			if (closeSuggestionTimerRef.current !== null) {
				window.clearTimeout(closeSuggestionTimerRef.current);
			}
		};
	}, []);

	useEffect(() => {
		if (scoreSongSuggestions.length === 0) {
			setActiveSongSuggestionIndex(-1);
			setSongSuggestionOpen(false);
			return;
		}
		setActiveSongSuggestionIndex((previous) => {
			if (previous >= 0 && previous < scoreSongSuggestions.length) {
				return previous;
			}
			return 0;
		});
	}, [scoreSongSuggestions]);

	const selectSongSuggestion = (title: string) => {
		onScoreSongNameChange(title);
		setSongSuggestionOpen(false);
		setActiveSongSuggestionIndex(-1);
	};

	return (
		<Card size="sm">
			<CardHeader>
				<CardTitle>{t("scores:title")}</CardTitle>
				<CardDescription>{t("scores:desc")}</CardDescription>
			</CardHeader>
			<CardContent className="flex flex-col gap-4">
				<section className="rounded-lg border p-3">
					<FieldGroup className="gap-2">
						<div className="flex flex-col gap-2 md:flex-row md:items-end">
							<Field className="flex-1">
								<FieldLabel htmlFor="score-search">{t("scores:searchScore")}</FieldLabel>
								<Input
									id="score-search"
									value={scoreSearchKeyword}
									onChange={(event) => onScoreSearchKeywordChange(event.target.value)}
								/>
							</Field>
							<Button className="h-9 w-full md:h-7 md:w-auto" size="sm" variant="outline" onClick={() => void onReload()}>
								<RefreshCwIcon data-icon="inline-start" />
								{t("scores:btnRefresh")}
							</Button>
						</div>
					</FieldGroup>
				</section>

				<section className="rounded-lg border p-3">
					<div className="mb-2 text-sm font-medium">{t("scores:addScore")}</div>
					<FieldGroup className="gap-3 md:flex-row md:items-end">
						<Field className="relative min-w-0 md:flex-[2]">
							<FieldLabel htmlFor="score-song">{t("scores:songName")}</FieldLabel>
							<Input
								id="score-song"
								value={scoreSongName}
								onFocus={() => {
									if (closeSuggestionTimerRef.current !== null) {
										window.clearTimeout(closeSuggestionTimerRef.current);
										closeSuggestionTimerRef.current = null;
									}
									if (scoreSongSuggestions.length > 0) {
										setSongSuggestionOpen(true);
									}
								}}
								onBlur={() => {
									closeSuggestionTimerRef.current = window.setTimeout(() => {
										setSongSuggestionOpen(false);
									}, 120);
								}}
								onChange={(event) => {
									onScoreSongNameChange(event.target.value);
									setSongSuggestionOpen(true);
								}}
								onKeyDown={(event) => {
									if (scoreSongSuggestions.length === 0) {
										return;
									}

									if (event.key === "ArrowDown") {
										event.preventDefault();
										setSongSuggestionOpen(true);
										setActiveSongSuggestionIndex((previous) => {
											if (previous < 0) {
												return 0;
											}
											return previous >= scoreSongSuggestions.length - 1 ? 0 : previous + 1;
										});
										return;
									}

									if (event.key === "ArrowUp") {
										event.preventDefault();
										setSongSuggestionOpen(true);
										setActiveSongSuggestionIndex((previous) => {
											if (previous < 0) {
												return scoreSongSuggestions.length - 1;
											}
											return previous <= 0 ? scoreSongSuggestions.length - 1 : previous - 1;
										});
										return;
									}

									if (event.key === "Enter" && songSuggestionOpen && activeSongSuggestionIndex >= 0) {
										event.preventDefault();
										const suggestion = scoreSongSuggestions[activeSongSuggestionIndex];
										if (suggestion) {
											selectSongSuggestion(suggestion.title);
										}
										return;
									}

									if (event.key === "Escape") {
										setSongSuggestionOpen(false);
									}
								}}
							/>
							{songSuggestionOpen && scoreSongSuggestions.length > 0 ? (
								<div className="absolute top-full z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border bg-popover p-1 shadow-md ring-1 ring-foreground/10">
									{scoreSongSuggestions.map((suggestion, index) => (
										<button
											key={suggestion.songIdentifier}
											type="button"
											className={cn(
												"flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left",
												index === activeSongSuggestionIndex ? "bg-accent text-accent-foreground" : "hover:bg-accent/60",
											)}
											onMouseEnter={() => setActiveSongSuggestionIndex(index)}
											onMouseDown={(event) => {
												event.preventDefault();
												selectSongSuggestion(suggestion.title);
											}}
										>
											<Avatar className="size-9 rounded-md">
												<AvatarImage src={resolveSongCoverUrl(suggestion.songIdentifier) ?? undefined} />
												<AvatarFallback>{suggestion.title.slice(0, 1).toUpperCase()}</AvatarFallback>
											</Avatar>
											<span className="min-w-0 flex-1">
												<span className="block truncate text-sm">{suggestion.title}</span>
												<span className="block truncate text-xs text-muted-foreground">{suggestion.artist || "-"}</span>
											</span>
										</button>
									))}
								</div>
							) : null}
						</Field>
						<Field className="w-full md:w-40 md:flex-none">
							<FieldLabel>Type</FieldLabel>
							<Select value={scoreType} onValueChange={onScoreTypeChange}>
								<SelectTrigger className="w-full">
									<SelectValue placeholder={t("scores:selectType")} />
								</SelectTrigger>
								<SelectContent>
									<SelectGroup>
										<SelectItem value="standard">standard</SelectItem>
										<SelectItem value="dx">dx</SelectItem>
										<SelectItem value="utage">utage</SelectItem>
									</SelectGroup>
								</SelectContent>
							</Select>
						</Field>
						<Field className="w-full md:w-44 md:flex-none">
							<FieldLabel>Difficulty</FieldLabel>
							<Select value={scoreDifficulty} onValueChange={onScoreDifficultyChange}>
								<SelectTrigger className="w-full">
									<SelectValue placeholder={t("scores:selectDiff")} />
								</SelectTrigger>
								<SelectContent>
									<SelectGroup>
										<SelectItem value="basic">basic</SelectItem>
										<SelectItem value="advanced">advanced</SelectItem>
										<SelectItem value="expert">expert</SelectItem>
										<SelectItem value="master">master</SelectItem>
										<SelectItem value="remaster">remaster</SelectItem>
									</SelectGroup>
								</SelectContent>
							</Select>
						</Field>
						<Field className="w-full md:w-44 md:flex-none">
							<FieldLabel htmlFor="score-achievements">Achievements</FieldLabel>
							<Input
								id="score-achievements"
								value={scoreAchievements}
								onChange={(event) => onScoreAchievementsChange(event.target.value)}
							/>
						</Field>
						<Button className="h-9 w-full md:h-7 md:w-auto md:self-end" size="sm" onClick={() => void onSubmitScore()}>
							{t("scores:btnSubmit")}
						</Button>
					</FieldGroup>
				</section>

				<section className="flex flex-col gap-3">
					<h3 className="text-sm font-medium">{t("scores:bestScores")}</h3>
					{scores.length === 0 ? (
						<Empty>
							<EmptyHeader>
								<EmptyTitle>{t("scores:emptyScores")}</EmptyTitle>
							</EmptyHeader>
						</Empty>
					) : (
						<div className="flex flex-col gap-3">
							<div className="space-y-3 md:hidden">
								{scoresPagination.pagedItems.map((row) => {
									const songIdentifier = row.sheet?.songIdentifier ?? "";
									const songTitle = resolveSongDisplayTitle(row, resolveSongTitle, t);
									return (
										<article key={row.id} className="rounded-lg border p-3">
											<div className="flex items-start gap-3">
												<Avatar className="size-11 rounded-md">
													<AvatarImage src={songIdentifier ? (resolveSongCoverUrl(songIdentifier) ?? undefined) : undefined} />
													<AvatarFallback>{songTitle.slice(0, 1).toUpperCase()}</AvatarFallback>
												</Avatar>
												<div className="min-w-0 flex-1">
													<p className="truncate text-sm font-medium">{songTitle}</p>
													<p className="mt-1 text-xs text-muted-foreground">
														{`${formatChartType(row.sheet?.chartType)} / ${formatDifficulty(row.sheet?.difficulty)}`}
													</p>
													<div className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
														<span className="rounded-md border px-2 py-1">
															{t("scores:achievements")} {String(row.achievements)}
														</span>
														<span className="rounded-md border px-2 py-1">
															{t("scores:rank")} {row.rank || "-"}
														</span>
														<span className="rounded-md border px-2 py-1">
															{t("scores:dxScore")} {row.dxScore}
														</span>
													</div>
												</div>
											</div>
											<div className="mt-3 grid grid-cols-2 gap-2">
												<Button className="h-9 w-full" variant="outline" onClick={() => onOpenScoreEdit(row)}>
													{t("scores:btnEdit")}
												</Button>
												<Button className="h-9 w-full" variant="destructive" onClick={() => void onDeleteScore(row.id)}>
													{t("scores:btnDelete")}
												</Button>
											</div>
										</article>
									);
								})}
							</div>

							<div className="hidden md:block">
								<Table>
									<TableHeader>
										<TableRow>
											<TableHead>{t("scores:colSong")}</TableHead>
											<TableHead>{t("scores:colChart")}</TableHead>
											<TableHead>{t("scores:colAchievements")}</TableHead>
											<TableHead>{t("scores:colRank")}</TableHead>
											<TableHead>{t("scores:colDxScore")}</TableHead>
											<TableHead>{t("scores:colAction")}</TableHead>
										</TableRow>
									</TableHeader>
									<TableBody>
										{scoresPagination.pagedItems.map((row) => {
											const songIdentifier = row.sheet?.songIdentifier ?? "";
											const songTitle = resolveSongDisplayTitle(row, resolveSongTitle, t);
											return (
												<TableRow key={row.id}>
													<TableCell>
														<div className="flex items-center gap-2">
															<Avatar className="size-10 rounded-md">
																<AvatarImage
																	src={songIdentifier ? (resolveSongCoverUrl(songIdentifier) ?? undefined) : undefined}
																/>
																<AvatarFallback>{songTitle.slice(0, 1).toUpperCase()}</AvatarFallback>
															</Avatar>
															<span className="max-w-[300px] truncate font-medium">{songTitle}</span>
														</div>
													</TableCell>
													<TableCell>{`${formatChartType(row.sheet?.chartType)} / ${formatDifficulty(row.sheet?.difficulty)}`}</TableCell>
													<TableCell>{String(row.achievements)}</TableCell>
													<TableCell>{row.rank || "-"}</TableCell>
													<TableCell>{row.dxScore}</TableCell>
													<TableCell className="flex flex-wrap gap-2">
														<Button size="sm" variant="outline" onClick={() => onOpenScoreEdit(row)}>
															{t("scores:btnEdit")}
														</Button>
														<Button size="sm" variant="outline" onClick={() => void onDeleteScore(row.id)}>
															{t("scores:btnDelete")}
														</Button>
													</TableCell>
												</TableRow>
											);
										})}
									</TableBody>
								</Table>
							</div>

							<TablePagination
								page={scoresPagination.page}
								pageCount={scoresPagination.pageCount}
								pageSize={scoresPagination.pageSize}
								onPageChange={scoresPagination.setPage}
								onPageSizeChange={scoresPagination.setPageSize}
							/>
						</div>
					)}
				</section>

				<section className="flex flex-col gap-3">
					<h3 className="text-sm font-medium">{t("scores:recentPlays")}</h3>
					{playRecords.length === 0 ? (
						<Empty>
							<EmptyHeader>
								<EmptyTitle>{t("scores:emptyPlays")}</EmptyTitle>
							</EmptyHeader>
						</Empty>
					) : (
						<div className="flex flex-col gap-3">
							<div className="space-y-3 md:hidden">
								{playRecordsPagination.pagedItems.map((row) => {
									const songIdentifier = row.sheet?.songIdentifier ?? "";
									const songTitle = resolveSongDisplayTitle(row, resolveSongTitle, t);
									return (
										<article key={row.id} className="rounded-lg border p-3">
											<div className="flex items-start gap-3">
												<Avatar className="size-11 rounded-md">
													<AvatarImage src={songIdentifier ? (resolveSongCoverUrl(songIdentifier) ?? undefined) : undefined} />
													<AvatarFallback>{songTitle.slice(0, 1).toUpperCase()}</AvatarFallback>
												</Avatar>
												<div className="min-w-0 flex-1">
													<p className="truncate text-sm font-medium">{songTitle}</p>
													<p className="mt-1 text-xs text-muted-foreground">
														{`${formatChartType(row.sheet?.chartType)} / ${formatDifficulty(row.sheet?.difficulty)}`}
													</p>
													<div className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
														<span className="rounded-md border px-2 py-1">
															{t("scores:achievements")} {String(row.achievements)}
														</span>
														<span className="rounded-md border px-2 py-1">
															{t("scores:dxScore")} {row.dxScore}
														</span>
														<span className="rounded-md border px-2 py-1">
															{t("scores:time")} {formatDate(row.playTime)}
														</span>
													</div>
												</div>
											</div>
											<Button className="mt-3 h-9 w-full" variant="destructive" onClick={() => void onDeletePlayRecord(row.id)}>
												{t("scores:btnDelete")}
											</Button>
										</article>
									);
								})}
							</div>

							<div className="hidden md:block">
								<Table>
									<TableHeader>
										<TableRow>
											<TableHead>{t("scores:colSong")}</TableHead>
											<TableHead>{t("scores:colChart")}</TableHead>
											<TableHead>{t("scores:colAchievements")}</TableHead>
											<TableHead>{t("scores:colDxScore")}</TableHead>
											<TableHead>{t("scores:colTime")}</TableHead>
											<TableHead>{t("scores:colAction")}</TableHead>
										</TableRow>
									</TableHeader>
									<TableBody>
										{playRecordsPagination.pagedItems.map((row) => {
											const songIdentifier = row.sheet?.songIdentifier ?? "";
											const songTitle = resolveSongDisplayTitle(row, resolveSongTitle, t);
											return (
												<TableRow key={row.id}>
													<TableCell>
														<div className="flex items-center gap-2">
															<Avatar className="size-10 rounded-md">
																<AvatarImage
																	src={songIdentifier ? (resolveSongCoverUrl(songIdentifier) ?? undefined) : undefined}
																/>
																<AvatarFallback>{songTitle.slice(0, 1).toUpperCase()}</AvatarFallback>
															</Avatar>
															<span className="max-w-[300px] truncate font-medium">{songTitle}</span>
														</div>
													</TableCell>
													<TableCell>{`${formatChartType(row.sheet?.chartType)} / ${formatDifficulty(row.sheet?.difficulty)}`}</TableCell>
													<TableCell>{String(row.achievements)}</TableCell>
													<TableCell>{row.dxScore}</TableCell>
													<TableCell>{formatDate(row.playTime)}</TableCell>
													<TableCell>
														<Button size="sm" variant="outline" onClick={() => void onDeletePlayRecord(row.id)}>
															{t("scores:btnDelete")}
														</Button>
													</TableCell>
												</TableRow>
											);
										})}
									</TableBody>
								</Table>
							</div>

							<TablePagination
								page={playRecordsPagination.page}
								pageCount={playRecordsPagination.pageCount}
								pageSize={playRecordsPagination.pageSize}
								onPageChange={playRecordsPagination.setPage}
								onPageSizeChange={playRecordsPagination.setPageSize}
							/>
						</div>
					)}
				</section>
			</CardContent>
		</Card>
	);
}
