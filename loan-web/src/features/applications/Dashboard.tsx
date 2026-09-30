import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Pagination,
  Stack,
  Typography,
} from "@mui/material";
import { Link } from "react-router-dom";
import { api } from "../../api/client";
import type { ApplicationSummary, Page } from "../../types/api";
import { scopeKey, useAuth } from "../auth/Auth";
import {
  Empty,
  ErrorNotice,
  Loading,
  SectionTitle,
  Status,
} from "../../components/Feedback";
import { timestamp, editable } from "../../lib/format";
export function Dashboard() {
  const { me } = useAuth();
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: [...scopeKey(me), "applications", "mine", page],
    queryFn: ({ signal }) =>
      api<Page<ApplicationSummary>>(
        `/loan-applications?view=mine&page=${page}&size=10`,
        { signal },
      ),
  });
  return (
    <>
      <SectionTitle
        eyebrow="Your personal workspace"
        title={`Welcome${me ? ", " + me.displayName.split(" ")[0] : ""}.`}
        detail="Keep track of your applications and the next step in each review."
      />
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          mb: 3,
        }}
      >
        <Typography component="h2" variant="h3">
          My applications
        </Typography>
        <Button component={Link} to="/app/new" variant="contained">
          New application
        </Button>
      </Stack>
      {!me?.emailVerified && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          Your email must be verified before you can submit.{" "}
          <a href={me?.accountUrl}>Open account settings</a>.
        </Alert>
      )}
      {result.isPending ? (
        <Loading />
      ) : result.error ? (
        <ErrorNotice error={result.error} retry={() => void result.refetch()} />
      ) : result.data.items.length === 0 ? (
        <Empty
          title="Your next chapter starts here"
          detail="Create a draft to explore the current product. You can save it and return later."
        />
      ) : (
        <Stack spacing={2}>
          {result.data.items.map((a) => (
            <Card key={a.id}>
              <CardContent sx={{ p: { xs: 2.5, md: 3 } }}>
                <Box
                  sx={{
                    display: "flex",
                    alignItems: { xs: "start", sm: "center" },
                    gap: 2,
                    flexDirection: { xs: "column", sm: "row" },
                  }}
                >
                  <Box sx={{ flex: 1 }}>
                    <Stack
                      direction="row"
                      spacing={1}
                      sx={{
                        alignItems: "center",
                        mb: 1,
                      }}
                    >
                      <Status state={a.state} />
                      <Typography variant="caption">
                        Revision {a.revision}
                      </Typography>
                    </Stack>
                    <Typography component="h3" variant="h4">
                      Personal loan application
                    </Typography>
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{ my: 0.5 }}
                    >
                      Updated {timestamp(a.updatedAt)}
                    </Typography>
                    <Typography
                      variant="caption"
                      sx={{ overflowWrap: "anywhere" }}
                    >
                      Reference {a.id}
                    </Typography>
                    {a.state === "DOCUMENTS_REQUIRED" && (
                      <Typography sx={{ mt: 1, fontWeight: 600 }}>
                        Information requested · Review the message and add
                        evidence.
                      </Typography>
                    )}
                  </Box>
                  <Stack
                    direction="row"
                    sx={{
                      gap: 1,
                      flexWrap: "wrap",
                    }}
                  >
                    <Button
                      component={Link}
                      to={`/app/applications/${a.id}`}
                      variant="outlined"
                    >
                      View application
                    </Button>
                    {editable(a.state) && (
                      <Button
                        component={Link}
                        to={`/app/applications/${a.id}/edit`}
                      >
                        Resume draft
                      </Button>
                    )}
                    {a.loanId && (
                      <Button
                        component={Link}
                        to={`/app/loans/${a.loanId}`}
                        variant="contained"
                      >
                        View loan
                      </Button>
                    )}
                  </Stack>
                </Box>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}
      {result.data && result.data.total > 10 && (
        <Pagination
          page={page + 1}
          count={Math.ceil(result.data.total / 10)}
          onChange={(_, p) => setPage(p - 1)}
          sx={{ mt: 3 }}
        />
      )}
    </>
  );
}
