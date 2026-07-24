import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../auth";

export function OperatorAccessPage() {
  const { authenticated, isOperator, loginOperator, registerOperator, logout } = useAuth();
  const showCredentials = (import.meta.env.VITE_SHOW_DEV_CREDENTIALS ?? "true") === "true";

  if (isOperator) return <Navigate to="/operator" replace />;

  return (
    <main className="login-page operator-access-page">
      <section className="login-copy operator-access-copy">
        <div className="brand brand-light">
          <span className="brand-mark">T</span>
          <span><strong>Trip</strong><small>Operadores</small></span>
        </div>
        <p className="eyebrow">Portal da companhia</p>
        <h1>Gerencie ofertas e passageiros em um só painel.</h1>
        <p>Cadastre voos, hospedagens e transportes, publique pacotes e acompanhe as reservas criadas para seus clientes.</p>
        <div className="operator-access-benefits">
          <article><strong>Catálogo</strong><span>Ofertas disponíveis para os viajantes</span></article>
          <article><strong>Reservas assistidas</strong><span>Atendimento em nome do passageiro</span></article>
          <article><strong>Pacotes</strong><span>Voo, hotel e transporte combinados</span></article>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-card operator-login-card">
          <span className="login-icon operator-login-icon" aria-hidden="true">↗</span>
          <p className="eyebrow">Acesso empresarial</p>
          <h2>Entrar como operador</h2>
          <p>Use a conta liberada para sua companhia. O login continua protegido pelo Keycloak e PKCE.</p>

          {authenticated && (
            <div className="operator-access-warning" role="status">
              Esta conta ainda não possui acesso de operador. Peça a um administrador para atribuir a role <code>OPERATOR</code>
              ou troque para uma conta já habilitada.
            </div>
          )}

          <div className="auth-actions">
            <button data-testid="operator-login-button" className="button button-full"
              onClick={() => void loginOperator()}>
              {authenticated ? "Trocar para conta de operador" : "Entrar como operador"}
            </button>
            {authenticated && (
              <button className="button button-ghost button-full" onClick={() => void logout()}>
                Sair da conta atual
              </button>
            )}
          </div>

          {!authenticated && (
            <div className="operator-registration">
              <strong>Representa uma companhia?</strong>
              <p>Crie a conta responsável. Por segurança, um administrador deverá liberar o perfil de operador.</p>
              <button data-testid="operator-register-button"
                className="button button-secondary button-full"
                onClick={() => void registerOperator()}>
                Cadastrar conta da companhia
              </button>
            </div>
          )}

          {showCredentials && (
            <div className="dev-credentials">
              <strong>Conta local de operador</strong>
              <code>operator / operator</code>
            </div>
          )}

          <Link className="operator-back-link" to="/">← Voltar para acesso de viajante</Link>
        </div>
      </section>
    </main>
  );
}
