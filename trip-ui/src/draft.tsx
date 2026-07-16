import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { DraftItem } from "./types";

const STORAGE_KEY = "trip.booking.draft";

export function canAddDraftItem(items: DraftItem[], item: DraftItem): boolean {
  return items.length === 0 || items[0].price.currency === item.price.currency;
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
