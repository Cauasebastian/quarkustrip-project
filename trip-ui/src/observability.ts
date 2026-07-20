const JAEGER_BASE_URL = (import.meta.env.VITE_JAEGER_BASE_URL ?? "http://localhost:16686").replace(/\/$/, "");

export function jaegerTraceUrl(traceId: string): string {
  return `${JAEGER_BASE_URL}/trace/${encodeURIComponent(traceId)}`;
}

export function jaegerDependenciesUrl(): string {
  return `${JAEGER_BASE_URL}/dependencies`;
}
