"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/icon";

const items = [
  { href: "/", label: "Overview", icon: "overview" },
  { href: "/watchlist", label: "Watchlist", icon: "watchlist" },
  { href: "/valuation", label: "Valuation", icon: "valuation" },
  { href: "/research", label: "Research", icon: "research" },
] as const;

export function Navigation() {
  const pathname = usePathname();

  return (
    <nav aria-label="Main navigation" className="navigation">
      {items.map((item, index) => (
        <Link
          key={item.href}
          href={item.href}
          aria-current={pathname === item.href ? "page" : undefined}
          className="nav-link"
        >
          <Icon name={item.icon} />
          <span>{item.label}</span>
          <span className="nav-number">0{index + 1}</span>
        </Link>
      ))}
    </nav>
  );
}
