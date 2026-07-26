import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "./test/server";

const { updateToken } = vi.hoisted(() => ({ updateToken: vi.fn().mockResolvedValue(true) }));
vi.mock("./auth", () => ({
  keycloak: { updateToken, token: "test-token", login: vi.fn().mockResolvedValue(undefined) }
}));

import { tripApi } from "./api";

describe("Trip API client", () => {
  beforeEach(() => updateToken.mockClear());

  it("refreshes the token and sends the bearer token", async () => {
    server.use(http.get("http://localhost:8080/api/v1/bookings", ({ request }) => {
      expect(request.headers.get("Authorization")).toBe("Bearer test-token");
      expect(new URL(request.url).searchParams.get("size")).toBe("10");
      return HttpResponse.json({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    }));
    await expect(tripApi.listBookings(0, 10)).resolves.toMatchObject({ items: [] });
    expect(updateToken).toHaveBeenCalledWith(30);
  });

  it("preserves the supplied idempotency key", async () => {
    server.use(http.post("http://localhost:8080/api/v1/bookings", async ({ request }) => {
      expect(request.headers.get("Idempotency-Key")).toBe("attempt-123");
      expect(await request.json()).toMatchObject({ paymentMethodRef: "pm_test_success" });
      return HttpResponse.json({ bookingId: "booking-1", status: "RESERVING", location: "/api/v1/bookings/booking-1" }, { status: 202 });
    }));
    await expect(tripApi.createBooking({
      currency: "BRL", paymentMethodRef: "pm_test_success",
      items: [{ type: "FLIGHT", resourceId: "flight-1", seatNumber: "1A" }]
    }, "attempt-123")).resolves.toMatchObject({ bookingId: "booking-1" });
  });

  it("loads the Jaeger summary through the protected booking endpoint", async () => {
    server.use(http.get("http://localhost:8080/api/v1/bookings/booking-1/observability", () =>
      HttpResponse.json({
        available: true,
        bookingId: "booking-1",
        primaryTraceId: "0123456789abcdef0123456789abcdef",
        traceIds: ["0123456789abcdef0123456789abcdef"],
        complete: true,
        expectedSagaServices: ["api-gateway-service", "booking-service"],
        observedServices: ["api-gateway-service", "booking-service"],
        missingServices: [],
        totalDurationMs: 1200,
        stages: [],
        communications: [],
        signals: { retryCount: 0, duplicateCount: 0, dlqCount: 0, failedSpanCount: 0,
          compensationStarted: false, refundRequested: false, notificationStatus: null }
      })));

    await expect(tripApi.getBookingObservability("booking-1")).resolves.toMatchObject({
      available: true,
      totalDurationMs: 1200
    });
  });

  it("omits optional hotel and transport periods when they are empty", async () => {
    server.use(
      http.get("http://localhost:8080/api/v1/catalog/hotels", ({ request }) => {
        const params = new URL(request.url).searchParams;
        expect([...params.keys()]).toEqual([]);
        return HttpResponse.json({
          items: [],
          checkIn: "2026-07-26",
          checkOut: "2026-07-29",
          defaultPeriod: true
        });
      }),
      http.get("http://localhost:8080/api/v1/catalog/transports", ({ request }) => {
        const params = new URL(request.url).searchParams;
        expect(params.get("type")).toBe("CAR_RENTAL");
        expect(params.has("startsAt")).toBe(false);
        expect(params.has("endsAt")).toBe(false);
        return HttpResponse.json({
          items: [],
          startsAt: "2026-07-26T09:00:00Z",
          endsAt: "2026-07-29T09:00:00Z",
          defaultPeriod: true
        });
      })
    );

    await expect(tripApi.searchHotels("", "", "", "")).resolves.toMatchObject({ defaultPeriod: true });
    await expect(tripApi.searchTransports("CAR_RENTAL", "", "")).resolves.toMatchObject({ defaultPeriod: true });
  });
});
