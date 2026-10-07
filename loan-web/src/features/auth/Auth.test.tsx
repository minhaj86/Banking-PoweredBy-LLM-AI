import { useState } from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider, RequireSession, useAuth } from "./Auth";
import { me } from "../../test/fixtures";
import { Layout } from "../../components/Layout";
vi.mock("../../api/client", async () => {
  const actual = await vi.importActual("../../api/client");
  return { ...actual, api: vi.fn(async () => me) };
});
function SensitiveForm() {
  const { me: current } = useAuth();
  const [original] = useState(current?.identityKey);
  return <div>{original}</div>;
}
function wrapper(children: React.ReactNode, cache: QueryClient) {
  return (
    <QueryClientProvider client={cache}>
      <AuthProvider>
        <MemoryRouter>{children}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
describe("session boundaries", () => {
  it("clears sensitive caches and unmounts local form state when identity changes", async () => {
    const cache = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    cache.setQueryData(["demo", "oidc:applicant", "application", "one"], {
      private: true,
    });
    render(
      wrapper(
        <RequireSession>
          <SensitiveForm />
        </RequireSession>,
        cache,
      ),
    );
    await screen.findByText("oidc:applicant");
    await act(async () => {
      cache.setQueryData(["session"], { ...me, identityKey: "oidc:other" });
    });
    await screen.findByText("oidc:other");
    expect(
      cache.getQueryData(["demo", "oidc:applicant", "application", "one"]),
    ).toBeUndefined();
  });
  it("clears forms and caches on session expiry", async () => {
    const cache = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      wrapper(
        <RequireSession>
          <SensitiveForm />
        </RequireSession>,
        cache,
      ),
    );
    await screen.findByText("oidc:applicant");
    cache.setQueryData(["private"], { private: true });
    await act(async () => {
      globalThis.dispatchEvent(new Event("session-invalid"));
    });
    await screen.findByText("Sign in to continue");
    expect(screen.queryByText("oidc:applicant")).not.toBeInTheDocument();
    expect(cache.getQueryData(["private"])).toBeUndefined();
  });
  it("shows staff navigation only for effective staff permissions or a required step-up", async () => {
    const cache = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(wrapper(<Layout />, cache));
    await waitFor(() =>
      expect(
        screen.getByRole("link", { name: "My applications" }),
      ).toBeVisible(),
    );
    expect(
      screen.queryByRole("link", { name: "Staff workspace" }),
    ).not.toBeInTheDocument();
    await act(async () => {
      cache.setQueryData(["session"], {
        ...me,
        roles: ["APPLICANT", "LOAN_OFFICER"],
        staffStepUpRequired: true,
      });
    });
    expect(
      await screen.findByRole("link", { name: "Staff workspace" }),
    ).toBeVisible();
  });
});
