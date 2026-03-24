import { alpha, createTheme, type PaletteMode } from "@mui/material/styles";

function getPalette(mode: PaletteMode) {
  if (mode === "dark") {
    const borderColor = alpha("#cbd5e1", 0.14);
    return {
      mode,
      primary: { main: "#60a5fa" },
      secondary: { main: "#818cf8" },
      success: { main: "#34d399" },
      warning: { main: "#f59e0b" },
      error: { main: "#f87171" },
      background: {
        default: "#0b1220",
        paper: "#111a2c",
      },
      divider: borderColor,
      borderColor,
      cardShadow: "0 10px 28px rgba(2, 6, 23, 0.45)",
      inputBg: alpha("#0f172a", 0.72),
      bodyBg: "#0b1220",
    } as const;
  }

  const borderColor = alpha("#0f172a", 0.08);
  return {
    mode,
    primary: { main: "#2563eb" },
    secondary: { main: "#4f46e5" },
    success: { main: "#0f766e" },
    warning: { main: "#b45309" },
    error: { main: "#dc2626" },
    background: {
      default: "#f4f7fc",
      paper: "#ffffff",
    },
    divider: borderColor,
    borderColor,
    cardShadow: "0 8px 24px rgba(15, 23, 42, 0.04)",
    inputBg: alpha("#ffffff", 0.9),
    bodyBg: "#f4f7fc",
  } as const;
}

export function createAppTheme(mode: PaletteMode) {
  const palette = getPalette(mode);

  return createTheme({
    palette: {
      mode: palette.mode,
      primary: palette.primary,
      secondary: palette.secondary,
      success: palette.success,
      warning: palette.warning,
      error: palette.error,
      background: palette.background,
      divider: palette.divider,
    },
    shape: {
      borderRadius: 12,
    },
    typography: {
      fontFamily: ["Inter", "SF Pro Display", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "sans-serif"].join(", "),
      h5: {
        fontWeight: 700,
      },
      h6: {
        fontWeight: 700,
      },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            backgroundColor: palette.bodyBg,
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: "none",
          },
        },
      },
      MuiCard: {
        defaultProps: {
          elevation: 0,
        },
        styleOverrides: {
          root: {
            backgroundImage: "none",
            border: `1px solid ${palette.borderColor}`,
            boxShadow: palette.cardShadow,
          },
        },
      },
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: "none",
            fontWeight: 600,
            borderRadius: 10,
          },
          contained: {
            boxShadow: "none",
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 10,
            backgroundColor: palette.inputBg,
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontWeight: 600,
          },
        },
      },
    },
  });
}
