import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { DraftItem } from "./types";

const STORAGE_KEY = "trip.booking.draft";

export function canAddDraftItem(items: DraftItem[], item: DraftItem): boolean {
  return items.length === 0 || items[0].price.currency === item.price.currency;
}

export function canAddDraftItems(items: DraftItem[], newItems: DraftItem[]): boolean {
  const expectedCurrency = items[0]?.price.currency ?? newItems[0]?.price.currency;
  return Boolean(expectedCurrency && newItems.length > 0
    && newItems.every(item => item.price.currency === expectedCurrency));
}

function readDraft(): DraftItem[] {
  try {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    return stored ? JSON.parse(stored) as DraftItem[] : [];
  } catch {
    return [];
  }
}

interface DraftContextValue {
  items: DraftItem[];
  currency: string | null;
  addItem: (item: DraftItem) => boolean;
  addItems: (items: DraftItem[]) => boolean;
  removeItem: (id: string) => void;
  clear: () => void;
}

const DraftContext = createContext<DraftContextValue | null>(null);

export function DraftProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<DraftItem[]>(readDraft);

  useEffect(() => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  }, [items]);

  const value = useMemo<DraftContextValue>(() => ({
    items,
    currency: items[0]?.price.currency ?? null,
    addItem: (item) => {
      if (!canAddDraftItem(items, item)) return false;
      setItems(current => current.some(value => value.id === item.id) ? current : [...current, item]);
      return true;
    },
    addItems: (newItems) => {
      if (!canAddDraftItems(items, newItems)) return false;
      setItems(current => {
        const ids = new Set(current.map(item => item.id));
        return [...current, ...newItems.filter(item => !ids.has(item.id))];
      });
      return true;
    },
    removeItem: (id) => setItems(current => current.filter(item => item.id !== id)),
    clear: () => setItems([])
  }), [items]);

  return <DraftContext.Provider value={value}>{children}</DraftContext.Provider>;
}

export function useDraft(): DraftContextValue {
  const value = useContext(DraftContext);
  if (!value) throw new Error("useDraft must be used inside DraftProvider");
  return value;
}
