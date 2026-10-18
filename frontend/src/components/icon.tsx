import type { SVGProps } from "react";

type IconName =
  "overview" | "watchlist" | "valuation" | "research" | "arrow" | "signal";

const paths: Record<IconName, string> = {
  overview: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  watchlist: "M6 3h12v18l-6-4-6 4z",
  valuation: "M4 20V10 M10 20V4 M16 20v-7 M22 20H2",
  research: "M5 3h10l4 4v14H5z M14 3v5h5 M8 12h8 M8 16h5",
  arrow: "M5 12h14 M13 6l6 6-6 6",
  signal: "M3 16h3l3-9 5 13 3-8h4",
};

export function Icon({
  name,
  ...props
}: SVGProps<SVGSVGElement> & { name: IconName }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d={paths[name]} />
    </svg>
  );
}
