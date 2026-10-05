import { lazy, Suspense } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import { useAuth } from "../auth/Auth";
import { Loading } from "../../components/Feedback";
import { APP_NAME } from "../../app/brand";
import { StaffDashboard } from "./StaffDashboard";
import { ApplicationQueue } from "./ApplicationQueue";
const ReviewWorkspace = lazy(() => import("./ReviewWorkspace"));
const Operations = lazy(() => import("./Operations"));
const Configuration = lazy(() => import("./Configuration"));
export default function StaffRoutes() {
  const { me } = useAuth();
  const review = me?.capabilities.includes("REVIEW");
  const compliance = me?.capabilities.includes("COMPLIANCE");
  const operations = me?.capabilities.includes("OPERATE");
  return (
    <>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        sx={{
          gap: 2,
          justifyContent: "space-between",
          mb: 4,
        }}
      >
        <Box>
          <Typography variant="overline" color="secondary">
            {APP_NAME} · Staff administration
          </Typography>
          <Typography variant="body2">
            Tenant {me?.tenant} · Multifactor session
          </Typography>
        </Box>
        <Stack
          component="nav"
          aria-label="Staff navigation"
          direction="row"
          sx={{
            gap: 0.5,
            flexWrap: "wrap",
          }}
        >
          <Button component={NavLink} to="/staff" end>
            Overview
          </Button>
          {(review || compliance || operations) && (
            <Button component={NavLink} to="/staff/applications">
              Applications
            </Button>
          )}
          {operations && (
            <Button component={NavLink} to="/staff/operations">
              Operations
            </Button>
          )}
          <Button component={NavLink} to="/staff/configuration">
            Configuration
          </Button>
        </Stack>
      </Stack>
      <Suspense fallback={<Loading label="Opening staff tools" />}>
        <Routes>
          <Route index element={<StaffDashboard />} />
          <Route
            path="applications"
            element={
              review || compliance || operations ? (
                <ApplicationQueue />
              ) : (
                <Denied />
              )
            }
          />
          <Route
            path="applications/:id"
            element={
              review || compliance || operations ? (
                <ReviewWorkspace />
              ) : (
                <Denied />
              )
            }
          />
          <Route
            path="operations"
            element={operations ? <Operations /> : <Denied />}
          />
          <Route path="configuration" element={<Configuration />} />
          <Route path="*" element={<Denied />} />
        </Routes>
      </Suspense>
    </>
  );
}
function Denied() {
  return (
    <Alert severity="warning">
      Your effective permissions do not include this workspace.
    </Alert>
  );
}
