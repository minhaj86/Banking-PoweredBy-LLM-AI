import { createContext, useContext, useEffect, useRef } from "react";
import type { ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, useLocation } from "react-router-dom";
import {
  Alert,
  Button,
  CircularProgress,
  Stack,
  Typography,
} from "@mui/material";
import { api, ApiError, resetCsrf } from "../../api/client";
import type { Me } from "../../types/api";
const AuthContext = createContext<{
  me: Me | null;
  loading: boolean;
  unavailable: boolean;
  reload: () => Promise<void>;
}>({ me: null, loading: true, unavailable: false, reload: async () => {} });
export function AuthProvider({ children }: { children: ReactNode }) {
  const cache = useQueryClient();
  const session = useQuery({
    queryKey: ["session"],
    queryFn: async () => {
      try {
        return await api<Me>("/bff/me");
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
    retry: false,
    refetchInterval: 45000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    staleTime: 0,
    gcTime: 0,
  });
  const me = session.error ? null : (session.data ?? null);
  const fingerprint = me
    ? `${me.tenant}:${me.identityKey}:${me.roles.join(",")}:${me.capabilities.join(",")}`
    : "anonymous";
  const previous = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (previous.current !== undefined && previous.current !== fingerprint) {
      cache.removeQueries({ predicate: (q) => q.queryKey[0] !== "session" });
      resetCsrf();
    }
    previous.current = fingerprint;
  }, [fingerprint, cache]);
  useEffect(() => {
    const invalidate = () => {
      void cache.cancelQueries();
      cache.removeQueries({ predicate: (q) => q.queryKey[0] !== "session" });
      cache.setQueryData(["session"], null);
      resetCsrf();
    };
    globalThis.addEventListener("session-invalid", invalidate);
    return () => globalThis.removeEventListener("session-invalid", invalidate);
  }, [cache]);
  return (
    <AuthContext.Provider
      value={{
        me,
        loading: session.isPending,
        unavailable: Boolean(session.error),
        reload: async () => {
          await session.refetch();
        },
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export const useAuth = () => useContext(AuthContext);
export const scopeKey = (me: Me | null) => [
  me?.tenant ?? "none",
  me?.identityKey ?? "anonymous",
];
export function RequireSession({
  children,
  staff = false,
}: {
  children: ReactNode;
  staff?: boolean;
}) {
  const { me, loading, unavailable, reload } = useAuth();
  const location = useLocation();
  if (loading) return <CircularProgress aria-label="Checking secure session" />;
  if (unavailable)
    return (
      <Alert
        severity="error"
        action={<Button onClick={() => void reload()}>Retry</Button>}
      >
        Authentication is unavailable. Access is paused.
      </Alert>
    );
  if (!me)
    return (
      <Stack spacing={2} sx={{ maxWidth: 560, py: 6 }}>
        <Typography variant="h2">Sign in to continue</Typography>
        <Typography>
          Your saved applications are held securely in the service.
        </Typography>
        <Button
          variant="contained"
          href={`/bff/login?returnTo=${encodeURIComponent(location.pathname)}&staff=${staff}`}
        >
          Sign in securely
        </Button>
      </Stack>
    );
  if (staff && me.staffStepUpRequired)
    return (
      <Alert
        severity="info"
        action={
          <Button href="/bff/login?returnTo=/staff&staff=true">
            Complete MFA
          </Button>
        }
      >
        Staff access requires multifactor authentication.
      </Alert>
    );
  if (
    staff &&
    !me.capabilities.some((c) =>
      ["REVIEW", "COMPLIANCE", "OPERATE", "CONFIGURATION"].includes(c),
    )
  )
    return <Navigate to="/app" replace />;
  return <div key={`${me.tenant}:${me.identityKey}`}>{children}</div>;
}
