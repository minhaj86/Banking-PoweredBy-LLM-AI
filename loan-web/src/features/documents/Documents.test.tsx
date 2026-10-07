import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, it, vi } from "vitest";
import { Documents } from "./Documents";
import { api } from "../../api/client";
import { me } from "../../test/fixtures";
vi.mock("../auth/Auth", () => ({
  useAuth: () => ({ me }),
  scopeKey: () => ["demo", "oidc:applicant"],
}));
vi.mock("../../api/client", async () => ({
  ...(await vi.importActual("../../api/client")),
  api: vi.fn(),
}));
function open() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <Documents applicationId="one" />
    </QueryClientProvider>,
  );
}
it("keeps quarantined evidence unavailable for preview and distinguishes extraction from verification", async () => {
  vi.mocked(api).mockResolvedValue([
    {
      id: "doc",
      category: "PAYSLIP",
      uploadedForRevision: 1,
      periodStart: "2026-10-01",
      periodEnd: "2026-10-27",
      uploadStatus: "COMPLETE",
      scanStatus: "REVIEW_REQUIRED",
      processingStatus: "FAILED",
      supersedes: null,
      evidence: null,
    },
  ]);
  open();
  expect(
    await screen.findByText("Quarantined.", { exact: false }),
  ).toBeVisible();
  expect(
    screen.getByRole("button", { name: "Preview evidence" }),
  ).toBeDisabled();
  expect(
    screen.getByText("Processing needs attention.", { exact: false }),
  ).toBeVisible();
});
it("presents a document API failure with an explicit retry action", async () => {
  vi.mocked(api).mockRejectedValue(
    new Error("Document service temporarily unavailable"),
  );
  open();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Document service temporarily unavailable",
  );
  expect(screen.getByRole("button", { name: "Retry" })).toBeVisible();
});
