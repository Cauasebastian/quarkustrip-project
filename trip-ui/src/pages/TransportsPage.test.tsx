import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({ searchTransports: vi.fn() }));
vi.mock("../api", () => ({ tripApi: api }));
vi.mock("../auth", () => ({ useAuth: () => ({ isOperator: false }) }));
vi.mock("../draft", () => ({ useDraft: () => ({ addItem: vi.fn(), items: [] }) }));

import { TransportsPage } from "./TransportsPage";

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><TransportsPage /></MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Transport catalog", () => {
  beforeEach(() => {
    api.searchTransports.mockReset().mockResolvedValue({
      items: [
        {
          id: "car-1",
          transportType: "CAR_RENTAL",
          providerName: "Trip Cars",
          vehicleDetailsJson: "{\"model\":\"SUV\"}",
          price: { currency: "BRL", amountMinor: 25000 },
          available: true
        }
      ],
      startsAt: "2026-07-26T09:00:00Z",
      endsAt: "2026-07-29T09:00:00Z",
      defaultPeriod: true
    });
  });

  it("loads cars immediately without requiring a period", async () => {
    renderPage();

    expect(await screen.findByText("Trip Cars")).toBeInTheDocument();
    expect(screen.getByText("Disponível nos próximos dias")).toBeInTheDocument();
    expect(screen.getByText("Período sugerido")).toBeInTheDocument();
    await waitFor(() => expect(api.searchTransports).toHaveBeenCalledWith("CAR_RENTAL", "", ""));
    expect(screen.getByLabelText(/Início/)).not.toBeRequired();
    expect(screen.getByLabelText(/Término/)).not.toBeRequired();
  });
});
