import type { BookingStatus, Money } from "./types";

export function formatMoney(value: Money): string {
  const formatter = new Intl.NumberFormat("pt-BR", { style: "currency", currency: value.currency });
  const digits = formatter.resolvedOptions().maximumFractionDigits ?? 2;
  return formatter.format(value.amountMinor / 10 ** digits);
}

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
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
