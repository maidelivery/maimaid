import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { ChevronLeftIcon, ChevronRightIcon, ChevronsLeftIcon, ChevronsRightIcon } from "lucide-react";
import { useTranslation } from "react-i18next";

const DEFAULT_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

type TablePaginationProps = {
	page: number;
	pageCount: number;
	pageSize: number;
	onPageChange: (page: number) => void;
	onPageSizeChange: (pageSize: number) => void;
	pageSizeOptions?: number[];
	className?: string;
};

export function TablePagination({
	page,
	pageCount,
	pageSize,
	onPageChange,
	onPageSizeChange,
	pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
	className,
}: TablePaginationProps) {
	const canGoPrev = page > 1;
	const canGoNext = page < pageCount;
	const normalizedOptions = pageSizeOptions.length > 0 ? pageSizeOptions : DEFAULT_PAGE_SIZE_OPTIONS;
	const { t } = useTranslation();

	return (
		<div
			className={cn(
				"mt-3 flex flex-col gap-3 rounded-lg border border-border/60 bg-card/40 px-3 py-2 md:flex-row md:items-center md:justify-between",
				className,
			)}
		>
			<div className="flex w-full flex-wrap items-center justify-between gap-3 md:w-auto md:flex-1 md:justify-start">
				<div className="flex items-center gap-2">
					<span className="text-sm font-medium text-muted-foreground">{t("app:paginationRowsPerPage")}</span>
					<Select
						value={String(pageSize)}
						onValueChange={(value) => {
							const parsed = Number.parseInt(value, 10);
							if (Number.isFinite(parsed)) {
								onPageSizeChange(parsed);
							}
						}}
					>
						<SelectTrigger className="h-9 w-[96px]">
							<SelectValue placeholder="10" />
						</SelectTrigger>
						<SelectContent>
							<SelectGroup>
								{normalizedOptions.map((option) => (
									<SelectItem key={option} value={String(option)}>
										{option}
									</SelectItem>
								))}
							</SelectGroup>
						</SelectContent>
					</Select>
				</div>

				<div className="text-sm font-medium md:ml-4">{t("app:paginationPageOf", { page, pageCount })}</div>
			</div>

			<div className="flex w-full items-center justify-center gap-2 md:w-auto md:justify-end">
				<Button
					variant="outline"
					size="icon-lg"
					disabled={!canGoPrev}
					onClick={() => onPageChange(1)}
					aria-label={t("app:paginationFirstPage")}
				>
					<ChevronsLeftIcon />
				</Button>
				<Button
					variant="outline"
					size="icon-lg"
					disabled={!canGoPrev}
					onClick={() => onPageChange(page - 1)}
					aria-label={t("app:paginationPrevPage")}
				>
					<ChevronLeftIcon />
				</Button>
				<Button
					variant="outline"
					size="icon-lg"
					disabled={!canGoNext}
					onClick={() => onPageChange(page + 1)}
					aria-label={t("app:paginationNextPage")}
				>
					<ChevronRightIcon />
				</Button>
				<Button
					variant="outline"
					size="icon-lg"
					disabled={!canGoNext}
					onClick={() => onPageChange(pageCount)}
					aria-label={t("app:paginationLastPage")}
				>
					<ChevronsRightIcon />
				</Button>
			</div>
		</div>
	);
}
