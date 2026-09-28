import { createTheme } from "@mui/material/styles";
export const theme = createTheme({
  palette: {
    primary: { main: "#173c35", light: "#315d51", contrastText: "#fff" },
    secondary: { main: "#866129" },
    background: { default: "#f5f6f2", paper: "#ffffff" },
    text: { primary: "#203a34", secondary: "#52635c" },
    success: { main: "#246244" },
    warning: { main: "#855900" },
    error: { main: "#b23436" },
  },
  typography: {
    fontFamily: 'Inter, "Segoe UI", system-ui, sans-serif',
    h1: {
      fontFamily: "Georgia, serif",
      fontSize: "clamp(2.5rem, 5vw, 4.8rem)",
      fontWeight: 400,
      lineHeight: 1.05,
    },
    h2: {
      fontFamily: "Georgia, serif",
      fontSize: "clamp(2rem, 4vw, 3rem)",
      fontWeight: 400,
    },
    h3: { fontSize: "1.4rem", fontWeight: 600 },
    h4: { fontSize: "1.15rem", fontWeight: 600 },
    button: { textTransform: "none", fontWeight: 600 },
    body1: { lineHeight: 1.65 },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { borderRadius: 7, minHeight: 44, paddingInline: 20 },
      },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { root: { border: "1px solid #dde4dc" } },
    },
    MuiTextField: {
      defaultProps: { fullWidth: true, size: "small", variant: "outlined" },
    },
    MuiLink: { defaultProps: { underline: "hover" } },
    MuiCssBaseline: {
      styleOverrides: {
        "a:focus-visible, button:focus-visible, input:focus-visible, [tabindex]:focus-visible":
          { outline: "3px solid #a77a31", outlineOffset: 3 },
        "::selection": { background: "#d9e9df" },
      },
    },
  },
});
