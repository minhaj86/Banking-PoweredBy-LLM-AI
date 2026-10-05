import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { api, mutationIntent } from "../../api/client";
import type { Intent } from "../../api/client";
import type {
  Application,
  Assessment,
  Processing,
  Product,
  ReviewRequest,
  Verification,
} from "../../types/api";
import { useAuth, scopeKey } from "../auth/Auth";
import {
  ErrorNotice,
  Loading,
  SectionTitle,
  Status,
} from "../../components/Feedback";
import { ApplicationSummary } from "../applications/ApplicationSummary";
import { Documents } from "../documents/Documents";
import { AssessmentPanel } from "./AssessmentPanel";
import { DecisionControls } from "./DecisionControls";
import { timestamp, words } from "../../lib/format";
export default function ReviewWorkspace() {
  const { id } = useParams();
  const { me } = useAuth();
  const cache = useQueryClient();
  const key = [...scopeKey(me), "staff-application", id];
  const review = Boolean(me?.capabilities.includes("REVIEW"));
  const compliance = Boolean(me?.capabilities.includes("COMPLIANCE"));
  const app = useQuery({
    queryKey: key,
    queryFn: ({ signal }) =>
      api<Application>(`/loan-applications/${id}`, { signal }),
  });
  const assessment = useQuery({
    queryKey: [...key, "assessment", app.data?.revision],
    queryFn: ({ signal }) =>
      api<Assessment>(`/loan-applications/${id}/assessment`, { signal }),
    enabled: review && Boolean(app.data?.revision),
  });
  const verification = useQuery({
    queryKey: [...key, "verifications", app.data?.version],
    queryFn: ({ signal }) =>
      api<Verification[]>(`/loan-applications/${id}/verifications`, { signal }),
    enabled: (review || compliance) && Boolean(app.data?.revision),
  });
  const processing = useQuery({
    queryKey: [...key, "processing", app.data?.version],
    queryFn: ({ signal }) =>
      api<Processing>(`/loan-applications/${id}/processing-status`, { signal }),
    enabled: Boolean(app.data),
  });
  const requests = useQuery({
    queryKey: [...key, "review-requests"],
    queryFn: ({ signal }) =>
      api<ReviewRequest[]>(`/loan-applications/${id}/review-requests`, {
        signal,
      }),
    enabled: Boolean(app.data),
  });
  const audit = useQuery({
    queryKey: [...key, "audit", app.data?.version],
    queryFn: ({ signal }) =>
      api<
        {
          id: string;
          action: string;
          actor: string;
          revision: number;
          occurred_at: string;
        }[]
      >(`/operations/applications/${id}/audit`, { signal }),
    enabled: Boolean(app.data),
  });
  const product = useQuery({
    queryKey: ["public-product"],
    queryFn: ({ signal }) => api<Product>("/bff/product", { signal }),
  });
  const onDone = async () => {
    await cache.invalidateQueries({ queryKey: [...scopeKey(me)] });
  };
  if (app.isPending) return <Loading />;
  if (app.error)
    return <ErrorNotice error={app.error} retry={() => void app.refetch()} />;
  const a = app.data;
  return (
    <>
      <Button component={Link} to="/staff/applications" sx={{ mb: 2 }}>
        ← Application queue
      </Button>
      <SectionTitle
        eyebrow={`Application ${a.id}`}
        title="Review workspace"
        detail={`Revision ${a.revision} · version ${a.version} · Last updated ${timestamp(a.updatedAt)}`}
      />
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          mb: 3,
        }}
      >
        <Status state={a.state} />
        <Button onClick={() => void onDone()}>Reload current revision</Button>
      </Stack>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: {
            xs: "1fr",
            lg: "minmax(0,1.3fr) minmax(300px,1fr)",
          },
          gap: 3,
          alignItems: "start",
        }}
      >
        <Stack
          sx={{
            gap: 3,
          }}
        >
          <Paper sx={{ p: 3 }}>
            <Typography variant="h3" component="h2" gutterBottom>
              Applicant declaration
            </Typography>
            <ApplicationSummary input={a.application} />
          </Paper>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h3" component="h2" gutterBottom>
              Evidence workspace
            </Typography>
            <Documents applicationId={a.id} />
          </Paper>
          {requests.data && requests.data.length > 0 && (
            <Paper sx={{ p: 3 }}>
              <Typography variant="h3" component="h2" gutterBottom>
                Human reconsideration requests
              </Typography>
              {requests.data.map((r) => (
                <Box key={r.id} sx={{ mb: 2 }}>
                  <Typography
                    sx={{
                      fontWeight: 600,
                    }}
                  >
                    {r.request.reasonCode} · revision {r.revision}
                  </Typography>
                  <Typography>{r.request.explanation}</Typography>
                  <Typography variant="caption">
                    {timestamp(r.createdAt)}
                  </Typography>
                </Box>
              ))}
            </Paper>
          )}
          <Paper sx={{ p: 3 }}>
            <Typography variant="h3" component="h2" gutterBottom>
              Audit & processing
            </Typography>
            {processing.data?.jobs?.map((j) => (
              <Box key={j.id} sx={{ mb: 2 }}>
                <Typography>
                  {words(j.kind)} · {words(j.state)} · {j.attempts} attempt(s)
                </Typography>
                {j.lastError && (
                  <Typography color="error">{words(j.lastError)}</Typography>
                )}
                <Typography variant="caption">
                  Next eligible {timestamp(j.availableAt)}
                </Typography>
              </Box>
            ))}
            {audit.error && <ErrorNotice error={audit.error} />}
            <Box
              role="region"
              aria-label="Audit history"
              tabIndex={0}
              sx={{ maxHeight: 400, overflow: "auto" }}
            >
              {audit.data?.map((event) => (
                <Box
                  key={event.id}
                  sx={{ py: 1, borderBottom: "1px solid #e2e8df" }}
                >
                  <Typography variant="body2">
                    {words(event.action)} · revision {event.revision}
                  </Typography>
                  <Typography variant="caption">
                    {timestamp(event.occurred_at)}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Paper>
        </Stack>
        <Stack
          sx={{
            gap: 3,
          }}
        >
          {assessment.data ? (
            <>
              <AssessmentPanel
                assessment={assessment.data}
                verifications={verification.data}
              />
              <DecisionControls
                key={`${a.revision}:${a.state}`}
                application={a}
                assessment={assessment.data}
                product={product.data}
                onDone={onDone}
              />
            </>
          ) : review && assessment.error ? (
            <ErrorNotice error={assessment.error} />
          ) : (
            <Alert severity="info">
              {review
                ? "A current completed assessment is required before a credit decision."
                : "Your role does not include credit decisions or AI underwriting advice."}
            </Alert>
          )}
          {compliance && verification.data && (
            <ComplianceControl
              application={a}
              gates={verification.data}
              onDone={onDone}
            />
          )}{" "}
          {a.loanId && (
            <Paper sx={{ p: 3 }}>
              <Typography variant="h3" component="h2" gutterBottom>
                Approved loan record
              </Typography>
              <Button component={Link} to={`/app/loans/${a.loanId}`}>
                Inspect agreement, account and balance
              </Button>
            </Paper>
          )}
        </Stack>
      </Box>
    </>
  );
}
function ComplianceControl({
  application,
  gates,
  onDone,
}: {
  application: Application;
  gates: Verification[];
  onDone: () => Promise<unknown>;
}) {
  const [outcome, setOutcome] = useState("SATISFIED");
  const [reference, setReference] = useState("");
  const [explanation, setExplanation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [confirm, setConfirm] = useState(false);
  const intent = useRef<Intent | undefined>(undefined);
  const eligible = gates.some(
    (g) => g.gate === "COMPLIANCE" && g.status === "REVIEW_REQUIRED",
  );
  async function record() {
    setBusy(true);
    setError(undefined);
    const body = {
      outcome,
      evidenceReference: reference,
      restrictedExplanation: explanation,
    };
    intent.current = mutationIntent(
      { body, version: application.version },
      intent.current,
    );
    try {
      await api(`/loan-applications/${application.id}/verification-reviews`, {
        method: "POST",
        body,
        intent: intent.current,
        version: application.version,
      });
      intent.current = undefined;
      setConfirm(false);
      await onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Paper sx={{ p: 3 }}>
      <Typography component="h2" variant="h3" gutterBottom>
        Compliance review
      </Typography>
      <Stack
        sx={{
          gap: 2,
        }}
      >
        {gates.map((g) => (
          <Typography key={g.gate}>
            {words(g.gate)} · {words(g.status)}
            {g.synthetic ? " · synthetic" : ""}
          </Typography>
        ))}
        <Alert severity="info">
          This records a compliance finding only. It does not approve credit or
          replace identity and obligation verification.
        </Alert>
        {Boolean(error) && <ErrorNotice error={error} />}
        <TextField
          select
          label="Compliance outcome"
          value={outcome}
          onChange={(e) => setOutcome(e.target.value)}
        >
          <MenuItem value="SATISFIED">Satisfied</MenuItem>
          <MenuItem value="BLOCKED">Blocked</MenuItem>
        </TextField>
        <TextField
          label="Independent evidence reference"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
        />
        <TextField
          label="Restricted compliance explanation"
          value={explanation}
          onChange={(e) => setExplanation(e.target.value)}
          multiline
          minRows={3}
        />
        {confirm ? (
          <Alert
            severity="warning"
            action={
              <Button disabled={busy} onClick={() => void record()}>
                Confirm finding
              </Button>
            }
          >
            Record {words(outcome)} on revision {application.revision}, version{" "}
            {application.version}?
          </Alert>
        ) : (
          <Button
            variant="contained"
            disabled={!eligible || !reference || !explanation || busy}
            onClick={() => setConfirm(true)}
          >
            Review compliance finding
          </Button>
        )}
        {!eligible && (
          <Typography variant="caption">
            The backend permits manual compliance resolution only for a current
            provider REVIEW_REQUIRED result.
          </Typography>
        )}
      </Stack>
    </Paper>
  );
}
