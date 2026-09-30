import { useRef, useState } from "react";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import type { FieldPath, Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocation, useNavigate, useParams } from "react-router-dom";
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
  Step,
  StepButton,
  Stepper,
  Typography,
} from "@mui/material";
import { api, mutationIntent } from "../../api/client";
import type { Intent } from "../../api/client";
import type { Application, ApplicationInput, Product } from "../../types/api";
import { useAuth, scopeKey } from "../auth/Auth";
import { ErrorNotice, Loading, SectionTitle } from "../../components/Feedback";
import { applicationSchema, newDraft, steps, stepFields } from "./form";
import { Check, StepFields } from "./FormFields";
import { ApplicationSummary } from "./ApplicationSummary";
import { Documents } from "../documents/Documents";
import { editable, money, percent } from "../../lib/format";
export default function Wizard() {
  const { id } = useParams();
  const { me } = useAuth();
  const product = useQuery({
    queryKey: ["public-product"],
    queryFn: ({ signal }) => api<Product>("/bff/product", { signal }),
  });
  const current = useQuery({
    queryKey: [...scopeKey(me), "application", id],
    queryFn: ({ signal }) =>
      api<Application>(`/loan-applications/${id}`, { signal }),
    enabled: Boolean(id),
  });
  if (product.error || current.error)
    return <ErrorNotice error={product.error ?? current.error} />;
  if (!product.data || (id && !current.data))
    return <Loading label="Opening your saved draft" />;
  if (current.data && !editable(current.data.state))
    return (
      <Alert severity="info">
        This application is no longer editable. Open its detail page to follow
        the review.
      </Alert>
    );
  return (
    <WizardForm
      key={id ?? "new"}
      product={product.data}
      current={current.data}
    />
  );
}
export function WizardForm({
  product,
  current,
}: {
  product: Product;
  current?: Application;
}) {
  const location = useLocation();
  const navigate = useNavigate();
  const query = useQueryClient();
  const { me } = useAuth();
  const [step, setStep] = useState<number>(
    (
      location.state as {
        wizardStep?: number;
      } | null
    )?.wizardStep ?? 0,
  );
  const [version, setVersion] = useState(current?.version ?? 0);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [saved, setSaved] = useState(Boolean(current));
  const [savedSnapshot, setSavedSnapshot] = useState(
    current ? JSON.stringify(current.application) : "",
  );
  const [error, setError] = useState<unknown>();
  const [validation, setValidation] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const saveIntent = useRef<Intent | undefined>(undefined);
  const submitIntent = useRef<Intent | undefined>(undefined);
  const heading = useRef<HTMLHeadingElement>(null);
  const form = useForm<ApplicationInput>({
    defaultValues: current?.application ?? newDraft(product),
    resolver: zodResolver(
      applicationSchema(product),
    ) as Resolver<ApplicationInput>,
    mode: "onTouched",
  });
  const values = useWatch({ control: form.control });
  const dirty = JSON.stringify(values) !== savedSnapshot;
  const busy = saving || submitting;
  async function save(nextStep = step): Promise<Application | undefined> {
    setSaving(true);
    setError(undefined);
    try {
      const input = structuredClone(form.getValues());
      saveIntent.current = mutationIntent(
        { input, version },
        saveIntent.current,
      );
      const result = await api<Application>(
        current ? `/drafts/${current.id}` : "/drafts",
        {
          method: current ? "PUT" : "POST",
          body: input,
          version: current ? version : undefined,
          intent: saveIntent.current,
        },
      );
      setVersion(result.version);
      setSaved(true);
      setSavedSnapshot(JSON.stringify(input));
      query.setQueryData([...scopeKey(me), "application", result.id], result);
      void query.invalidateQueries({
        queryKey: [...scopeKey(me), "applications"],
      });
      if (!current)
        navigate(`/app/applications/${result.id}/edit`, {
          replace: true,
          state: { wizardStep: nextStep },
        });
      return result;
    } catch (e) {
      setError(e);
      return undefined;
    } finally {
      setSaving(false);
    }
  }
  async function next() {
    const fields = stepFields[step] as readonly FieldPath<ApplicationInput>[];
    const valid = fields.length
      ? await form.trigger([...fields], { shouldFocus: true })
      : true;
    if (!valid) {
      setValidation(true);
      document.querySelector<HTMLElement>("#validation-summary")?.focus();
      return;
    }
    setValidation(false);
    const result = await save(step + 1);
    if (result && current) changeStep(Math.min(step + 1, 7));
  }
  function changeStep(value: number) {
    setStep(value);
    setValidation(false);
    queueMicrotask(() => heading.current?.focus());
  }
  async function submit() {
    if (!current) return;
    setSubmitting(true);
    setError(undefined);
    try {
      const result = await save();
      if (!result) return;
      submitIntent.current = mutationIntent(
        { id: current.id, version: result.version },
        submitIntent.current,
      );
      await api<Application>(`/loan-applications/${current.id}/submit`, {
        method: "POST",
        version: result.version,
        intent: submitIntent.current,
      });
      await query.invalidateQueries({ queryKey: [...scopeKey(me)] });
      navigate(`/app/applications/${current.id}`, {
        state: { submitted: true },
      });
    } catch (e) {
      setError(e);
    } finally {
      setSubmitting(false);
      setConfirm(false);
    }
  }
  return (
    <>
      <SectionTitle
        eyebrow="Personal loan application"
        title="A few details. A considered decision."
        detail={`${money(product.minAmount)}–${money(product.maxAmount)} · ${product.minMonths}–${product.maxMonths} months · ${percent(product.nominalAnnualRate)} nominal annual interest. This is not APR.`}
      />
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "230px minmax(0,1fr)" },
          gap: 4,
        }}
      >
        <Box component="nav" aria-label="Application steps">
          <Stepper
            nonLinear
            activeStep={step}
            orientation="vertical"
            sx={{ display: { xs: "none", md: "flex" } }}
          >
            {steps.map((label, i) => (
              <Step key={label} completed={i < step}>
                <StepButton disabled={busy} onClick={() => changeStep(i)}>
                  {label}
                </StepButton>
              </Step>
            ))}
          </Stepper>
          <Typography
            sx={{
              fontWeight: 600,
              display: { md: "none" },
              mb: 2,
            }}
          >
            Step {step + 1} of {steps.length} · {steps[step]}
          </Typography>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ display: "block", mt: 3 }}
          >
            Your draft is stored in the backend. No sensitive form data is saved
            in browser storage.
          </Typography>
        </Box>
        <Paper sx={{ p: { xs: 2.5, md: 4 } }}>
          <FormProvider {...form}>
            <form onSubmit={(e) => e.preventDefault()} noValidate>
              <Stack
                sx={{
                  gap: 3,
                }}
              >
                <Stack
                  direction="row"
                  sx={{
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: 2,
                  }}
                >
                  <Typography
                    ref={heading}
                    tabIndex={-1}
                    variant="h3"
                    component="h2"
                  >
                    {steps[step]}
                  </Typography>
                  <Typography
                    variant="caption"
                    role="status"
                    color={error ? "error" : "text.secondary"}
                  >
                    {busy
                      ? "Saving…"
                      : error
                        ? "Save failed"
                        : saved && !dirty
                          ? "Saved securely"
                          : saved
                            ? "Unsaved changes"
                            : "Not saved yet"}
                  </Typography>
                </Stack>
                {validation && (
                  <Alert severity="error" id="validation-summary" tabIndex={-1}>
                    Check the highlighted fields before continuing. Use Back to
                    review earlier steps.
                  </Alert>
                )}
                {Boolean(error) && <ErrorNotice error={error} />}
                <StepFields step={step} />
                {step === 6 &&
                  (current ? (
                    <Documents
                      applicationId={current.id}
                      product={product}
                      canUpload
                      onChanged={async () => {
                        const app = await api<Application>(
                          `/loan-applications/${current.id}`,
                        );
                        setVersion(app.version);
                        query.setQueryData(
                          [...scopeKey(me), "application", current.id],
                          app,
                        );
                      }}
                    />
                  ) : (
                    <Alert severity="info">
                      Save your draft before adding evidence.
                    </Alert>
                  ))}
                {step === 7 && (
                  <>
                    <Alert severity="info">
                      Review every detail. Submission starts processing; it is
                      not approval or a funding instruction.
                    </Alert>
                    <ApplicationSummary input={form.getValues()} />
                    {current && <Documents applicationId={current.id} />}
                    <Check
                      name="privacy.financialEvidenceProcessingAcknowledged"
                      label={`I acknowledge financial evidence processing under privacy notice ${product.privacyNoticeVersion}.`}
                    />
                    <Typography variant="body2">
                      Submitting freezes this form, document references and
                      product/policy versions. Corrections or additional
                      evidence require another revision.
                    </Typography>
                  </>
                )}
                <Divider />
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  sx={{
                    justifyContent: "space-between",
                    gap: 1.5,
                  }}
                >
                  <Button
                    disabled={step === 0 || busy}
                    onClick={() => changeStep(step - 1)}
                  >
                    Back
                  </Button>
                  <Stack
                    direction="row"
                    sx={{
                      gap: 1.5,
                      flexWrap: "wrap",
                    }}
                  >
                    <Button
                      variant="outlined"
                      disabled={busy}
                      onClick={() => void save()}
                    >
                      Save draft
                    </Button>
                    {step < 7 ? (
                      <Button
                        variant="contained"
                        disabled={busy}
                        onClick={() => void next()}
                      >
                        Save & continue
                      </Button>
                    ) : (
                      <Button
                        variant="contained"
                        disabled={busy || !current || !me?.emailVerified}
                        onClick={() => {
                          void form
                            .trigger(undefined, { shouldFocus: true })
                            .then((valid) => {
                              if (valid) setConfirm(true);
                              else {
                                setValidation(true);
                                document
                                  .querySelector<HTMLElement>(
                                    "#validation-summary",
                                  )
                                  ?.focus();
                              }
                            });
                        }}
                      >
                        Review submission
                      </Button>
                    )}
                  </Stack>
                </Stack>
                {step === 7 && !me?.emailVerified && (
                  <Alert severity="warning">
                    Verify your email in account settings before submitting.
                  </Alert>
                )}
              </Stack>
            </form>
          </FormProvider>
        </Paper>
      </Box>
      <Dialog
        open={confirm}
        onClose={() => !busy && setConfirm(false)}
        aria-labelledby="submit-title"
      >
        <DialogTitle id="submit-title">
          Submit this application for review?
        </DialogTitle>
        <DialogContent>
          <Typography>
            {money(form.getValues("requestedAmount"))} over{" "}
            {form.getValues("termMonths")} months, product {product.version}. A
            reviewer will assess the frozen revision. No agreement or funding is
            created by submission.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirm(false)} disabled={busy}>
            Keep editing
          </Button>
          <Button
            variant="contained"
            disabled={busy}
            onClick={() => void submit()}
          >
            Confirm submission
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
