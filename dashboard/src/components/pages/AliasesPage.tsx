import { useEffect, useMemo, useRef, useState } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Empty, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { TablePagination } from "@/components/ui/table-pagination";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { AdminCandidate, AdminDashboardStats, CommunityCandidate } from "@/lib/app-types";
import { useTablePagination } from "@/lib/use-table-pagination";
import { cn } from "@/lib/utils";
import { useTranslation } from "react-i18next";

type AliasesPageProps = {
	isAdmin: boolean;
	communityDailyCount: number;
	communityRows: CommunityCandidate[];
	communitySongName: string;
	communitySongSuggestions: Array<{ songIdentifier: string; title: string; artist: string }>;
	communityAliasText: string;
	adminStats: AdminDashboardStats | null;
	adminCandidates: AdminCandidate[];
	candidateVoteCloseDrafts: Record<string, string>;
	resolveSongTitle: (songIdentifier: string) => string;
	resolveSongCoverUrl: (songIdentifier: string) => string | null;
	onCommunitySongNameChange: (value: string) => void;
	onCommunityAliasTextChange: (value: string) => void;
	onCommunitySubmit: () => void | Promise<void>;
	onCommunityVote: (candidateId: string, vote: -1 | 1) => void | Promise<void>;
	onLoadAdminCandidates: () => void | Promise<void>;
	onAdminRollCycle: () => void | Promise<void>;
	onCandidateVoteCloseDraftChange: (candidateId: string, value: string) => void;
	onAdminVoteWindowUpdate: (candidateId: string) => void | Promise<void>;
	onAdminCandidateStatusUpdate: (candidateId: string, status: "voting" | "approved" | "rejected") => void | Promise<void>;
};

function formatDateTime(value?: string | null) {
	if (!value) {
		return "-";
	}
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return new Intl.DateTimeFormat("zh-CN", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
		hour12: false,
	}).format(date);
}

function toDateTimeLocalInputValue(value?: string | null) {
	if (!value) {
		return "";
	}
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return "";
	}
	const offsetMs = date.getTimezoneOffset() * 60_000;
	const local = new Date(date.getTime() - offsetMs);
	return local.toISOString().slice(0, 16);
}

