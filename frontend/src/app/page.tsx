import Link from "next/link";
import { BackendStatus } from "@/components/backend-status";
import { Icon } from "@/components/icon";
import { PageHeading } from "@/components/page-heading";

const benchmarks = [
  "S&P 500",
  "Nasdaq Composite",
  "Dow Jones",
  "US 10Y Treasury",
];
const tools = [
  {
    href: "/watchlist",
    icon: "watchlist",
    number: "01",
    title: "Follow your conviction",
    description: "A focused place for the companies you want to understand.",
    action: "Open watchlist",
  },
  {
    href: "/valuation",
    icon: "valuation",
    number: "02",
    title: "Make assumptions explicit",
    description: "Explore what a business could be worth, and why.",
    action: "Explore valuation",
  },
  {
    href: "/research",
    icon: "research",
    number: "03",
    title: "Build a view with evidence",
    description: "Bring financial records and research into one clear picture.",
    action: "Open research",
  },
] as const;

export default function OverviewPage() {
  return (
    <>
      <PageHeading
        section="Overview"
        title="Research overview"
        description="Follow companies, test assumptions, and build an informed view."
      />
      <section className="market-strip" aria-label="Market benchmarks">
        {benchmarks.map((name) => (
          <div className="benchmark" key={name}>
            <h2>{name}</h2>
            <span className="benchmark-value" aria-label="Value unavailable">
              —
            </span>
            <span className="muted small">Awaiting data source</span>
          </div>
        ))}
      </section>
      <div className="overview-grid">
        <section
          className="panel market-panel"
          aria-labelledby="market-heading"
        >
          <div className="panel-heading">
            <h2 id="market-heading">Market activity</h2>
            <span className="subtle-badge">NO DATA SOURCE</span>
          </div>
          <div className="chart-empty">
            <div className="chart-grid" aria-hidden="true" />
            <div className="chart-empty-content">
              <span className="empty-icon">
                <Icon name="valuation" width="28" height="28" />
              </span>
              <h3>A clear view starts with reliable data.</h3>
              <p>
                Connect a market data provider to bring prices
                <br className="desktop-break" /> and historical performance into
                your workspace.
              </p>
              <span className="text-label">No market data connected</span>
            </div>
          </div>
          <div className="panel-footnote">
            <span className="status-dot" />
            Values, sources, and timestamps will appear when data is available.
          </div>
        </section>
        <div className="overview-aside">
          <BackendStatus />
          <section className="principle-note">
            <span className="eyebrow">THE RESEARCH STANDARD</span>
            <h2>Know what you know.</h2>
            <p>
              Keep facts, calculations, assumptions, and interpretation
              distinct. Every figure should have a source.
            </p>
            <span className="note-rule" />
          </section>
        </div>
      </div>
      <section aria-labelledby="tools-heading" className="tools-section">
        <div className="section-heading">
          <h2 id="tools-heading">Your research toolkit</h2>
          <span className="eyebrow">FOUNDATION WORKSPACE</span>
        </div>
        <div className="tools-grid">
          {tools.map((tool) => (
            <Link href={tool.href} key={tool.href} className="tool-card">
              <div className="tool-top">
                <Icon name={tool.icon} />
                <span>{tool.number}</span>
              </div>
              <h3>{tool.title}</h3>
              <p>{tool.description}</p>
              <span className="tool-action">
                {tool.action}
                <Icon name="arrow" width="16" height="16" />
              </span>
            </Link>
          ))}
        </div>
      </section>
    </>
  );
}
