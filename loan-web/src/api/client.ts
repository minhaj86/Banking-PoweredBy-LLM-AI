export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    public correlationId?: string,
  ) {
    super(errorMessage(status, code));
  }
}
export function errorMessage(status: number, code: string): string {
  if (code === "EMAIL_VERIFICATION_REQUIRED")
    return "Verify your email in your account before submitting.";
  if (code === "STEP_UP_REQUIRED")
    return "Complete staff multifactor authentication to continue.";
  const messages: Record<number, string> = {
    401: "Your session has ended. Sign in again; your saved work is safe.",
    403: "This action is not permitted. Your session or security token may need renewal.",
    404: "This record is unavailable or you do not have access.",
    409: "This record changed or the action is no longer allowed. Reload and review the current version.",
    422: "The backend could not accept these details. Check required fields and the current product rules.",
    429: "The service is busy. Wait a moment before trying the same request again.",
  };
  return (
    messages[status] ??
    (status >= 500
      ? "The service is temporarily unavailable. Check the result before retrying an action."
      : "The request could not be completed. Check your details.")
  );
}
let csrfPromise: Promise<{ token: string; headerName: string }> | undefined;
export function resetCsrf() {
  csrfPromise = undefined;
}
export async function csrf() {
  csrfPromise ??= fetch("/bff/csrf", {
    credentials: "same-origin",
    cache: "no-store",
  })
    .then(async (r) => {
      if (!r.ok) throw new ApiError(r.status, "SESSION_UNAVAILABLE");
      return r.json() as Promise<{ token: string; headerName: string }>;
    })
    .catch((e) => {
      resetCsrf();
      throw e;
    });
  return csrfPromise;
}
export interface Intent {
  key: string;
  payload: string;
}
export function mutationIntent(payload: unknown, previous?: Intent): Intent {
  const encoded = JSON.stringify(payload);
  return previous?.payload === encoded
    ? previous
    : { key: crypto.randomUUID(), payload: encoded };
}
interface Options {
  method?: "GET" | "POST" | "PUT";
  body?: unknown;
  signal?: AbortSignal;
  intent?: Intent;
  version?: number;
}
async function problem(
  response: Response,
  notifySession = true,
): Promise<never> {
  const p = (await response.json().catch(() => ({}))) as {
    code?: string;
    correlationId?: string;
  };
  if (response.status === 401) {
    resetCsrf();
    if (notifySession) globalThis.dispatchEvent(new Event("session-invalid"));
  }
  if (response.status === 403) resetCsrf();
  throw new ApiError(
    response.status,
    p.code ?? "REQUEST_FAILED",
    p.correlationId,
  );
}
export async function api<T>(path: string, options: Options = {}): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-Correlation-Id": crypto.randomUUID(),
  };
  if (method !== "GET") {
    const token = await csrf();
    headers[token.headerName] = token.token;
  }
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (options.intent) headers["Idempotency-Key"] = options.intent.key;
  if (options.version !== undefined)
    headers["If-Match"] = String(options.version);
  const response = await fetch(
    path.startsWith("/bff/") ? path : `/bff/api${path}`,
    {
      method,
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
    },
  );
  if (!response.ok) return problem(response, path !== "/bff/me");
  return (response.status === 204 ? undefined : await response.json()) as T;
}
export async function apiBlob(
  path: string,
  signal?: AbortSignal,
): Promise<Blob> {
  const response = await fetch(`/bff/api${path}`, {
    credentials: "same-origin",
    cache: "no-store",
    signal,
    redirect: "error",
    headers: { "X-Correlation-Id": crypto.randomUUID() },
  });
  if (!response.ok) return problem(response);
  return response.blob();
}
export async function upload<T>(
  path: string,
  body: FormData,
  version: number,
  intent: Intent,
  signal: AbortSignal,
  progress: (percent: number) => void,
): Promise<T> {
  const token = await csrf();
  if (signal.aborted) throw new DOMException("Upload cancelled", "AbortError");
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `/bff/api${path}`);
    xhr.withCredentials = true;
    xhr.setRequestHeader(token.headerName, token.token);
    xhr.setRequestHeader("If-Match", String(version));
    xhr.setRequestHeader("Idempotency-Key", intent.key);
    xhr.setRequestHeader("X-Correlation-Id", crypto.randomUUID());
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) progress(Math.round((e.loaded * 100) / e.total));
    };
    const abort = () => xhr.abort();
    signal.addEventListener("abort", abort, { once: true });
    xhr.onloadend = () => signal.removeEventListener("abort", abort);
    xhr.onerror = () => reject(new ApiError(503, "UPLOAD_UNAVAILABLE"));
    xhr.onabort = () =>
      reject(new DOMException("Upload cancelled", "AbortError"));
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300)
        resolve(JSON.parse(xhr.responseText) as T);
      else {
        let code = "UPLOAD_FAILED";
        try {
          code = (JSON.parse(xhr.responseText) as { code: string }).code;
        } catch {
          /* server may reject before JSON */
        }
        if (xhr.status === 401)
          globalThis.dispatchEvent(new Event("session-invalid"));
        if (xhr.status === 403) resetCsrf();
        reject(new ApiError(xhr.status, code));
      }
    };
    if (signal.aborted) abort();
    else xhr.send(body);
  });
}
export async function logout() {
  const token = await csrf();
  // A normal form navigation follows the provider's client_id logout redirect without exposing tokens.
  globalThis.dispatchEvent(new Event("session-invalid"));
  const form = document.createElement("form");
  form.method = "POST";
  form.action = "/bff/logout";
  const input = document.createElement("input");
  input.type = "hidden";
  input.name = "_csrf";
  input.value = token.token;
  form.append(input);
  document.body.append(form);
  form.submit();
  resetCsrf();
}