export function AliasesPage({
	isAdmin,
	communityDailyCount,
	communityRows,
	communitySongName,
	communitySongSuggestions,
	communityAliasText,
	adminStats,
	adminCandidates,
	candidateVoteCloseDrafts,
	resolveSongTitle,
	resolveSongCoverUrl,
	onCommunitySongNameChange,
	onCommunityAliasTextChange,
	onCommunitySubmit,
	onCommunityVote,
	onLoadAdminCandidates,
	onAdminRollCycle,
	onCandidateVoteCloseDraftChange,
	onAdminVoteWindowUpdate,
	onAdminCandidateStatusUpdate,
}: AliasesPageProps) {
	const { t } = useTranslation("aliases");
	const [selectedCandidateId, setSelectedCandidateId] = useState<string | null>(null);
	const [songSuggestionOpen, setSongSuggestionOpen] = useState(false);
	const [activeSongSuggestionIndex, setActiveSongSuggestionIndex] = useState(-1);
	const closeSuggestionTimerRef = useRef<number | null>(null);

	const selectedCandidate = useMemo(
		() => communityRows.find((row) => row.candidateId === selectedCandidateId) ?? null,
		[communityRows, selectedCandidateId],
	);
	const adminCandidateById = useMemo(() => new Map(adminCandidates.map((row) => [row.candidateId, row])), [adminCandidates]);
	const selectedAdminCandidate = selectedCandidate ? adminCandidateById.get(selectedCandidate.candidateId) : undefined;
	const communityPagination = useTablePagination(communityRows);

	useEffect(() => {
		return () => {
			if (closeSuggestionTimerRef.current !== null) {
				window.clearTimeout(closeSuggestionTimerRef.current);
			}
		};
	}, []);

	useEffect(() => {
		if (communitySongSuggestions.length === 0) {
			setActiveSongSuggestionIndex(-1);
			setSongSuggestionOpen(false);
			return;
		}
		setActiveSongSuggestionIndex((previous) => {
			if (previous >= 0 && previous < communitySongSuggestions.length) {
				return previous;
			}
			return 0;
		});
	}, [communitySongSuggestions]);

	const handleOpenDetail = (row: CommunityCandidate) => {
		setSelectedCandidateId(row.candidateId);
		if (row.voteCloseAt && !candidateVoteCloseDrafts[row.candidateId]) {
			onCandidateVoteCloseDraftChange(row.candidateId, toDateTimeLocalInputValue(row.voteCloseAt));
		}
	};

	const submitterDisplay = isAdmin
		? [selectedAdminCandidate?.submitterHandle, selectedAdminCandidate?.submitterEmail].filter(Boolean).join(" · ") || "-"
		: (selectedCandidate?.submitterHandle ?? "-");

	const selectSongSuggestion = (title: string) => {
		onCommunitySongNameChange(title);
		setSongSuggestionOpen(false);
		setActiveSongSuggestionIndex(-1);
	};

	return (
		<Card>
			<CardHeader>
				<CardTitle>{t("pageTitle")}</CardTitle>
				<CardDescription>{t("pageDesc")}</CardDescription>
			</CardHeader>
			<CardContent className="flex flex-col gap-6">
				<div className="flex flex-wrap gap-2">
					<Badge variant="secondary">{t("badgeDailySubmit", { count: communityDailyCount })}</Badge>
					<Badge variant="secondary">{t("badgeCurrentCandidates", { count: communityRows.length })}</Badge>
					{isAdmin ? <Badge>{t("badgePendingSettle", { count: adminStats?.expiredVotingCount ?? 0 })}</Badge> : null}
				</div>

				<section className="rounded-lg border p-4">
					<div className="mb-3 text-sm font-medium">{t("sectionSubmit")}</div>
					<FieldGroup className="gap-3 md:flex-row md:items-end">
						<Field className="relative min-w-0 flex-1">
							<FieldLabel htmlFor="alias-song-name">{t("labelSongName")}</FieldLabel>
							<Input
								id="alias-song-name"
								value={communitySongName}
								onFocus={() => {
									if (closeSuggestionTimerRef.current !== null) {
										window.clearTimeout(closeSuggestionTimerRef.current);
										closeSuggestionTimerRef.current = null;
									}
									if (communitySongSuggestions.length > 0) {
										setSongSuggestionOpen(true);
									}
								}}
								onBlur={() => {
									closeSuggestionTimerRef.current = window.setTimeout(() => {
										setSongSuggestionOpen(false);
									}, 120);
								}}
								onChange={(event) => {
									onCommunitySongNameChange(event.target.value);
									setSongSuggestionOpen(true);
								}}
								onKeyDown={(event) => {
									if (communitySongSuggestions.length === 0) {
										return;
									}

									if (event.key === "ArrowDown") {
										event.preventDefault();
										setSongSuggestionOpen(true);
										setActiveSongSuggestionIndex((previous) => {
											if (previous < 0) {
												return 0;
											}
											return previous >= communitySongSuggestions.length - 1 ? 0 : previous + 1;
										});
										return;
									}

									if (event.key === "ArrowUp") {
										event.preventDefault();
										setSongSuggestionOpen(true);
										setActiveSongSuggestionIndex((previous) => {
											if (previous < 0) {
												return communitySongSuggestions.length - 1;
											}
											return previous <= 0 ? communitySongSuggestions.length - 1 : previous - 1;
										});
										return;
									}

									if (event.key === "Enter" && songSuggestionOpen && activeSongSuggestionIndex >= 0) {
										event.preventDefault();
										const suggestion = communitySongSuggestions[activeSongSuggestionIndex];
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
							{songSuggestionOpen && communitySongSuggestions.length > 0 ? (
								<div className="absolute top-full z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border bg-popover p-1 shadow-md ring-1 ring-foreground/10">
									{communitySongSuggestions.map((suggestion, index) => (
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
						<Field className="min-w-0 flex-1">
							<FieldLabel htmlFor="alias-text">{t("labelAliasText")}</FieldLabel>
							<Input
								id="alias-text"
								value={communityAliasText}
								onChange={(event) => onCommunityAliasTextChange(event.target.value)}
							/>
						</Field>
						<Button className="w-full md:w-auto md:shrink-0" onClick={() => void onCommunitySubmit()}>
							{t("btnSubmit")}
						</Button>
					</FieldGroup>
				</section>

				{isAdmin ? (
					<section className="rounded-lg border p-4">
						<div className="mb-3 text-sm font-medium">{t("sectionAdminQuickActions")}</div>
						<div className="flex flex-wrap gap-2">
							<Button className="h-9 w-full sm:w-auto" variant="outline" onClick={() => void onLoadAdminCandidates()}>
								{t("btnRefreshAdminData")}
							</Button>
							<Button className="h-9 w-full sm:w-auto" onClick={() => void onAdminRollCycle()}>
								{t("btnManualSettle")}
							</Button>
						</div>
					</section>
				) : null}

				<section className="flex flex-col gap-3">
					{communityRows.length === 0 ? (
						<Empty>
							<EmptyHeader>
								<EmptyTitle>{t("noCandidatesTitle")}</EmptyTitle>
							</EmptyHeader>
						</Empty>
					) : (
						<div className="flex flex-col gap-3">
							<div className="space-y-3 md:hidden">
								{communityPagination.pagedItems.map((row) => (
									<button
										key={row.candidateId}
										type="button"
										className="w-full rounded-lg border p-3 text-left"
										onClick={() => handleOpenDetail(row)}
									>
										<div className="flex items-start gap-3">
											<Avatar className="size-12 rounded-md">
												<AvatarImage
													src={resolveSongCoverUrl(row.songIdentifier) ?? undefined}
													alt={resolveSongTitle(row.songIdentifier)}
												/>
												<AvatarFallback>{resolveSongTitle(row.songIdentifier).slice(0, 1).toUpperCase()}</AvatarFallback>
											</Avatar>
											<div className="min-w-0 flex-1">
												<p className="truncate text-sm font-medium">{resolveSongTitle(row.songIdentifier)}</p>
												<p className="mt-1 break-all text-xs text-muted-foreground">
													{t("aliasPrefix")}
													{row.aliasText}
												</p>
												<div className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
													<span className="rounded-md border px-2 py-1">
														{t("supportCountPrefix", { count: row.supportCount })}
													</span>
													<span className="rounded-md border px-2 py-1">
														{t("opposeCountPrefix", { count: row.opposeCount })}
													</span>
												</div>
												<p className="mt-2 text-xs text-muted-foreground">
													{t("closeAtPrefix")}
													{formatDateTime(row.voteCloseAt)}
												</p>
											</div>
										</div>
										<span className="mt-3 block rounded-md border px-3 py-2 text-center text-sm">{t("btnViewDetails")}</span>
									</button>
								))}
							</div>

							<div className="hidden md:block">
								<Table>
									<TableHeader>
										<TableRow>
											<TableHead>{t("colSong")}</TableHead>
											<TableHead>{t("colAlias")}</TableHead>
											<TableHead>{t("colSupport")}</TableHead>
											<TableHead>{t("colOppose")}</TableHead>
											<TableHead>{t("colCloseAt")}</TableHead>
										</TableRow>
									</TableHeader>
									<TableBody>
										{communityPagination.pagedItems.map((row) => (
											<TableRow key={row.candidateId} className="cursor-pointer" onClick={() => handleOpenDetail(row)}>
												<TableCell>
													<div className="flex items-center gap-2">
														<Avatar className="size-11 rounded-md">
															<AvatarImage
																src={resolveSongCoverUrl(row.songIdentifier) ?? undefined}
																alt={resolveSongTitle(row.songIdentifier)}
															/>
															<AvatarFallback>{resolveSongTitle(row.songIdentifier).slice(0, 1).toUpperCase()}</AvatarFallback>
														</Avatar>
														<span className="max-w-[280px] truncate">{resolveSongTitle(row.songIdentifier)}</span>
													</div>
												</TableCell>
												<TableCell className="max-w-[220px] truncate">{row.aliasText}</TableCell>
												<TableCell>{row.supportCount}</TableCell>
												<TableCell>{row.opposeCount}</TableCell>
												<TableCell>{formatDateTime(row.voteCloseAt)}</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</div>

							<TablePagination
								page={communityPagination.page}
								pageCount={communityPagination.pageCount}
								pageSize={communityPagination.pageSize}
								onPageChange={communityPagination.setPage}
								onPageSizeChange={communityPagination.setPageSize}
							/>
						</div>
					)}
				</section>
			</CardContent>

			<Dialog open={Boolean(selectedCandidate)} onOpenChange={(open) => !open && setSelectedCandidateId(null)}>
				<DialogContent className="max-h-[calc(100dvh-1.5rem)] gap-3 overflow-y-auto sm:!max-w-3xl">
					<DialogHeader>
						<DialogTitle>{t("dialogTitle")}</DialogTitle>
						<DialogDescription>{t("dialogDesc")}</DialogDescription>
					</DialogHeader>

					{selectedCandidate ? (
						<div className="flex flex-col gap-3">
							<div className="flex flex-col gap-3 sm:flex-row">
								<Avatar className="size-20 rounded-md sm:size-24">
									<AvatarImage
										src={resolveSongCoverUrl(selectedCandidate.songIdentifier) ?? undefined}
										alt={resolveSongTitle(selectedCandidate.songIdentifier)}
									/>
									<AvatarFallback>
										{resolveSongTitle(selectedCandidate.songIdentifier).slice(0, 1).toUpperCase()}
									</AvatarFallback>
								</Avatar>
								<div className="flex flex-1 flex-col gap-2">
									<p className="text-base font-medium">{resolveSongTitle(selectedCandidate.songIdentifier)}</p>
									<p className="text-sm text-muted-foreground">
										{t("aliasPrefix")}
										{selectedCandidate.aliasText}
									</p>
									<div className="flex flex-wrap gap-2">
										<Badge variant="secondary">
											{t("statusPrefix")}
											{selectedAdminCandidate?.status ?? selectedCandidate.status ?? "voting"}
										</Badge>
										<Badge variant="secondary">{t("supportCountPrefix", { count: selectedCandidate.supportCount })}</Badge>
										<Badge variant="secondary">{t("opposeCountPrefix", { count: selectedCandidate.opposeCount })}</Badge>
									</div>
								</div>
							</div>

							<Separator />

							<div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
								<InfoBlock label={t("infoSubmitter")} value={submitterDisplay} />
								<InfoBlock
									label={t("infoSubmitTime")}
									value={formatDateTime(selectedAdminCandidate?.createdAt ?? selectedCandidate.createdAt ?? null)}
								/>
								<InfoBlock
									label={t("infoCloseTime")}
									value={formatDateTime(selectedAdminCandidate?.voteCloseAt ?? selectedCandidate.voteCloseAt)}
								/>
								<InfoBlock label={t("infoVoteStart")} value={formatDateTime(selectedCandidate.voteOpenAt)} />
								<InfoBlock
									label={t("infoMyVote")}
									value={
										selectedCandidate.myVote === 1
											? t("voteSupport")
											: selectedCandidate.myVote === -1
												? t("voteOppose")
												: t("voteNone")
									}
								/>
								<InfoBlock label={t("infoCandidateId")} value={selectedCandidate.candidateId} />
							</div>
							<InfoBlock label={t("infoSongId")} value={selectedCandidate.songIdentifier} />

							<div className="flex flex-wrap gap-2">
								<Button
									className="h-8 flex-1 sm:flex-none"
									variant={selectedCandidate.myVote === 1 ? "default" : "outline"}
									onClick={() => void onCommunityVote(selectedCandidate.candidateId, 1)}
								>
									{selectedCandidate.myVote === 1 ? t("btnCancelSupport") : t("btnSupport")}
								</Button>
								<Button
									className="h-8 flex-1 sm:flex-none"
									variant={selectedCandidate.myVote === -1 ? "default" : "outline"}
									onClick={() => void onCommunityVote(selectedCandidate.candidateId, -1)}
								>
									{selectedCandidate.myVote === -1 ? t("btnCancelOppose") : t("btnOppose")}
								</Button>
							</div>

							{isAdmin ? (
								<>
									<Separator />
									<div className="text-sm font-medium">{t("sectionAdminActions")}</div>
									<FieldGroup className="gap-2">
										<Field>
											<FieldLabel htmlFor="candidate-vote-close">{t("labelVoteCloseTime")}</FieldLabel>
											<Input
												id="candidate-vote-close"
												type="datetime-local"
												value={candidateVoteCloseDrafts[selectedCandidate.candidateId] ?? ""}
												onChange={(event) => onCandidateVoteCloseDraftChange(selectedCandidate.candidateId, event.target.value)}
											/>
										</Field>
									</FieldGroup>
									<div className="flex flex-wrap gap-2">
										<Button
											className="h-8 w-full sm:w-auto"
											variant="outline"
											onClick={() => void onAdminVoteWindowUpdate(selectedCandidate.candidateId)}
										>
											{t("btnUpdateCloseTime")}
										</Button>
										<Button
											className="h-8 w-full sm:w-auto"
											variant="outline"
											onClick={() => void onAdminCandidateStatusUpdate(selectedCandidate.candidateId, "voting")}
										>
											{t("btnSetVoting")}
										</Button>
										<Button
											className="h-8 w-full sm:w-auto"
											variant="outline"
											onClick={() => void onAdminCandidateStatusUpdate(selectedCandidate.candidateId, "approved")}
										>
											{t("btnSetApproved")}
										</Button>
										<Button
											className="h-8 w-full sm:w-auto"
											variant="outline"
											onClick={() => void onAdminCandidateStatusUpdate(selectedCandidate.candidateId, "rejected")}
										>
											{t("btnSetRejected")}
										</Button>
									</div>
								</>
							) : null}
						</div>
					) : null}

					<DialogFooter>
						<Button variant="outline" onClick={() => setSelectedCandidateId(null)}>
							{t("btnClose")}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</Card>
	);
}

function InfoBlock({ label, value }: { label: string; value: string }) {
	return (
		<div className="rounded-md border p-2.5">
			<p className="text-xs text-muted-foreground">{label}</p>
			<p className="mt-1 break-all text-[13px] leading-5">{value}</p>
		</div>
	);
}
