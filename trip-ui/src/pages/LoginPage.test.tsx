import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const login = vi.fn().mockResolvedValue(undefined);
const register = vi.fn().mockResolvedValue(undefined);
vi.mock("../auth", () => ({ useAuth: () => ({ login, register }) }));

import { LoginPage } from "./LoginPage";

describe("Login page", () => {
  it("starts the Keycloak login flow", () => {
    render(<LoginPage />);
    fireEvent.click(screen.getByTestId("login-button"));
    expect(login).toHaveBeenCalledOnce();
  });

  it("starts the Keycloak registration flow", () => {
    render(<LoginPage />);
    fireEvent.click(screen.getByTestId("register-button"));
    expect(register).toHaveBeenCalledOnce();
  });
});
