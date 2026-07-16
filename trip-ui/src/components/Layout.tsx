import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth";
import { useDraft } from "../draft";

export function Layout() {
  const { isAdmin, logout, token } = useAuth();
  const { items } = useDraft();
  const name = typeof token?.preferred_username === "string" ? token.preferred_username : "viajante";

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink to="/" className="brand" aria-label="Trip Platform - início">
          <span className="brand-mark">T</span>
          <span><strong>Trip</strong><small>Platform</small></span>
        </NavLink>
        <nav className="desktop-nav" aria-label="Navegação principal">
          <NavLink to="/">Reservas</NavLink>
          <NavLink to="/catalog/flights">Voos</NavLink>
          <NavLink to="/catalog/hotels">Hotéis</NavLink>
          <NavLink to="/catalog/transports">Transportes</NavLink>
          <NavLink to="/bookings/new">Rascunho <span className="count">{items.length}</span></NavLink>
          {isAdmin && <NavLink to="/admin">Admin</NavLink>}
        </nav>
        <div className="user-menu">
          <NavLink to="/profile" className="user-name">{name}</NavLink>
          <button className="button button-ghost button-small" onClick={() => void logout()}>Sair</button>
        </div>
      </header>
      <main className="main-content"><Outlet /></main>
      <nav className="mobile-nav" aria-label="Navegação móvel">
        <NavLink to="/">Reservas</NavLink>
        <NavLink to="/catalog/flights">Voos</NavLink>
        <NavLink to="/catalog/hotels">Hotéis</NavLink>
        <NavLink to="/catalog/transports">Transporte</NavLink>
        <NavLink to="/bookings/new">Rascunho ({items.length})</NavLink>
      </nav>
    </div>
  );
}
