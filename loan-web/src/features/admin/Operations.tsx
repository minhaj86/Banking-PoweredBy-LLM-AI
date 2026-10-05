import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { api, mutationIntent } from "../../api/client";
import type { Intent } from "../../api/client";
import type { Job, Page } from "../../types/api";
import { useAuth, scopeKey } from "../auth/Auth";
import {
  Empty,
  ErrorNotice,
  Loading,
  SectionTitle,
  Status,
} from "../../components/Feedback";
import { timestamp, words } from "../../lib/format";
interface OperationJob extends Job {
  applicationId: string;
  loanId: string | null;
}
export default function Operations() {
  const { me } = useAuth();
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("FAILED");
  const [selected, setSelected] = useState<OperationJob>();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const intent = useRef<Intent | undefined>(undefined);
  const jobs = useQuery({
    queryKey: [...scopeKey(me), "operations-jobs", page, status],
    queryFn: ({ signal }) =>
      api<Page<OperationJob>>(
        `/operations/jobs?page=${page}&size=20&status=${status}`,
        { signal },
      ),
  });
  async function retry() {
    if (!selected) return;
    setBusy(true);
    setError(undefined);
    const body = { reasonCode: "OPERATIONAL_RECOVERY", explanation: reason };
    intent.current = mutationIntent({ id: selected.id, body }, intent.current);
    try {
      await api(
        `/operations/applications/${selected.applicationId}/jobs/${selected.id}/retry`,
        { method: "POST", body, intent: intent.current },
      );
      setSelected(undefined);
      intent.current = undefined;
      await cache.invalidateQueries({ queryKey: [...scopeKey(me)] });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <SectionTitle
        title="Operational recovery"
        detail="Inspect durable jobs and retry eligible failures. Recovery preserves the existing loan, stable remote reference and any account already created."
      />
      <Alert severity="info" sx={{ mb: 3 }}>
        A retry resumes the recorded operation. It cannot bypass human approval,
        edit a job payload or change a financial balance. Provisioning account
        references are visible on each loan.
      </Alert>
      <Stack
        direction="row"
        sx={{
          gap: 2,
          mb: 3,
        }}
      >
        <TextField
          select
          label="Job status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
          sx={{ maxWidth: 280 }}
        >
          {["FAILED", "READY", "RUNNING", "DONE"].map((s) => (
            <MenuItem value={s} key={s}>
              {words(s)}
            </MenuItem>
          ))}
        </TextField>
        <Button onClick={() => void jobs.refetch()}>Refresh jobs</Button>
      </Stack>
      {jobs.isPending ? (
        <Loading />
      ) : jobs.error ? (
        <ErrorNotice error={jobs.error} retry={() => void jobs.refetch()} />
      ) : !jobs.data.items.length ? (
        <Empty
          title="No jobs in this state"
          detail="The queue has no matching jobs for your tenant."
        />
      ) : (
        <Paper>
          <TableContainer>
            <Table aria-label="Operational jobs">
              <TableHead>
                <TableRow>
                  {[
                    "Job",
                    "Status / attempts",
                    "Safe failure summary",
                    "Next eligible (UTC)",
                    "Actions",
                  ].map((s) => (
                    <TableCell key={s}>{s}</TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {jobs.data.items.map((j) => (
                  <TableRow key={j.id}>
                    <TableCell>
                      {words(j.kind)}
                      <Typography
                        variant="caption"
                        component="p"
                        sx={{ maxWidth: 190, overflowWrap: "anywhere" }}
                      >
                        {j.id}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Status state={j.state} /> · {j.attempts}
                    </TableCell>
                    <TableCell>
                      {j.lastError ? words(j.lastError) : "No recorded failure"}
                      <Typography
                        variant="caption"
                        component="p"
                        sx={{ overflowWrap: "anywhere" }}
                      >
                        Correlation{" "}
                        {j.correlationId ?? "not recorded for this legacy job"}
                      </Typography>
                    </TableCell>
                    <TableCell>{timestamp(j.availableAt)}</TableCell>
                    <TableCell>
                      <Stack>
                        <Button
                          component={Link}
                          to={`/staff/applications/${j.applicationId}`}
                        >
                          Inspect application
                        </Button>
                        {j.loanId && (
                          <Button
                            component={Link}
                            to={`/app/loans/${j.loanId}`}
                          >
                            Inspect loan/account
                          </Button>
                        )}
                        <Button
                          disabled={j.state !== "FAILED"}
                          onClick={() => {
                            setSelected(j);
                            setReason("");
                            setError(undefined);
                          }}
                        >
                          Retry recorded job
                        </Button>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={jobs.data.total}
            page={page}
            rowsPerPage={20}
            rowsPerPageOptions={[20]}
            onPageChange={(_, p) => setPage(p)}
          />
        </Paper>
      )}
      <Dialog
        open={Boolean(selected)}
        onClose={() => !busy && setSelected(undefined)}
        aria-labelledby="retry-title"
      >
        <DialogTitle id="retry-title">
          Retry the existing operation?
        </DialogTitle>
        <DialogContent>
          <Stack
            sx={{
              gap: 2,
              pt: 1,
            }}
          >
            {Boolean(error) && <ErrorNotice error={error} />}
            <Typography>
              Job {selected?.id}. The backend rechecks eligibility and stale
              revision protection.
            </Typography>
            <TextField
              label="Recovery reason"
              multiline
              minRows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button disabled={busy} onClick={() => setSelected(undefined)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!reason || busy}
            onClick={() => void retry()}
          >
            Confirm retry
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
