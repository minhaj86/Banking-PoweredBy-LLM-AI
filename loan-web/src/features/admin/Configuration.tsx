import { useQuery } from "@tanstack/react-query";
import { Alert, Box, Paper, Stack, Typography } from "@mui/material";
import { api } from "../../api/client";
import { useAuth, scopeKey } from "../auth/Auth";
import { ErrorNotice, Loading, SectionTitle } from "../../components/Feedback";
import { money, percent } from "../../lib/format";
interface Config {
  product: {
    id: string;
    version: number;
    policyVersion: string;
    eligible: boolean;
    minAmount: string;
    maxAmount: string;
    minMonths: number;
    maxMonths: number;
    nominalAnnualRate: string;
    stressAnnualRate: string;
    minimumResidual: string;
    maximumDsti: string;
    evidenceFreshnessDays: number;
    assessmentValidityDays: number;
    requiredCategories: string[];
  };
  editingSupported: boolean;
  syntheticVerification: boolean;
  aiProvider: string;
}
export default function Configuration() {
  const { me } = useAuth();
  const query = useQuery({
    queryKey: [...scopeKey(me), "configuration"],
    queryFn: ({ signal }) => api<Config>("/staff/configuration", { signal }),
  });
  if (query.isPending) return <Loading />;
  if (query.error)
    return (
      <ErrorNotice error={query.error} retry={() => void query.refetch()} />
    );
  const { product: p } = query.data;
  return (
    <>
      <SectionTitle
        title="Current product & policy"
        detail="Read-only configuration supplied by loan-service. Approved terms retain their immutable product and policy snapshots."
      />
      <Alert severity="info" sx={{ mb: 3 }}>
        There is no versioned configuration mutation API. Changes must follow
        the controlled backend deployment process. Identity and role
        administration remain in Keycloak.
      </Alert>
      <Paper sx={{ p: 4 }}>
        <Stack
          sx={{
            gap: 2,
          }}
        >
          <Typography variant="h3" component="h2">
            {p.id} · version {p.version}
          </Typography>
          <Typography>
            Policy {p.policyVersion} ·{" "}
            {p.eligible ? "Eligible" : "Not eligible"}
          </Typography>
          <Box component="dl" className="form-grid">
            <Box>
              <Typography component="dt" color="text.secondary">
                Amount range
              </Typography>
              <Typography component="dd" sx={{ m: 0 }}>
                {money(p.minAmount)}–{money(p.maxAmount)}
              </Typography>
            </Box>
            <Box>
              <Typography component="dt" color="text.secondary">
                Term range
              </Typography>
              <Typography component="dd" sx={{ m: 0 }}>
                {p.minMonths}–{p.maxMonths} months
              </Typography>
            </Box>
            <Box>
              <Typography component="dt" color="text.secondary">
                Nominal annual interest
              </Typography>
              <Typography component="dd" sx={{ m: 0 }}>
                {percent(p.nominalAnnualRate)} · not APR
              </Typography>
            </Box>
            <Box>
              <Typography component="dt" color="text.secondary">
                Stress annual interest
              </Typography>
              <Typography component="dd" sx={{ m: 0 }}>
                {percent(p.stressAnnualRate)}
              </Typography>
            </Box>
          </Box>
          <Typography>
            Minimum residual: {money(p.minimumResidual)} · Maximum debt service
            ratio: {p.maximumDsti}
          </Typography>
          <Typography>
            Evidence freshness: {p.evidenceFreshnessDays} days · Assessment
            validity: {p.assessmentValidityDays} days
          </Typography>
          <Typography>
            Required categories: {p.requiredCategories.join(", ")}
          </Typography>
          <Typography>
            AI provider: {query.data.aiProvider} · Synthetic verification:{" "}
            {query.data.syntheticVerification
              ? "enabled (local only)"
              : "disabled"}
          </Typography>
        </Stack>
      </Paper>
    </>
  );
}
