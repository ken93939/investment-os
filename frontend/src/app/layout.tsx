import type { Metadata } from "next";
import Link from "next/link";
import { Navigation } from "@/components/navigation";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "Investment OS | Research workspace",
    template: "%s | Investment OS",
  },
  description:
    "A personal workspace for market data, valuation, and evidence-grounded investment research.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <a className="skip-link" href="#main-content">
          Skip to content
        </a>
        <div className="terminal-shell">
          <aside className="sidebar">
            <Link className="brand" href="/" aria-label="Investment OS home">
              <span className="brand-mark">
                io<span>_</span>
              </span>
              <span>
                INVESTMENT<span className="brand-os">OS</span>
              </span>
            </Link>
            <p className="sidebar-label">RESEARCH TERMINAL</p>
            <Navigation />
            <div className="sidebar-footer">
              <span className="workspace-avatar">K</span>
              <div>
                Personal workspace<span>Read-only research</span>
              </div>
            </div>
          </aside>
          <div className="workspace">
            <div className="topbar">
              <span>
                <span className="topbar-square" />
                RESEARCH WORKSPACE
              </span>
              <span className="topbar-note">
                Independent thinking. Traceable evidence.
              </span>
            </div>
            <main id="main-content" tabIndex={-1}>
              {children}
            </main>
            <footer className="workspace-footer">
              <span>
                INVESTMENT OS <span className="footer-divider">/</span> PERSONAL
                TERMINAL
              </span>
              <span>Research only · No trade execution</span>
            </footer>
          </div>
        </div>
      </body>
    </html>
  );
}
