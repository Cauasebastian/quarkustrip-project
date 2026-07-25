import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  getProfile: vi.fn().mockResolvedValue(null),
  updateProfile: vi.fn().mockResolvedValue({
    id: "profile-1",
    subject: "subject-1",
    username: "cauasebastian",
    email: "caua@example.com",
    firstName: "Cauã",
    lastName: "Sebastian",
    preferencesJson: "{}"
  })
}));

vi.mock("../api", () => ({ tripApi: api }));
vi.mock("../auth", () => ({
  useAuth: () => ({
    isAdmin: false,
    isOperator: false,
    logout: vi.fn(),
    token: {
      preferred_username: "cauasebastian",
      email: "caua@example.com",
      given_name: "Cauã",
      family_name: "Sebastian"
    }
  })
}));
vi.mock("../draft", () => ({ useDraft: () => ({ items: [] }) }));

import { Layout } from "./Layout";

describe("Authenticated layout", () => {
  it("creates the searchable profile on the first authenticated visit", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={["/"]}>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<p>Conteúdo</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.getByText("Conteúdo")).toBeInTheDocument();
    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledTimes(1));
    expect(api.updateProfile.mock.calls[0][0]).toEqual({
      email: "caua@example.com",
      firstName: "Cauã",
      lastName: "Sebastian",
      preferencesJson: "{}"
    });
  });
});
