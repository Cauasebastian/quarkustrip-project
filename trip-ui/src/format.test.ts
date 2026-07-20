import { describe, expect, it } from "vitest";
import { formatDuration, shouldPollBooking } from "./format";

describe("Saga polling", () => {
  it("polls active states for up to five minutes", () => {
    expect(shouldPollBooking("RESERVING", 1_000, 2_000)).toBe(true);
    expect(shouldPollBooking("PAYMENT_PENDING", 1_000, 301_001)).toBe(false);
  });

  it("stops for terminal states", () => {
    expect(shouldPollBooking("CONFIRMED", 1_000, 2_000)).toBe(false);
    expect(shouldPollBooking("FAILED", 1_000, 2_000)).toBe(false);
    expect(shouldPollBooking("MANUAL_REVIEW", 1_000, 2_000)).toBe(false);
  });

  it("formats trace durations for people", () => {
    expect(formatDuration(842)).toBe("842 ms");
    expect(formatDuration(2_450)).toBe("2.5 s");
    expect(formatDuration(72_000)).toBe("1 min 12 s");
  });
});
