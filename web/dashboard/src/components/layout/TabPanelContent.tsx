import type { Dispatch, SetStateAction } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { AdminStaticPage } from "@/components/pages/AdminStaticPage";
import { AdminUsersPage } from "@/components/pages/AdminUsersPage";
import { AliasesPage } from "@/components/pages/AliasesPage";
import { ImportsPage } from "@/components/pages/ImportsPage";
import { ScoresPage } from "@/components/pages/ScoresPage";
import { SettingsPage } from "@/components/pages/SettingsPage";
import { SongFiltersCard } from "@/components/songs/SongFiltersCard";
import { SongsTable } from "@/components/songs/SongsTable";
import type { Alias, Sheet as SongSheet, Song, SongFilterSettings, SongSortOption } from "@/components/songs/types";
import type {
  AdminCandidate,
  AdminDashboardStats,
  AdminUserRow,
  BackupCodeStatus,
  CommunityCandidate,
  MfaSetup,
  MfaStatus,
  PasskeyCredential,
  PlayRecordRow,
  Profile,
  ScoreRow,
  StaticBundle,
  StaticBundleSchedule,
  StaticSource,
} from "@/lib/app-types";
import type { Session } from "@/lib/session";
import { useTranslation } from "react-i18next";

type TabPanelContentProps = {
  tab: string;
  isAdmin: boolean;
  sessionUser: Session["user"];
  enabledProfile: Profile | null;
  selectedProfile: Profile | null;
  activeProfileAvatarUrl: string | null;
  mfaStatus: MfaStatus | null;
  mfaSetup: MfaSetup | null;
  mfaSetupCode: string;
  passkeys: PasskeyCredential[];
  backupCodeStatus: BackupCodeStatus;
  setMfaSetupCode: Dispatch<SetStateAction<string>>;

  songKeyword: string;
  filteredSongs: Song[];
  songFilterExpanded: boolean;
  songFilters: SongFilterSettings;
  allCategories: string[];
  allVersions: string[];
  songSortOption: SongSortOption;
  songSortAscending: boolean;
  setSongFilterExpanded: Dispatch<SetStateAction<boolean>>;
  setSongKeyword: Dispatch<SetStateAction<string>>;
  setSongFilters: Dispatch<SetStateAction<SongFilterSettings>>;
  toggleSongFilterSet: (key: "selectedCategories" | "selectedVersions" | "selectedDifficulties" | "selectedTypes", value: string) => void;
  resetSongFilters: () => void;
  setSongSortOption: Dispatch<SetStateAction<SongSortOption>>;
  setSongSortAscending: Dispatch<SetStateAction<boolean>>;
  formatVersionDisplay: (version?: string | null) => string;
  songFavorites: Set<string>;
  buildCoverUrl: (imageName?: string | null) => string | null;
  toggleSongFavorite: (songIdentifier: string) => void;
  handleOpenSongDetail: (song: Song) => Promise<void>;

  activeProfileId: string;
  profiles: Profile[];
  scoreSongName: string;
  scoreSongSuggestions: Array<{ songIdentifier: string; title: string; artist: string }>;
  scoreSearchKeyword: string;
  scoreType: string;
  scoreDifficulty: string;
  scoreAchievements: string;
  filteredScores: ScoreRow[];
  filteredPlayRecords: PlayRecordRow[];
  resolveSongTitle: (songIdentifier: string) => string;
  resolveSongCoverUrl: (songIdentifier: string) => string | null;
  formatChartType: (value?: string | null) => string;
  formatDifficulty: (value?: string | null) => string;
  setActiveProfileId: Dispatch<SetStateAction<string>>;
  loadScores: () => Promise<void>;
  setScoreSongName: Dispatch<SetStateAction<string>>;
  setScoreSearchKeyword: Dispatch<SetStateAction<string>>;
  setScoreType: Dispatch<SetStateAction<string>>;
  setScoreDifficulty: Dispatch<SetStateAction<string>>;
  setScoreAchievements: Dispatch<SetStateAction<string>>;
  handleSubmitScore: () => Promise<void>;
  openScoreEditDialog: (row: ScoreRow) => void;
  handleDeleteScore: (scoreId: string) => Promise<void>;
  handleDeletePlayRecord: (recordId: string) => Promise<void>;

  dfQQ: string;
  dfImportToken: string;
  lxnsAuthCode: string;
  setDfQQ: Dispatch<SetStateAction<string>>;
  setDfImportToken: Dispatch<SetStateAction<string>>;
  setLxnsAuthCode: Dispatch<SetStateAction<string>>;
  handleImportDf: () => Promise<void>;
  handleImportLxns: (input: { codeVerifier: string }) => Promise<void>;

  communityDailyCount: number;
  communityRows: CommunityCandidate[];
  communitySongName: string;
  communitySongSuggestions: Array<{ songIdentifier: string; title: string; artist: string }>;
  communityAliasText: string;
  adminStats: AdminDashboardStats | null;
  adminCandidates: AdminCandidate[];
  candidateVoteCloseDrafts: Record<string, string>;
  setCommunitySongName: Dispatch<SetStateAction<string>>;
  setCommunityAliasText: Dispatch<SetStateAction<string>>;
  handleCommunitySubmit: () => Promise<void>;
  handleCommunityVote: (candidateId: string, vote: 1 | -1) => Promise<void>;
  loadAdminCandidates: () => Promise<void>;
  handleAdminRollCycle: () => Promise<void>;
  setCandidateVoteCloseDrafts: Dispatch<SetStateAction<Record<string, string>>>;
  handleAdminVoteWindowUpdate: (candidateId: string) => Promise<void>;
  handleAdminCandidateStatus: (candidateId: string, status: "voting" | "approved" | "rejected") => Promise<void>;

  loadProfiles: () => Promise<void>;
  handleStartTotpSetup: () => Promise<void>;
  handleDisableTotp: () => Promise<void>;
  handleConfirmTotpSetup: () => Promise<void>;
  handleRegisterPasskey: () => Promise<string | null>;
  handleRenamePasskey: (credentialId: string, name: string) => Promise<boolean>;
  handleDeletePasskey: (credentialId: string) => Promise<void>;
  handleRegenerateBackupCodes: () => Promise<(BackupCodeStatus & { codes: string[]; generatedAt: string }) | null>;

  newUserEmail: string;
  newUserPassword: string;
  adminUsers: AdminUserRow[];
  setNewUserEmail: Dispatch<SetStateAction<string>>;
  setNewUserPassword: Dispatch<SetStateAction<string>>;
  handleCreateUser: () => Promise<void>;
  loadAdminUsers: () => Promise<void>;
  handleDeleteUser: (userId: string) => Promise<void>;

  staticSources: StaticSource[];
  staticBundles: StaticBundle[];
  staticBundleSchedule: StaticBundleSchedule | null;
  handleBuildBundle: () => Promise<void>;
  handleUpdateStaticBundleSchedule: (input: { enabled: boolean; intervalHours: number }) => Promise<void>;
  loadStaticAdmin: () => Promise<void>;
  handleToggleSource: (source: StaticSource) => Promise<void>;
  handleEditSourceUrl: (source: StaticSource, nextUrl: string, nextExtraUrl?: string) => Promise<void>;
};

