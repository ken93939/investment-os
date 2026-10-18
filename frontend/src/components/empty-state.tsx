import Link from "next/link";
import { Icon } from "@/components/icon";

export function EmptyState({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <section className="panel feature-empty">
      <span className="empty-icon">
        <Icon name="research" width="28" height="28" />
      </span>
      <p className="eyebrow">Ready for the next step</p>
      <h2>{title}</h2>
      <p>{description}</p>
      <Link className="button secondary" href="/">
        Back to overview <Icon name="arrow" />
      </Link>
    </section>
  );
}
