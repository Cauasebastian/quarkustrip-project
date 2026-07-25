import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  searchHotels: vi.fn(),
  listRooms: vi.fn()
}));
vi.mock("../api", () => ({ tripApi: api }));
vi.mock("../auth", () => ({ useAuth: () => ({ isOperator: false }) }));
vi.mock("../draft", () => ({ useDraft: () => ({ addItem: vi.fn(), items: [] }) }));

import { HotelsPage } from "./HotelsPage";

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><HotelsPage /></MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Hotels catalog", () => {
  beforeEach(() => {
    api.searchHotels.mockReset().mockResolvedValue({
      items: [
        { id: "hotel-1", name: "Hotel Praia", address: "Av. Beira Mar", city: "Fortaleza", country: "BR", rating: 4, available: true },
        { id: "hotel-2", name: "Hotel Centro", address: "Rua Central", city: "Recife", country: "BR", rating: 3, available: false }
      ],
      checkIn: "2026-07-26",
      checkOut: "2026-07-29",
      defaultPeriod: true
    });
    api.listRooms.mockReset().mockResolvedValue({ items: [] });
  });

  it("loads every hotel immediately using the suggested period", async () => {
    renderPage();

    expect(await screen.findByText("Hotel Praia")).toBeInTheDocument();
    expect(screen.getByText("Hotel Centro")).toBeInTheDocument();
    expect(screen.getByText("Disponível nos próximos dias")).toBeInTheDocument();
    expect(screen.getByText("Sem quartos no período")).toBeInTheDocument();
    expect(screen.getByText("Período sugerido")).toBeInTheDocument();
    await waitFor(() => expect(api.searchHotels).toHaveBeenCalledWith("", "", "", ""));
    expect(screen.getByLabelText(/Entrada/)).not.toBeRequired();
    expect(screen.getByLabelText(/Saída/)).not.toBeRequired();
  });
});
