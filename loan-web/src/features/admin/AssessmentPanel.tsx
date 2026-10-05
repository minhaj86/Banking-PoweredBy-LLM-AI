import { Alert, Box, Divider, Paper, Stack, Typography } from "@mui/material";
import type { Assessment, Verification } from "../../types/api";
import { money, timestamp, words } from "../../lib/format";
import { Status } from "../../components/Feedback";
export function AssessmentPanel({
  assessment: a,
  verifications,
}: {
  assessment: Assessment;
  verifications?: Verification[];
}) {
  const financial = [
    ["Monthly net income", a.affordability.monthlyNetIncome],
    ["Non-debt living expenses", a.affordability.monthlyNonDebtExpenses],
    ["Existing debt repayments", a.affordability.existingMonthlyDebtRepayments],
    ["Proposed monthly instalment", a.affordability.proposedMonthlyInstalment],
    ["Residual income", a.affordability.residualIncome],
    ["Stressed residual income", a.affordability.stressedResidualIncome],
  ];
  return (
    <Stack spacing={3}>
      <Paper sx={{ p: 3 }}>
        <Typography component="h2" variant="h3" gutterBottom>
          Deterministic affordability
        </Typography>
        <Typography variant="caption">
          Assessed {timestamp(a.assessedAt)} · Revision {a.revision} · Product{" "}
          {a.productId} v{a.productVersion} · Policy {a.policyVersion}
        </Typography>
        <Box component="dl" className="form-grid" sx={{ mt: 3, mb: 0 }}>
          {financial.map(([label, value]) => (
            <Box key={label}>
              <Typography component="dt" color="text.secondary" variant="body2">
                {label}
              </Typography>
              <Typography
                component="dd"
                sx={{ m: 0, fontSize: 20, fontWeight: 600 }}
              >
                {money(value)}
              </Typography>
            </Box>
          ))}
        </Box>
        <Typography sx={{ mt: 2 }}>
          Debt service to income ratio:{" "}
          {a.affordability.debtServiceToIncome ?? "Unknown"}
        </Typography>
        {[...a.affordability.blockers, ...a.affordability.findings].map(
          (code) => (
            <Alert key={code} severity="warning" sx={{ mt: 1 }}>
              {words(code)}
            </Alert>
          ),
        )}
        <Divider sx={{ my: 3 }} />
        <Typography component="h3" variant="h4" gutterBottom>
          Evidence facts & provenance
        </Typography>
        {a.affordability.facts.map((f, i) => (
          <Paper key={i} sx={{ p: 2, mb: 1, bgcolor: "#f8faf7" }}>
            <Typography>
              {words(f.field)} · {money(f.amount, f.currency)} · {f.frequency}
            </Typography>
            <Typography variant="body2">
              {words(f.provenance)} · {f.periodStart}–{f.periodEnd}
            </Typography>
            <Typography variant="caption" sx={{ overflowWrap: "anywhere" }}>
              Document {f.source.documentId} · page {f.source.page}, line{" "}
              {f.source.line}
            </Typography>
            <Typography variant="body2">{f.source.snippet}</Typography>
          </Paper>
        ))}
      </Paper>
      <Paper sx={{ p: 3 }}>
        <Typography component="h2" variant="h3" gutterBottom>
          AI advisory · requires human judgement
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          The model has no decision or ledger tools. Its output is not a credit
          decision, identity verification or evidence authentication.
        </Alert>
        <Typography
          sx={{
            fontWeight: 600,
          }}
        >
          {words(a.ai.advisory.outcome)}
        </Typography>
        <Typography variant="caption">
          {a.ai.provider} / {a.ai.model} · Prompt {a.ai.promptVersion} · Schema{" "}
          {a.ai.schemaVersion}
        </Typography>
        <Stack
          sx={{
            gap: 1,
            mt: 2,
          }}
        >
          {[...a.ai.advisory.findings, ...a.ai.advisory.inconsistencies].map(
            (f, i) => (
              <Box key={i}>
                <Typography>{words(f.code)}</Typography>
                {f.evidenceReferences.map((r, j) => (
                  <Typography
                    key={j}
                    variant="caption"
                    component="p"
                    sx={{ overflowWrap: "anywhere" }}
                  >
                    Source {r.documentId} · p{r.page}, line {r.line}
                  </Typography>
                ))}
              </Box>
            ),
          )}
          {a.ai.advisory.missingInformation.map((v) => (
            <Typography key={v}>Missing: {words(v)}</Typography>
          ))}
          {a.ai.advisory.questionsForReviewer.map((v) => (
            <Typography key={v}>Reviewer: {words(v)}</Typography>
          ))}
        </Stack>
        <Typography variant="h4" component="h3" sx={{ mt: 3, mb: 1 }}>
          Material limitations
        </Typography>
        {[
          ...new Set([...a.evidenceLimitations, ...a.ai.advisory.limitations]),
        ].map((v) => (
          <Typography variant="body2" key={v}>
            • {words(v)}
          </Typography>
        ))}
      </Paper>
      <Paper sx={{ p: 3 }}>
        <Typography component="h2" variant="h3" gutterBottom>
          Independent verification gates
        </Typography>
        {(verifications ?? a.verifications).map((v) => (
          <Box key={v.gate} sx={{ py: 1.5 }}>
            <Stack
              direction="row"
              sx={{
                justifyContent: "space-between",
              }}
            >
              <Typography
                sx={{
                  fontWeight: 600,
                }}
              >
                {words(v.gate)}
              </Typography>
              <Status state={v.status} />
            </Stack>
            <Typography variant="body2">
              {v.provider} ·{" "}
              {v.synthetic
                ? "Synthetic local result"
                : "Independent provider / recorded review"}
            </Typography>
            <Typography variant="caption">
              Valid until {timestamp(v.validUntil)}
            </Typography>
          </Box>
        ))}
      </Paper>
    </Stack>
  );
}
