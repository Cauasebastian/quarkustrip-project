import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn()
}));

vi.mock("../api", () => ({ tripApi: api }));
vi.mock("../auth", () => ({
  useAuth: () => ({
    token: {
      preferred_username: "cauasebastian",
      email: "caua@example.com",
      given_name: "Cauã",
      family_name: "Sebastian"
    }
  })
}));

import { ProfilePage } from "./ProfilePage";

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ProfilePage />
    </QueryClientProvider>
  );
}

describe("Traveler profile", () => {
  beforeEach(() => {
    const profile = {
      id: "profile-1",
      subject: "subject-1",
      username: "cauasebastian",
      email: "caua@example.com",
      firstName: "Cauã",
      lastName: "Sebastian",
      preferencesJson: "{}"
    };
    api.getProfile.mockReset().mockResolvedValue(profile);
    api.updateProfile.mockReset().mockResolvedValue(profile);
  });

  it("explains how the operator finds the user without exposing JSON", async () => {
    renderPage();

    expect((await screen.findAllByText("@cauasebastian")).length).toBeGreaterThan(0);
    expect(screen.getByText(/operador usa para encontrar seu perfil/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Preferência de assento")).toBeInTheDocument();
    expect(screen.queryByText(/Preferências em JSON/i)).not.toBeInTheDocument();
  });

  it("serializes friendly travel preferences when saving", async () => {
    renderPage();
    await screen.findByDisplayValue("caua@example.com");

    fireEvent.change(screen.getByLabelText("Preferência de assento"), { target: { value: "WINDOW" } });
    fireEvent.change(screen.getByLabelText(/Observações para a viagem/), {
      target: { value: "Quarto silencioso" }
    });
    fireEvent.click(screen.getByLabelText(/Preciso de assistência durante a viagem/));
    fireEvent.click(screen.getByRole("button", { name: "Salvar alterações" }));

    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith(expect.objectContaining({
      preferencesJson: JSON.stringify({
        language: "pt-BR",
        seatPreference: "WINDOW",
        travelNotes: "Quarto silencioso",
        needsAssistance: true
      })
    })));
  });
});
