import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { TablePagination } from "@/components/ui/table-pagination";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useTablePagination } from "@/lib/use-table-pagination";
import { StarIcon } from "lucide-react";
import type { Song } from "./types";
import { useTranslation } from "react-i18next";

type SongsTableProps = {
	songs: Song[];
	favorites: Set<string>;
	buildCoverUrl: (imageName?: string | null) => string | null;
	formatVersionDisplay: (version?: string | null) => string;
	onToggleFavorite: (songIdentifier: string) => void;
	onSelectSong: (song: Song) => void | Promise<void>;
};

export function SongsTable({
	songs,
	favorites,
	buildCoverUrl,
	formatVersionDisplay,
	onToggleFavorite,
	onSelectSong,
}: SongsTableProps) {
	const { t } = useTranslation("tab");
	const shouldScrollTitle = (title: string) => title.trim().length > 24;
	const pagination = useTablePagination(songs);

	return (
		<div className="flex flex-col gap-3">
			<div className="space-y-2 md:hidden">
				{pagination.pagedItems.map((song) => {
					const isFavorite = favorites.has(song.songIdentifier);
					return (
						<article key={song.songIdentifier} className="rounded-lg border p-3">
							<div className="flex items-start gap-3">
								<Avatar className="size-12 rounded-md">
									<AvatarImage src={buildCoverUrl(song.imageName) ?? undefined} />
									<AvatarFallback>{song.title.slice(0, 1).toUpperCase()}</AvatarFallback>
								</Avatar>
								<div className="min-w-0 flex-1">
									<p className="truncate text-sm font-medium">{song.title}</p>
									<p className="truncate text-xs text-muted-foreground">{song.artist || "-"}</p>
									<p className="mt-1 text-xs text-muted-foreground">
										{t("tableColVersion")}：{formatVersionDisplay(song.version)}
									</p>
								</div>
								<Button
									variant="ghost"
									size="icon-lg"
									onClick={() => {
										onToggleFavorite(song.songIdentifier);
									}}
									aria-label={isFavorite ? t("tableBtnUnfav") : t("tableBtnFav")}
								>
									<StarIcon className={isFavorite ? "fill-amber-400 text-amber-400" : "text-muted-foreground"} />
								</Button>
							</div>
							<Button
								variant="outline"
								className="mt-3 h-9 w-full"
								onClick={() => {
									void onSelectSong(song);
								}}
							>
								{t("tableBtnDetail")}
							</Button>
						</article>
					);
				})}
			</div>

			<div className="hidden md:block">
				<Table className="table-fixed">
					<TableHeader>
						<TableRow>
							<TableHead className="w-[80px]">{t("tableColFav")}</TableHead>
							<TableHead>{t("tableColSong")}</TableHead>
							<TableHead className="hidden md:table-cell md:w-[320px]">{t("tableColArtist")}</TableHead>
							<TableHead className="hidden sm:table-cell sm:w-[220px]">{t("tableColVersion")}</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{pagination.pagedItems.map((song) => {
							const isFavorite = favorites.has(song.songIdentifier);
							return (
								<TableRow
									key={song.songIdentifier}
									className="cursor-pointer"
									onClick={() => {
										void onSelectSong(song);
									}}
								>
									<TableCell>
										<Button
											variant="ghost"
											size="icon-sm"
											onClick={(event) => {
												event.stopPropagation();
												onToggleFavorite(song.songIdentifier);
											}}
										>
											<StarIcon className={isFavorite ? "fill-amber-400 text-amber-400" : "text-muted-foreground"} />
										</Button>
									</TableCell>
									<TableCell>
										<div className="flex min-w-0 max-w-full items-center gap-2">
											<Avatar className="size-10 rounded-md">
												<AvatarImage src={buildCoverUrl(song.imageName) ?? undefined} />
												<AvatarFallback>{song.title.slice(0, 1).toUpperCase()}</AvatarFallback>
											</Avatar>
											<div className="min-w-0 w-full max-w-[34rem] xl:max-w-[42rem]">
												{shouldScrollTitle(song.title) ? (
													<div className="song-title-marquee text-sm font-medium" title={song.title}>
														<span className="song-title-marquee-track">
															<span className="song-title-marquee-text">{song.title}</span>
															<span className="song-title-marquee-text" aria-hidden="true">
																{song.title}
															</span>
														</span>
													</div>
												) : (
													<p className="truncate text-sm font-medium">{song.title}</p>
												)}
												<p className="truncate text-xs text-muted-foreground md:hidden">{song.artist}</p>
											</div>
										</div>
									</TableCell>
									<TableCell className="hidden max-w-[320px] truncate md:table-cell">{song.artist}</TableCell>
									<TableCell className="hidden max-w-[220px] truncate sm:table-cell">
										{formatVersionDisplay(song.version)}
									</TableCell>
								</TableRow>
							);
						})}
					</TableBody>
				</Table>
			</div>

			<TablePagination
				page={pagination.page}
				pageCount={pagination.pageCount}
				pageSize={pagination.pageSize}
				onPageChange={pagination.setPage}
				onPageSizeChange={pagination.setPageSize}
			/>
		</div>
	);
}
