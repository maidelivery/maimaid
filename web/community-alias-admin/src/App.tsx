import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import GavelRoundedIcon from "@mui/icons-material/GavelRounded";
import HowToVoteRoundedIcon from "@mui/icons-material/HowToVoteRounded";
import LibraryMusicRoundedIcon from "@mui/icons-material/LibraryMusicRounded";
import LoginRoundedIcon from "@mui/icons-material/LoginRounded";
import LogoutRoundedIcon from "@mui/icons-material/LogoutRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import ShieldRoundedIcon from "@mui/icons-material/ShieldRounded";
import ThumbDownRoundedIcon from "@mui/icons-material/ThumbDownRounded";
import ThumbUpRoundedIcon from "@mui/icons-material/ThumbUpRounded";
import UpdateRoundedIcon from "@mui/icons-material/UpdateRounded";
import VerifiedRoundedIcon from "@mui/icons-material/VerifiedRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import {
  Alert,
  AppBar,
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  InputAdornment,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Toolbar,
  Typography,
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import { DataGrid, type GridColDef, type GridPaginationModel } from "@mui/x-data-grid";
import { zhCN as dataGridZhCN } from "@mui/x-data-grid/locales";
import { DateTimePicker } from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { zhCN as datePickerZhCN } from "@mui/x-date-pickers/locales";
import { createClient, type Session, type SupabaseClient } from "@supabase/supabase-js";
import dayjs, { type Dayjs } from "dayjs";
import "dayjs/locale/zh-cn";
import type { AdminContext, CandidateRecord, DashboardStats, FilterState, SongCatalogItem, ToastState } from "./types";

const FILTER_STORAGE_KEY = "communityAliasAdmin.filters";
const PAGE_SIZE = 20;
const SUPABASE_URL = (import.meta.env.VITE_SUPABASE_URL ?? "").trim();
const SUPABASE_ANON_KEY = (import.meta.env.VITE_SUPABASE_ANON_KEY ?? "").trim();
const HAS_SUPABASE_ENV = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY);
const MAIMAI_REMOTE_DATA_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json";
const MAIMAI_LXNS_SONG_LIST_URL = "https://maimai.lxns.net/api/v0/maimai/song/list";
const MAIMAI_COVER_BASE_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover";

const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  dateStyle: "medium",
  timeStyle: "short",
});

const DEFAULT_FILTERS: FilterState = {
  search: "",
  status: "all",
  sort: "updated_desc",
};

const SORT_OPTIONS = [
  { value: "updated_desc", label: "最近更新" },
  { value: "deadline_asc", label: "最早截止" },
  { value: "votes_desc", label: "票差最高" },
  { value: "created_desc", label: "最新创建" },
] as const;

const STATUS_OPTIONS = [
  { value: "all", label: "全部" },
  { value: "voting", label: "投票中" },
  { value: "approved", label: "已通过" },
  { value: "rejected", label: "已驳回" },
] as const;

function normalizeSearch(value: string): string {
  return value
    .normalize("NFKC")
    .trim()
    .toLowerCase()
    .replace(/\s+/gu, " ");
}

function buildCoverUrl(imageName: string): string | null {
  const normalized = imageName.trim();
  if (!normalized) {
    return null;
  }
  return `${MAIMAI_COVER_BASE_URL}/${encodeURIComponent(normalized)}`;
}

function normalizeErrorMessage(error: unknown): string {
  const raw = String(
    (error as { message?: string; error_description?: string } | undefined)?.message
      ?? (error as { error_description?: string } | undefined)?.error_description
      ?? error
      ?? "未知错误",
  );

  if (raw.includes("Admin permission required")) {
    return "当前账号没有管理员权限，请检查 app_metadata.role / roles / is_admin。";
  }
  if (raw.includes("Not authenticated")) {
    return "请先登录管理员账号。";
  }
  if (raw.includes("Invalid login credentials")) {
    return "邮箱或密码错误。";
  }

  return raw;
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }

  return dateFormatter.format(date);
}

function statusLabel(status: string): string {
  switch (status) {
    case "approved":
      return "已通过";
    case "rejected":
      return "已驳回";
    case "voting":
      return "投票中";
    default:
      return status || "未知";
  }
}

function statusColor(status: string): "success" | "warning" | "error" | "default" {
  switch (status) {
    case "approved":
      return "success";
    case "rejected":
      return "error";
    case "voting":
      return "warning";
    default:
      return "default";
  }
}

function shortId(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  return `${value.slice(0, 8)}…`;
}

function parseInitialFilters(): FilterState {
  const raw = localStorage.getItem(FILTER_STORAGE_KEY);
  if (!raw) {
    return DEFAULT_FILTERS;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<FilterState>;
    return {
      search: typeof parsed.search === "string" ? parsed.search : DEFAULT_FILTERS.search,
      status: typeof parsed.status === "string" ? parsed.status : DEFAULT_FILTERS.status,
      sort: typeof parsed.sort === "string" ? parsed.sort : DEFAULT_FILTERS.sort,
    };
  } catch {
    return DEFAULT_FILTERS;
  }
}

