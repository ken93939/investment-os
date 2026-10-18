// @vitest-environment node

import { afterEach, expect, it, vi } from "vitest";
import { GET } from "./route";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

it("GIVEN a healthy backend WHEN checking its health THEN the route reports connected without caching", async () => {
  // GIVEN
  vi.stubEnv("BACKEND_URL", "http://127.0.0.1:8080");
  const fetchMock = vi.fn().mockResolvedValue(Response.json({ status: "UP" }));
  vi.stubGlobal("fetch", fetchMock);

  // WHEN
  const response = await GET();

  // THEN
  expect(response.status).toBe(200);
  expect(await response.json()).toEqual({ status: "connected" });
  expect(response.headers.get("Cache-Control")).toBe("no-store");
  expect(fetchMock).toHaveBeenCalledWith(
    new URL("http://127.0.0.1:8080/ready"),
    expect.objectContaining({
      cache: "no-store",
      redirect: "error",
      signal: expect.any(AbortSignal),
    }),
  );
});

it.each([
  [
    "a non-success response",
    () => new Response("unavailable", { status: 503 }),
  ],
  ["a DOWN health status", () => Response.json({ status: "DOWN" })],
  ["an unexpected response shape", () => Response.json({ healthy: true })],
  ["a null response body", () => Response.json(null)],
  ["malformed JSON", () => new Response("invalid JSON")],
])(
  "GIVEN %s WHEN checking backend health THEN the route reports unavailable",
  async (_name, responseFactory) => {
    // GIVEN
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(responseFactory()));

    // WHEN
    const response = await GET();

    // THEN
    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({ status: "unavailable" });
  },
);

it.each([
  new TypeError("connection refused"),
  new DOMException("timed out", "TimeoutError"),
])(
  "GIVEN a network failure %s WHEN checking backend health THEN the route returns a safe unavailable response",
  async (error) => {
    // GIVEN
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(error));

    // WHEN
    const response = await GET();

    // THEN
    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({ status: "unavailable" });
  },
);

it.each([
  "invalid-url",
  "file:///tmp",
  "http://user:secret@localhost",
  "http://localhost/base",
  "http://localhost?target=other",
])(
  "GIVEN an invalid backend origin %s WHEN checking health THEN configuration is rejected before any request",
  async (origin) => {
    // GIVEN
    vi.stubEnv("BACKEND_URL", origin);
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    // WHEN
    const response = await GET();

    // THEN
    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({ status: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  },
);
