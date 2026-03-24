import React, { useMemo } from "react";
import ReactDOM from "react-dom/client";
import { CssBaseline, ThemeProvider, useMediaQuery } from "@mui/material";
import App from "./App";
import { createAppTheme } from "./theme";
import "./styles.css";

function RootApp() {
  const prefersDarkMode = useMediaQuery("(prefers-color-scheme: dark)", { noSsr: true });
  const theme = useMemo(() => createAppTheme(prefersDarkMode ? "dark" : "light"), [prefersDarkMode]);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline enableColorScheme />
      <App />
    </ThemeProvider>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <RootApp />
  </React.StrictMode>,
);
