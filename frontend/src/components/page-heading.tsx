export function PageHeading({
  section,
  title,
  description,
}: {
  section: string;
  title: string;
  description: string;
}) {
  return (
    <header className="page-heading">
      <p className="eyebrow">
        Workspace <span>/</span> {section}
      </p>
      <div className="heading-row">
        <div>
          <h1>{title}</h1>
          <p className="page-description">{description}</p>
        </div>
        <span className="outline-badge">PERSONAL RESEARCH</span>
      </div>
    </header>
  );
}
