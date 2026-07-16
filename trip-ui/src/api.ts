import { keycloak } from "./auth";
import type {
  ApiErrorBody,
  Booking,
  BookingCreated,
  BookingPage,
  BookingRequestItem,
  Flight,
  Hotel,
  Money,
  Profile,
  Room,
  Transport
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class TripApiError extends Error {
  constructor(public readonly status: number, public readonly body: ApiErrorBody) {
    super(body.message);
  }
}

async function authenticatedFetch(path: string, options: RequestInit = {}, retry = true): Promise<Response> {
  try {
    await keycloak.updateToken(30);
  } catch {
    await keycloak.login({ redirectUri: window.location.href });
    throw new Error("Sessão expirada");
  }

  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body) headers.set("Content-Type", "application/json");
  if (keycloak.token) headers.set("Authorization", `Bearer ${keycloak.token}`);

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (response.status === 401 && retry) {
    try {
      await keycloak.updateToken(-1);
      return authenticatedFetch(path, options, false);
    } catch {
      await keycloak.login({ redirectUri: window.location.href });
    }
  }
  return response;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await authenticatedFetch(path, options);
  const text = await response.text();
  const body = text ? JSON.parse(text) as unknown : undefined;
  if (!response.ok) {
    throw new TripApiError(response.status, body as ApiErrorBody ?? {
      error: `HTTP_${response.status}`,
      message: response.statusText
    });
  }
  return body as T;
}

function query(params: Record<string, string | number | undefined>): string {
  const values = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") values.set(key, String(value));
  });
  return values.toString();
}

export const tripApi = {
  listBookings: (page = 0, size = 20) =>
    request<BookingPage>(`/api/v1/bookings?${query({ page, size })}`),

  getBooking: (id: string) => request<Booking>(`/api/v1/bookings/${id}`),

  createBooking: (body: { currency: string; paymentMethodRef: string; items: BookingRequestItem[] }, key: string) =>
    request<BookingCreated>("/api/v1/bookings", {
      method: "POST",
      headers: { "Idempotency-Key": key },
      body: JSON.stringify(body)
    }),

  cancelBooking: (id: string, reason = "USER_CANCELLED") =>
    request<{ bookingId: string; status: string }>(`/api/v1/bookings/${id}/cancel`, {
      method: "POST",
      body: JSON.stringify({ reason })
    }),

  searchFlights: (origin: string, destination: string, departsAfter: string) =>
    request<{ items: Flight[] }>(`/api/v1/catalog/flights?${query({ origin, destination, departsAfter })}`),

  searchHotels: (city: string, country: string, checkIn: string, checkOut: string) =>
    request<{ items: Hotel[] }>(`/api/v1/catalog/hotels?${query({ city, country, checkIn, checkOut })}`),

  listRooms: (hotelId: string, checkIn: string, checkOut: string) =>
    request<{ items: Room[] }>(`/api/v1/catalog/hotels/${hotelId}/rooms?${query({ checkIn, checkOut })}`),

  searchTransports: (type: string, startsAt: string, endsAt: string) =>
    request<{ items: Transport[] }>(`/api/v1/catalog/transports?${query({ type, startsAt, endsAt })}`),

  getProfile: async () => {
    try {
      return await request<Profile>("/api/v1/users/me");
    } catch (error) {
      if (error instanceof TripApiError && error.status === 404) return null;
      throw error;
    }
  },

  updateProfile: (body: Pick<Profile, "email" | "firstName" | "lastName" | "preferencesJson">) =>
    request<Profile>("/api/v1/users/me", { method: "PUT", body: JSON.stringify(body) }),

  createFlight: (body: {
    flightNumber: string; origin: string; destination: string; departureTime: string;
    arrivalTime: string; totalSeats: number; seatPrice: Money;
  }) => request<Flight>("/api/v1/catalog/flights", { method: "POST", body: JSON.stringify(body) }),

  createHotel: (body: { name: string; address: string; city: string; country: string; rating: number }) =>
    request<Hotel>("/api/v1/catalog/hotels", { method: "POST", body: JSON.stringify(body) }),

  createRoom: (body: { hotelId: string; roomNumber: string; roomType: string; nightlyPrice: Money }) =>
    request<Room>("/api/v1/catalog/rooms", { method: "POST", body: JSON.stringify(body) }),

  createTransport: (body: {
    transportType: string; providerName: string; vehicleDetailsJson: string; price: Money;
  }) => request<Transport>("/api/v1/catalog/transports", { method: "POST", body: JSON.stringify(body) })
};
