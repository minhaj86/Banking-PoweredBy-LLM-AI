import { lazy, Suspense, useEffect } from "react";
import { BrowserRouter, Route, Routes, useLocation } from "react-router-dom";
import { CssBaseline, ThemeProvider, Typography } from "@mui/material";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { theme } from "./theme";
import { AuthProvider, RequireSession } from "../features/auth/Auth";
import { Layout } from "../components/Layout";
import { Loading } from "../components/Feedback";
import { PublicPage } from "../features/applications/PublicPage";
import { Dashboard } from "../features/applications/Dashboard";
import { ApiError } from "../api/client";
const Wizard = lazy(() => import("../features/applications/Wizard"));
const ApplicationDetail = lazy(
  () => import("../features/applications/ApplicationDetail"),
);
const LoanDetail = lazy(() => import("../features/loans/LoanDetail"));
const StaffRoutes = lazy(() => import("../features/admin/StaffRoutes"));
const query = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10000,
      gcTime: 120000,
      refetchOnWindowFocus: false,
      retry: (count, e) =>
        count < 1 && !(e instanceof ApiError && e.status < 500),
    },
    mutations: { retry: false },
  },
});
function RouteFocus() {
  const location = useLocation();
  useEffect(() => {
    document
      .querySelector<HTMLElement>("#main")
      ?.focus({ preventScroll: true });
    globalThis.scrollTo(0, 0);
  }, [location.pathname]);
  return null;
}
export function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={query}>
        <AuthProvider>
          <BrowserRouter>
            <RouteFocus />
            <Suspense fallback={<Loading label="Opening your workspace" />}>
              <Routes>
                <Route element={<Layout />}>
                  <Route index element={<PublicPage />} />
                  <Route
                    path="app"
                    element={
                      <RequireSession>
                        <Dashboard />
                      </RequireSession>
                    }
                  />
                  <Route
                    path="app/new"
                    element={
                      <RequireSession>
                        <Wizard />
                      </RequireSession>
                    }
                  />
                  <Route
                    path="app/applications/:id/edit"
                    element={
                      <RequireSession>
                        <Wizard />
                      </RequireSession>
                    }
                  />
                  <Route
                    path="app/applications/:id"
                    element={
                      <RequireSession>
                        <ApplicationDetail />
                      </RequireSession>
                    }
                  />
                  <Route
                    path="app/loans/:id"
                    element={
                      <RequireSession>
                        <LoanDetail />
                      </RequireSession>
                    }
                  />
                  <Route
                    path="staff/*"
                    element={
                      <RequireSession staff>
                        <StaffRoutes />
                      </RequireSession>
                    }
                  />
                  <Route
                    path="*"
                    element={
                      <Typography component="h1" variant="h2">
                        This page is unavailable.
                      </Typography>
                    }
                  />
                </Route>
              </Routes>
            </Suspense>
          </BrowserRouter>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
