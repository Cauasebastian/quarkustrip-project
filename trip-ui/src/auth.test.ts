import { afterEach, describe, expect, it, vi } from "vitest";
import { keycloak, loginWithKeycloak, registerWithKeycloak } from "./auth";

describe("Keycloak authentication actions", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("opens login and returns to the current page", async () => {
    const login = vi.spyOn(keycloak, "login").mockResolvedValue(undefined);

    await loginWithKeycloak();

    expect(login).toHaveBeenCalledWith({ redirectUri: window.location.href, locale: "pt-BR" });
  });

  it("opens registration and returns to the current page", async () => {
    const register = vi.spyOn(keycloak, "register").mockResolvedValue(undefined);

    await registerWithKeycloak();

    expect(register).toHaveBeenCalledWith({ redirectUri: `${window.location.origin}/profile`, locale: "pt-BR" });
  });
});
