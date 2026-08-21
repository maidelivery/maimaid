import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Empty, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { TablePagination } from "@/components/ui/table-pagination";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useConfirmDialog } from "@/hooks/use-confirm-dialog";
import { useTablePagination } from "@/lib/use-table-pagination";
import { RefreshCwIcon } from "lucide-react";
import { useTranslation } from "react-i18next";

type StaticSource = {
	id: string;
	category: string;
	activeUrl: string;
	fallbackUrls: string[];
	enabled: boolean;
};

type StaticBundle = {
	id: string;
	version: string;
	md5: string;
	active: boolean;
	createdAt: string;
	manifestUrl?: string | null;
	bundleUrl?: string | null;
};

type AdminStaticPageProps = {
	staticSources: StaticSource[];
	staticBundles: StaticBundle[];
	onReloadStatic: () => void | Promise<void>;
	onToggleSource: (source: StaticSource) => void | Promise<void>;
	onEditSourceUrl: (source: StaticSource, nextUrl: string, nextExtraUrl?: string) => void | Promise<void>;
};

export function AdminStaticPage({
	staticSources,
	staticBundles,
	onReloadStatic,
	onToggleSource,
	onEditSourceUrl,
}: AdminStaticPageProps) {
	const { t } = useTranslation("adminStatic");
	const sourcesPagination = useTablePagination(staticSources);
	const bundlesPagination = useTablePagination(staticBundles);
	const [editingSource, setEditingSource] = useState<StaticSource | null>(null);
	const [editingSourceUrl, setEditingSourceUrl] = useState("");
	const [editingSourceExtraUrl, setEditingSourceExtraUrl] = useState("");
	const { confirm, confirmDialogNode } = useConfirmDialog();

	const handleToggleSource = async (source: StaticSource) => {
		const enableAction = source.enabled ? t("actionDisable") : t("actionEnable");
		const confirmed = await confirm({
			title: t("toggleSourceTitle", { action: enableAction }),
			description: t("toggleSourceDesc", { action: enableAction, category: source.category }),
			confirmText: t("confirmToggle", { action: enableAction }),
			tone: source.enabled ? "destructive" : "default",
		});
		if (!confirmed) {
			return;
		}
		await onToggleSource(source);
	};

	const openEditSourceDialog = (source: StaticSource) => {
		setEditingSource(source);
		setEditingSourceUrl(source.activeUrl);
		setEditingSourceExtraUrl(source.fallbackUrls[0] ?? "");
	};

	const handleSubmitEditSourceUrl = async () => {
		if (!editingSource) {
			return;
		}
		const normalizedUrl = editingSourceUrl.trim();
		if (!normalizedUrl) {
			return;
		}
		const confirmed = await confirm({
			title: t("updateSourceUrlTitle"),
			description: t("updateSourceUrlDesc", { category: editingSource.category }),
			confirmText: t("confirmUpdate"),
		});
		if (!confirmed) {
			return;
		}
		await onEditSourceUrl(editingSource, normalizedUrl, editingSourceExtraUrl.trim());
		setEditingSource(null);
	};

	return (
		<div className="flex min-w-0 flex-col gap-4">
			<div className="flex flex-wrap gap-2">
				<Button className="h-9 w-full sm:w-auto" variant="outline" onClick={() => void onReloadStatic()}>
					<RefreshCwIcon data-icon="inline-start" />
					{t("btnRefresh")}
				</Button>
			</div>

			<Card size="sm">
				<CardHeader>
					<CardTitle>{t("sourcesSectionTitle")}</CardTitle>
				</CardHeader>
				<CardContent>
					{staticSources.length === 0 ? (
						<Empty>
							<EmptyHeader>
								<EmptyTitle>{t("noSources")}</EmptyTitle>
							</EmptyHeader>
						</Empty>
					) : (
						<div className="flex flex-col gap-3">
							<div className="-mt-1 divide-y divide-border/60 md:hidden">
								{sourcesPagination.pagedItems.map((source) => (
									<article key={source.id} className="py-3">
										<div className="flex items-start justify-between gap-2">
											<p className="text-sm font-medium">{source.category}</p>
											<span className="text-xs text-muted-foreground">
												{source.enabled ? t("statusEnabled") : t("statusDisabled")}
											</span>
										</div>
										<p className="mt-2 break-all text-xs text-muted-foreground">{source.activeUrl}</p>
										<div className="mt-3 grid grid-cols-2 gap-2">
											<Button variant="outline" className="h-9 w-full" onClick={() => void handleToggleSource(source)}>
												{source.enabled ? t("actionDisable") : t("actionEnable")}
											</Button>
											<Button variant="outline" className="h-9 w-full" onClick={() => openEditSourceDialog(source)}>
												{t("btnEditUrl")}
											</Button>
										</div>
									</article>
								))}
							</div>

							<div className="hidden md:block">
								<Table>
									<TableHeader>
										<TableRow>
											<TableHead>{t("colCategory")}</TableHead>
											<TableHead>{t("colActiveUrl")}</TableHead>
											<TableHead>{t("colEnabled")}</TableHead>
											<TableHead>{t("colAction")}</TableHead>
										</TableRow>
									</TableHeader>
									<TableBody>
										{sourcesPagination.pagedItems.map((source) => (
											<TableRow key={source.id}>
												<TableCell>{source.category}</TableCell>
												<TableCell className="max-w-[560px] truncate">{source.activeUrl}</TableCell>
												<TableCell>{source.enabled ? t("yes") : t("no")}</TableCell>
												<TableCell className="flex flex-wrap gap-2">
													<Button variant="outline" size="sm" onClick={() => void handleToggleSource(source)}>
														{source.enabled ? t("actionDisable") : t("actionEnable")}
													</Button>
													<Button variant="outline" size="sm" onClick={() => openEditSourceDialog(source)}>
														{t("btnEditUrl")}
													</Button>
												</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</div>

							<TablePagination
								page={sourcesPagination.page}
								pageCount={sourcesPagination.pageCount}
								pageSize={sourcesPagination.pageSize}
								onPageChange={sourcesPagination.setPage}
								onPageSizeChange={sourcesPagination.setPageSize}
							/>
						</div>
					)}
				</CardContent>
			</Card>

			<Card size="sm">
				<CardHeader>
					<CardTitle>{t("bundlesSectionTitle")}</CardTitle>
				</CardHeader>
				<CardContent>
					{staticBundles.length === 0 ? (
						<Empty>
							<EmptyHeader>
								<EmptyTitle>{t("noBundles")}</EmptyTitle>
							</EmptyHeader>
						</Empty>
					) : (
						<div className="flex flex-col gap-3">
							<div className="-mt-1 divide-y divide-border/60 md:hidden">
								{bundlesPagination.pagedItems.map((bundle) => (
									<article key={bundle.id} className="py-3">
										<p className="text-sm font-medium">
											{bundle.manifestUrl ? (
												<a
													className="underline-offset-4 hover:underline"
													href={bundle.manifestUrl}
													target="_blank"
													rel="noreferrer"
												>
													{bundle.version}
												</a>
											) : (
												bundle.version
											)}
										</p>
										<p className="mt-1 break-all text-xs text-muted-foreground">MD5：{bundle.md5}</p>
										<div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
											<span>{bundle.active ? t("statusActive") : t("statusInactive")}</span>
											<span>{new Date(bundle.createdAt).toLocaleString()}</span>
										</div>
									</article>
								))}
							</div>

							<div className="hidden md:block">
								<Table>
									<TableHeader>
										<TableRow>
											<TableHead>{t("colVersion")}</TableHead>
											<TableHead>{t("colMd5")}</TableHead>
											<TableHead>{t("colActive")}</TableHead>
											<TableHead>{t("colCreated")}</TableHead>
										</TableRow>
									</TableHeader>
									<TableBody>
										{bundlesPagination.pagedItems.map((bundle) => (
											<TableRow key={bundle.id}>
												<TableCell>
													{bundle.manifestUrl ? (
														<a
															className="underline-offset-4 hover:underline"
															href={bundle.manifestUrl}
															target="_blank"
															rel="noreferrer"
														>
															{bundle.version}
														</a>
													) : (
														bundle.version
													)}
												</TableCell>
												<TableCell className="max-w-[260px] truncate">
													{bundle.bundleUrl ? (
														<a
															className="underline-offset-4 hover:underline"
															href={bundle.bundleUrl}
															target="_blank"
															rel="noreferrer"
														>
															{bundle.md5}
														</a>
													) : (
														bundle.md5
													)}
												</TableCell>
												<TableCell>{bundle.active ? t("yes") : t("no")}</TableCell>
												<TableCell>{new Date(bundle.createdAt).toLocaleString()}</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</div>

							<TablePagination
								page={bundlesPagination.page}
								pageCount={bundlesPagination.pageCount}
								pageSize={bundlesPagination.pageSize}
								onPageChange={bundlesPagination.setPage}
								onPageSizeChange={bundlesPagination.setPageSize}
							/>
						</div>
					)}
				</CardContent>
			</Card>

			<Dialog open={Boolean(editingSource)} onOpenChange={(open) => !open && setEditingSource(null)}>
				<DialogContent className="sm:max-w-lg">
					<DialogHeader>
						<DialogTitle>{t("dialogEditUrlTitle")}</DialogTitle>
						<DialogDescription>
							{editingSource ? t("dialogEditUrlDesc", { category: editingSource.category }) : t("dialogEditUrlFallbackDesc")}
						</DialogDescription>
					</DialogHeader>
					<Field>
						<FieldLabel htmlFor="edit-static-source-url">{t("labelActiveUrl")}</FieldLabel>
						<Input
							id="edit-static-source-url"
							value={editingSourceUrl}
							onChange={(event) => setEditingSourceUrl(event.target.value)}
						/>
					</Field>
					{editingSource?.category === "chart_fit" ? (
						<Field>
							<FieldLabel htmlFor="edit-static-source-extra-url">{t("labelExtraUrl")}</FieldLabel>
							<Input
								id="edit-static-source-extra-url"
								value={editingSourceExtraUrl}
								onChange={(event) => setEditingSourceExtraUrl(event.target.value)}
								placeholder={t("labelExtraUrlPlaceholder")}
							/>
						</Field>
					) : null}
					<DialogFooter>
						<Button variant="outline" onClick={() => setEditingSource(null)}>
							{t("btnCancel")}
						</Button>
						<Button onClick={() => void handleSubmitEditSourceUrl()} disabled={!editingSourceUrl.trim()}>
							{t("btnUpdateUrl")}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			{confirmDialogNode}
		</div>
	);
}