function App() {
  const clientRef = useRef<SupabaseClient | null>(null);
  const authSubscriptionRef = useRef<{ unsubscribe: () => void } | null>(null);

  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const [filters, setFilters] = useState<FilterState>(parseInitialFilters);
  const [searchDraft, setSearchDraft] = useState<string>(parseInitialFilters().search);
  const [session, setSession] = useState<Session | null>(null);
  const [adminContext, setAdminContext] = useState<AdminContext | null>(null);
  const [accessStatus, setAccessStatus] = useState<"loading" | "admin" | "forbidden" | "error">("loading");
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [items, setItems] = useState<CandidateRecord[]>([]);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [selectedCandidateId, setSelectedCandidateId] = useState<string | null>(null);
  const [detailDialogOpen, setDetailDialogOpen] = useState<boolean>(false);
  const [songs, setSongs] = useState<SongCatalogItem[]>([]);
  const [catalogStatus, setCatalogStatus] = useState<string>("正在加载曲库索引…");
  const [songSearch, setSongSearch] = useState<string>("");
  const [songIdentifierInput, setSongIdentifierInput] = useState<string>("");
  const [aliasInput, setAliasInput] = useState<string>("");
  const [createStatus, setCreateStatus] = useState<string>("approved");
  const [createHint, setCreateHint] = useState<string>("可直接补录已确认的社区别名。");
  const [deadlineValue, setDeadlineValue] = useState<Dayjs | null>(null);
  const [toast, setToast] = useState<ToastState>({
    open: false,
    message: "",
    severity: "info",
  });
  const [isBootstrapping, setIsBootstrapping] = useState<boolean>(HAS_SUPABASE_ENV);
  const [isSigningIn, setIsSigningIn] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isRollingCycle, setIsRollingCycle] = useState<boolean>(false);
  const [isCreating, setIsCreating] = useState<boolean>(false);
  const [detailBusyAction, setDetailBusyAction] = useState<string>("");

  const isAdmin = accessStatus === "admin";
  const songMap = useMemo(() => new Map(songs.map((song) => [song.songIdentifier, song])), [songs]);

  const selectedCandidate = useMemo(
    () => items.find((item) => item.candidate_id === selectedCandidateId) ?? null,
    [items, selectedCandidateId],
  );

  const songSuggestions = useMemo(() => {
    const query = normalizeSearch(songSearch);
    if (!query) {
      return [];
    }
    return songs.filter((song) => song.searchText.includes(query)).slice(0, 6);
  }, [songSearch, songs]);

  const metrics = useMemo(() => {
    return [
      { label: "总候选", value: stats?.total_count ?? "-", icon: <LibraryMusicRoundedIcon fontSize="small" /> },
      { label: "投票中", value: stats?.voting_count ?? "-", icon: <HowToVoteRoundedIcon fontSize="small" /> },
      { label: "已通过", value: stats?.approved_count ?? "-", icon: <VerifiedRoundedIcon fontSize="small" /> },
      { label: "已驳回", value: stats?.rejected_count ?? "-", icon: <ThumbDownRoundedIcon fontSize="small" /> },
      { label: "24 小时内截止", value: stats?.closing_soon_count ?? "-", icon: <UpdateRoundedIcon fontSize="small" /> },
      { label: "待结算", value: stats?.expired_voting_count ?? "-", icon: <WarningAmberRoundedIcon fontSize="small" /> },
      { label: "今日新增", value: stats?.today_submissions ?? "-", icon: <AddRoundedIcon fontSize="small" /> },
    ];
  }, [stats]);

  const showToast = useCallback((message: string, severity: ToastState["severity"] = "info") => {
    setToast({
      open: true,
      message,
      severity,
    });
  }, []);

  const resolveSongTitle = useCallback((songIdentifier: string) => {
    return songMap.get(songIdentifier)?.title || "未知歌曲";
  }, [songMap]);

  const resolveSongCoverUrl = useCallback((songIdentifier: string) => {
    return songMap.get(songIdentifier)?.coverUrl ?? null;
  }, [songMap]);

  const resolveSubmitterLabel = useCallback((row: CandidateRecord) => {
    return row.submitter_email?.trim() || shortId(row.submitter_id);
  }, []);

  const teardownClient = useCallback(() => {
    authSubscriptionRef.current?.unsubscribe();
    authSubscriptionRef.current = null;
    clientRef.current = null;
  }, []);

  const clearWorkspaceState = useCallback(() => {
    setStats(null);
    setItems([]);
    setTotalCount(0);
    setSelectedCandidateId(null);
    setDetailDialogOpen(false);
    setDeadlineValue(null);
  }, []);

  const rpc = useCallback(
    async <T,>(name: string, params: Record<string, unknown> = {}): Promise<T> => {
      if (!clientRef.current) {
        throw new Error("Supabase 客户端未初始化。");
      }

      const { data, error } = await clientRef.current.rpc(name, params);
      if (error) {
        throw error;
      }
      return data as T;
    },
    [],
  );

  const refreshWorkspace = useCallback(async () => {
    if (!clientRef.current || !session) {
      return;
    }

    setIsRefreshing(true);
    try {
      const contextPayload = await rpc<AdminContext[] | AdminContext>("community_alias_admin_get_context");
      const nextAdminContext = Array.isArray(contextPayload) ? (contextPayload[0] ?? null) : contextPayload;
      setAdminContext(nextAdminContext);

      if (!nextAdminContext?.is_admin) {
        setAccessStatus("forbidden");
        clearWorkspaceState();
        return;
      }

      setAccessStatus("admin");

      const [statsPayload, itemsPayload] = await Promise.all([
        rpc<DashboardStats[] | DashboardStats>("community_alias_admin_dashboard_stats"),
        rpc<CandidateRecord[]>("community_alias_admin_list_candidates", {
          p_status: filters.status,
          p_search: filters.search || null,
          p_sort: filters.sort,
          p_limit: PAGE_SIZE,
          p_offset: page * PAGE_SIZE,
        }),
      ]);

      const nextStats = Array.isArray(statsPayload) ? (statsPayload[0] ?? null) : statsPayload;
      setStats(nextStats);
      setItems(itemsPayload);
      setTotalCount(itemsPayload[0]?.total_count ?? 0);
      setSelectedCandidateId((currentId) => {
        if (currentId && itemsPayload.some((item) => item.candidate_id === currentId)) {
          return currentId;
        }
        return itemsPayload[0]?.candidate_id ?? null;
      });
    } catch (error) {
      setAccessStatus("error");
      showToast(normalizeErrorMessage(error), "error");
    } finally {
      setIsRefreshing(false);
    }
  }, [clearWorkspaceState, filters.search, filters.sort, filters.status, page, rpc, session, showToast]);

  useEffect(() => {
    localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify(filters));
  }, [filters]);

  useEffect(() => {
    if (selectedCandidate?.vote_close_at) {
      setDeadlineValue(dayjs(selectedCandidate.vote_close_at));
      return;
    }
    setDeadlineValue(null);
  }, [selectedCandidate]);

  useEffect(() => {
    async function loadCatalog() {
      setCatalogStatus("正在同步远程曲库…");

      try {
        const response = await fetch(MAIMAI_REMOTE_DATA_URL);
        if (!response.ok) {
          throw new Error(`远程曲库拉取失败：${response.status}`);
        }

        const payload = (await response.json()) as { songs?: Array<Record<string, unknown>> };
        const nextSongs: SongCatalogItem[] = Array.isArray(payload.songs)
          ? payload.songs.map((song) => ({
              songIdentifier: String(song.songId ?? song.songIdentifier ?? song.title ?? ""),
              title: String(song.title ?? song.songId ?? ""),
              artist: String(song.artist ?? ""),
              version: String(song.version ?? ""),
              coverUrl: buildCoverUrl(String(song.imageName ?? "")),
              searchText: normalizeSearch(
                `${String(song.songId ?? "")} ${String(song.songIdentifier ?? "")} ${String(song.title ?? "")} ${String(song.artist ?? "")}`,
              ),
            }))
          : [];

        if (!nextSongs.length) {
          throw new Error("远程曲库为空");
        }

        setSongs(nextSongs);
        setCatalogStatus(`远程曲库已加载 ${nextSongs.length} 首歌`);
      } catch {
        try {
          const fallbackResponse = await fetch(MAIMAI_LXNS_SONG_LIST_URL);
          if (!fallbackResponse.ok) {
            throw new Error(`LXNS 曲库拉取失败：${fallbackResponse.status}`);
          }

          const payload = (await fallbackResponse.json()) as { songs?: Array<Record<string, unknown>> };
          const nextSongs: SongCatalogItem[] = Array.isArray(payload.songs)
            ? payload.songs.map((song) => ({
                songIdentifier: String(song.id ?? song.songId ?? song.songIdentifier ?? song.title ?? ""),
                title: String(song.title ?? song.songId ?? song.id ?? ""),
                artist: String(song.artist ?? ""),
                version: String(song.version ?? ""),
                coverUrl: null,
                searchText: normalizeSearch(
                  `${String(song.id ?? "")} ${String(song.songId ?? "")} ${String(song.songIdentifier ?? "")} ${String(song.title ?? "")} ${String(song.artist ?? "")}`,
                ),
              }))
            : [];

          setSongs(nextSongs);
          setCatalogStatus(`远程曲库已加载 ${nextSongs.length} 首歌（LXNS）`);
        } catch {
          setSongs([]);
          setCatalogStatus("远程曲库加载失败，可手动填写歌曲名。");
        }
      }
    }

    void loadCatalog();
  }, []);

  useEffect(() => {
    if (!HAS_SUPABASE_ENV) {
      setIsBootstrapping(false);
      return;
    }

    const client = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      auth: {
        autoRefreshToken: true,
        persistSession: true,
        detectSessionInUrl: true,
      },
    });

    clientRef.current = client;
    const { data } = client.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession);
      if (!nextSession) {
        setAdminContext(null);
        setAccessStatus("loading");
        clearWorkspaceState();
      }
    });
    authSubscriptionRef.current = data.subscription;

    void (async () => {
      try {
        const { data: sessionPayload, error } = await client.auth.getSession();
        if (error) {
          throw error;
        }
        setSession(sessionPayload.session ?? null);
      } catch (error) {
        showToast(normalizeErrorMessage(error), "error");
      } finally {
        setIsBootstrapping(false);
      }
    })();

    return () => {
      teardownClient();
    };
  }, [clearWorkspaceState, showToast, teardownClient]);

  useEffect(() => {
    if (!session) {
      setAccessStatus("loading");
      clearWorkspaceState();
      return;
    }
    setAccessStatus("loading");
  }, [clearWorkspaceState, session]);

  useEffect(() => {
    if (!session) {
      return;
    }
    void refreshWorkspace();
  }, [refreshWorkspace, session]);

  const handleSignIn = async () => {
    if (!clientRef.current) {
      showToast("系统初始化未完成，请稍后重试。", "error");
      return;
    }
    if (!email.trim() || !password) {
      showToast("请输入邮箱和密码。", "error");
      return;
    }

    setIsSigningIn(true);
    try {
      const { error } = await clientRef.current.auth.signInWithPassword({
        email: email.trim(),
        password,
      });
      if (error) {
        throw error;
      }
      setPassword("");
      showToast("登录成功。", "success");
    } catch (error) {
      showToast(normalizeErrorMessage(error), "error");
    } finally {
      setIsSigningIn(false);
    }
  };

  const handleSignOut = async () => {
    if (!clientRef.current) {
      return;
    }
    const { error } = await clientRef.current.auth.signOut();
    if (error) {
      showToast(normalizeErrorMessage(error), "error");
      return;
    }
    setEmail("");
    setPassword("");
    setAdminContext(null);
    setAccessStatus("loading");
    clearWorkspaceState();
    showToast("已退出登录。", "success");
  };

  const handleSearch = () => {
    setPage(0);
    setFilters((currentFilters) => ({
      ...currentFilters,
      search: searchDraft.trim(),
    }));
  };

  const handleRollCycle = async () => {
    if (!isAdmin) {
      showToast("需要管理员权限。", "error");
      return;
    }

    setIsRollingCycle(true);
    try {
      const result = await rpc<{ settled_count?: number }>("community_alias_admin_roll_cycle", { p_now: null });
      showToast(`已完成结算，处理 ${Number(result?.settled_count ?? 0)} 条候选。`, "success");
      await refreshWorkspace();
    } catch (error) {
      showToast(normalizeErrorMessage(error), "error");
    } finally {
      setIsRollingCycle(false);
    }
  };

  const handleCreateCandidate = async () => {
    if (!isAdmin) {
      showToast("需要管理员权限。", "error");
      return;
    }
    if (!songIdentifierInput.trim() || !aliasInput.trim()) {
      showToast("请填写歌曲名和别名。", "error");
      return;
    }

    setIsCreating(true);
    try {
      const payload = await rpc<CandidateRecord[] | CandidateRecord>("community_alias_admin_create_candidate", {
        p_song_identifier: songIdentifierInput.trim(),
        p_alias_text: aliasInput.trim(),
        p_status: createStatus,
      });

      const created = Array.isArray(payload) ? (payload[0] ?? null) : payload;
      setAliasInput("");
      setCreateHint("创建成功，列表已刷新。");
      setSelectedCandidateId(created?.candidate_id ?? null);
      showToast(`已创建候选：${created?.alias_text ?? aliasInput.trim()}`, "success");
      await refreshWorkspace();
    } catch (error) {
      const message = normalizeErrorMessage(error);
      setCreateHint(message);
      showToast(message, "error");
    } finally {
      setIsCreating(false);
    }
  };

  const handleDetailMutation = async (busyKey: string, action: () => Promise<void>) => {
    setDetailBusyAction(busyKey);
    try {
      await action();
      await refreshWorkspace();
    } catch (error) {
      showToast(normalizeErrorMessage(error), "error");
    } finally {
      setDetailBusyAction("");
    }
  };

  const handleStatusChange = async (status: string) => {
    if (!selectedCandidate) {
      return;
    }
    await handleDetailMutation(status, async () => {
      await rpc("community_alias_admin_set_status", {
        p_candidate_id: selectedCandidate.candidate_id,
        p_status: status,
      });
      showToast("状态更新成功。", "success");
    });
  };

  const handleDeadlineSave = async (nextDeadline: Dayjs | null) => {
    if (!selectedCandidate) {
      return;
    }
    if (!nextDeadline) {
      showToast("请先选择新的截止时间。", "error");
      return;
    }
    await handleDetailMutation("deadline", async () => {
      await rpc("community_alias_admin_update_vote_window", {
        p_candidate_id: selectedCandidate.candidate_id,
        p_vote_close_at: nextDeadline.toISOString(),
      });
      showToast("投票截止时间已更新。", "success");
    });
  };

  const columns = useMemo<GridColDef<CandidateRecord>[]>(
    () => [
      {
        field: "song",
        headerName: "歌曲",
        flex: 1.25,
        sortable: false,
        valueGetter: (_value, row) => resolveSongTitle(row.song_identifier),
        renderCell: (params) => (
          <Stack
            direction="row"
            spacing={1.25}
            alignItems="center"
            sx={{
              minWidth: 0,
              width: "100%",
              height: "100%",
              py: 0,
            }}
          >
            <Avatar
              variant="rounded"
              src={resolveSongCoverUrl(params.row.song_identifier) ?? undefined}
              imgProps={{ loading: "lazy", referrerPolicy: "no-referrer" }}
              sx={{
                width: 42,
                height: 42,
                bgcolor: "action.hover",
                color: "text.secondary",
                flexShrink: 0,
              }}
            >
              <LibraryMusicRoundedIcon fontSize="small" />
            </Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                {params.value as string}
              </Typography>
            </Box>
          </Stack>
        ),
      },
      {
        field: "alias_text",
        headerName: "别名",
        flex: 0.9,
      },
      {
        field: "status",
        headerName: "状态",
        width: 120,
        renderCell: (params) => (
          <Chip
            label={statusLabel(String(params.value))}
            color={statusColor(String(params.value))}
            size="small"
            variant={params.value === "voting" ? "outlined" : "filled"}
          />
        ),
      },
      {
        field: "votes",
        headerName: "票数",
        width: 120,
        sortable: false,
        valueGetter: (_value, row) => `${row.support_count} / ${row.oppose_count}`,
      },
      {
        field: "submitter_id",
        headerName: "提交者邮箱",
        width: 220,
        valueGetter: (_value, row) => resolveSubmitterLabel(row),
      },
      {
        field: "updated_at",
        headerName: "更新时间",
        width: 180,
        valueGetter: (_value, row) => formatDateTime(row.updated_at),
      },
    ],
    [resolveSongCoverUrl, resolveSongTitle, resolveSubmitterLabel],
  );

  let content: ReactNode;

  if (!HAS_SUPABASE_ENV) {
    content = (
      <FullscreenPanel
        icon={<WarningAmberRoundedIcon color="warning" />}
        title="环境变量未配置"
        description="当前部署缺少 VITE_SUPABASE_URL 或 VITE_SUPABASE_ANON_KEY，无法启动管理后台。"
      />
    );
  } else if (isBootstrapping) {
    content = (
      <FullscreenPanel
        icon={<CircularProgress size={22} />}
        title="正在初始化"
        description="正在检查登录会话，请稍候。"
      />
    );
  } else if (!session) {
    content = (
      <FullscreenPanel
        icon={<GavelRoundedIcon color="primary" />}
        title="管理员登录"
        description="请先登录管理员账号，登录后才能进入社区别名管理。"
      >
        <Stack spacing={2} sx={{ mt: 3 }}>
          <TextField
            label="邮箱"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            fullWidth
          />
          <TextField
            label="密码"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            fullWidth
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                void handleSignIn();
              }
            }}
          />
          <Button
            variant="contained"
            size="large"
            startIcon={<LoginRoundedIcon />}
            onClick={() => void handleSignIn()}
            disabled={isSigningIn}
          >
            {isSigningIn ? "登录中…" : "登录并进入管理台"}
          </Button>
        </Stack>
      </FullscreenPanel>
    );
  } else if (accessStatus === "loading") {
    content = (
      <FullscreenPanel
        icon={<CircularProgress size={22} />}
        title="正在验证权限"
        description="正在校验管理员权限。"
      />
    );
  } else if (accessStatus === "forbidden") {
    content = (
      <FullscreenPanel
        icon={<ShieldRoundedIcon color="warning" />}
        title="账号无管理员权限"
        description="当前账号已登录，但不包含 community_alias_admin 权限 Claim。"
      >
        <Stack direction="row" spacing={1.5} sx={{ mt: 3 }}>
          <Button variant="outlined" startIcon={<RefreshRoundedIcon />} onClick={() => void refreshWorkspace()}>
            重新校验
          </Button>
          <Button variant="contained" startIcon={<LogoutRoundedIcon />} onClick={() => void handleSignOut()}>
            退出登录
          </Button>
        </Stack>
      </FullscreenPanel>
    );
  } else if (accessStatus === "error") {
    content = (
      <FullscreenPanel
        icon={<WarningAmberRoundedIcon color="error" />}
        title="后台连接失败"
        description="请求管理接口失败，请重试或重新登录。"
      >
        <Stack direction="row" spacing={1.5} sx={{ mt: 3 }}>
          <Button variant="outlined" startIcon={<RefreshRoundedIcon />} onClick={() => void refreshWorkspace()}>
            重试
          </Button>
          <Button variant="contained" startIcon={<LogoutRoundedIcon />} onClick={() => void handleSignOut()}>
            退出登录
          </Button>
        </Stack>
      </FullscreenPanel>
    );
  } else {
    content = (
      <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
        <AppBar
          position="sticky"
          color="inherit"
          elevation={0}
          sx={{
            borderBottom: (theme) => `1px solid ${theme.palette.divider}`,
            backgroundColor: (theme) => alpha(theme.palette.background.paper, 0.92),
            backdropFilter: "blur(8px)",
          }}
        >
          <Toolbar sx={{ gap: 1.5, py: 1 }}>
            <Avatar sx={{ bgcolor: "primary.main", width: 36, height: 36 }}>
              <GavelRoundedIcon fontSize="small" />
            </Avatar>
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="h6" sx={{ lineHeight: 1.2 }}>
                社区别名管理台
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {adminContext?.email ?? "管理员"}
              </Typography>
            </Box>
            <Chip color="success" icon={<VerifiedRoundedIcon />} label="管理员已认证" variant="outlined" />
            <Button
              variant="outlined"
              startIcon={<RefreshRoundedIcon />}
              onClick={() => void refreshWorkspace()}
              disabled={isRefreshing}
            >
              {isRefreshing ? "刷新中…" : "刷新"}
            </Button>
            <Button
              variant="outlined"
              color="warning"
              startIcon={<UpdateRoundedIcon />}
              onClick={() => void handleRollCycle()}
              disabled={isRollingCycle}
            >
              {isRollingCycle ? "结算中…" : "结算到期投票"}
            </Button>
            <Button variant="contained" color="inherit" startIcon={<LogoutRoundedIcon />} onClick={() => void handleSignOut()}>
              退出
            </Button>
          </Toolbar>
        </AppBar>

        <Container maxWidth={false} sx={{ px: { xs: 2, md: 3 }, py: 3 }}>
          <Stack spacing={2.5}>
            <Box
              sx={{
                display: "grid",
                gap: 1.75,
                gridTemplateColumns: {
                  xs: "1fr",
                  sm: "repeat(2, minmax(0, 1fr))",
                  lg: "repeat(4, minmax(0, 1fr))",
                  xl: "repeat(7, minmax(0, 1fr))",
                },
              }}
            >
              {metrics.map((metric) => (
                <MetricCard key={metric.label} label={metric.label} value={metric.value} icon={metric.icon} />
              ))}
            </Box>

            <Card>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Stack direction={{ xs: "column", lg: "row" }} spacing={1.5} justifyContent="space-between">
                    <Box>
                      <Typography variant="h6">候选列表</Typography>
                      <Typography variant="body2" color="text.secondary">
                        搜索并选择候选项进行状态管理
                      </Typography>
                    </Box>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1.25}>
                      <TextField
                        placeholder="按曲名、别名、邮箱搜索"
                        value={searchDraft}
                        onChange={(event) => setSearchDraft(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") {
                            handleSearch();
                          }
                        }}
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <SearchRoundedIcon fontSize="small" />
                            </InputAdornment>
                          ),
                        }}
                        sx={{ minWidth: { xs: "100%", md: 320 } }}
                      />
                      <TextField
                        select
                        label="状态"
                        value={filters.status}
                        onChange={(event) => {
                          setPage(0);
                          setFilters((currentFilters) => ({
                            ...currentFilters,
                            status: event.target.value,
                          }));
                        }}
                        sx={{ minWidth: 128 }}
                      >
                        {STATUS_OPTIONS.map((option) => (
                          <MenuItem key={option.value} value={option.value}>
                            {option.label}
                          </MenuItem>
                        ))}
                      </TextField>
                      <TextField
                        select
                        label="排序"
                        value={filters.sort}
                        onChange={(event) => {
                          setPage(0);
                          setFilters((currentFilters) => ({
                            ...currentFilters,
                            sort: event.target.value,
                          }));
                        }}
                        sx={{ minWidth: 140 }}
                      >
                        {SORT_OPTIONS.map((option) => (
                          <MenuItem key={option.value} value={option.value}>
                            {option.label}
                          </MenuItem>
                        ))}
                      </TextField>
                      <Button variant="contained" onClick={handleSearch}>
                        查询
                      </Button>
                    </Stack>
                  </Stack>

                  <Box
                    sx={{
                      height: 650,
                      borderRadius: 3,
                      overflow: "hidden",
                      border: (theme) => `1px solid ${theme.palette.divider}`,
                    }}
                  >
                    <DataGrid
                      localeText={dataGridZhCN.components.MuiDataGrid.defaultProps.localeText}
                      rows={items}
                      columns={columns}
                      getRowId={(row) => row.candidate_id}
                      rowHeight={76}
                      loading={isRefreshing}
                      disableColumnMenu
                      disableRowSelectionOnClick
                      paginationMode="server"
                      rowCount={totalCount}
                      paginationModel={{ page, pageSize: PAGE_SIZE }}
                      pageSizeOptions={[PAGE_SIZE]}
                      onPaginationModelChange={(model: GridPaginationModel) => {
                        if (model.page !== page) {
                          setPage(model.page);
                        }
                      }}
                      onRowClick={(params) => {
                        setSelectedCandidateId(String(params.id));
                        setDetailDialogOpen(true);
                      }}
                      getRowClassName={(params) => (String(params.id) === selectedCandidateId ? "is-selected-row" : "")}
                      sx={{
                        "& .is-selected-row": {
                          backgroundColor: (theme) => alpha(theme.palette.primary.main, 0.08),
                        },
                        "& .MuiDataGrid-row:hover": {
                          backgroundColor: (theme) => alpha(theme.palette.primary.main, 0.05),
                        },
                        "& .MuiDataGrid-cell": {
                          alignItems: "center",
                        },
                      }}
                    />
                  </Box>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="h6">新增别名</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {catalogStatus}
                    </Typography>
                  </Box>

                  <TextField
                    label="歌曲检索"
                    placeholder="输入歌曲名"
                    value={songSearch}
                    onChange={(event) => setSongSearch(event.target.value)}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <LibraryMusicRoundedIcon fontSize="small" />
                        </InputAdornment>
                      ),
                    }}
                  />

                  {songSearch ? (
                    <Paper
                      variant="outlined"
                      sx={{
                        borderRadius: 2,
                        maxHeight: 220,
                        overflow: "auto",
                      }}
                    >
                      {songSuggestions.length ? (
                        <List disablePadding>
                          {songSuggestions.map((song) => (
                            <ListItemButton
                              key={song.songIdentifier}
                              onClick={() => {
                                setSongIdentifierInput(song.songIdentifier);
                                setSongSearch(song.title || "");
                              }}
                            >
                              <ListItemText
                                primary={song.title || "未知歌曲"}
                                secondary={song.artist || undefined}
                              />
                            </ListItemButton>
                          ))}
                        </List>
                      ) : (
                        <Alert severity="info" variant="outlined" sx={{ m: 2 }}>
                          没有找到匹配歌曲，可直接手填歌曲名。
                        </Alert>
                      )}
                    </Paper>
                  ) : null}

                  <TextField
                    label="歌曲名"
                    value={songIdentifierInput}
                    onChange={(event) => setSongIdentifierInput(event.target.value)}
                  />

                  <TextField
                    label="社区别名"
                    value={aliasInput}
                    onChange={(event) => setAliasInput(event.target.value)}
                    placeholder="输入要补录的社区别名"
                    inputProps={{ maxLength: 64 }}
                  />

                  <TextField
                    select
                    label="初始状态"
                    value={createStatus}
                    onChange={(event) => setCreateStatus(event.target.value)}
                  >
                    <MenuItem value="approved">直接通过</MenuItem>
                    <MenuItem value="voting">进入投票</MenuItem>
                  </TextField>

                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1.25}>
                    <Button
                      variant="contained"
                      startIcon={<AddRoundedIcon />}
                      onClick={() => void handleCreateCandidate()}
                      disabled={isCreating}
                    >
                      {isCreating ? "创建中…" : "创建候选"}
                    </Button>
                    <Typography variant="body2" color="text.secondary" sx={{ alignSelf: "center" }}>
                      {createHint}
                    </Typography>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>

            <Dialog
              fullWidth
              maxWidth="md"
              open={detailDialogOpen && Boolean(selectedCandidate)}
              onClose={() => setDetailDialogOpen(false)}
            >
              <DialogTitle sx={{ pb: 1.5 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="h6">候选详情</Typography>
                  <Chip
                    color={selectedCandidate ? statusColor(selectedCandidate.status) : "default"}
                    label={selectedCandidate ? statusLabel(selectedCandidate.status) : "未选择"}
                    variant={selectedCandidate?.status === "voting" ? "outlined" : "filled"}
                  />
                </Stack>
              </DialogTitle>
              {selectedCandidate ? (
                <>
                  <DialogContent dividers>
                    <Stack spacing={2}>
                      <Box
                        sx={{
                          display: "grid",
                          gap: 1.25,
                          gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                        }}
                      >
                        <DetailField label="歌曲" value={resolveSongTitle(selectedCandidate.song_identifier)} />
                        <DetailField label="社区别名" value={selectedCandidate.alias_text} />
                        <DetailField label="提交者" value={resolveSubmitterLabel(selectedCandidate)} />
                        <DetailField label="支持 / 反对" value={`${selectedCandidate.support_count} / ${selectedCandidate.oppose_count}`} />
                        <DetailField label="当前状态" value={statusLabel(selectedCandidate.status)} />
                        <DetailField label="创建时间" value={formatDateTime(selectedCandidate.created_at)} />
                        <DetailField label="更新时间" value={formatDateTime(selectedCandidate.updated_at)} />
                        <DetailField label="投票开始" value={formatDateTime(selectedCandidate.vote_open_at)} />
                        <DetailField label="投票截止" value={formatDateTime(selectedCandidate.vote_close_at)} />
                        <DetailField label="通过时间" value={formatDateTime(selectedCandidate.approved_at)} />
                        <DetailField label="驳回时间" value={formatDateTime(selectedCandidate.rejected_at)} />
                      </Box>

                      <Divider />

                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.25} flexWrap="wrap">
                        <Button
                          variant="contained"
                          color="success"
                          startIcon={<ThumbUpRoundedIcon />}
                          onClick={() => void handleStatusChange("approved")}
                          disabled={Boolean(detailBusyAction)}
                        >
                          {detailBusyAction === "approved" ? "处理中…" : "通过"}
                        </Button>
                        <Button
                          variant="contained"
                          color="error"
                          startIcon={<ThumbDownRoundedIcon />}
                          onClick={() => void handleStatusChange("rejected")}
                          disabled={Boolean(detailBusyAction)}
                        >
                          {detailBusyAction === "rejected" ? "处理中…" : "驳回"}
                        </Button>
                        <Button
                          variant="outlined"
                          color="warning"
                          startIcon={<HowToVoteRoundedIcon />}
                          onClick={() => void handleStatusChange("voting")}
                          disabled={Boolean(detailBusyAction)}
                        >
                          {detailBusyAction === "voting" ? "处理中…" : "恢复投票"}
                        </Button>
                      </Stack>

                      <DateTimePicker
                        label="投票截止时间"
                        value={deadlineValue}
                        onChange={(value) => setDeadlineValue(value)}
                        slotProps={{
                          textField: {
                            fullWidth: true,
                          },
                        }}
                      />

                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.25}>
                        <Button
                          variant="outlined"
                          onClick={() => {
                            const nextValue = selectedCandidate.vote_close_at
                              ? dayjs(selectedCandidate.vote_close_at).add(3, "day")
                              : dayjs().add(3, "day");
                            setDeadlineValue(nextValue);
                            void handleDeadlineSave(nextValue);
                          }}
                          disabled={Boolean(detailBusyAction)}
                        >
                          顺延 3 天
                        </Button>
                        <Button
                          variant="contained"
                          onClick={() => void handleDeadlineSave(deadlineValue)}
                          disabled={Boolean(detailBusyAction)}
                        >
                          {detailBusyAction === "deadline" ? "处理中…" : "保存截止时间"}
                        </Button>
                      </Stack>
                    </Stack>
                  </DialogContent>
                  <DialogActions>
                    <Button onClick={() => setDetailDialogOpen(false)}>关闭</Button>
                  </DialogActions>
                </>
              ) : null}
            </Dialog>
          </Stack>
        </Container>
      </Box>
    );
  }

  return (
    <LocalizationProvider
      dateAdapter={AdapterDayjs}
      adapterLocale="zh-cn"
      localeText={datePickerZhCN.components.MuiLocalizationProvider.defaultProps.localeText}
    >
      {content}
      <Snackbar
        open={toast.open}
        autoHideDuration={3200}
        onClose={() => setToast((currentToast) => ({ ...currentToast, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
      >
        <Alert
          onClose={() => setToast((currentToast) => ({ ...currentToast, open: false }))}
          severity={toast.severity}
          variant="filled"
          sx={{ width: "100%" }}
        >
          {toast.message}
        </Alert>
      </Snackbar>
    </LocalizationProvider>
  );
}

function FullscreenPanel({
  icon,
  title,
  description,
  children,
}: {
  icon: ReactNode;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        p: 2,
        bgcolor: "background.default",
      }}
    >
      <Card sx={{ width: "100%", maxWidth: 480 }}>
        <CardContent sx={{ p: 4 }}>
          <Stack spacing={1.5}>
            <Avatar sx={{ bgcolor: "action.hover", color: "text.primary", width: 44, height: 44 }}>{icon}</Avatar>
            <Typography variant="h5">{title}</Typography>
            <Typography variant="body2" color="text.secondary">
              {description}
            </Typography>
            {children}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}

function MetricCard({ label, value, icon }: { label: string; value: string | number; icon: ReactNode }) {
  return (
    <Card>
      <CardContent sx={{ p: 2 }}>
        <Stack spacing={1.25}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="body2" color="text.secondary">
              {label}
            </Typography>
            <Avatar
              variant="rounded"
              sx={{
                bgcolor: (theme) => alpha(theme.palette.primary.main, 0.12),
                color: "primary.main",
                width: 30,
                height: 30,
              }}
            >
              {icon}
            </Avatar>
          </Stack>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            {value}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

function DetailField({ label, value }: { label: string; value: string }) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 1.5,
        borderRadius: 2,
        bgcolor: (theme) => alpha(theme.palette.background.default, 0.45),
      }}
    >
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ mt: 0.5, fontWeight: 600, wordBreak: "break-word" }}>
        {value || "—"}
      </Typography>
    </Paper>
  );
}

export default App;
