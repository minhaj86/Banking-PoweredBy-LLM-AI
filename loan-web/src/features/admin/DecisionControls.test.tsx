import { expect, it, vi } from "vitest";
import { render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DecisionControls } from "./DecisionControls";
import { application, assessment, product } from "../../test/fixtures";
it("requires a review confirmation with exact terms and revision before any decision request", async () => {
  const user = userEvent.setup();
  const a = { ...application(), revision: 2, state: "MANUAL_REVIEW" };
  const onDone = vi.fn();
  render(
    <DecisionControls
      application={a}
      assessment={assessment}
      product={product}
      onDone={onDone}
    />,
  );
  await user.type(screen.getByLabelText("Reason codes"), "EVIDENCE_REQUIRED");
  await user.type(
    screen.getByLabelText("Restricted reviewer explanation"),
    "Need current supporting evidence",
  );
  await user.type(
    screen.getByLabelText("Explanation shown to the applicant"),
    "Please provide an updated document",
  );
  await user.click(screen.getByRole("button", { name: "Review decision" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("Revision 2 · version 4")).toBeVisible();
  expect(within(dialog).getByText(/50.000.00 SEK/)).toBeVisible();
  expect(within(dialog).getByText(/policy demo-1/)).toBeVisible();
  expect(onDone).not.toHaveBeenCalled();
  await user.click(within(dialog).getByRole("button", { name: "Go back" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
});
