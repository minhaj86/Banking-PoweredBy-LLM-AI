import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Alert, Box, Button, Paper, Typography } from "@mui/material";
import { api } from "../../api/client";
import { useAuth, scopeKey } from "../auth/Auth";
import {
  Empty,
  ErrorNotice,
  Loading,
  SectionTitle,
} from "../../components/Feedback";
import { words } from "../../lib/format";
interface Summary {
  applications?: {
    state: string;
    count: number;
  }[];
  provisioning?: {
    state: string;
    count: number;
  }[];
}
export function StaffDashboard() {
  const { me } = useAuth();
  const data = useQuery({
    queryKey: [...scopeKey(me), "staff-summary"],
    queryFn: ({ signal }) => api<Summary>("/staff/summary", { signal }),
  });
  return (
    <>
      <SectionTitle
        title="Every review has a person behind it."
        detail="Live workflow counts for your tenant and effective permissions. Credit review and compliance are separate responsibilities."
      />
      {data.isPending ? (
        <Loading />
      ) : data.error ? (
        <ErrorNotice error={data.error} retry={() => void data.refetch()} />
      ) : (
        <>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: {
                xs: "1fr",
                sm: "repeat(2,1fr)",
                md: "repeat(3,1fr)",
              },
              gap: 2,
            }}
          >
            {data.data.applications?.map((item) => (
              <Paper key={item.state} sx={{ p: 3 }}>
                <Typography color="text.secondary">
                  {words(item.state)}
                </Typography>
                <Typography
                  component="p"
                  sx={{ fontFamily: "Georgia,serif", fontSize: 48, my: 1 }}
                >
                  {item.count}
                </Typography>
                <Button
                  component={Link}
                  to={`/staff/applications?status=${item.state}`}
                >
                  Open applications ↗
                </Button>
              </Paper>
            ))}
          </Box>
          {!data.data.applications && (
            <Empty
              title="Configuration access"
              detail="Your role can inspect approved configuration. It does not grant credit-decision or document access."
            />
          )}
          {data.data.provisioning && (
            <>
              <Typography component="h2" variant="h3" sx={{ mt: 4, mb: 2 }}>
                Ledger account provisioning
              </Typography>
              <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
                {data.data.provisioning.map((item) => (
                  <Paper sx={{ p: 3, minWidth: 180 }} key={item.state}>
                    <Typography>{words(item.state)}</Typography>
                    <Typography variant="h2" component="p">
                      {item.count}
                    </Typography>
                  </Paper>
                ))}
              </Box>
              <Button component={Link} to="/staff/operations" sx={{ mt: 2 }}>
                Inspect operations
              </Button>
            </>
          )}
        </>
      )}
      <Alert severity="info" sx={{ mt: 4 }}>
        AI output supports review only. Approval still requires current
        verification gates, evidence review and an explicit human decision.
      </Alert>
    </>
  );
}
