import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App } from "./App";
import { AuthProvider, initializeAuthentication } from "./auth";
import { DraftProvider } from "./draft";
import "./styles.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 10_000, refetchOnWindowFocus: false },
    mutations: { retry: false }
  }
});

const root = createRoot(document.getElementById("root")!);

initializeAuthentication()
  .then(authenticated => root.render(
    <StrictMode>
      <AuthProvider initialAuthenticated={authenticated}>
        <QueryClientProvider client={queryClient}>
          <DraftProvider>
            <BrowserRouter><App /></BrowserRouter>
          </DraftProvider>
        </QueryClientProvider>
      </AuthProvider>
    </StrictMode>
  ))
  .catch(error => root.render(
    <main className="startup-error">
      <h1>Não foi possível iniciar a autenticação</h1>
      <p>{error instanceof Error ? error.message : "Verifique se o Keycloak está disponível."}</p>
      <button className="button" onClick={() => window.location.reload()}>Tentar novamente</button>
    </main>
  ));
