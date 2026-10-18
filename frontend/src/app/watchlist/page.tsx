import type { Metadata } from "next";
import { EmptyState } from "@/components/empty-state";
import { PageHeading } from "@/components/page-heading";

export const metadata: Metadata = { title: "Watchlist" };

export default function WatchlistPage() {
  return (
    <>
      <PageHeading
        section="Watchlist"
        title="Your watchlist"
        description="Keep the companies that matter within reach."
      />
      <EmptyState
        title="No instruments yet"
        description="This is where you will follow companies and ETFs. Instrument search and saved watchlists are not available yet."
      />
    </>
  );
}
