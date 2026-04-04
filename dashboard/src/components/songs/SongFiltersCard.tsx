import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { SongFilterSettings, SongSortOption } from "./types";
import { useTranslation } from "react-i18next";

type SongFiltersCardProps = {
  songKeyword: string;
  filteredSongCount: number;
  expanded: boolean;
  songFilters: SongFilterSettings;
  allCategories: string[];
  allVersions: string[];
  sortOption: SongSortOption;
  sortAscending: boolean;
  onExpandedChange: (expanded: boolean) => void;
  onSongKeywordChange: (value: string) => void;
  onShowFavoritesOnlyChange: (value: boolean) => void;
  onHideDeletedSongsChange: (value: boolean) => void;
  onToggleFilterSet: (
    key: "selectedCategories" | "selectedVersions" | "selectedDifficulties" | "selectedTypes",
    value: string,
  ) => void;
  onLevelRangeChange: (min: number, max: number) => void;
  onResetFilters: () => void;
  onSortOptionChange: (value: SongSortOption) => void;
  onSortAscendingChange: (value: boolean) => void;
  formatVersionDisplay: (value?: string | null) => string;
};

const difficultyOptions = [
  { value: "basic", label: "Basic" },
  { value: "advanced", label: "Advanced" },
  { value: "expert", label: "Expert" },
  { value: "master", label: "Master" },
  { value: "remaster", label: "Re: Master" },
] as const;

const chartTypeOptions = [
  { value: "dx", label: "DX" },
  { value: "std", label: "STD" },
  { value: "utage", label: "UTAGE" },
] as const;

function syncToggleSet(
  previous: Set<string>,
  nextValues: string[],
  key: "selectedDifficulties" | "selectedTypes",
  onToggleFilterSet: SongFiltersCardProps["onToggleFilterSet"],
) {
  for (const value of previous) {
    if (!nextValues.includes(value)) {
      onToggleFilterSet(key, value);
    }
  }
  for (const value of nextValues) {
    if (!previous.has(value)) {
      onToggleFilterSet(key, value);
    }
  }
}

