"use client";

import { useState } from "react";
import { Icon } from "@/components/icon";

type ConnectionStatus = "idle" | "checking" | "connected" | "unavailable";

const labels: Record<ConnectionStatus, string> = {
  idle: "Not checked",
  checking: "Checking connection…",
  connected: "Backend connected",
  unavailable: "Backend unavailable",
};

export function BackendStatus() {
  const [status, setStatus] = useState<ConnectionStatus>("idle");

  async function checkConnection() {
    setStatus("checking");
    try {
      const response = await fetch("/api/backend/health", {
        cache: "no-store",
        signal: AbortSignal.timeout(5000),
      });
      const body: unknown = await response.json();
      const connected =
        response.ok &&
        typeof body === "object" &&
        body !== null &&
        "status" in body &&
        body.status === "connected";
      setStatus(connected ? "connected" : "unavailable");
    } catch {
      setStatus("unavailable");
    }
  }

  return (
    <section
      className="panel connection-panel"
      aria-labelledby="connection-heading"
    >
      <div className="panel-heading">
        <h2 id="connection-heading">Workspace connection</h2>
        <Icon name="signal" />
      </div>
      <div
        className={`connection-state ${status}`}
        role="status"
        aria-live="polite"
      >
        <span className="status-dot" />
        {labels[status]}
      </div>
      <p className="muted small">
        Check whether the research backend and its database are available.
      </p>
      <button
        className="button secondary"
        onClick={checkConnection}
        disabled={status === "checking"}
      >
        {status === "checking" ? "Checking…" : "Check connection"}
        <Icon name="arrow" />
      </button>
      {status === "connected" && (
        <p className="connection-note">
          Backend health is UP. Market data is not connected yet.
        </p>
      )}
      {status === "unavailable" && (
        <p className="connection-note">
          Check that the backend is running, then try again.
        </p>
      )}
    </section>
  );
}
