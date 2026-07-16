import { useAuth } from "../auth";

export function LoginPage() {
  const { login } = useAuth();
  const showCredentials = (import.meta.env.VITE_SHOW_DEV_CREDENTIALS ?? "true") === "true";
  return (
    <main className="login-page">
      <section className="login-copy">
        <div className="brand brand-light"><span className="brand-mark">T</span><span><strong>Trip</strong><small>Platform</small></span></div>
        <p className="eyebrow">Ambiente local de reservas</p>
        <h1>Teste toda a viagem em um só lugar.</h1>
        <p>Pesquise catálogos, combine serviços e acompanhe a Saga em tempo real — do bloqueio dos recursos à confirmação.</p>
        <div className="login-features">
          <span>Voos</span><span>Hotéis</span><span>Transportes</span><span>Pagamento seguro</span>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <span className="login-icon">→</span>
          <h2>Acessar a plataforma</h2>
          <p>Você será direcionado ao Keycloak. A autenticação usa Authorization Code com PKCE.</p>
          <button data-testid="login-button" className="button button-full" onClick={() => void login()}>Entrar com Keycloak</button>
          {showCredentials && <div className="dev-credentials"><strong>Contas de desenvolvimento</strong><code>demo / demo</code><code>admin / admin</code></div>}
        </div>
      </section>
    </main>
  );
}
