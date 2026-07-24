import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

const loginOperator = vi.fn().mockResolvedValue(undefined);
const registerOperator = vi.fn().mockResolvedValue(undefined);
const logout = vi.fn().mockResolvedValue(undefined);
vi.mock("../auth", () => ({
  useAuth: () => ({
    authenticated: false,
    isOperator: false,
    loginOperator,
    registerOperator,
    logout
  })
}));

import { OperatorAccessPage } from "./OperatorAccessPage";

describe("Operator access page", () => {
  it("starts the dedicated operator login", () => {
    render(<MemoryRouter><OperatorAccessPage /></MemoryRouter>);
    fireEvent.click(screen.getByTestId("operator-login-button"));
    expect(loginOperator).toHaveBeenCalledOnce();
  });

  it("starts the company account registration", () => {
    render(<MemoryRouter><OperatorAccessPage /></MemoryRouter>);
    fireEvent.click(screen.getByTestId("operator-register-button"));
    expect(registerOperator).toHaveBeenCalledOnce();
  });
});
