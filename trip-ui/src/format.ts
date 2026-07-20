import type { BookingStatus, Money } from "./types";

export function formatMoney(value: Money): string {
  const formatter = new Intl.NumberFormat("pt-BR", { style: "currency", currency: value.currency });
  const digits = formatter.resolvedOptions().maximumFractionDigits ?? 2;
  return formatter.format(value.amountMinor / 10 ** digits);
}

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function formatDuration(milliseconds: number): string {
  if (milliseconds < 1_000) return `${Math.max(0, Math.round(milliseconds))} ms`;
  if (milliseconds < 60_000) return `${(milliseconds / 1_000).toFixed(milliseconds < 10_000 ? 1 : 0)} s`;
  const minutes = Math.floor(milliseconds / 60_000);
  const seconds = Math.round((milliseconds % 60_000) / 1_000);
  return `${minutes} min ${seconds} s`;
}

export function localDateTimeToIso(value: string): string {
  return new Date(value).toISOString();
}

export const activeStatuses = new Set<BookingStatus>([
  "RESERVING", "PAYMENT_PENDING", "CONFIRMING", "COMPENSATING"
]);

export function shouldPollBooking(status: BookingStatus | undefined, startedAt: number, now = Date.now()): boolean {
  return Boolean(status && activeStatuses.has(status) && now - startedAt < 5 * 60_000);
}

export function friendlyError(error: unknown): string {
  if (error instanceof Error) return error.message;
  return "Não foi possível concluir a requisição.";
}
