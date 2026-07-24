import { Link } from "react-router-dom";
import { useAuth } from "../auth";

export function LoginPage() {
  const { login, register } = useAuth();
  const showCredentials = (import.meta.env.VITE_SHOW_DEV_CREDENTIALS ?? "true") === "true";

  return (
    <main className="login-page">
      <section className="login-copy">
        <div className="brand brand-light">
          <span className="brand-mark">T</span>
          <span><strong>Trip</strong><small>Platform</small></span>
        </div>
        <p className="eyebrow">Ambiente local de reservas</p>
        <h1>Teste toda a viagem em um só lugar.</h1>
        <p>Pesquise catálogos, combine serviços e acompanhe a Saga em tempo real — do bloqueio dos recursos à confirmação.</p>
        <div className="login-features">
          <span>Voos</span><span>Hotéis</span><span>Transportes</span><span>Pagamento seguro</span>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <span className="login-icon" aria-hidden="true">→</span>
          <h2>Acessar a plataforma</h2>
          <p>Entre com sua conta ou crie um acesso novo. A autenticação segura é feita pelo Keycloak com PKCE.</p>
          <div className="auth-actions">
            <button data-testid="login-button" className="button button-full" onClick={() => void login()}>
              Entrar
            </button>
            <button data-testid="register-button" className="button button-secondary button-full" onClick={() => void register()}>
              Criar conta
            </button>
          </div>
          <Link className="operator-access-link" to="/operator/access">
            <span className="operator-access-mark">↗</span>
            <span><strong>Sou operador</strong><small>Acessar a área da companhia</small></span>
          </Link>
          {showCredentials && (
            <div className="dev-credentials">
              <strong>Contas de desenvolvimento</strong>
              <code>demo / demo</code>
              <code>admin / admin</code>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
