import { EmptyState } from "@/components/empty-state";

export default function NotFound() {
  return (
    <EmptyState
      title="Page not found"
      description="This page does not exist. Return to your research overview to continue."
    />
  );
}
