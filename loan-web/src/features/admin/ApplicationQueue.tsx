import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import {
  Button,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { api } from "../../api/client";
import type { ApplicationSummary, Page, Product } from "../../types/api";
import { useAuth, scopeKey } from "../auth/Auth";
import {
  Empty,
  ErrorNotice,
  Loading,
  SectionTitle,
  Status,
} from "../../components/Feedback";
import { timestamp, words } from "../../lib/format";
const statuses = [
  "DRAFT",
  "SUBMITTED",
  "ASSESSMENT_PENDING",
  "DOCUMENTS_REQUIRED",
  "MANUAL_REVIEW",
  "ASSESSMENT_FAILED",
  "APPROVED",
  "REJECTED",
  "WITHDRAWN",
];
export function ApplicationQueue() {
  const { me } = useAuth();
  const [searchParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState(searchParams.get("status") ?? "");
  const [product, setProduct] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [sort, setSort] = useState("oldest");
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => {
      setQ(search);
      setPage(0);
    }, 400);
    return () => clearTimeout(timer);
  }, [search]);
  const params = new URLSearchParams({
    view: "staff",
    page: String(page),
    size: "20",
    sort,
  });
  if (status) params.set("status", status);
  if (product) params.set("productId", product);
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (q) params.set("q", q);
  const productInfo = useQuery({
    queryKey: ["public-product"],
    queryFn: ({ signal }) => api<Product>("/bff/product", { signal }),
  });
  const data = useQuery({
    queryKey: [...scopeKey(me), "staff-queue", params.toString()],
    queryFn: ({ signal }) =>
      api<Page<ApplicationSummary>>(`/loan-applications?${params}`, { signal }),
  });
  return (
    <>
      <SectionTitle
        title="Application queue"
        detail="Search and filters run in the backend within your tenant. Assignment is not supported by the current workflow; all authorised tenant reviews are shown."
      />
      <Paper sx={{ p: 3, mb: 3 }}>
        <div className="form-grid">
          <TextField
            select
            label="Application status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <MenuItem value="">All states</MenuItem>
            {statuses.map((s) => (
              <MenuItem key={s} value={s}>
                {words(s)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Product"
            value={product}
            onChange={(e) => {
              setProduct(e.target.value);
              setPage(0);
            }}
          >
            <MenuItem value="">All products</MenuItem>
            {productInfo.data && (
              <MenuItem value={productInfo.data.id}>
                {productInfo.data.name}
              </MenuItem>
            )}
          </TextField>
          <TextField
            label="Created from (UTC date)"
            type="date"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setPage(0);
            }}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Created through (UTC date)"
            type="date"
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setPage(0);
            }}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Search exact application ID"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            helperText="Opaque reference only; no names or financial facts in search"
          />
          <TextField
            select
            label="Sort queue"
            value={sort}
            onChange={(e) => {
              setSort(e.target.value);
              setPage(0);
            }}
          >
            {[
              ["oldest", "Oldest first"],
              ["newest", "Newest first"],
              ["updated", "Recently updated"],
            ].map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
        </div>
      </Paper>
      {data.isPending ? (
        <Loading />
      ) : data.error ? (
        <ErrorNotice error={data.error} retry={() => void data.refetch()} />
      ) : data.data.items.length === 0 ? (
        <Empty
          title="No applications in this view"
          detail="Try another state, date range or application reference."
        />
      ) : (
        <Paper>
          <TableContainer>
            <Table aria-label="Authorised application queue">
              <TableHead>
                <TableRow>
                  <TableCell>Application</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Revision</TableCell>
                  <TableCell>Updated (UTC)</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.data.items.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell>
                      <Typography
                        variant="body2"
                        sx={{ maxWidth: 230, overflowWrap: "anywhere" }}
                      >
                        {a.id}
                      </Typography>
                      <Typography variant="caption">
                        {a.productId ?? "Legacy product metadata"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Status state={a.state} />
                    </TableCell>
                    <TableCell>{a.revision}</TableCell>
                    <TableCell>{timestamp(a.updatedAt)}</TableCell>
                    <TableCell>
                      <Button
                        component={Link}
                        to={`/staff/applications/${a.id}`}
                      >
                        Open review
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={data.data.total}
            page={page}
            rowsPerPage={20}
            rowsPerPageOptions={[20]}
            onPageChange={(_, p) => setPage(p)}
          />
        </Paper>
      )}
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          mt: 2,
        }}
      >
        <Typography variant="caption">
          Server-side page size: 20 records
        </Typography>
        <Button onClick={() => void data.refetch()}>Refresh queue</Button>
      </Stack>
    </>
  );
}
