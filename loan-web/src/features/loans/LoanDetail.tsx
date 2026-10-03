import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { api } from "../../api/client";
import type {
  AccountMapping,
  Balance,
  Loan,
  Schedule,
  Terms,
} from "../../types/api";
import { useAuth, scopeKey } from "../auth/Auth";
import {
  ErrorNotice,
  Loading,
  SectionTitle,
  Status,
} from "../../components/Feedback";
import { money, percent, timestamp, words } from "../../lib/format";
import { useBoundedPoll } from "../../hooks/useBoundedPoll";
export default function LoanDetail() {
  const { id } = useParams();
  const { me } = useAuth();
  const poll = useBoundedPoll();
  const key = [...scopeKey(me), "loan", id];
  const loan = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => api<Loan>(`/loans/${id}`, { signal }),
    refetchInterval: (q) =>
      q.state.data?.provisioningStatus !== "COMPLETE"
        ? poll(q.state.dataUpdateCount)
        : false,
  });
  const terms = useQuery({
    queryKey: [...key, "terms"],
    queryFn: ({ signal }) => api<Terms>(`/loans/${id}/terms`, { signal }),
    enabled: Boolean(loan.data),
  });
  const schedule = useQuery({
    queryKey: [...key, "schedule"],
    queryFn: ({ signal }) =>
      api<Schedule>(`/loans/${id}/repayment-schedule`, { signal }),
    enabled: Boolean(loan.data),
  });
  const balance = useQuery({
    queryKey: [...key, "balance"],
    queryFn: ({ signal }) => api<Balance>(`/loans/${id}/balance`, { signal }),
    enabled: loan.data?.provisioningStatus === "COMPLETE",
  });
  const mapping = useQuery({
    queryKey: [...key, "mapping"],
    queryFn: ({ signal }) =>
      api<AccountMapping>(`/loans/${id}/ledger-account`, { signal }),
    enabled:
      loan.data?.provisioningStatus === "COMPLETE" &&
      Boolean(me?.capabilities.some((c) => ["REVIEW", "OPERATE"].includes(c))),
  });
  if (loan.isPending) return <Loading />;
  if (loan.error)
    return <ErrorNotice error={loan.error} retry={() => void loan.refetch()} />;
  const l = loan.data;
  return (
    <>
      <Button
        component={Link}
        to={`/app/applications/${l.applicationId}`}
        sx={{ mb: 2 }}
      >
        ← Application
      </Button>
      <SectionTitle
        eyebrow={l.publicReference}
        title="Your approved loan"
        detail={`Approved ${timestamp(l.approvedAt)} · Revision ${l.approvedRevision}`}
      />
      <Alert severity="info" sx={{ mb: 3 }}>
        Approval and account provisioning do not accept an agreement or disburse
        funds. This loan is awaiting a separate agreement process. This demo has
        no signing or payout function.
      </Alert>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: {
            xs: "minmax(0,1fr)",
            md: "repeat(2,minmax(0,1fr))",
          },
          gap: 3,
          "& > *": { minWidth: 0 },
        }}
      >
        <Paper sx={{ p: 3 }}>
          <Typography component="h2" variant="h3" gutterBottom>
            Approved terms
          </Typography>
          {terms.error ? (
            <ErrorNotice error={terms.error} />
          ) : terms.data ? (
            <Stack
              sx={{
                gap: 2,
              }}
            >
              <Typography variant="h2" component="p">
                {money(terms.data.principal, terms.data.currency)}
              </Typography>
              <Typography>
                Approved principal · {terms.data.termMonths} months
              </Typography>
              <Typography>
                Nominal annual interest:{" "}
                <strong>{percent(terms.data.nominalAnnualRate)}</strong> (not
                APR)
              </Typography>
              <Typography>
                Product {terms.data.productId} v{terms.data.productVersion} ·
                policy {terms.data.policyVersion}
              </Typography>
            </Stack>
          ) : (
            <Loading />
          )}
        </Paper>
        <Paper sx={{ p: 3 }}>
          <Typography component="h2" variant="h3" gutterBottom>
            Actual ledger balance
          </Typography>
          {balance.data ? (
            <>
              <Typography
                variant="h2"
                component="p"
                data-testid="ledger-balance"
              >
                {money(balance.data.balance, balance.data.currency)}
              </Typography>
              <Typography sx={{ mt: 1 }}>
                Principal receivable · Current financial balance.
              </Typography>
              <Typography variant="caption">
                Debits {money(balance.data.totalDebits)} · Credits{" "}
                {money(balance.data.totalCredits)}
              </Typography>
            </>
          ) : balance.error ? (
            <ErrorNotice
              error={balance.error}
              retry={() => void balance.refetch()}
            />
          ) : (
            <Typography>
              The ledger account is still being provisioned. A balance is not
              available yet.
            </Typography>
          )}
          <Button
            onClick={() => {
              void loan.refetch();
              if (l.provisioningStatus === "COMPLETE") void balance.refetch();
            }}
            sx={{ mt: 2 }}
          >
            Refresh account status
          </Button>
        </Paper>
        <Paper sx={{ p: 3, gridColumn: "1/-1" }}>
          <Typography component="h2" variant="h3" gutterBottom>
            Agreement & account status
          </Typography>
          <Stack
            direction={{ xs: "column", md: "row" }}
            sx={{
              gap: 3,
            }}
          >
            <Box>
              <Typography variant="caption">Lifecycle</Typography>
              <Box>
                <Status state={l.lifecycle} />
              </Box>
            </Box>
            <Box>
              <Typography variant="caption">Agreement</Typography>
              <Box>
                <Status state={l.agreementStatus} />
              </Box>
            </Box>
            <Box>
              <Typography variant="caption">Account provisioning</Typography>
              <Box>
                <Status state={l.provisioningStatus} />
              </Box>
            </Box>
          </Stack>
          <Typography sx={{ mt: 2 }}>
            Current lifecycle: {words(l.lifecycle)}. Approved amount and actual
            balance are separate records.
          </Typography>
          {mapping.data && (
            <Box sx={{ mt: 3, pt: 2, borderTop: "1px solid #dde4dc" }}>
              <Typography variant="h4" component="h3">
                Provisioning reference
              </Typography>
              <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
                Loan {l.id} · Account {mapping.data.accountId}
              </Typography>
              <Typography variant="caption" sx={{ overflowWrap: "anywhere" }}>
                Stable external reference: {mapping.data.externalReference}
              </Typography>
            </Box>
          )}
          {mapping.error && (
            <ErrorNotice
              error={mapping.error}
              retry={() => void mapping.refetch()}
            />
          )}
        </Paper>
        <Paper sx={{ p: 3, gridColumn: "1/-1" }}>
          <Typography component="h2" variant="h3" gutterBottom>
            Indicative repayment schedule
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            Calculated by the backend for review. This is not an executed
            agreement or a record of payments.
          </Typography>
          {schedule.error ? (
            <ErrorNotice error={schedule.error} />
          ) : schedule.data ? (
            <TableContainer
              role="region"
              tabIndex={0}
              aria-label="Scrollable indicative schedule"
              sx={{ maxHeight: 480, maxWidth: "100%", overflowX: "auto" }}
            >
              <Table
                stickyHeader
                size="small"
                aria-label="Indicative repayment schedule"
              >
                <TableHead>
                  <TableRow>
                    {[
                      "Month",
                      "Payment (SEK)",
                      "Principal (SEK)",
                      "Interest (SEK)",
                      "Remaining principal (SEK)",
                    ].map((h) => (
                      <TableCell key={h}>{h}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {schedule.data.periods.map((p) => (
                    <TableRow key={p.period}>
                      <TableCell component="th" scope="row">
                        {p.period}
                      </TableCell>
                      <TableCell>{p.payment}</TableCell>
                      <TableCell>{p.principal}</TableCell>
                      <TableCell>{p.interest}</TableCell>
                      <TableCell>{p.remainingPrincipal}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <Loading />
          )}
        </Paper>
      </Box>
    </>
  );
}
