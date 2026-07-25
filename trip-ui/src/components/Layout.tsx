import { useEffect, useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth";
import { tripApi } from "../api";
import { useDraft } from "../draft";

export function Layout() {
  const { isAdmin, isOperator, logout, token } = useAuth();
  const { items } = useDraft();
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ["profile"], queryFn: tripApi.getProfile, retry: false });
  const syncStarted = useRef(false);
  const syncProfile = useMutation({
    mutationFn: tripApi.updateProfile,
    onSuccess: value => queryClient.setQueryData(["profile"], value)
  });
  const name = typeof token?.preferred_username === "string" ? token.preferred_username : "viajante";
  const email = typeof token?.email === "string" ? token.email : "";
  const firstName = typeof token?.given_name === "string" ? token.given_name : "";
  const lastName = typeof token?.family_name === "string" ? token.family_name : "";

  useEffect(() => {
    if (profile.data !== null || syncStarted.current) return;
    if (!email) return;
    syncStarted.current = true;
    syncProfile.mutate({
      email,
      firstName,
      lastName,
      preferencesJson: "{}"
    });
  }, [profile.data, email, firstName, lastName]);

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
          <NavLink to="/packages">Pacotes</NavLink>
          <NavLink to="/bookings/new">Rascunho <span className="count">{items.length}</span></NavLink>
          {isAdmin && <NavLink to="/admin">Admin</NavLink>}
          {isOperator && <NavLink to="/operator">Operador</NavLink>}
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
        <NavLink to="/packages">Pacotes</NavLink>
        <NavLink to="/bookings/new">Rascunho ({items.length})</NavLink>
        {isOperator && <NavLink to="/operator">Operador</NavLink>}
      </nav>
    </div>
  );
}
