import type { Metadata } from "next";
import { EmptyState } from "@/components/empty-state";
import { PageHeading } from "@/components/page-heading";

export const metadata: Metadata = { title: "Valuation" };

export default function ValuationPage() {
  return (
    <>
      <PageHeading
        section="Valuation"
        title="Understand the value"
        description="Ground every scenario in financial records and explicit assumptions."
      />
      <EmptyState
        title="No valuation models yet"
        description="Compare company financials and explicit assumptions here. Valuation tools are not available yet."
      />
    </>
  );
}
