import type { Metadata } from "next";
import { EmptyState } from "@/components/empty-state";
import { PageHeading } from "@/components/page-heading";

export const metadata: Metadata = { title: "Research" };

export default function ResearchPage() {
  return (
    <>
      <PageHeading
        section="Research"
        title="Evidence before opinion"
        description="Turn research questions into a view you can trace back to its sources."
      />
      <EmptyState
        title="No research reports yet"
        description="This workspace will bring company research and source citations together. Research reports are not available yet."
      />
    </>
  );
}
