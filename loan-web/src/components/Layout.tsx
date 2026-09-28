import {
  AppBar,
  Box,
  Button,
  Container,
  Divider,
  Link as MuiLink,
  Stack,
  Toolbar,
  Typography,
} from "@mui/material";
import { Link, NavLink, Outlet } from "react-router-dom";
import { logout } from "../api/client";
import { useAuth } from "../features/auth/Auth";
import { APP_NAME } from "../app/brand";
export function Layout() {
  const { me } = useAuth();
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <AppBar
        position="static"
        color="transparent"
        elevation={0}
        sx={{ bgcolor: "background.paper", borderBottom: "1px solid #dde4dc" }}
      >
        <Container maxWidth="lg">
          <Toolbar
            disableGutters
            sx={{
              gap: 2,
              minHeight: { xs: 76, md: 86 },
              flexWrap: "wrap",
              py: 1,
            }}
          >
            <MuiLink
              component={Link}
              to="/"
              color="primary"
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 1.2,
                mr: "auto",
                textDecoration: "none",
              }}
            >
              <Box
                aria-hidden
                sx={{
                  width: 30,
                  height: 34,
                  border: "2px solid",
                  borderRadius: "16px 16px 4px 4px",
                  position: "relative",
                  "&:after": {
                    content: '""',
                    position: "absolute",
                    inset: "7px",
                    borderLeft: "2px solid",
                    transform: "rotate(25deg)",
                  },
                }}
              />
              <Typography sx={{ fontFamily: "Georgia, serif", fontSize: 27 }}>
                {APP_NAME}
                <span style={{ color: "#866129" }}>.</span>
              </Typography>
            </MuiLink>
            <Stack
              component="nav"
              aria-label="Main navigation"
              direction="row"
              spacing={{ xs: 0.5, md: 2 }}
              sx={{
                alignItems: "center",
                flexWrap: "wrap",
              }}
            >
              {me && (
                <Button component={NavLink} to="/app">
                  My applications
                </Button>
              )}
              {me &&
                (me.staffStepUpRequired ||
                  me.capabilities.some((c) =>
                    [
                      "REVIEW",
                      "COMPLIANCE",
                      "OPERATE",
                      "CONFIGURATION",
                    ].includes(c),
                  )) && (
                  <Button component={NavLink} to="/staff">
                    Staff workspace
                  </Button>
                )}
              {me ? (
                <>
                  <Button href={me.accountUrl}>My account</Button>
                  <Button variant="outlined" onClick={() => void logout()}>
                    Sign out
                  </Button>
                </>
              ) : (
                <Button variant="outlined" href="/bff/login?returnTo=/app">
                  Sign in
                </Button>
              )}
            </Stack>
          </Toolbar>
        </Container>
      </AppBar>
      <Container
        component="main"
        id="main"
        maxWidth="lg"
        tabIndex={-1}
        sx={{ py: { xs: 3, md: 6 }, minHeight: "75vh" }}
      >
        <Outlet />
      </Container>
      <Container maxWidth="lg" component="footer" sx={{ pb: 3 }}>
        <Divider sx={{ mb: 3 }} />
        <Stack
          direction={{ xs: "column", md: "row" }}
          sx={{
            justifyContent: "space-between",
            gap: 1,
          }}
        >
          <Typography variant="caption" color="text.secondary">
            {APP_NAME} · Local origination demo · Synthetic data only
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Human review. No agreement signing or funding. Times shown in UTC.
          </Typography>
        </Stack>
      </Container>
    </>
  );
}
