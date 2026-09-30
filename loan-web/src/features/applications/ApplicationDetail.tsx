import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useLocation, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { api, mutationIntent } from "../../api/client";
import type { Intent } from "../../api/client";
import type {
  Application,
  InformationRequest,
  Processing,
  Timeline,
} from "../../types/api";
import { useAuth, scopeKey } from "../auth/Auth";
import {
  ErrorNotice,
  Loading,
  SectionTitle,
  Status,
} from "../../components/Feedback";
import { Documents } from "../documents/Documents";
import { ApplicationSummary } from "./ApplicationSummary";
import { editable, timestamp, words } from "../../lib/format";
import { useBoundedPoll } from "../../hooks/useBoundedPoll";
export default function ApplicationDetail() {
  const { id } = useParams();
  const { me } = useAuth();
  const location = useLocation();
  const cache = useQueryClient();
  const poll = useBoundedPoll();
  const key = [...scopeKey(me), "application", id];
  const result = useQuery({
    queryKey: key,
    queryFn: ({ signal }) =>
      api<Application>(`/loan-applications/${id}`, { signal }),
    refetchInterval: (q) =>
      q.state.data &&
      ["SUBMITTED", "ASSESSMENT_PENDING"].includes(q.state.data.state)
        ? poll(q.state.dataUpdateCount)
        : false,
  });
  const timeline = useQuery({
    queryKey: [...key, "timeline", result.data?.version],
    queryFn: ({ signal }) =>
      api<Timeline[]>(`/loan-applications/${id}/timeline`, { signal }),
    enabled: Boolean(result.data),
  });
  const requests = useQuery({
    queryKey: [...key, "information", result.data?.version],
    queryFn: ({ signal }) =>
      api<InformationRequest[]>(
        `/loan-applications/${id}/information-requests`,
        { signal },
      ),
    enabled: Boolean(result.data),
  });
  const processing = useQuery({
    queryKey: [...key, "processing", result.data?.version],
    queryFn: ({ signal }) =>
      api<Processing>(`/loan-applications/${id}/processing-status`, { signal }),
    enabled: Boolean(result.data),
  });
  const [dialog, setDialog] = useState<"withdraw" | "review">();
  const [reason, setReason] = useState("");
  const [explanation, setExplanation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const intent = useRef<Intent | undefined>(undefined);
  async function mutate() {
    if (!result.data) return;
    setBusy(true);
    setError(undefined);
    const body =
      dialog === "review" ? { reasonCode: reason, explanation } : undefined;
    intent.current = mutationIntent(
      { action: dialog, version: result.data.version, body },
      intent.current,
    );
    try {
      await api(
        `/loan-applications/${id}/${dialog === "review" ? "review-requests" : "withdraw"}`,
        {
          method: "POST",
          body,
          intent: intent.current,
          version: dialog === "withdraw" ? result.data.version : undefined,
        },
      );
      setDialog(undefined);
      intent.current = undefined;
      await cache.invalidateQueries({ queryKey: [...scopeKey(me)] });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  if (result.isPending) return <Loading />;
  if (result.error)
    return (
      <ErrorNotice error={result.error} retry={() => void result.refetch()} />
    );
  const a = result.data;
  return (
    <>
      <Button component={Link} to="/app" sx={{ mb: 2 }}>
        ← My applications
      </Button>
      <SectionTitle
        eyebrow={`Reference ${a.id}`}
        title="Your application"
        detail={`Revision ${a.revision} · Updated ${timestamp(a.updatedAt)}`}
      />
      {(
        location.state as {
          submitted?: boolean;
        } | null
      )?.submitted && (
        <Alert severity="info" sx={{ mb: 3 }}>
          Submission accepted for processing. This is not approval.
        </Alert>
      )}
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "minmax(0,1fr) 300px" },
          gap: 3,
        }}
      >
        <Stack
          sx={{
            gap: 3,
          }}
        >
          <Paper sx={{ p: 3 }}>
            <Stack
              direction="row"
              sx={{
                justifyContent: "space-between",
                gap: 2,
                alignItems: "center",
              }}
            >
              <Status state={a.state} />
              <Typography variant="caption">Version {a.version}</Typography>
            </Stack>
            <Typography sx={{ my: 2 }}>
              {a.state === "DOCUMENTS_REQUIRED"
                ? "Your reviewer needs more information. Read the request, update the draft and submit a new revision."
                : a.state === "APPROVED"
                  ? "Your application was approved. Agreement acceptance and any funding are separate steps."
                  : a.state === "MANUAL_REVIEW"
                    ? "Your evidence is ready for human review. A person will make the credit decision."
                    : a.state === "DRAFT"
                      ? "Your saved draft is ready when you are."
                      : a.state === "ASSESSMENT_FAILED"
                        ? "Processing requires attention. Your saved application is available for correction or human assistance."
                        : "Follow processing and review updates here."}
            </Typography>
            {a.decisionExplanation && (
              <Alert severity={a.state === "REJECTED" ? "warning" : "info"}>
                {a.decisionExplanation}
              </Alert>
            )}
            <Stack
              direction="row"
              sx={{
                gap: 1,
                flexWrap: "wrap",
                mt: 2,
              }}
            >
              {editable(a.state) && (
                <Button
                  variant="contained"
                  component={Link}
                  to={`/app/applications/${a.id}/edit`}
                >
                  Edit / add evidence
                </Button>
              )}
              {a.loanId && (
                <Button
                  variant="contained"
                  component={Link}
                  to={`/app/loans/${a.loanId}`}
                >
                  View approved loan
                </Button>
              )}
              <Button onClick={() => void result.refetch()}>
                Refresh status
              </Button>
            </Stack>
          </Paper>
          {requests.data && requests.data.length > 0 && (
            <Paper sx={{ p: 3 }}>
              <Typography component="h2" variant="h3" gutterBottom>
                Information requests
              </Typography>
              <Stack
                sx={{
                  gap: 2,
                }}
              >
                {requests.data.map((r) => (
                  <Box key={r.id}>
                    <Typography
                      sx={{
                        fontWeight: 600,
                      }}
                    >
                      Revision {r.revision} · {timestamp(r.createdAt)}
                    </Typography>
                    <Typography>{r.explanation}</Typography>
                  </Box>
                ))}
              </Stack>
            </Paper>
          )}
          <Paper sx={{ p: 3 }}>
            <Typography component="h2" variant="h3" sx={{ mb: 3 }}>
              Your declared details
            </Typography>
            <ApplicationSummary input={a.application} />
          </Paper>
          <Paper sx={{ p: 3 }}>
            <Typography component="h2" variant="h3" gutterBottom>
              Supporting documents
            </Typography>
            <Documents applicationId={a.id} />
          </Paper>
        </Stack>
        <Stack
          sx={{
            gap: 3,
          }}
        >
          <Paper sx={{ p: 3 }}>
            <Typography component="h2" variant="h3" gutterBottom>
              Progress
            </Typography>
            {timeline.error ? (
              <ErrorNotice error={timeline.error} />
            ) : (
              timeline.data?.map((event, i) => (
                <Box
                  key={i}
                  sx={{ pl: 2, py: 1.5, borderLeft: "2px solid #b4cbb7" }}
                >
                  <Typography
                    variant="body2"
                    sx={{
                      fontWeight: 600,
                    }}
                  >
                    {words(event.action)}
                  </Typography>
                  <Typography variant="caption">
                    Revision {event.revision} · {timestamp(event.occurred_at)}
                  </Typography>
                </Box>
              ))
            )}
            <Divider sx={{ my: 2 }} />
            <Typography variant="body2">
              Processing: {words(processing.data?.state ?? a.state)}
            </Typography>
          </Paper>
          <Paper sx={{ p: 3 }}>
            <Typography component="h2" variant="h4" gutterBottom>
              Need to correct something?
            </Typography>
            <Typography variant="body2" sx={{ mb: 2 }}>
              Ask for human reconsideration or explain a correction. This does
              not change a credit decision automatically.
            </Typography>
            <Button variant="outlined" onClick={() => setDialog("review")}>
              Request human review
            </Button>
            {!["APPROVED", "REJECTED", "WITHDRAWN"].includes(a.state) && (
              <Button
                color="error"
                sx={{ mt: 2 }}
                onClick={() => setDialog("withdraw")}
              >
                Withdraw application
              </Button>
            )}
          </Paper>
        </Stack>
      </Box>
      <Dialog
        open={Boolean(dialog)}
        onClose={() => !busy && setDialog(undefined)}
        fullWidth
        aria-labelledby="request-title"
      >
        <DialogTitle id="request-title">
          {dialog === "withdraw"
            ? "Withdraw this application?"
            : "Request human reconsideration"}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ pt: 1 }}>
            {Boolean(error) && <ErrorNotice error={error} />}
            <Typography>
              {dialog === "withdraw"
                ? "The backend will close this application if its current state permits withdrawal. No loan agreement is cancelled by this action."
                : "Describe the correction or review you need. Your request is recorded for an authorised human reviewer."}
            </Typography>
            {dialog === "review" && (
              <>
                <TextField
                  label="Reason code"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  helperText="For example CORRECTION or RECONSIDERATION"
                />
                <TextField
                  label="Explanation"
                  value={explanation}
                  onChange={(e) => setExplanation(e.target.value)}
                  multiline
                  minRows={3}
                />
              </>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialog(undefined)} disabled={busy}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={
              busy || (dialog === "review" && (!reason || !explanation))
            }
            onClick={() => void mutate()}
          >
            {busy ? "Sending…" : "Confirm request"}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
