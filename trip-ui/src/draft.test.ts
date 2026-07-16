import { describe, expect, it } from "vitest";
import { canAddDraftItem } from "./draft";
import type { DraftItem } from "./types";

const flight: DraftItem = {
  id: "flight", label: "FOR → GRU", detail: "TP100", price: { currency: "BRL", amountMinor: 10000 },
  request: { type: "FLIGHT", resourceId: "flight-id", seatNumber: "1A" }
};

describe("booking draft", () => {
  it("accepts the first item and another item in the same currency", () => {
    expect(canAddDraftItem([], flight)).toBe(true);
    expect(canAddDraftItem([flight], { ...flight, id: "other" })).toBe(true);
  });

  it("rejects mixed currencies", () => {
    expect(canAddDraftItem([flight], { ...flight, id: "usd", price: { currency: "USD", amountMinor: 100 } })).toBe(false);
  });
});