export function SongFiltersCard({
  songKeyword,
  filteredSongCount,
  expanded,
  songFilters,
  allCategories,
  allVersions,
  sortOption,
  sortAscending,
  onExpandedChange,
  onSongKeywordChange,
  onShowFavoritesOnlyChange,
  onHideDeletedSongsChange,
  onToggleFilterSet,
  onLevelRangeChange,
  onResetFilters,
  onSortOptionChange,
  onSortAscendingChange,
  formatVersionDisplay,
}: SongFiltersCardProps) {
  const { t } = useTranslation("tab");
  const selectedDifficulties = Array.from(songFilters.selectedDifficulties);
  const selectedTypes = Array.from(songFilters.selectedTypes);

  return (
    <div className="flex flex-col gap-3">
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="song-search">{t("filterSearchLabel")}</FieldLabel>
          <Input id="song-search" value={songKeyword} onChange={(event) => onSongKeywordChange(event.target.value)} />
        </Field>
      </FieldGroup>
      <div className="text-sm text-muted-foreground">{t("filterCountPrefix")}{filteredSongCount}{t("filterCountSuffix")}</div>

      <Accordion
        type="single"
        collapsible
        value={expanded ? "filters" : ""}
        onValueChange={(value) => onExpandedChange(value === "filters")}
      >
        <AccordionItem value="filters" className="rounded-lg border px-4">
          <AccordionTrigger>
            <div className="flex flex-wrap items-center gap-2">
              <span>{t("filterSortToggle")}</span>
              <Badge variant="secondary">{sortAscending ? t("filterAsc") : t("filterDesc")}</Badge>
              {songFilters.hideDeletedSongs ? <Badge>{t("filterHideDeleted")}</Badge> : null}
            </div>
          </AccordionTrigger>
          <AccordionContent>
            <div className="flex flex-col gap-4 pt-2">
              <FieldGroup>
                <Field>
                  <FieldLabel>{t("filterSortLabel")}</FieldLabel>
                  <Select value={sortOption} onValueChange={(value) => onSortOptionChange(value as SongSortOption)}>
                    <SelectTrigger className="w-full sm:w-[220px]">
                      <SelectValue placeholder={t("filterSortPlaceholder")} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="default">{t("filterSortDefault")}</SelectItem>
                        <SelectItem value="versionDate">{t("filterSortDate")}</SelectItem>
                        <SelectItem value="difficulty">{t("filterSortDiff")}</SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              </FieldGroup>

              <div className="flex flex-wrap items-center gap-3">
                <label className="flex w-full items-center gap-2 text-sm sm:w-auto">
                  <Switch checked={sortAscending} onCheckedChange={onSortAscendingChange} />
                  {t("filterAsc")}
                </label>
                <label className="flex w-full items-center gap-2 text-sm sm:w-auto">
                  <Switch checked={songFilters.showFavoritesOnly} onCheckedChange={onShowFavoritesOnlyChange} />
                  {t("filterSwitchFav")}
                </label>
                <label className="flex w-full items-center gap-2 text-sm sm:w-auto">
                  <Switch checked={songFilters.hideDeletedSongs} onCheckedChange={onHideDeletedSongsChange} />
                  {t("filterSwitchHideDel")}
                </label>
              </div>

              <div className="flex flex-wrap gap-2">
                <Button variant="outline" size="sm" onClick={onResetFilters}>
                  {t("filterBtnReset")}
                </Button>
              </div>

              <div className="flex flex-col gap-2">
                <p className="text-xs text-muted-foreground">{t("filterLabelDiff")}</p>
                <ToggleGroup
                  className="w-full flex-wrap"
                  type="multiple"
                  variant="outline"
                  spacing={1}
                  value={selectedDifficulties}
                  onValueChange={(values) =>
                    syncToggleSet(songFilters.selectedDifficulties, values, "selectedDifficulties", onToggleFilterSet)
                  }
                >
                  {difficultyOptions.map((item) => (
                    <ToggleGroupItem key={item.value} value={item.value} className="min-w-[6.4rem] flex-1 sm:flex-none">
                      {item.label}
                    </ToggleGroupItem>
                  ))}
                </ToggleGroup>
              </div>

              <div className="flex flex-col gap-2">
                <p className="text-xs text-muted-foreground">{t("filterLabelLevel")}{songFilters.minLevel.toFixed(1)} - {songFilters.maxLevel.toFixed(1)}</p>
                <Slider
                  value={[songFilters.minLevel, songFilters.maxLevel]}
                  min={1}
                  max={15}
                  step={0.1}
                  onValueChange={(value) => {
                    if (value.length !== 2) {
                      return;
                    }
                    onLevelRangeChange(Number(value[0]), Number(value[1]));
                  }}
                  disabled={songFilters.selectedDifficulties.size === 0}
                />
              </div>

              <div className="flex flex-col gap-2">
                <p className="text-xs text-muted-foreground">{t("filterLabelType")}</p>
                <ToggleGroup
                  className="w-full flex-wrap"
                  type="multiple"
                  variant="outline"
                  spacing={1}
                  value={selectedTypes}
                  onValueChange={(values) =>
                    syncToggleSet(songFilters.selectedTypes, values, "selectedTypes", onToggleFilterSet)
                  }
                >
                  {chartTypeOptions.map((item) => (
                    <ToggleGroupItem key={item.value} value={item.value} className="min-w-[6.4rem] flex-1 sm:flex-none">
                      {item.label}
                    </ToggleGroupItem>
                  ))}
                </ToggleGroup>
              </div>

              <div className="flex flex-col gap-2">
                <p className="text-xs text-muted-foreground">{t("filterLabelCat")}</p>
                <div className="flex flex-wrap gap-2">
                  {allCategories.map((category) => (
                    <Button
                      key={category}
                      size="sm"
                      variant={songFilters.selectedCategories.has(category) ? "default" : "outline"}
                      onClick={() => onToggleFilterSet("selectedCategories", category)}
                    >
                      {category}
                    </Button>
                  ))}
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <p className="text-xs text-muted-foreground">{t("filterLabelVer")}</p>
                <div className="flex flex-wrap gap-2">
                  {[...allVersions].reverse().map((version) => (
                    <Button
                      key={version}
                      size="sm"
                      variant={songFilters.selectedVersions.has(version) ? "default" : "outline"}
                      onClick={() => onToggleFilterSet("selectedVersions", version)}
                    >
                      {formatVersionDisplay(version)}
                    </Button>
                  ))}
                </div>
              </div>
            </div>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </div>
  );
}
