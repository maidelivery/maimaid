import { useEffect, useMemo, useState } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Empty, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { TablePagination } from "@/components/ui/table-pagination";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { useTablePagination } from "@/lib/use-table-pagination";
import { Loader2Icon, PlayIcon, TvIcon } from "lucide-react";
import type { Alias, Sheet, Song } from "./types";
import { useTranslation } from "react-i18next";

const DIFFICULTY_ORDER: Record<string, number> = {
  basic: 1,
  advanced: 2,
  expert: 3,
  master: 4,
  remaster: 5,
  re_master: 5,
  utage: 6,
};

type RatingThreshold = {
  rank: string;
  threshold: number;
};

type RatingBreakpoint = {
  achievement: number;
  coefficient: number;
};

const RATING_THRESHOLDS: RatingThreshold[] = [
  { rank: "C", threshold: 50.0 },
  { rank: "B", threshold: 60.0 },
  { rank: "BB", threshold: 70.0 },
  { rank: "BBB", threshold: 75.0 },
  { rank: "A", threshold: 80.0 },
  { rank: "AA", threshold: 90.0 },
  { rank: "AAA", threshold: 94.0 },
  { rank: "S", threshold: 97.0 },
  { rank: "S+", threshold: 98.0 },
  { rank: "SS", threshold: 99.0 },
  { rank: "SS+", threshold: 99.5 },
  { rank: "SSS", threshold: 100.0 },
  { rank: "SSS+", threshold: 100.5 },
];

const RATING_BREAKPOINTS: RatingBreakpoint[] = [
  { achievement: 0.0, coefficient: 0.0 },
  { achievement: 10.0, coefficient: 1.6 },
  { achievement: 20.0, coefficient: 3.2 },
  { achievement: 30.0, coefficient: 4.8 },
  { achievement: 40.0, coefficient: 6.4 },
  { achievement: 50.0, coefficient: 8.0 },
  { achievement: 60.0, coefficient: 9.6 },
  { achievement: 70.0, coefficient: 11.2 },
  { achievement: 75.0, coefficient: 12.0 },
  { achievement: 79.9999, coefficient: 12.8 },
  { achievement: 80.0, coefficient: 13.6 },
  { achievement: 90.0, coefficient: 15.2 },
  { achievement: 94.0, coefficient: 16.8 },
  { achievement: 96.9999, coefficient: 17.6 },
  { achievement: 97.0, coefficient: 20.0 },
  { achievement: 98.0, coefficient: 20.3 },
  { achievement: 98.9999, coefficient: 20.6 },
  { achievement: 99.0, coefficient: 20.8 },
  { achievement: 99.5, coefficient: 21.1 },
  { achievement: 99.9999, coefficient: 21.4 },
  { achievement: 100.0, coefficient: 21.6 },
  { achievement: 100.4999, coefficient: 22.2 },
  { achievement: 100.5, coefficient: 22.4 },
];

