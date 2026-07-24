import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

const login = vi.fn().mockResolvedValue(undefined);
const register = vi.fn().mockResolvedValue(undefined);
vi.mock("../auth", () => ({ useAuth: () => ({ login, register }) }));

import { LoginPage } from "./LoginPage";

describe("Login page", () => {
  it("starts the Keycloak login flow", () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByTestId("login-button"));
    expect(login).toHaveBeenCalledOnce();
  });

  it("starts the Keycloak registration flow", () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByTestId("register-button"));
    expect(register).toHaveBeenCalledOnce();
  });

  it("offers a separate operator entry", () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByRole("link", { name: /Sou operador/ })).toHaveAttribute("href", "/operator/access");
  });
});
