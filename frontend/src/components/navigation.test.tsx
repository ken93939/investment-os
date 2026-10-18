import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { Navigation } from "./navigation";

vi.mock("next/navigation", () => ({ usePathname: () => "/watchlist" }));

it("GIVEN the watchlist route WHEN rendering navigation THEN only watchlist is marked as the current page", () => {
  // GIVEN
  const currentPage = "Watchlist";

  // WHEN
  render(<Navigation />);

  // THEN
  expect(
    screen.getByRole("link", { name: new RegExp(currentPage) }),
  ).toHaveAttribute("aria-current", "page");
  expect(screen.getByRole("link", { name: /Overview/ })).not.toHaveAttribute(
    "aria-current",
  );
  expect(screen.getByRole("link", { name: /Valuation/ })).toHaveAttribute(
    "href",
    "/valuation",
  );
  expect(screen.getByRole("link", { name: /Research/ })).toHaveAttribute(
    "href",
    "/research",
  );
});
