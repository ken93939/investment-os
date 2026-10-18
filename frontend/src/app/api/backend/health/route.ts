export const dynamic = "force-dynamic";

function reply(status: "connected" | "unavailable", code: number) {
  return Response.json(
    { status },
    { status: code, headers: { "Cache-Control": "no-store" } },
  );
}

export async function GET() {
  try {
    const backend = new URL(process.env.BACKEND_URL ?? "http://127.0.0.1:8080");
    if (
      !["http:", "https:"].includes(backend.protocol) ||
      backend.username ||
      backend.password ||
      backend.pathname !== "/" ||
      backend.search ||
      backend.hash
    ) {
      return reply("unavailable", 503);
    }

    const response = await fetch(new URL("/ready", backend), {
      cache: "no-store",
      signal: AbortSignal.timeout(3000),
      redirect: "error",
    });
    if (!response.ok) return reply("unavailable", 503);

    const body: unknown = await response.json();
    if (
      typeof body !== "object" ||
      body === null ||
      !("status" in body) ||
      body.status !== "UP"
    ) {
      return reply("unavailable", 503);
    }
    return reply("connected", 200);
  } catch {
    return reply("unavailable", 503);
  }
}