function parseNumeric(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string") {
    const parsed = Number.parseFloat(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return null;
}

function getRatingCoefficient(achievement: number): number {
  for (let index = 0; index < RATING_BREAKPOINTS.length; index += 1) {
    const current = RATING_BREAKPOINTS[index]!;
    const next = index < RATING_BREAKPOINTS.length - 1 ? RATING_BREAKPOINTS[index + 1]! : null;
    if (!next || achievement < next.achievement) {
      return current.coefficient;
    }
  }
  return 0;
}

function calculateRating(internalLevel: number, achievement: number): number {
  if (!(internalLevel > 0) || !(achievement > 0)) {
    return 0;
  }
  const coefficient = getRatingCoefficient(achievement);
  const rating = internalLevel * (coefficient / 100) * Math.min(achievement, 100.5);
  return Math.floor(rating + 0.000001);
}

function rankColor(rank: string): string {
  if (rank === "SSS+" || rank === "SSS") return "#fbc02d";
  if (rank === "SS+" || rank === "SS") return "#ffb74d";
  if (rank === "S+" || rank === "S") return "#ff9800";
  if (rank === "AAA") return "#b39ddb";
  if (rank === "AA") return "#90caf9";
  if (rank === "A") return "#81c784";
  return "#90a4ae";
}

type SongDetailDialogProps = {
  song: Song | null;
  songAliases: Alias[];
  songSheets: Sheet[];
  songDetailChartTypes: string[];
  songDetailChartType: string;
  songDetailLoading: boolean;
  selectedSongRegionSummary: {
    jp: boolean;
    intl: boolean;
    cn: boolean;
  };
  buildCoverUrl: (imageName?: string | null) => string | null;
  formatVersionDisplay: (version?: string | null) => string;
  formatDateToYmd: (value?: string | null) => string | null;
  formatChartType: (value?: string | null) => string;
  formatDifficulty: (value?: string | null) => string;
  normalizeSheetType: (value?: string | null) => string;
  onClose: () => void;
  onChangeChartType: (value: string) => void;
};

export function SongDetailDialog({
  song,
  songAliases,
  songSheets,
  songDetailChartTypes,
  songDetailChartType,
  songDetailLoading,
  selectedSongRegionSummary,
  buildCoverUrl,
  formatVersionDisplay,
  formatDateToYmd,
  formatChartType,
  formatDifficulty,
  normalizeSheetType,
  onClose,
  onChangeChartType,
}: SongDetailDialogProps) {
  const { t } = useTranslation("tab");
  const normalizedCurrentType = normalizeSheetType(songDetailChartType);
  const filteredSongDetailSheets = normalizedCurrentType
    ? songSheets.filter((sheet) => normalizeSheetType(sheet.chartType) === normalizedCurrentType)
    : songSheets;
  const sortedSongDetailSheets = [...filteredSongDetailSheets].sort((left, right) => {
    const diffLeft = DIFFICULTY_ORDER[left.difficulty.trim().toLowerCase()] ?? 999;
    const diffRight = DIFFICULTY_ORDER[right.difficulty.trim().toLowerCase()] ?? 999;
    if (diffLeft !== diffRight) {
      return diffLeft - diffRight;
    }
    return (left.level ?? "").localeCompare(right.level ?? "");
  });

  const [ratingSheetId, setRatingSheetId] = useState("");

  useEffect(() => {
    if (sortedSongDetailSheets.length === 0) {
      setRatingSheetId("");
      return;
    }
    if (sortedSongDetailSheets.some((sheet) => sheet.id === ratingSheetId)) {
      return;
    }
    const fallback = sortedSongDetailSheets.at(-1) ?? sortedSongDetailSheets[0];
    setRatingSheetId(fallback?.id ?? "");
  }, [ratingSheetId, sortedSongDetailSheets]);

  const selectedRatingSheet = useMemo(
    () => sortedSongDetailSheets.find((sheet) => sheet.id === ratingSheetId) ?? sortedSongDetailSheets.at(-1) ?? null,
    [ratingSheetId, sortedSongDetailSheets],
  );

  const selectedRatingLevel = useMemo(() => {
    if (!selectedRatingSheet) {
      return 0;
    }
    return (
      parseNumeric(selectedRatingSheet.internalLevelValue) ??
      parseNumeric(selectedRatingSheet.levelValue) ??
      parseNumeric(selectedRatingSheet.internalLevel) ??
      0
    );
  }, [selectedRatingSheet]);

  const ratingRows = useMemo(() => {
    if (!(selectedRatingLevel > 0)) {
      return [];
    }
    const rows = [...RATING_THRESHOLDS]
      .map((item) => ({
        rank: item.rank,
        threshold: item.threshold,
        rating: calculateRating(selectedRatingLevel, item.threshold),
      }))
      .sort((left, right) => right.threshold - left.threshold);

    return rows.map((item, index) => {
      const nextRating = index < rows.length - 1 ? rows[index + 1]!.rating : 0;
      const delta = index < rows.length - 1 ? item.rating - nextRating : 0;
      return { ...item, delta };
    });
  }, [selectedRatingLevel]);

  const detailSheetsPagination = useTablePagination(sortedSongDetailSheets);
  const ratingRowsPagination = useTablePagination(ratingRows);

  const open = Boolean(song);

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !nextOpen && onClose()}>
      <DialogContent className="max-h-[90vh] sm:max-w-6xl lg:max-w-7xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("detailTitle")}</DialogTitle>
        </DialogHeader>

        {song ? (
          <div className="flex flex-col gap-5">
            <section className="flex flex-col gap-3 md:flex-row">
              <Avatar className="size-32 rounded-md">
                <AvatarImage src={buildCoverUrl(song.imageName) ?? undefined} />
                <AvatarFallback>{song.title.slice(0, 1).toUpperCase()}</AvatarFallback>
              </Avatar>
              <div className="flex min-w-0 flex-1 flex-col gap-2">
                <p className="text-xl font-semibold">{song.title}</p>
                <p className="text-sm text-muted-foreground">{song.artist}</p>
                <div className="flex flex-wrap gap-2">
                  <Badge variant="secondary">{song.category ?? t("detailCatUnknown")}</Badge>
                  {song.bpm ? <Badge variant="secondary">BPM {Math.trunc(song.bpm)}</Badge> : null}
                  {song.version ? <Badge variant="secondary">{formatVersionDisplay(song.version)}</Badge> : null}
                  {song.releaseDate ? <Badge variant="secondary">{formatDateToYmd(song.releaseDate)}</Badge> : null}
                  <Badge>{song.isLocked ? t("detailLockLocked") : t("detailLockUnlocked")}</Badge>
                  {song.isNew ? <Badge>NEW</Badge> : null}
                </div>
                {song.searchKeywords ? <p className="text-sm text-muted-foreground">{t("detailKeywordPrefix")}{song.searchKeywords}</p> : null}
                {song.comment ? <p className="text-sm text-muted-foreground">{t("detailCommentPrefix")}{song.comment}</p> : null}
                <div className="flex flex-wrap gap-2">
                  <Badge variant="secondary">🇯🇵 JP {selectedSongRegionSummary.jp ? t("detailPlayable") : t("detailUnplayable")}</Badge>
                  <Badge variant="secondary">🌏 INTL {selectedSongRegionSummary.intl ? t("detailPlayable") : t("detailUnplayable")}</Badge>
                  <Badge variant="secondary">🇨🇳 CN {selectedSongRegionSummary.cn ? t("detailPlayable") : t("detailUnplayable")}</Badge>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button asChild className="bg-red-600 text-white hover:bg-red-500 focus-visible:ring-red-500/40">
                    <a
                      href={`https://www.youtube.com/results?search_query=${encodeURIComponent(`maimai ${song.title}`)}`}
                      target="_blank"
                      rel="noreferrer"
                    >
                      <PlayIcon data-icon="inline-start" />
                      YouTube
                    </a>
                  </Button>
                  <Button asChild className="bg-blue-600 text-white hover:bg-blue-500 focus-visible:ring-blue-500/40">
                    <a
                      href={`https://search.bilibili.com/all?keyword=${encodeURIComponent(`maimai ${song.title}`)}`}
                      target="_blank"
                      rel="noreferrer"
                    >
                      <TvIcon data-icon="inline-start" />
                      Bilibili
                    </a>
                  </Button>
                </div>
              </div>
            </section>

            <Separator />

            <section className="rounded-lg border p-4">
              <h3 className="mb-2 text-sm font-medium">{t("detailAliasTitle")}</h3>
              <div className="flex flex-wrap gap-2">
                {songAliases.length > 0 ? (
                  songAliases.map((alias) => {
                    const source = alias.source.trim().toLowerCase();
                    const isCommunity = source.includes("community");
                    return (
                      <Badge key={alias.id} variant="secondary" className={isCommunity ? "border border-dashed" : undefined}>
                        {alias.aliasText}
                      </Badge>
                    );
                  })
                ) : (
                  <span className="text-sm text-muted-foreground">{t("detailAliasEmpty")}</span>
                )}
              </div>
            </section>

            <section className="rounded-lg border p-4">
              <div className="mb-3 flex flex-wrap items-center gap-2">
                <h3 className="text-sm font-medium">{t("detailChartTitle")}</h3>
                {songDetailChartTypes.length > 1 ? (
                  <ToggleGroup
                    type="single"
                    variant="outline"
                    value={songDetailChartType}
                    onValueChange={(value) => {
                      if (value) {
                        onChangeChartType(value);
                      }
                    }}
                  >
                    {songDetailChartTypes.map((type) => (
                      <ToggleGroupItem key={type} value={type}>
                        {formatChartType(type)}
                      </ToggleGroupItem>
                    ))}
                  </ToggleGroup>
                ) : null}
              </div>

              {songDetailLoading ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2Icon className="size-4 animate-spin" />
                  {t("detailChartLoading")}
                </div>
              ) : sortedSongDetailSheets.length === 0 ? (
                <Empty>
                  <EmptyHeader>
                    <EmptyTitle>{t("detailChartEmpty")}</EmptyTitle>
                  </EmptyHeader>
                </Empty>
              ) : (
                <div className="flex flex-col gap-3">
                  <div className="space-y-3 md:hidden">
                    {detailSheetsPagination.pagedItems.map((sheet) => (
                      <article key={sheet.id} className="rounded-lg border p-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge>{formatChartType(sheet.chartType)}</Badge>
                          <Badge variant="secondary">{formatDifficulty(sheet.difficulty)}</Badge>
                          <Badge variant="secondary">{t("detailColLevel")} {sheet.level}</Badge>
                          <Badge variant="secondary">{t("detailColInternal")} {sheet.internalLevel ?? "-"}</Badge>
                          {sheet.isSpecial ? <Badge variant="secondary">{t("detailBadgeSpecial")}</Badge> : null}
                        </div>
                        <p className="mt-2 text-xs text-muted-foreground">{t("detailColDesigner")}：{sheet.noteDesigner ?? "-"}</p>
                        <div className="mt-2 grid grid-cols-2 gap-2 text-xs text-muted-foreground">
                          <span className="rounded-md border px-2 py-1">TAP {sheet.tap ?? "-"}</span>
                          <span className="rounded-md border px-2 py-1">HOLD {sheet.hold ?? "-"}</span>
                          <span className="rounded-md border px-2 py-1">SLIDE {sheet.slide ?? "-"}</span>
                          <span className="rounded-md border px-2 py-1">TOUCH {sheet.touch ?? "-"}</span>
                          <span className="rounded-md border px-2 py-1">BREAK {sheet.breakCount ?? "-"}</span>
                          <span className="rounded-md border px-2 py-1">TOTAL {sheet.total ?? "-"}</span>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-1">
                          <Badge variant="secondary">🇯🇵 {sheet.regionJp ? "✓" : "-"}</Badge>
                          <Badge variant="secondary">🌏 {sheet.regionIntl ? "✓" : "-"}</Badge>
                          <Badge variant="secondary">🇨🇳 {sheet.regionCn ? "✓" : "-"}</Badge>
                        </div>
                      </article>
                    ))}
                  </div>

                  <div className="hidden md:block">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>{t("detailColType")}</TableHead>
                          <TableHead>{t("detailColDiff")}</TableHead>
                          <TableHead>{t("detailColLevel")}</TableHead>
                          <TableHead>{t("detailColInternal")}</TableHead>
                          <TableHead>{t("detailColDesigner")}</TableHead>
                          <TableHead>TAP</TableHead>
                          <TableHead>HOLD</TableHead>
                          <TableHead>SLIDE</TableHead>
                          <TableHead>TOUCH</TableHead>
                          <TableHead>BREAK</TableHead>
                          <TableHead>TOTAL</TableHead>
                          <TableHead>{t("detailColRegion")}</TableHead>
                          <TableHead>{t("detailColSpecial")}</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {detailSheetsPagination.pagedItems.map((sheet) => (
                          <TableRow key={sheet.id}>
                            <TableCell>{formatChartType(sheet.chartType)}</TableCell>
                            <TableCell>{formatDifficulty(sheet.difficulty)}</TableCell>
                            <TableCell>{sheet.level}</TableCell>
                            <TableCell>{sheet.internalLevel ?? "-"}</TableCell>
                            <TableCell>{sheet.noteDesigner ?? "-"}</TableCell>
                            <TableCell>{sheet.tap ?? "-"}</TableCell>
                            <TableCell>{sheet.hold ?? "-"}</TableCell>
                            <TableCell>{sheet.slide ?? "-"}</TableCell>
                            <TableCell>{sheet.touch ?? "-"}</TableCell>
                            <TableCell>{sheet.breakCount ?? "-"}</TableCell>
                            <TableCell>{sheet.total ?? "-"}</TableCell>
                            <TableCell>
                              <div className="flex flex-wrap gap-1">
                                <Badge variant="secondary">🇯🇵 {sheet.regionJp ? "✓" : "-"}</Badge>
                                <Badge variant="secondary">🌏 {sheet.regionIntl ? "✓" : "-"}</Badge>
                                <Badge variant="secondary">🇨🇳 {sheet.regionCn ? "✓" : "-"}</Badge>
                              </div>
                            </TableCell>
                            <TableCell>{sheet.isSpecial ? t("detailIsSpecialYes") : "-"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>

                  <TablePagination
                    page={detailSheetsPagination.page}
                    pageCount={detailSheetsPagination.pageCount}
                    pageSize={detailSheetsPagination.pageSize}
                    onPageChange={detailSheetsPagination.setPage}
                    onPageSizeChange={detailSheetsPagination.setPageSize}
                  />
                </div>
              )}
            </section>

            <section className="rounded-lg border p-4">
              <div className="mb-3 flex flex-wrap items-center gap-2">
                <h3 className="text-sm font-medium">{t("detailRatingTitle")}</h3>
                {sortedSongDetailSheets.length > 1 ? (
                  <Select value={ratingSheetId} onValueChange={setRatingSheetId}>
                    <SelectTrigger className="w-full sm:w-[380px]">
                      <SelectValue placeholder={t("detailRatingPlaceholder")} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {sortedSongDetailSheets.map((sheet) => (
                          <SelectItem key={sheet.id} value={sheet.id}>
                            {formatChartType(sheet.chartType)} {formatDifficulty(sheet.difficulty)} {sheet.level}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                ) : null}
              </div>

              {selectedRatingSheet && ratingRows.length > 0 ? (
                <div className="flex flex-col gap-3">
                  <div className="space-y-3 md:hidden">
                    {ratingRowsPagination.pagedItems.map((row) => (
                      <article key={`rating-row-mobile-${row.rank}`} className="rounded-lg border p-3">
                        <div className="flex items-center justify-between gap-3">
                          <p className="text-sm">
                            <span style={{ color: rankColor(row.rank) }} className="font-semibold">
                              {row.rank}
                            </span>{" "}
                            {row.threshold.toFixed(4)}%
                          </p>
                          <span className="rounded-md border px-2 py-1 text-xs text-muted-foreground">
                            {row.delta > 0 ? `↑ ${row.delta}` : "—"}
                          </span>
                        </div>
                        <p className="mt-2 text-sm font-medium">{t("detailRatingColRating")}：{row.rating}</p>
                      </article>
                    ))}
                  </div>

                  <div className="hidden md:block">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>{t("detailRatingColAchieve")}</TableHead>
                          <TableHead>{t("detailRatingColRating")}</TableHead>
                          <TableHead>{t("detailRatingColDelta")}</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {ratingRowsPagination.pagedItems.map((row) => (
                          <TableRow key={`rating-row-${row.rank}`}>
                            <TableCell>
                              <span style={{ color: rankColor(row.rank) }} className="font-semibold">
                                {row.rank}
                              </span>{" "}
                              {row.threshold.toFixed(4)}%
                            </TableCell>
                            <TableCell>{row.rating}</TableCell>
                            <TableCell>{row.delta > 0 ? `↑ ${row.delta}` : "-"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>

                  <TablePagination
                    page={ratingRowsPagination.page}
                    pageCount={ratingRowsPagination.pageCount}
                    pageSize={ratingRowsPagination.pageSize}
                    onPageChange={ratingRowsPagination.setPage}
                    onPageSizeChange={ratingRowsPagination.setPageSize}
                  />
                </div>
              ) : (
                <span className="text-sm text-muted-foreground">{t("detailRatingEmpty")}</span>
              )}
            </section>
          </div>
        ) : null}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t("detailBtnClose")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
