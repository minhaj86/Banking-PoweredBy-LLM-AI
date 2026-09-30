import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Divider,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { Link } from "react-router-dom";
import { api } from "../../api/client";
import type { Product } from "../../types/api";
import { money, percent } from "../../lib/format";
import { ErrorNotice } from "../../components/Feedback";
export function PublicPage() {
  const product = useQuery({
    queryKey: ["public-product"],
    queryFn: ({ signal }) => api<Product>("/bff/product", { signal }),
    staleTime: 60000,
  });
  return (
    <>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "1.25fr 1fr" },
          gap: { xs: 4, md: 8 },
          alignItems: "center",
          py: { xs: 2, md: 5 },
        }}
      >
        <Box>
          <Typography
            variant="overline"
            color="secondary"
            sx={{ letterSpacing: 3 }}
          >
            A clearer way forward
          </Typography>
          <Typography component="h1" variant="h1" sx={{ my: 2 }}>
            Your next chapter.
            <br />
            <Box
              component="span"
              sx={{ color: "primary.light", fontStyle: "italic" }}
            >
              Considered with care.
            </Box>
          </Typography>
          <Typography
            color="text.secondary"
            sx={{ maxWidth: 540, fontSize: 18, my: 3 }}
          >
            A straightforward personal loan application, with a secure place for
            your documents and a person behind every credit decision.
          </Typography>
          <Stack
            direction="row"
            spacing={1.5}
            useFlexGap
            sx={{
              flexWrap: "wrap",
            }}
          >
            <Button
              variant="contained"
              size="large"
              component={Link}
              to="/app/new"
            >
              Start an application{" "}
              <span aria-hidden style={{ marginLeft: 16 }}>
                ↗
              </span>
            </Button>
            <Button
              variant="outlined"
              href="/bff/login?returnTo=/app&register=true"
            >
              Create an account
            </Button>
          </Stack>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ display: "block", mt: 2 }}
          >
            Save as you go. Return when you’re ready. Applying does not
            guarantee approval.
          </Typography>
        </Box>
        <Paper
          sx={{
            bgcolor: "#e8eee5",
            p: { xs: 3, md: 4 },
            borderRadius: "20px 20px 70px 20px",
            position: "relative",
            overflow: "hidden",
          }}
        >
          <Typography variant="overline" sx={{ letterSpacing: 2 }}>
            Personal loan · SEK
          </Typography>
          {product.data ? (
            <>
              <Typography variant="h2" component="p" sx={{ mt: 2 }}>
                {money(product.data.minAmount)}
                <br />
                <Box
                  component="span"
                  sx={{ fontSize: 24, color: "text.secondary" }}
                >
                  to {money(product.data.maxAmount)}
                </Box>
              </Typography>
              <Divider sx={{ my: 3 }} />
              <Stack spacing={2}>
                <Stack
                  direction="row"
                  sx={{
                    justifyContent: "space-between",
                  }}
                >
                  <Typography>Loan term</Typography>
                  <Typography
                    sx={{
                      fontWeight: 600,
                    }}
                  >
                    {product.data.minMonths}–{product.data.maxMonths} months
                  </Typography>
                </Stack>
                <Stack
                  direction="row"
                  sx={{
                    justifyContent: "space-between",
                  }}
                >
                  <Typography>Nominal annual interest</Typography>
                  <Typography
                    sx={{
                      fontWeight: 600,
                    }}
                  >
                    {percent(product.data.nominalAnnualRate)}
                  </Typography>
                </Stack>
              </Stack>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: "block", mt: 3 }}
              >
                Demo product {product.data.version} · Nominal interest is not
                APR. Final terms require human approval.
              </Typography>
            </>
          ) : product.error ? (
            <ErrorNotice
              error={product.error}
              retry={() => void product.refetch()}
            />
          ) : (
            <Typography role="status">Loading current product…</Typography>
          )}
          <Typography
            sx={{
              mt: 4,
              pt: 3,
              borderTop: "1px solid #bacab6",
              fontFamily: "Georgia,serif",
              fontStyle: "italic",
              fontSize: 22,
            }}
          >
            Clear steps. Thoughtful review.
          </Typography>
        </Paper>
      </Box>
      <Alert severity="info" sx={{ my: 4 }}>
        This local demonstration uses synthetic financial checks and AI advice.
        IAM verification is separate from financial KYC. Approval creates an
        unfunded loan awaiting agreement.
      </Alert>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "repeat(3,1fr)" },
          gap: 4,
          mt: 5,
        }}
      >
        {[
          [
            "01",
            "Tell us your plans",
            "Complete eight short steps and save your draft securely along the way.",
          ],
          [
            "02",
            "Share your evidence",
            "Upload supporting documents for a security scan and financial evidence review.",
          ],
          [
            "03",
            "Follow the human review",
            "See requests for information and track the decision and account provisioning.",
          ],
        ].map(([n, title, detail]) => (
          <Box key={n}>
            <Typography color="secondary" variant="overline">
              {n} /
            </Typography>
            <Typography variant="h3" component="h2" gutterBottom>
              {title}
            </Typography>
            <Typography color="text.secondary">{detail}</Typography>
          </Box>
        ))}
      </Box>
    </>
  );
}
