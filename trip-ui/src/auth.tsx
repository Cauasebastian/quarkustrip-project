import Keycloak, { type KeycloakTokenParsed } from "keycloak-js";
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8180",
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? "trip",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "trip-gateway"
});

const defaultKeycloakLocale = import.meta.env.VITE_KEYCLOAK_LOCALE ?? "pt-BR";

export async function initializeAuthentication(): Promise<boolean> {
  return keycloak.init({
    onLoad: "check-sso",
    pkceMethod: "S256",
    silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
    checkLoginIframe: true
  });
}

export async function loginWithKeycloak(): Promise<void> {
  await keycloak.login({ redirectUri: window.location.href, locale: defaultKeycloakLocale });
}

export async function registerWithKeycloak(): Promise<void> {
  await keycloak.register({ redirectUri: window.location.href, locale: defaultKeycloakLocale });
}

interface AuthContextValue {
  authenticated: boolean;
  token: KeycloakTokenParsed | undefined;
  isAdmin: boolean;
  login: () => Promise<void>;
  register: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ initialAuthenticated, children }: {
  initialAuthenticated: boolean;
  children: ReactNode;
}) {
  const [authenticated, setAuthenticated] = useState(initialAuthenticated);
  const [token, setToken] = useState<KeycloakTokenParsed | undefined>(keycloak.tokenParsed);

  useEffect(() => {
    keycloak.onAuthSuccess = () => {
      setAuthenticated(true);
      setToken(keycloak.tokenParsed);
    };
    keycloak.onAuthRefreshSuccess = () => setToken(keycloak.tokenParsed);
    keycloak.onAuthLogout = () => {
      setAuthenticated(false);
      setToken(undefined);
    };
    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30).catch(() => keycloak.login());
    };
    return () => {
      keycloak.onAuthSuccess = undefined;
      keycloak.onAuthRefreshSuccess = undefined;
      keycloak.onAuthLogout = undefined;
      keycloak.onTokenExpired = undefined;
    };
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    authenticated,
    token,
    isAdmin: keycloak.realmAccess?.roles.includes("ADMIN") ?? false,
    login: loginWithKeycloak,
    register: registerWithKeycloak,
    logout: async () => { await keycloak.logout({ redirectUri: window.location.origin }); }
  }), [authenticated, token]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
