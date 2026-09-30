import { Controller, useFieldArray, useFormContext } from "react-hook-form";
import type { FieldPath } from "react-hook-form";
import {
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  FormHelperText,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import type { ApplicationInput } from "../../types/api";
type FieldProps = {
  name: FieldPath<ApplicationInput>;
  label: string;
  type?: string;
  money?: boolean;
  numeric?: boolean;
  options?: [string, string][];
  help?: string;
  multiline?: boolean;
};
export function Field({
  name,
  label,
  type = "text",
  money = false,
  numeric = false,
  options,
  help,
  multiline,
}: FieldProps) {
  const { control } = useFormContext<ApplicationInput>();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          value={field.value ?? ""}
          onChange={(e) =>
            field.onChange(
              numeric
                ? Number(e.target.value)
                : money
                  ? e.target.value === ""
                    ? null
                    : e.target.value
                  : type === "date"
                    ? e.target.value || null
                    : e.target.value,
            )
          }
          label={label}
          type={type}
          select={Boolean(options)}
          multiline={multiline}
          minRows={multiline ? 3 : undefined}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error?.message ?? help}
          slotProps={{
            inputLabel: { shrink: type === "date" ? true : undefined },
            htmlInput: {
              inputMode: money ? "decimal" : numeric ? "numeric" : undefined,
              maxLength: multiline ? 2000 : 500,
            },
          }}
        >
          {options?.map(([value, text]) => (
            <MenuItem key={value} value={value}>
              {text}
            </MenuItem>
          ))}
        </TextField>
      )}
    />
  );
}
export function Check({
  name,
  label,
}: {
  name: FieldPath<ApplicationInput>;
  label: string;
}) {
  const { control } = useFormContext<ApplicationInput>();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <Box>
          <FormControlLabel
            control={
              <Checkbox
                checked={Boolean(field.value)}
                onChange={(e) => field.onChange(e.target.checked)}
                slotProps={{ input: { ref: field.ref } }}
              />
            }
            label={label}
          />
          {fieldState.error && (
            <FormHelperText error>{fieldState.error.message}</FormHelperText>
          )}
        </Box>
      )}
    />
  );
}
export function StepFields({ step }: { step: number }) {
  if (step === 0)
    return (
      <div className="form-grid">
        <Field
          name="requestedAmount"
          label="Requested amount (SEK)"
          help="Use a decimal string, for example 50000.00"
        />
        <Field name="termMonths" label="Term (months)" numeric type="number" />
        <Box className="full-width">
          <Field
            name="purpose"
            label="What will the loan be used for?"
            multiline
          />
        </Box>
      </div>
    );
  if (step === 1)
    return (
      <div className="form-grid">
        <Field name="identity.legalName" label="Full legal name" />
        <Field name="identity.dateOfBirth" label="Date of birth" type="date" />
        <Field
          name="identity.identityIdentifier"
          label="Identity identifier"
          help="Use a synthetic identifier in this demo"
        />
        <Field name="identity.email" label="Contact email" type="email" />
        <Field name="identity.phone" label="Phone number" type="tel" />
        <Field
          name="identity.countryOfResidence"
          label="Country of residence"
          help="Two-letter country code, for example SE"
        />
        <Box className="full-width">
          <Field name="identity.address" label="Home address" />
        </Box>
      </div>
    );
  if (step === 2)
    return (
      <Stack spacing={3}>
        <div className="form-grid">
          <Field
            name="employment.type"
            label="Employment status"
            options={[
              "EMPLOYED",
              "SELF_EMPLOYED",
              "RETIRED",
              "UNEMPLOYED",
              "OTHER",
            ].map((s) => [s, s.replaceAll("_", " ").toLowerCase()])}
          />
          <Field name="employment.employer" label="Employer or business" />
          <Field
            name="employment.startDate"
            label="Employment start date"
            type="date"
          />
          <Field
            name="income.frequency"
            label="Income frequency"
            options={["MONTHLY", "ANNUAL", "WEEKLY", "IRREGULAR"].map((s) => [
              s,
              s.toLowerCase(),
            ])}
          />
          <Field
            name="income.grossAmount"
            label="Gross income (SEK)"
            money
            help="Leave blank if unknown"
          />
          <Field name="income.netAmount" label="Net income (SEK)" money />
          <Field
            name="income.periodStart"
            label="Income period start"
            type="date"
          />
          <Field
            name="income.periodEnd"
            label="Income period end"
            type="date"
          />
        </div>
        <Check
          name="income.variable"
          label="My income is variable or irregular"
        />
        <OtherIncome />
      </Stack>
    );
  if (step === 3)
    return (
      <Stack spacing={3}>
        <div className="form-grid">
          <Field
            name="expenses.housing"
            label="Monthly housing costs (SEK)"
            money
          />
          <Field
            name="expenses.otherNonDebt"
            label="Other monthly living costs (SEK)"
            money
            help="Food, utilities, transport and other non-debt costs"
          />
          <Field
            name="expenses.householdSize"
            label="Household members"
            numeric
            type="number"
          />
        </div>
        <Check
          name="expenses.excludesDebtRepayments"
          label="These living costs exclude all debt repayments, which I will declare separately."
        />
      </Stack>
    );
  if (step === 4) return <Debts />;
  if (step === 5)
    return (
      <Stack spacing={3}>
        <Typography color="text.secondary">
          These declarations support financial due diligence. Account login and
          email verification do not verify your legal identity.
        </Typography>
        <Field
          name="kyc.relationshipPurpose"
          label="Purpose of your relationship with the lender"
          multiline
        />
        <Field name="kyc.sourceOfFunds" label="Source of funds" multiline />
        <TaxResidencies />
        <Check
          name="kyc.pepDeclared"
          label="I or a close family member/associate hold or have held a prominent public function (PEP)."
        />
        <Typography variant="caption">
          A PEP declaration is a separate compliance review matter.
        </Typography>
      </Stack>
    );
  return null;
}
function Debts() {
  const { control } = useFormContext<ApplicationInput>();
  const array = useFieldArray({ control, name: "debts" });
  return (
    <Stack spacing={3}>
      <Typography color="text.secondary">
        Declare each loan, credit card and revolving credit facility. If you
        have no existing credit, continue without adding a row. Unknown amounts
        remain unknown.
      </Typography>
      {array.fields.map((item, i) => (
        <Paper component="fieldset" key={item.id} sx={{ p: 3, m: 0 }}>
          <Typography component="legend" variant="h4">
            Credit facility {i + 1}
          </Typography>
          <div className="form-grid">
            <Field name={`debts.${i}.creditor`} label="Creditor" />
            <Field
              name={`debts.${i}.type`}
              label="Credit type"
              options={["LOAN", "CREDIT_CARD", "REVOLVING_CREDIT", "OTHER"].map(
                (s) => [s, s.replaceAll("_", " ").toLowerCase()],
              )}
            />
            <Field
              name={`debts.${i}.outstandingBalance`}
              label="Outstanding principal (SEK)"
              money
            />
            <Field
              name={`debts.${i}.monthlyRepayment`}
              label="Monthly repayment (SEK)"
              money
            />
            <Field
              name={`debts.${i}.creditLimit`}
              label="Credit limit (SEK)"
              money
            />
            <Field
              name={`debts.${i}.utilisedBalance`}
              label="Utilised credit (SEK)"
              money
            />
            <Field
              name={`debts.${i}.remainingMonths`}
              label="Remaining months"
              numeric
              type="number"
            />
          </div>
          <Button color="error" onClick={() => array.remove(i)} sx={{ mt: 2 }}>
            Remove credit facility {i + 1}
          </Button>
        </Paper>
      ))}
      <Button
        variant="outlined"
        disabled={array.fields.length >= 50}
        onClick={() =>
          array.append({
            creditor: "",
            type: "LOAN",
            outstandingBalance: null,
            creditLimit: null,
            utilisedBalance: null,
            monthlyRepayment: null,
            currency: "SEK",
            remainingMonths: null,
            source: "APPLICANT_DECLARED",
          })
        }
      >
        Add existing credit
      </Button>
    </Stack>
  );
}
function OtherIncome() {
  const { control } = useFormContext<ApplicationInput>();
  const array = useFieldArray({ control, name: "income.otherSources" });
  return (
    <Stack spacing={2}>
      <Typography variant="h4" component="h3">
        Other income sources
      </Typography>
      {array.fields.map((f, i) => (
        <Paper key={f.id} sx={{ p: 2 }}>
          <Stack
            sx={{
              gap: 2,
            }}
          >
            <Field
              name={`income.otherSources.${i}.kind`}
              label="Income source"
            />
            <Field
              name={`income.otherSources.${i}.netAmount`}
              label="Other net income (SEK)"
              money
            />
            <Field
              name={`income.otherSources.${i}.frequency`}
              label="Other income frequency"
              options={["MONTHLY", "ANNUAL", "WEEKLY", "IRREGULAR"].map((s) => [
                s,
                s.toLowerCase(),
              ])}
            />
            <Check
              name={`income.otherSources.${i}.variable`}
              label="This income is variable"
            />
            <Button onClick={() => array.remove(i)}>
              Remove income source {i + 1}
            </Button>
          </Stack>
        </Paper>
      ))}
      <Button
        variant="text"
        disabled={array.fields.length >= 20}
        onClick={() =>
          array.append({
            kind: "",
            netAmount: null,
            frequency: "MONTHLY",
            currency: "SEK",
            variable: false,
          })
        }
      >
        Add other income
      </Button>
    </Stack>
  );
}
function TaxResidencies() {
  const { control } = useFormContext<ApplicationInput>();
  return (
    <Controller
      name="kyc.taxResidencies"
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          label="Tax residency country codes"
          value={field.value.join(", ")}
          onChange={(e) =>
            field.onChange(
              e.target.value
                .toUpperCase()
                .split(",")
                .map((s) => s.trim()),
            )
          }
          error={Boolean(fieldState.error)}
          helperText="Comma-separated two-letter codes, for example SE, FI"
        />
      )}
    />
  );
}
