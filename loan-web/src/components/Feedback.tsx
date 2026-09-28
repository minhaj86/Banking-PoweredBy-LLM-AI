import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Stack,
  Typography,
} from "@mui/material";
import { ApiError } from "../api/client";
import { words } from "../lib/format";
export function ErrorNotice({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  return (
    <Alert
      severity="error"
      role="alert"
      action={
        retry ? (
          <Button color="inherit" onClick={retry}>
            Retry
          </Button>
        ) : undefined
      }
    >
      <Typography>
        {error instanceof Error
          ? error.message
          : "This request could not be completed."}
      </Typography>
      {error instanceof ApiError && (
        <Typography variant="caption">
          {error.code}
          {error.correlationId ? ` · Reference ${error.correlationId}` : ""}
        </Typography>
      )}
    </Alert>
  );
}
export function Loading({
  label = "Loading your records",
}: {
  label?: string;
}) {
  return (
    <Stack
      direction="row"
      spacing={2}
      role="status"
      sx={{
        alignItems: "center",
        py: 4,
      }}
    >
      <CircularProgress size={22} aria-label={label} />
      <Typography>{label}</Typography>
    </Stack>
  );
}
export function Status({ state }: { state: string }) {
  return (
    <Chip
      label={words(state)}
      size="small"
      variant="outlined"
      color={
        state === "APPROVED" || state === "COMPLETE" || state === "CLEAN"
          ? "success"
          : state.includes("FAILED") || state === "REJECTED"
            ? "error"
            : "default"
      }
      sx={{
        fontWeight: 600,
        height: "auto",
        minHeight: 28,
        "& .MuiChip-label": { py: 0.3, whiteSpace: "normal" },
      }}
    />
  );
}
export function Empty({ title, detail }: { title: string; detail: string }) {
  return (
    <Box sx={{ py: 5, textAlign: "center" }}>
      <Typography variant="h3" gutterBottom>
        {title}
      </Typography>
      <Typography color="text.secondary">{detail}</Typography>
    </Box>
  );
}
export function SectionTitle({
  eyebrow,
  title,
  detail,
}: {
  eyebrow?: string;
  title: string;
  detail?: string;
}) {
  return (
    <Box sx={{ mb: 4 }}>
      {eyebrow && (
        <Typography
          variant="overline"
          color="secondary"
          sx={{ letterSpacing: 2 }}
        >
          {eyebrow}
        </Typography>
      )}
      <Typography variant="h2" component="h1" gutterBottom>
        {title}
      </Typography>
      {detail && (
        <Typography color="text.secondary" sx={{ maxWidth: 720 }}>
          {detail}
        </Typography>
      )}
    </Box>
  );
}
