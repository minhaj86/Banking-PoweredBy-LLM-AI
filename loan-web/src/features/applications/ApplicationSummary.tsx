import { Box, Divider, Stack, Typography } from "@mui/material";
import type { ApplicationInput } from "../../types/api";
import { money, words } from "../../lib/format";
function Item({
  label,
  value,
}: {
  label: string;
  value: string | number | null | undefined;
}) {
  return (
    <Box>
      <Typography component="dt" variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, overflowWrap: "anywhere" }}>
        {value || "Not provided"}
      </Typography>
    </Box>
  );
}
export function ApplicationSummary({ input }: { input: ApplicationInput }) {
  return (
    <Stack
      sx={{
        gap: 3,
      }}
    >
      <Box component="dl" className="form-grid" sx={{ m: 0 }}>
        <Item label="Requested amount" value={money(input.requestedAmount)} />
        <Item label="Term" value={`${input.termMonths} months`} />
        <Item label="Purpose" value={input.purpose} />
        <Item
          label="Product version"
          value={`${input.productId} / ${input.productVersion}`}
        />
      </Box>
      <Divider />
      <Typography component="h3" variant="h4">
        Personal & contact details
      </Typography>
      <Box component="dl" className="form-grid" sx={{ m: 0 }}>
        {Object.entries(input.identity ?? {}).map(([k, v]) => (
          <Item key={k} label={words(k.replace(/([A-Z])/g, "_$1"))} value={v} />
        ))}
      </Box>
      <Divider />
      <Typography component="h3" variant="h4">
        Employment & declared finances
      </Typography>
      <Box component="dl" className="form-grid" sx={{ m: 0 }}>
        <Item
          label="Employment"
          value={words(input.employment?.type ?? "UNKNOWN")}
        />
        <Item label="Employer" value={input.employment?.employer} />
        <Item label="Employment start" value={input.employment?.startDate} />
        <Item
          label="Declared net income"
          value={money(input.income?.netAmount)}
        />
        <Item
          label="Declared gross income"
          value={money(input.income?.grossAmount)}
        />
        <Item label="Income frequency" value={input.income?.frequency} />
        <Item
          label="Covered income period"
          value={`${input.income?.periodStart ?? "?"} – ${input.income?.periodEnd ?? "?"}`}
        />
        <Item
          label="Variable income"
          value={input.income?.variable ? "Yes" : "No"}
        />
        <Item label="Monthly housing" value={money(input.expenses?.housing)} />
        <Item
          label="Other monthly living costs"
          value={money(input.expenses?.otherNonDebt)}
        />
        <Item label="Household members" value={input.expenses?.householdSize} />
        <Item
          label="Living costs exclude debt payments"
          value={
            input.expenses?.excludesDebtRepayments
              ? "Confirmed"
              : "Not confirmed"
          }
        />
      </Box>
      {input.income?.otherSources?.map((income, i) => (
        <Typography key={i}>
          Other income: {income.kind} · {money(income.netAmount)} ·{" "}
          {income.frequency} · {income.variable ? "variable" : "regular"}
        </Typography>
      ))}
      <Typography component="h3" variant="h4">
        Existing credit
      </Typography>
      {input.debts?.length ? (
        input.debts.map((d, i) => (
          <Box component="dl" key={i} className="form-grid" sx={{ m: 0 }}>
            <Item label="Creditor / type" value={`${d.creditor} · ${d.type}`} />
            <Item label="Monthly repayment" value={money(d.monthlyRepayment)} />
            <Item
              label="Outstanding balance"
              value={money(d.outstandingBalance)}
            />
            <Item
              label="Credit limit / utilised"
              value={`${money(d.creditLimit)} / ${money(d.utilisedBalance)}`}
            />
            <Item
              label="Remaining months"
              value={d.remainingMonths ?? "Unknown"}
            />
          </Box>
        ))
      ) : (
        <Typography>No existing credit declared.</Typography>
      )}
      <Divider />
      <Typography component="h3" variant="h4">
        KYC declarations
      </Typography>
      <Box component="dl" className="form-grid" sx={{ m: 0 }}>
        <Item
          label="Relationship purpose"
          value={input.kyc?.relationshipPurpose}
        />
        <Item label="Source of funds" value={input.kyc?.sourceOfFunds} />
        <Item
          label="Tax residencies"
          value={input.kyc?.taxResidencies?.join(", ")}
        />
        <Item
          label="PEP declaration"
          value={
            input.kyc?.pepDeclared ? "Yes — separate compliance review" : "No"
          }
        />
        <Item
          label="Questionnaire version"
          value={input.kyc?.questionnaireVersion}
        />
        <Item
          label="Privacy notice"
          value={`${input.privacy?.noticeVersion} · ${input.privacy?.financialEvidenceProcessingAcknowledged ? "acknowledged" : "not yet acknowledged"}`}
        />
      </Box>
    </Stack>
  );
}
