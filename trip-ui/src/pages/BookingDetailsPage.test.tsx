import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  getBooking: vi.fn(),
  getBookingObservability: vi.fn(),
  cancelBooking: vi.fn()
}));
vi.mock("../api", () => ({ tripApi: api }));

import { BookingDetailsPage } from "./BookingDetailsPage";

const booking = {
  id: "booking-123", userId: "user-1", status: "CONFIRMED", total: { currency: "BRL", amountMinor: 15000 },
  items: [], failureCode: "", createdAt: "2026-07-20T10:00:00Z", updatedAt: "2026-07-20T10:00:05Z"
};
const trace = {
  available: true, unavailableReason: null, bookingId: "booking-123",
  primaryTraceId: "0123456789abcdef0123456789abcdef",
  traceIds: ["0123456789abcdef0123456789abcdef"], complete: true,
  expectedSagaServices: ["api-gateway-service", "booking-service"],
  observedServices: ["api-gateway-service", "booking-service"], missingServices: [], totalDurationMs: 2300,
  stages: [{ status: "RESERVING", startedAt: "2026-07-20T10:00:00Z", finishedAt: "2026-07-20T10:00:01Z", durationMs: 1000, active: false }],
  communications: [{ source: "api-gateway-service", target: "booking-service", protocol: "GRPC", destination: "BookingCommandService/CreateBooking", count: 1, totalDurationMs: 18, errorCount: 0 }],
  signals: { retryCount: 0, duplicateCount: 0, dlqCount: 0, failedSpanCount: 0, compensationStarted: false, refundRequested: false, notificationStatus: "SENT" }
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={["/bookings/booking-123"]}>
    <Routes><Route path="/bookings/:id" element={<BookingDetailsPage />} /></Routes>
  </MemoryRouter></QueryClientProvider>);
}

describe("Booking observability", () => {
  beforeEach(() => {
    api.getBooking.mockReset().mockResolvedValue(booking);
    api.getBookingObservability.mockReset().mockResolvedValue(trace);
    api.cancelBooking.mockReset();
  });

  it("shows Jaeger links, timings, signals and communication protocols", async () => {
    renderPage();
    expect(await screen.findByText("Comunicação da reserva")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Abrir trace no Jaeger" })).toHaveAttribute("href", expect.stringContaining(trace.primaryTraceId));
    expect(screen.getByText("gRPC")).toBeInTheDocument();
    expect(screen.getByText("2.3 s")).toBeInTheDocument();
    expect(screen.getByText("Trace completo")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Ver comunicação entre serviços" }));
    expect(screen.getAllByText("api-gateway-service")).toHaveLength(2);
    expect(screen.getByText("BookingCommandService/CreateBooking")).toBeInTheDocument();
  });

  it("highlights services missing from a partial trace", async () => {
    api.getBookingObservability.mockResolvedValue({
      ...trace,
      complete: false,
      expectedSagaServices: [...trace.expectedSagaServices, "payment-service"],
      missingServices: ["payment-service"]
    });
    renderPage();
    expect(await screen.findByText("Trace parcial")).toBeInTheDocument();
    expect(screen.getAllByText(/payment-service/)).toHaveLength(2);
  });

  it("keeps the reservation usable when Jaeger has no trace", async () => {
    api.getBookingObservability.mockResolvedValue({ ...trace, available: false, primaryTraceId: null, unavailableReason: "TRACE_NOT_FOUND" });
    renderPage();
    expect(await screen.findByText(/armazenamento efêmero/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Abrir trace no Jaeger" })).toBeDisabled();
  });
});
