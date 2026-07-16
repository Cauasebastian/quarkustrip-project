export interface Money {
  currency: string;
  amountMinor: number;
}

export interface Flight {
  id: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  seatPrice: Money;
  availableSeats: string[];
}

export interface Hotel {
  id: string;
  name: string;
  address: string;
  city: string;
  country: string;
  rating: number;
}

export interface Room {
  id: string;
  hotelId: string;
  roomNumber: string;
  roomType: string;
  nightlyPrice: Money;
  available: boolean;
}

export interface Transport {
  id: string;
  transportType: string;
  providerName: string;
  vehicleDetailsJson: string;
  price: Money;
  available: boolean;
}

export type BookingStatus =
  | "RESERVING"
  | "PAYMENT_PENDING"
  | "CONFIRMING"
  | "CONFIRMED"
  | "COMPENSATING"
  | "CANCELLED"
  | "FAILED"
  | "MANUAL_REVIEW";

export interface BookingItem {
  id: string;
  type: "FLIGHT" | "HOTEL" | "TRANSPORT";
  resourceId: string;
  status: string;
  externalReservationId: string;
  price: Money;
  failureReason: string;
}

export interface Booking {
  id: string;
  userId: string;
  status: BookingStatus;
  total: Money;
  items: BookingItem[];
  failureCode: string;
  createdAt: string;
  updatedAt: string;
}

export interface BookingPage {
  items: Booking[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface BookingCreated {
  bookingId: string;
  status: BookingStatus;
  location: string;
}

export interface Profile {
  id: string;
  subject: string;
  email: string;
  firstName: string;
  lastName: string;
  preferencesJson: string;
}

export type BookingRequestItem =
  | { type: "FLIGHT"; resourceId: string; seatNumber: string }
  | { type: "HOTEL"; resourceId: string; checkIn: string; checkOut: string }
  | { type: "TRANSPORT"; resourceId: string; startsAt: string; endsAt: string };

export interface DraftItem {
  id: string;
  label: string;
  detail: string;
  price: Money;
  request: BookingRequestItem;
}

export interface ApiErrorBody {
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
