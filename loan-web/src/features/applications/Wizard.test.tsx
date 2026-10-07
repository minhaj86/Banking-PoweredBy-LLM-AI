import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { WizardForm } from "./Wizard";
import { api, ApiError } from "../../api/client";
import { application, me, product } from "../../test/fixtures";
vi.mock("../auth/Auth", () => ({
  useAuth: () => ({ me }),
  scopeKey: () => ["demo", "oidc:applicant"],
}));
vi.mock("../documents/Documents", () => ({
  Documents: () => <div>Private document status</div>,
}));
vi.mock("../../api/client", async () => ({
  ...(await vi.importActual("../../api/client")),
  api: vi.fn(),
}));
function open() {
  const cache = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={cache}>
      <MemoryRouter>
        <WizardForm product={product} current={application()} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  vi.mocked(api).mockReset();
});
it("validates the active step, announces errors, and supports back/next navigation", async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockResolvedValue({ ...application(), version: 5 });
  open();
  const amount = screen.getByLabelText("Requested amount (SEK)");
  await user.clear(amount);
  await user.type(amount, "50000.001");
  await user.click(screen.getByRole("button", { name: "Save & continue" }));
  expect(screen.getByRole("alert")).toHaveTextContent(
    "Check the highlighted fields",
  );
  expect(api).not.toHaveBeenCalled();
  await user.clear(amount);
  await user.type(amount, "50000.00");
  await user.click(screen.getByRole("button", { name: "Save & continue" }));
  expect(
    await screen.findByRole("heading", { name: "Personal details" }),
  ).toBeVisible();
  await user.click(screen.getByRole("button", { name: "Back" }));
  expect(screen.getByRole("heading", { name: "Your loan" })).toBeVisible();
});
it("shows saving and conflict states without overwriting newer local edits", async () => {
  const user = userEvent.setup();
  let complete: (value: unknown) => void = () => {};
  vi.mocked(api).mockImplementation(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  open();
  await user.click(screen.getByRole("button", { name: "Save draft" }));
  expect(screen.getByRole("status")).toHaveTextContent("Saving");
  const purpose = screen.getByLabelText("What will the loan be used for?");
  await user.clear(purpose);
  await user.type(purpose, "A newer unsaved edit");
  await act(async () => complete({ ...application(), version: 5 }));
  expect(purpose).toHaveValue("A newer unsaved edit");
  expect(screen.getByRole("status")).toHaveTextContent("Unsaved changes");
  vi.mocked(api).mockRejectedValueOnce(new ApiError(409, "VERSION_CONFLICT"));
  await user.click(screen.getByRole("button", { name: "Save draft" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "VERSION_CONFLICT",
  );
  expect(screen.getByRole("status")).toHaveTextContent("Save failed");
});