export function TabPanelContent(props: TabPanelContentProps) {
  const {
    tab,
    isAdmin,
    sessionUser,
    enabledProfile,
    selectedProfile,
    activeProfileAvatarUrl,
    mfaStatus,
    mfaSetup,
    mfaSetupCode,
    passkeys,
    backupCodeStatus,
    setMfaSetupCode,
    songKeyword,
    filteredSongs,
    songFilterExpanded,
    songFilters,
    allCategories,
    allVersions,
    songSortOption,
    songSortAscending,
    setSongFilterExpanded,
    setSongKeyword,
    setSongFilters,
    toggleSongFilterSet,
    resetSongFilters,
    setSongSortOption,
    setSongSortAscending,
    formatVersionDisplay,
    songFavorites,
    buildCoverUrl,
    toggleSongFavorite,
    handleOpenSongDetail,
    activeProfileId,
    profiles,
    scoreSongName,
    scoreSongSuggestions,
    scoreSearchKeyword,
    scoreType,
    scoreDifficulty,
    scoreAchievements,
    filteredScores,
    filteredPlayRecords,
    resolveSongTitle,
    resolveSongCoverUrl,
    formatChartType,
    formatDifficulty,
    setActiveProfileId,
    loadScores,
    setScoreSongName,
    setScoreSearchKeyword,
    setScoreType,
    setScoreDifficulty,
    setScoreAchievements,
    handleSubmitScore,
    openScoreEditDialog,
    handleDeleteScore,
    handleDeletePlayRecord,
    dfQQ,
    dfImportToken,
    lxnsAuthCode,
    setDfQQ,
    setDfImportToken,
    setLxnsAuthCode,
    handleImportDf,
    handleImportLxns,
    communityDailyCount,
    communityRows,
    communitySongName,
    communitySongSuggestions,
    communityAliasText,
    adminStats,
    adminCandidates,
    candidateVoteCloseDrafts,
    setCommunitySongName,
    setCommunityAliasText,
    handleCommunitySubmit,
    handleCommunityVote,
    loadAdminCandidates,
    handleAdminRollCycle,
    setCandidateVoteCloseDrafts,
    handleAdminVoteWindowUpdate,
    handleAdminCandidateStatus,
    loadProfiles,
    handleStartTotpSetup,
    handleDisableTotp,
    handleConfirmTotpSetup,
    handleRegisterPasskey,
    handleRenamePasskey,
    handleDeletePasskey,
    handleRegenerateBackupCodes,
    newUserEmail,
    newUserPassword,
    adminUsers,
    setNewUserEmail,
    setNewUserPassword,
    handleCreateUser,
    loadAdminUsers,
    handleDeleteUser,
    staticSources,
    staticBundles,
    staticBundleSchedule,
    handleBuildBundle,
    handleUpdateStaticBundleSchedule,
    loadStaticAdmin,
    handleToggleSource,
    handleEditSourceUrl,
  } = props;

  const { t } = useTranslation();

  if (tab === "songs") {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{t("tab:songsTitle")}</CardTitle>
          <CardDescription>{t("tab:songsDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <SongFiltersCard
            songKeyword={songKeyword}
            filteredSongCount={filteredSongs.length}
            expanded={songFilterExpanded}
            songFilters={songFilters}
            allCategories={allCategories}
            allVersions={allVersions}
            sortOption={songSortOption}
            sortAscending={songSortAscending}
            onExpandedChange={setSongFilterExpanded}
            onSongKeywordChange={setSongKeyword}
            onShowFavoritesOnlyChange={(value) =>
              setSongFilters((previous) => ({ ...previous, showFavoritesOnly: value }))
            }
            onHideDeletedSongsChange={(value) =>
              setSongFilters((previous) => ({ ...previous, hideDeletedSongs: value }))
            }
            onToggleFilterSet={toggleSongFilterSet}
            onLevelRangeChange={(min, max) =>
              setSongFilters((previous) => ({
                ...previous,
                minLevel: min,
                maxLevel: max,
              }))
            }
            onResetFilters={resetSongFilters}
            onSortOptionChange={setSongSortOption}
            onSortAscendingChange={setSongSortAscending}
            formatVersionDisplay={formatVersionDisplay}
          />
          <SongsTable
            songs={filteredSongs}
            favorites={songFavorites}
            buildCoverUrl={buildCoverUrl}
            formatVersionDisplay={formatVersionDisplay}
            onToggleFavorite={toggleSongFavorite}
            onSelectSong={handleOpenSongDetail}
          />
        </CardContent>
      </Card>
    );
  }

  if (tab === "scores") {
    return (
      <ScoresPage
        scoreSongName={scoreSongName}
        scoreSongSuggestions={scoreSongSuggestions}
        scoreSearchKeyword={scoreSearchKeyword}
        scoreType={scoreType}
        scoreDifficulty={scoreDifficulty}
        scoreAchievements={scoreAchievements}
        scores={filteredScores}
        playRecords={filteredPlayRecords}
        resolveSongTitle={resolveSongTitle}
        resolveSongCoverUrl={resolveSongCoverUrl}
        formatChartType={formatChartType}
        formatDifficulty={formatDifficulty}
        onReload={loadScores}
        onScoreSongNameChange={setScoreSongName}
        onScoreSearchKeywordChange={setScoreSearchKeyword}
        onScoreTypeChange={setScoreType}
        onScoreDifficultyChange={setScoreDifficulty}
        onScoreAchievementsChange={setScoreAchievements}
        onSubmitScore={handleSubmitScore}
        onOpenScoreEdit={openScoreEditDialog}
        onDeleteScore={handleDeleteScore}
        onDeletePlayRecord={handleDeletePlayRecord}
      />
    );
  }

  if (tab === "import") {
    return (
      <ImportsPage
        dfQQ={dfQQ}
        dfImportToken={dfImportToken}
        lxnsAuthCode={lxnsAuthCode}
        onDfQQChange={setDfQQ}
        onDfImportTokenChange={setDfImportToken}
        onLxnsAuthCodeChange={setLxnsAuthCode}
        onImportDf={handleImportDf}
        onImportLxns={handleImportLxns}
      />
    );
  }

  if (tab === "aliases") {
    return (
      <AliasesPage
        isAdmin={isAdmin}
        communityDailyCount={communityDailyCount}
        communityRows={communityRows}
        communitySongName={communitySongName}
        communitySongSuggestions={communitySongSuggestions}
        communityAliasText={communityAliasText}
        adminStats={adminStats}
        adminCandidates={adminCandidates}
        candidateVoteCloseDrafts={candidateVoteCloseDrafts}
        resolveSongTitle={resolveSongTitle}
        resolveSongCoverUrl={resolveSongCoverUrl}
        onCommunitySongNameChange={setCommunitySongName}
        onCommunityAliasTextChange={setCommunityAliasText}
        onCommunitySubmit={handleCommunitySubmit}
        onCommunityVote={handleCommunityVote}
        onLoadAdminCandidates={loadAdminCandidates}
        onAdminRollCycle={handleAdminRollCycle}
        onCandidateVoteCloseDraftChange={(candidateId, value) =>
          setCandidateVoteCloseDrafts((previous) => ({
            ...previous,
            [candidateId]: value,
          }))
        }
        onAdminVoteWindowUpdate={handleAdminVoteWindowUpdate}
        onAdminCandidateStatusUpdate={handleAdminCandidateStatus}
      />
    );
  }

  if (tab === "settings") {
    return (
      <SettingsPage
        sessionUser={sessionUser}
        enabledProfile={enabledProfile}
        selectedProfile={selectedProfile}
        profiles={profiles}
        activeProfileId={activeProfileId}
        activeProfileAvatarUrl={activeProfileAvatarUrl}
        mfaStatus={mfaStatus}
        mfaSetup={mfaSetup}
        mfaSetupCode={mfaSetupCode}
        passkeys={passkeys}
        backupCodeStatus={backupCodeStatus}
        onReloadProfiles={loadProfiles}
        onActiveProfileIdChange={setActiveProfileId}
        onStartTotpSetup={handleStartTotpSetup}
        onDisableTotp={handleDisableTotp}
        onMfaSetupCodeChange={setMfaSetupCode}
        onConfirmTotpSetup={handleConfirmTotpSetup}
        onRegisterPasskey={handleRegisterPasskey}
        onRenamePasskey={handleRenamePasskey}
        onDeletePasskey={handleDeletePasskey}
        onRegenerateBackupCodes={handleRegenerateBackupCodes}
      />
    );
  }

  if (tab === "admin-users" && isAdmin) {
    return (
      <AdminUsersPage
        newUserEmail={newUserEmail}
        newUserPassword={newUserPassword}
        adminUsers={adminUsers}
        onNewUserEmailChange={setNewUserEmail}
        onNewUserPasswordChange={setNewUserPassword}
        onCreateUser={handleCreateUser}
        onReloadUsers={loadAdminUsers}
        onDeleteUser={handleDeleteUser}
      />
    );
  }

  if (tab === "admin-static" && isAdmin) {
    return (
      <AdminStaticPage
        staticSources={staticSources}
        staticBundles={staticBundles}
        staticBundleSchedule={staticBundleSchedule}
        onBuildBundle={handleBuildBundle}
        onUpdateBundleSchedule={handleUpdateStaticBundleSchedule}
        onReloadStatic={loadStaticAdmin}
        onToggleSource={handleToggleSource}
        onEditSourceUrl={handleEditSourceUrl}
      />
    );
  }

  return null;
}
