import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { api, mutationIntent, upload } from "../../api/client";
import type { Intent } from "../../api/client";
import type { Application, Document, Product } from "../../types/api";
import { useAuth, scopeKey } from "../auth/Auth";
import { Empty, ErrorNotice, Loading, Status } from "../../components/Feedback";
import { useBoundedPoll } from "../../hooks/useBoundedPoll";
import { words } from "../../lib/format";
const Viewer = lazy(() => import("./DocumentViewer"));
export function Documents({
  applicationId,
  product,
  canUpload = false,
  onChanged,
}: {
  applicationId: string;
  product?: Product;
  canUpload?: boolean;
  onChanged?: () => Promise<unknown> | void;
}) {
  const { me } = useAuth();
  const poll = useBoundedPoll();
  const [selected, setSelected] = useState<Document>();
  const query = useQuery({
    queryKey: [...scopeKey(me), "documents", applicationId],
    queryFn: ({ signal }) =>
      api<Document[]>(`/loan-applications/${applicationId}/documents`, {
        signal,
      }),
    refetchInterval: (q) =>
      q.state.data?.some(
        (d) =>
          d.scanStatus === "PENDING" ||
          ["PENDING", "PROCESSING"].includes(d.processingStatus),
      )
        ? poll(q.state.dataUpdateCount)
        : false,
    refetchIntervalInBackground: false,
  });
  const [file, setFile] = useState<File>();
  const [category, setCategory] = useState("PAYSLIP");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [supersedes, setSupersedes] = useState("");
  const [percent, setPercent] = useState<number>();
  const [error, setError] = useState<unknown>();
  const [message, setMessage] = useState("");
  const attempt = useRef<
    | {
        intent: Intent;
        version: number;
      }
    | undefined
  >(undefined);
  const abort = useRef<AbortController | undefined>(undefined);
  useEffect(() => () => abort.current?.abort(), []);
  function invalidateAttempt() {
    attempt.current = undefined;
    setError(undefined);
    setMessage("");
  }
  async function send() {
    if (!file || !start || !end) {
      setError(new Error("Choose a document, category and covered dates."));
      return;
    }
    if (
      product &&
      (file.size > product.maxFileBytes ||
        !product.mediaTypes.includes(file.type))
    ) {
      setError(
        new Error(
          "Choose a supported PDF, PNG or JPEG within the product file limit.",
        ),
      );
      return;
    }
    setError(undefined);
    setMessage("");
    setPercent(0);
    abort.current = new AbortController();
    try {
      if (!attempt.current) {
        const current = await api<Application>(
          `/loan-applications/${applicationId}`,
        );
        attempt.current = {
          intent: mutationIntent({
            name: file.name,
            size: file.size,
            lastModified: file.lastModified,
            category,
            start,
            end,
            supersedes,
          }),
          version: current.version,
        };
      }
      const body = new FormData();
      body.set("file", file);
      body.set("category", category);
      body.set("periodStart", start);
      body.set("periodEnd", end);
      if (supersedes) body.set("supersedes", supersedes);
      await upload<Document>(
        `/loan-applications/${applicationId}/documents`,
        body,
        attempt.current.version,
        attempt.current.intent,
        abort.current.signal,
        setPercent,
      );
      setMessage(
        "Upload received. Security scanning and extraction are separate processing steps.",
      );
      attempt.current = undefined;
      setFile(undefined);
      await query.refetch();
      await onChanged?.();
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError")
        setMessage(
          "Upload cancelled. A server may already have received it; refresh before retrying the same upload.",
        );
      else setError(e);
    } finally {
      setPercent(undefined);
    }
  }
  const replaced = new Set(
    query.data?.map((d) => d.supersedes).filter(Boolean),
  );
  return (
    <Stack spacing={3}>
      {canUpload && product && (
        <Paper
          component="section"
          aria-label="Upload evidence"
          sx={{ p: { xs: 2, md: 3 }, bgcolor: "#f8faf7" }}
        >
          <Typography variant="h4" component="h3" gutterBottom>
            Add supporting evidence
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Required: {product.requiredCategories.map(words).join(", ")}.{" "}
            {product.requiredPayslips} payslip(s). Evidence must be within{" "}
            {product.evidenceFreshnessDays} days. PDF, PNG or JPEG; up to{" "}
            {product.maxFileBytes / 1048576} MiB and {product.maxPages} PDF
            pages.
          </Typography>
          <Stack spacing={3}>
            <div className="form-grid">
              <TextField
                select
                label="Document category"
                value={category}
                onChange={(e) => {
                  setCategory(e.target.value);
                  invalidateAttempt();
                }}
              >
                {[
                  "PAYSLIP",
                  "TAX_ASSESSMENT",
                  "EMPLOYMENT_EVIDENCE",
                  "EXISTING_LOAN_STATEMENT",
                  "CREDIT_CARD_STATEMENT",
                  "OTHER",
                ].map((s) => (
                  <MenuItem key={s} value={s}>
                    {words(s)}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Replaces an earlier document"
                value={supersedes}
                onChange={(e) => {
                  setSupersedes(e.target.value);
                  invalidateAttempt();
                }}
              >
                <MenuItem value="">New evidence</MenuItem>
                {query.data
                  ?.filter((d) => !replaced.has(d.id))
                  .map((d) => (
                    <MenuItem key={d.id} value={d.id}>
                      {words(d.category)} · {d.periodEnd}
                    </MenuItem>
                  ))}
              </TextField>
              <TextField
                label="Covered period start"
                type="date"
                value={start}
                onChange={(e) => {
                  setStart(e.target.value);
                  invalidateAttempt();
                }}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label="Covered period end"
                type="date"
                value={end}
                onChange={(e) => {
                  setEnd(e.target.value);
                  invalidateAttempt();
                }}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </div>
            <Box
              sx={{
                p: 3,
                border: "2px dashed #a4b5a4",
                borderRadius: 2,
                textAlign: "center",
              }}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                if (percent === undefined) {
                  setFile(e.dataTransfer.files[0]);
                  invalidateAttempt();
                }
              }}
            >
              <Typography sx={{ mb: 1 }}>
                Drop a file here, or choose one below.
              </Typography>
              <input
                aria-label="Choose supporting document"
                type="file"
                accept="application/pdf,image/jpeg,image/png"
                disabled={percent !== undefined}
                onChange={(e) => {
                  setFile(e.target.files?.[0]);
                  invalidateAttempt();
                }}
              />
              {file && (
                <Typography variant="body2" sx={{ mt: 1 }}>
                  {file.name}
                </Typography>
              )}
            </Box>
            {percent !== undefined && (
              <Box role="status">
                <Typography>Uploading · {percent}%</Typography>
                <LinearProgress variant="determinate" value={percent} aria-label="Document upload progress" />
              </Box>
            )}
            <Stack
              direction="row"
              sx={{
                gap: 1,
              }}
            >
              <Button
                variant="contained"
                disabled={!file || percent !== undefined}
                onClick={() => void send()}
              >
                {error ? "Retry same upload" : "Upload document"}
              </Button>
              {percent !== undefined && (
                <Button onClick={() => abort.current?.abort()}>
                  Cancel upload
                </Button>
              )}
            </Stack>
          </Stack>
        </Paper>
      )}
      {Boolean(error) && <ErrorNotice error={error} />}{" "}
      {message && (
        <Alert severity="info" role="status">
          {message}
        </Alert>
      )}
      {query.isPending ? (
        <Loading label="Loading document status" />
      ) : query.error ? (
        <ErrorNotice error={query.error} retry={() => void query.refetch()} />
      ) : !query.data?.length ? (
        <Empty
          title="No evidence uploaded yet"
          detail="Your documents will stay private and quarantined until a clean security scan."
        />
      ) : (
        <Stack
          sx={{
            gap: 2,
          }}
        >
          {query.data.map((d) => (
            <Paper
              key={d.id}
              sx={{
                p: 2.5,
                bgcolor: replaced.has(d.id) ? "#f1f4ef" : "background.paper",
              }}
            >
              <Stack
                direction={{ xs: "column", sm: "row" }}
                sx={{
                  gap: 2,
                  justifyContent: "space-between",
                }}
              >
                <Box>
                  <Typography component="h3" variant="h4">
                    {words(d.category)}
                    {replaced.has(d.id) ? " · superseded" : ""}
                  </Typography>
                  <Typography variant="body2">
                    {d.periodStart} – {d.periodEnd} · Revision{" "}
                    {d.uploadedForRevision}
                  </Typography>
                  <Stack
                    direction="row"
                    sx={{
                      gap: 1,
                      flexWrap: "wrap",
                      my: 1,
                    }}
                  >
                    <Status state={d.uploadStatus} />
                    <Status state={d.scanStatus} />
                    <Status state={d.processingStatus} />
                  </Stack>
                  {d.scanStatus !== "CLEAN" && (
                    <Typography variant="body2">
                      Quarantined. Preview is unavailable until a real clean
                      scan.
                    </Typography>
                  )}
                  {d.processingStatus === "EXTRACTED" && (
                    <Typography variant="caption">
                      Extraction completed; authenticity and financial facts
                      still require independent review.
                    </Typography>
                  )}
                  {d.processingStatus.includes("FAILED") && (
                    <Typography variant="body2">
                      Processing needs attention. Replace the evidence or
                      contact a reviewer.
                    </Typography>
                  )}
                  {d.evidence?.facts.map((f, i) => (
                    <Typography key={i} variant="body2" sx={{ mt: 1 }}>
                      {words(f.field)}: {f.amount} {f.currency} · {f.provenance}{" "}
                      · page {f.source.page}, line {f.source.line}
                    </Typography>
                  ))}
                </Box>
                <Button
                  disabled={
                    d.scanStatus !== "CLEAN" || d.uploadStatus !== "COMPLETE"
                  }
                  onClick={() => setSelected(d)}
                >
                  Preview evidence
                </Button>
              </Stack>
            </Paper>
          ))}
        </Stack>
      )}
      <Button sx={{ alignSelf: "start" }} onClick={() => void query.refetch()}>
        Refresh document status
      </Button>
      {selected && (
        <Suspense fallback={<Loading label="Opening viewer" />}>
          <Viewer
            applicationId={applicationId}
            document={selected}
            close={() => setSelected(undefined)}
          />
        </Suspense>
      )}
    </Stack>
  );
}
