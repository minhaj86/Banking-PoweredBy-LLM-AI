import { useRef, useState } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
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
  Decision,
  Product,
} from "../../types/api";
import { ErrorNotice } from "../../components/Feedback";
import { money, percent, words } from "../../lib/format";
export function materialFindings(a: Assessment): string[] {
  return [
    ...new Set([
      ...a.evidenceLimitations,
      ...a.affordability.findings,
      ...a.affordability.blockers,
      ...a.ai.advisory.findings.map((f) => f.code),
      ...a.ai.advisory.inconsistencies.map((f) => f.code),
      ...a.ai.advisory.limitations,
    ]),
  ];
}
export function DecisionControls({
  application,
  assessment,
  product,
  onDone,
}: {
  application: Application;
  assessment: Assessment;
  product?: Product;
  onDone: () => Promise<unknown>;
}) {
  const [outcome, setOutcome] = useState<Decision["outcome"]>(
    "REQUEST_INFORMATION",
  );
  const [reason, setReason] = useState("");
  const [explanation, setExplanation] = useState("");
  const [applicantExplanation, setApplicantExplanation] = useState("");
  const [documents, setDocuments] = useState<string[]>([]);
  const [findings, setFindings] = useState<string[]>([]);
  const [facts, setFacts] = useState(false);
  const [debts, setDebts] = useState(false);
  const [authenticity, setAuthenticity] = useState(false);
  const [confirmation, setConfirmation] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const intent = useRef<Intent | undefined>(undefined);
  const material = materialFindings(assessment);
  const approval = outcome === "APPROVE";
  const statePermits = ["MANUAL_REVIEW", "DOCUMENTS_REQUIRED"].includes(
    application.state,
  );
  const approveReady =
    facts &&
    debts &&
    authenticity &&
    assessment.documentIds.every((d) => documents.includes(d)) &&
    material.every((f) => findings.includes(f));
  async function decide() {
    setBusy(true);
    setError(undefined);
    const request: Decision = {
      outcome,
      reasonCodes: reason.split(",").map((s) => s.trim()),
      explanation,
      applicantExplanation,
      acknowledgedDocuments: documents,
      acknowledgedFindings: findings,
      financialFactsConfirmed: facts,
      declaredExpensesAndDebtsReviewed: debts,
      authenticityReviewed: authenticity,
    };
    intent.current = mutationIntent(
      { request, version: application.version },
      intent.current,
    );
    try {
      await api(
        `/loan-applications/${application.id}/${outcome === "REQUEST_INFORMATION" ? "information-requests" : "decisions"}`,
        {
          method: "POST",
          body: request,
          intent: intent.current,
          version: application.version,
        },
      );
      intent.current = undefined;
      setConfirmation(false);
      await onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  function toggle(value: string, values: string[], set: (v: string[]) => void) {
    set(
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
  }
  return (
    <Paper sx={{ p: 3 }}>
      <Typography component="h2" variant="h3" gutterBottom>
        Human decision
      </Typography>
      <Typography variant="body2" sx={{ mb: 3 }}>
        Decide revision {application.revision}, version {application.version}.
        The backend rechecks every gate and the immutable assessed terms.
      </Typography>
      {!statePermits && (
        <Alert severity="info">
          The current state does not permit a review decision.
        </Alert>
      )}
      {Boolean(error) && <ErrorNotice error={error} />}
      <Stack
        sx={{
          gap: 3,
          mt: 2,
        }}
      >
        <TextField
          select
          label="Decision outcome"
          value={outcome}
          disabled={!statePermits || busy}
          onChange={(e) => setOutcome(e.target.value as Decision["outcome"])}
        >
          <MenuItem value="REQUEST_INFORMATION">Request information</MenuItem>
          <MenuItem
            value="APPROVE"
            disabled={application.state !== "MANUAL_REVIEW"}
          >
            Approve
          </MenuItem>
          <MenuItem
            value="REJECT"
            disabled={application.state !== "MANUAL_REVIEW"}
          >
            Reject
          </MenuItem>
        </TextField>
        <TextField
          label="Reason codes"
          value={reason}
          onChange={(e) => setReason(e.target.value.toUpperCase())}
          helperText="Uppercase codes separated by commas, for example EVIDENCE_REQUIRED"
        />
        <TextField
          label="Restricted reviewer explanation"
          multiline
          minRows={3}
          value={explanation}
          onChange={(e) => setExplanation(e.target.value)}
        />
        <TextField
          label="Explanation shown to the applicant"
          multiline
          minRows={3}
          value={applicantExplanation}
          onChange={(e) => setApplicantExplanation(e.target.value)}
        />
        {approval && (
          <>
            <Typography component="h3" variant="h4">
              Acknowledge the evidence
            </Typography>
            {assessment.documentIds.map((id) => (
              <FormControlLabel
                key={id}
                control={
                  <Checkbox
                    checked={documents.includes(id)}
                    onChange={() => toggle(id, documents, setDocuments)}
                  />
                }
                label={`I reviewed document ${id}`}
              />
            ))}
            <Typography component="h3" variant="h4">
              Acknowledge each material finding
            </Typography>
            {material.map((f) => (
              <FormControlLabel
                key={f}
                control={
                  <Checkbox
                    checked={findings.includes(f)}
                    onChange={() => toggle(f, findings, setFindings)}
                  />
                }
                label={words(f)}
              />
            ))}
            <FormControlLabel
              control={
                <Checkbox
                  checked={facts}
                  onChange={(e) => setFacts(e.target.checked)}
                />
              }
              label="I independently confirmed the material financial facts."
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={debts}
                  onChange={(e) => setDebts(e.target.checked)}
                />
              }
              label="I reviewed declared expenses, debts and revolving credit obligations."
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={authenticity}
                  onChange={(e) => setAuthenticity(e.target.checked)}
                />
              }
              label="I completed the document authenticity review."
            />
          </>
        )}
        <Button
          variant="contained"
          disabled={
            !statePermits ||
            busy ||
            !reason ||
            !explanation ||
            !applicantExplanation ||
            (approval && !approveReady)
          }
          onClick={() => setConfirmation(true)}
        >
          Review decision
        </Button>
      </Stack>
      <Dialog
        open={confirmation}
        onClose={() => !busy && setConfirmation(false)}
        aria-labelledby="decision-confirm-title"
        fullWidth
      >
        <DialogTitle id="decision-confirm-title">
          Confirm human {words(outcome).toLowerCase()}
        </DialogTitle>
        <DialogContent>
          <Stack
            sx={{
              gap: 2,
            }}
          >
            {Boolean(error) && <ErrorNotice error={error} />}
            <Typography>Application {application.id}</Typography>
            <Typography>
              <strong>
                Revision {application.revision} · version {application.version}
              </strong>
            </Typography>
            <Typography>
              Principal {money(application.application.requestedAmount)} ·{" "}
              {application.application.termMonths} months
            </Typography>
            <Typography>
              Nominal annual interest{" "}
              {product
                ? percent(product.nominalAnnualRate)
                : "See current configuration"}{" "}
              (not APR)
            </Typography>
            <Typography>
              Product {assessment.productId} v{assessment.productVersion} ·
              policy {assessment.policyVersion}
            </Typography>
            <Typography>
              Applicant explanation: {applicantExplanation}
            </Typography>
            {approval && (
              <Alert severity="info">
                Approval creates one unfunded loan and a durable
                account-provisioning job. It does not accept an agreement, post
                a journal or disburse funds.
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button disabled={busy} onClick={() => setConfirmation(false)}>
            Go back
          </Button>
          <Button
            disabled={busy}
            variant="contained"
            onClick={() => void decide()}
          >
            {busy ? "Recording…" : "Confirm human decision"}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
}
