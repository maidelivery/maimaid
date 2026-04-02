import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Empty, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
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
};

type StaticBundleSchedule = {
  enabled: boolean;
  intervalHours: number;
  cronExpression: string;
};

type AdminStaticPageProps = {
  staticSources: StaticSource[];
  staticBundles: StaticBundle[];
  staticBundleSchedule: StaticBundleSchedule | null;
  onBuildBundle: () => Promise<boolean>;
  onUpdateBundleSchedule: (input: { enabled: boolean; intervalHours: number }) => void | Promise<void>;
  onReloadStatic: () => void | Promise<void>;
  onToggleSource: (source: StaticSource) => void | Promise<void>;
  onEditSourceUrl: (source: StaticSource, nextUrl: string, nextExtraUrl?: string) => void | Promise<void>;
};

export function AdminStaticPage({
  staticSources,
  staticBundles,
  staticBundleSchedule,
  onBuildBundle,
  onUpdateBundleSchedule,
  onReloadStatic,
  onToggleSource,
  onEditSourceUrl,
}: AdminStaticPageProps) {
  const { t } = useTranslation("adminStatic");
  const sourcesPagination = useTablePagination(staticSources);
  const bundlesPagination = useTablePagination(staticBundles);
  const [buildState, setBuildState] = useState<"idle" | "running" | "succeeded" | "failed">("idle");
  const [buildProgress, setBuildProgress] = useState(0);
  const [editingSource, setEditingSource] = useState<StaticSource | null>(null);
  const [editingSourceUrl, setEditingSourceUrl] = useState("");
  const [editingSourceExtraUrl, setEditingSourceExtraUrl] = useState("");
  const [scheduleEnabledDraft, setScheduleEnabledDraft] = useState(staticBundleSchedule?.enabled ?? true);
  const [scheduleIntervalDraft, setScheduleIntervalDraft] = useState(String(staticBundleSchedule?.intervalHours ?? 6));
  const { confirm, confirmDialogNode } = useConfirmDialog();

  useEffect(() => {
    if (!staticBundleSchedule) {
      return;
    }
    setScheduleEnabledDraft(staticBundleSchedule.enabled);
    setScheduleIntervalDraft(String(staticBundleSchedule.intervalHours));
  }, [staticBundleSchedule]);

  const normalizedScheduleInterval = Math.trunc(Number(scheduleIntervalDraft));
  const scheduleIntervalValid = Number.isFinite(normalizedScheduleInterval)
    && normalizedScheduleInterval >= 1;
  const scheduleReady = staticBundleSchedule !== null;
  const scheduleDirty = staticBundleSchedule
    ? scheduleEnabledDraft !== staticBundleSchedule.enabled
      || normalizedScheduleInterval !== staticBundleSchedule.intervalHours
    : true;
  const buildRunning = buildState === "running";

  useEffect(() => {
    if (!buildRunning) {
      return;
    }
    const timer = window.setInterval(() => {
      setBuildProgress((previous) => {
        const next = previous < 70
          ? previous + 6
          : previous < 85
            ? previous + 3
            : previous < 93
              ? previous + 1.2
              : previous + 0.4;
        return Math.min(95, next);
      });
    }, 400);

    return () => {
      window.clearInterval(timer);
    };
  }, [buildRunning]);

  const handleBuildBundle = async () => {
    if (buildRunning) {
      return;
    }
    const confirmed = await confirm({
      title: t("btnForceBuild"),
      description: t("descForceBuild"),
      confirmText: t("confirmBuild"),
    });
    if (!confirmed) {
      return;
    }
    setBuildState("running");
    setBuildProgress(8);

    const success = await onBuildBundle();
    setBuildProgress(100);
    setBuildState(success ? "succeeded" : "failed");
  };

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

  const handleUpdateBundleSchedule = async () => {
    if (!scheduleIntervalValid) {
      return;
    }
    const confirmed = await confirm({
      title: t("updateScheduleTitle"),
      description: scheduleEnabledDraft
        ? t("enableScheduleDesc", { intervalHours: normalizedScheduleInterval })
        : t("disableScheduleDesc"),
      confirmText: t("confirmUpdate"),
      tone: scheduleEnabledDraft ? "default" : "destructive",
    });
    if (!confirmed) {
      return;
    }
    await onUpdateBundleSchedule({
      enabled: scheduleEnabledDraft,
      intervalHours: normalizedScheduleInterval,
    });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("pageTitle")}</CardTitle>
        <CardDescription>{t("pageDesc")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <div className="flex flex-wrap gap-2">
          <Button className="h-9 w-full sm:w-auto" onClick={() => void handleBuildBundle()} disabled={buildRunning}>
            {buildRunning ? t("btnBuilding") : t("btnForceBuild")}
          </Button>
          <Button className="h-9 w-full sm:w-auto" variant="outline" onClick={() => void onReloadStatic()} disabled={buildRunning}>
            <RefreshCwIcon data-icon="inline-start" />
            {t("btnRefresh")}
          </Button>
        </div>

        {buildState !== "idle" ? (
          <section className="flex flex-col gap-2 rounded-lg border p-3">
            <div className="flex items-center justify-between">
              <p className="text-xs font-medium">{t("buildProgressTitle")}</p>
              <p className="text-xs tabular-nums text-muted-foreground">
                {t("buildProgressPercent", { value: Math.round(buildProgress) })}
              </p>
            </div>
            <div
              className="h-2 w-full overflow-hidden rounded-full bg-muted"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={Math.round(buildProgress)}
            >
              <div
                className={`h-full transition-[width] duration-300 ${buildState === "failed" ? "bg-destructive" : "bg-primary"}`}
                style={{ width: `${Math.max(0, Math.min(100, buildProgress))}%` }}
              />
            </div>
            <p className={`text-xs ${buildState === "failed" ? "text-destructive" : "text-muted-foreground"}`}>
              {buildState === "running"
                ? t("buildProgressRunning")
                : buildState === "succeeded"
                  ? t("buildProgressSuccess")
                  : t("buildProgressFailed")}
            </p>
          </section>
        ) : null}

        <section className="flex flex-col gap-3 rounded-lg border p-3">
          <h3 className="text-sm font-medium">{t("scheduleSectionTitle")}</h3>
          <p className="text-xs text-muted-foreground">
            {t("scheduleSectionDesc")}
          </p>
          <div className="grid gap-2 md:grid-cols-[minmax(0,1fr)_176px_auto] md:items-end">
            <div className="flex h-9 items-center justify-between rounded-md border px-3">
              <span className="text-sm">{t("enableAutoBuild")}</span>
              <Switch checked={scheduleEnabledDraft} onCheckedChange={setScheduleEnabledDraft} disabled={!scheduleReady} />
            </div>
            <Field>
              <FieldLabel htmlFor="static-bundle-interval-hours">{t("intervalHours")}</FieldLabel>
              <Input
                id="static-bundle-interval-hours"
                type="number"
                min={1}
                step={1}
                value={scheduleIntervalDraft}
                onChange={(event) => setScheduleIntervalDraft(event.target.value)}
                disabled={!scheduleReady}
              />
            </Field>
            <Button
              className="h-9 w-full md:w-auto"
              onClick={() => void handleUpdateBundleSchedule()}
              disabled={!scheduleReady || !scheduleDirty || !scheduleIntervalValid}
            >
              {t("saveSchedule")}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            {t("currentConfig")}
            {staticBundleSchedule
              ? `${staticBundleSchedule.enabled ? t("enabled") : t("disabled")}${t("scheduleTemplate", { intervalHours: staticBundleSchedule.intervalHours, cronExpression: staticBundleSchedule.cronExpression })}`
              : t("loading")}
          </p>
        </section>

        <section className="flex flex-col gap-3">
          <h3 className="text-sm font-medium">{t("sourcesSectionTitle")}</h3>
          {staticSources.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>{t("noSources")}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <div className="flex flex-col gap-3">
              <div className="space-y-3 md:hidden">
                {sourcesPagination.pagedItems.map((source) => (
                  <article key={source.id} className="rounded-lg border p-3">
                    <div className="flex items-start justify-between gap-2">
                       <p className="text-sm font-medium">{source.category}</p>
                      <span className="rounded-md border px-2 py-1 text-xs text-muted-foreground">
                        {source.enabled ? t("statusEnabled") : t("statusDisabled")}
                      </span>
                    </div>
                    <p className="mt-2 break-all text-xs text-muted-foreground">{source.activeUrl}</p>
                    <div className="mt-3 grid grid-cols-2 gap-2">
                      <Button
                        variant={source.enabled ? "destructive" : "outline"}
                        className="h-9 w-full"
                        onClick={() => void handleToggleSource(source)}
                      >
                        {source.enabled ? t("actionDisable") : t("actionEnable")}
                      </Button>
                      <Button
                        variant="outline"
                        className="h-9 w-full"
                        onClick={() => openEditSourceDialog(source)}
                      >
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
        </section>

        <section className="flex flex-col gap-3">
          <h3 className="text-sm font-medium">{t("bundlesSectionTitle")}</h3>
          {staticBundles.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>{t("noBundles")}</EmptyTitle>
              </EmptyHeader>
            </Empty>
          ) : (
            <div className="flex flex-col gap-3">
              <div className="space-y-3 md:hidden">
                {bundlesPagination.pagedItems.map((bundle) => (
                  <article key={bundle.id} className="rounded-lg border p-3">
                    <p className="text-sm font-medium">{bundle.version}</p>
                    <p className="mt-2 break-all text-xs text-muted-foreground">MD5：{bundle.md5}</p>
                    <div className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
                       <span className="rounded-md border px-2 py-1">{bundle.active ? t("statusActive") : t("statusInactive")}</span>
                      <span className="rounded-md border px-2 py-1">{new Date(bundle.createdAt).toLocaleString()}</span>
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
                        <TableCell>{bundle.version}</TableCell>
                        <TableCell className="max-w-[260px] truncate">{bundle.md5}</TableCell>
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
        </section>

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
      </CardContent>
    </Card>
  );
}
