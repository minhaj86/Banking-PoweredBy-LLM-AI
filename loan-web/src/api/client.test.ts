import { afterEach, describe, expect, it, vi } from "vitest";
import {
  api,
  ApiError,
  errorMessage,
  mutationIntent,
  resetCsrf,
  upload,
} from "./client";
afterEach(() => {
  resetCsrf();
  vi.unstubAllGlobals();
});
describe("mutation safety", () => {
  it("settles an upload cancelled while obtaining CSRF without sending its body", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ token: "csrf", headerName: "X-CSRF-TOKEN" }),
          ),
        ),
    );
    const controller = new AbortController();
    controller.abort();
    await expect(
      upload(
        "/loan-applications/one/documents",
        new FormData(),
        1,
        mutationIntent({ file: "one" }),
        controller.signal,
        () => {},
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
  it("retains a key for the same logical payload and changes it when intent changes", () => {
    const first = mutationIntent({ amount: "50000.00", version: 1 });
    expect(mutationIntent({ amount: "50000.00", version: 1 }, first)).toBe(
      first,
    );
    expect(
      mutationIntent({ amount: "50001.00", version: 1 }, first).key,
    ).not.toBe(first.key);
  });
  it("sends session credentials, CSRF, correlation, idempotency and version centrally", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ token: "test-csrf", headerName: "X-CSRF-TOKEN" }),
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ state: "SUBMITTED" }), { status: 202 }),
      );
    vi.stubGlobal("fetch", fetcher);
    const intent = mutationIntent({ id: "one", version: 3 });
    await api("/loan-applications/one/submit", {
      method: "POST",
      intent,
      version: 3,
    });
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls[1][1]).toMatchObject({
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
      headers: {
        "X-CSRF-TOKEN": "test-csrf",
        "Idempotency-Key": intent.key,
        "If-Match": "3",
      },
    });
    expect(fetcher.mock.calls[1][1].headers["X-Correlation-Id"]).toBeTruthy();
  });
  it.each([401, 403, 404, 409, 422, 429, 503])(
    "distinguishes %s and never replays a failed mutation",
    async (status) => {
      const fetcher = vi
        .fn()
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({ token: "csrf", headerName: "X-CSRF-TOKEN" }),
          ),
        )
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({ code: "TEST", correlationId: "safe-reference" }),
            { status },
          ),
        );
      vi.stubGlobal("fetch", fetcher);
      await expect(
        api("/loan-applications/one/decisions", {
          method: "POST",
          body: { outcome: "APPROVE" },
          intent: mutationIntent({ outcome: "APPROVE" }),
        }),
      ).rejects.toEqual(new ApiError(status, "TEST", "safe-reference"));
      expect(fetcher).toHaveBeenCalledTimes(2);
      expect(errorMessage(status, "TEST")).toBeTruthy();
    },
  );
});
