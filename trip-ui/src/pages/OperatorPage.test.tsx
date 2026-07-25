import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  listOperatorBookings: vi.fn(),
  listPackages: vi.fn(),
  searchOperatorUsers: vi.fn(),
  createOperatorBooking: vi.fn(),
  createPackage: vi.fn()
}));
const draft = vi.hoisted(() => ({
  items: [],
  currency: null,
  clear: vi.fn(),
  removeItem: vi.fn()
}));

vi.mock("../api", () => ({ tripApi: api }));
vi.mock("../draft", () => ({ useDraft: () => draft }));
vi.mock("./AdminPage", () => ({ CatalogManagement: () => <div>Catálogo administrativo</div> }));

import { OperatorPage } from "./OperatorPage";

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><OperatorPage /></MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Operator assisted booking", () => {
  beforeEach(() => {
    api.listOperatorBookings.mockReset().mockResolvedValue({
      items: [], page: 0, size: 50, totalElements: 0, totalPages: 0
    });
    api.listPackages.mockReset().mockResolvedValue({
      items: [], page: 0, size: 50, totalElements: 0
    });
    api.searchOperatorUsers.mockReset().mockResolvedValue({
      items: [{
        id: "profile-1",
        subject: "subject-1",
        username: "cauasebastian",
        email: "caua@example.com",
        firstName: "Cauã",
        lastName: "Sebastian",
        preferencesJson: "{}"
      }],
      page: 0,
      size: 20,
      totalElements: 1
    });
  });

  it("shows a guided three-step flow with service examples", () => {
    renderPage();

    expect(screen.getByText("Para quem é a viagem?")).toBeInTheDocument();
    expect(screen.getByText("O que deseja reservar?")).toBeInTheDocument();
    expect(screen.getByText("Revise e confirme")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Voo/ })).toHaveAttribute("href", "/catalog/flights");
    expect(screen.getByRole("link", { name: /Hospedagem/ })).toHaveAttribute("href", "/catalog/hotels");
    expect(screen.getByRole("link", { name: /Transporte/ })).toHaveAttribute("href", "/catalog/transports");
  });

  it("finds and selects a passenger by Keycloak username", async () => {
    renderPage();
    fireEvent.change(screen.getByPlaceholderText(/cauasebastian/), {
      target: { value: "cauasebastian" }
    });

    const username = await screen.findByText("@cauasebastian");
    expect(api.searchOperatorUsers).toHaveBeenCalledWith("cauasebastian");
    fireEvent.click(username.closest("button")!);
    expect(screen.getAllByText("@cauasebastian").length).toBeGreaterThan(1);
    expect(screen.getByRole("button", { name: /Confirmar reserva para Cauã/ })).toBeDisabled();
  });
});
