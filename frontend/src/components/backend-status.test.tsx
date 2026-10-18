import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { BackendStatus } from "./backend-status";

afterEach(() => vi.unstubAllGlobals());

it("GIVEN an unchecked connection WHEN a check is pending THEN progress is shown and duplicate checks are disabled", async () => {
  // GIVEN
  let resolveResponse!: (response: Response) => void;
  const pending = new Promise<Response>((resolve) => {
    resolveResponse = resolve;
  });
  const fetchMock = vi.fn().mockReturnValue(pending);
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<BackendStatus />);
  expect(screen.getByRole("status")).toHaveTextContent("Not checked");
  expect(fetchMock).not.toHaveBeenCalled();

  // WHEN
  await user.click(screen.getByRole("button", { name: /check connection/i }));

  // THEN
  expect(screen.getByRole("status")).toHaveTextContent("Checking connection");
  expect(screen.getByRole("button", { name: /checking/i })).toBeDisabled();
  await act(async () =>
    resolveResponse(Response.json({ status: "connected" })),
  );
  expect(screen.getByRole("status")).toHaveTextContent("Backend connected");
  expect(
    screen.getByRole("button", { name: /check connection/i }),
  ).toBeEnabled();
});

it("GIVEN a healthy backend WHEN the check succeeds THEN backend availability is shown separately from market data", async () => {
  // GIVEN
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(Response.json({ status: "connected" })),
  );
  const user = userEvent.setup();
  render(<BackendStatus />);

  // WHEN
  await user.click(screen.getByRole("button", { name: /check connection/i }));

  // THEN
  expect(await screen.findByText("Backend connected")).toBeVisible();
  expect(screen.getByText(/Market data is not connected yet/)).toBeVisible();
});

it("GIVEN an unreachable backend WHEN a check fails and is retried THEN the user can recover", async () => {
  // GIVEN
  const fetchMock = vi
    .fn()
    .mockRejectedValueOnce(new TypeError("network failure"))
    .mockResolvedValueOnce(Response.json({ status: "connected" }));
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<BackendStatus />);

  // WHEN
  await user.click(screen.getByRole("button", { name: /check connection/i }));

  // THEN
  expect(await screen.findByText("Backend unavailable")).toBeVisible();
  expect(
    screen.getByRole("button", { name: /check connection/i }),
  ).toBeEnabled();

  // WHEN
  await user.click(screen.getByRole("button", { name: /check connection/i }));

  // THEN
  expect(await screen.findByText("Backend connected")).toBeVisible();
});

it("GIVEN an unexpected proxy response WHEN checking the connection THEN the UI does not claim success", async () => {
  // GIVEN
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(Response.json({ status: "UP" })),
  );
  const user = userEvent.setup();
  render(<BackendStatus />);

  // WHEN
  await user.click(screen.getByRole("button", { name: /check connection/i }));

  // THEN
  expect(await screen.findByText("Backend unavailable")).toBeVisible();
});
