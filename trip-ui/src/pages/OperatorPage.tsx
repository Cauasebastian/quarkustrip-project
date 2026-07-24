import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { tripApi } from "../api";
import { useDraft } from "../draft";
import { formatDateTime, formatMoney, friendlyError } from "../format";
import type { Profile } from "../types";
import { Empty, ErrorNotice, Loading, StatusBadge, SuccessNotice } from "../components/Ui";
import { CatalogManagement } from "./AdminPage";

type OperatorTab = "reservations" | "catalog" | "packages";
const ATTEMPT_KEY = "trip.operator.booking.attempt";

export function OperatorPage() {
  const queryClient = useQueryClient();
  const { items, currency, clear } = useDraft();
  const [tab, setTab] = useState<OperatorTab>("reservations");
  const [search, setSearch] = useState("");
  const [selectedPassenger, setSelectedPassenger] = useState<Profile | null>(null);
  const [paymentMethodRef, setPaymentMethodRef] = useState("pm_test_success");
  const [packageForm, setPackageForm] = useState({ name: "", description: "" });

  const bookings = useQuery({
    queryKey: ["operator-bookings", 0, 50],
    queryFn: () => tripApi.listOperatorBookings(0, 50)
  });
  const packages = useQuery({
    queryKey: ["packages", 0, 50],
    queryFn: () => tripApi.listPackages(0, 50)
  });
  const users = useQuery({
    queryKey: ["operator-users", search],
    queryFn: () => tripApi.searchOperatorUsers(search),
    enabled: search.trim().length >= 2
  });

  const createBooking = useMutation({
    mutationFn: () => {
      if (!selectedPassenger || !currency || items.length === 0) {
        throw new Error("Selecione um passageiro e adicione itens ao rascunho.");
      }
      const body = {
        userId: selectedPassenger.id,
        currency,
        paymentMethodRef,
        items: items.map(item => item.request)
      };
      return tripApi.createOperatorBooking(body, attempt(JSON.stringify(body)));
    },
    onSuccess: async () => {
      sessionStorage.removeItem(ATTEMPT_KEY);
      clear();
      await queryClient.invalidateQueries({ queryKey: ["operator-bookings"] });
    }
  });

  const createPackage = useMutation({
    mutationFn: () => {
      if (!currency || items.length === 0) throw new Error("Adicione itens ao rascunho antes de criar o pacote.");
      return tripApi.createPackage({
        name: packageForm.name,
        description: packageForm.description,
        currency,
        items: items.map(item => ({
          item: item.request,
          displayPrice: item.price,
          label: item.label,
          detail: item.detail
        }))
      });
    },
    onSuccess: async () => {
      setPackageForm({ name: "", description: "" });
      clear();
      await queryClient.invalidateQueries({ queryKey: ["packages"] });
    }
  });

  const stats = useMemo(() => {
    const values = bookings.data?.items ?? [];
    return {
      total: bookings.data?.totalElements ?? 0,
      confirmed: values.filter(value => value.status === "CONFIRMED").length,
      active: values.filter(value => ["RESERVING", "PAYMENT_PENDING", "CONFIRMING"].includes(value.status)).length,
      cancelled: values.filter(value => value.status === "CANCELLED").length
    };
  }, [bookings.data]);

  return (
    <div className="page operator-page">
      <header className="operator-header">
        <div>
          <p className="eyebrow">Área da companhia</p>
          <h1>Painel do operador</h1>
          <p>Cadastre ofertas, monte pacotes e faça reservas para passageiros da plataforma.</p>
        </div>
        <Link className="button button-secondary" to="/catalog/flights">Selecionar itens no catálogo</Link>
      </header>

      <section className="operator-stats" aria-label="Resumo das reservas">
        <Stat value={stats.total} label="Reservas criadas" tone="blue" />
        <Stat value={stats.confirmed} label="Confirmadas" tone="green" />
        <Stat value={stats.active} label="Em andamento" tone="amber" />
        <Stat value={stats.cancelled} label="Canceladas" tone="red" />
      </section>

      <div className="operator-tabs" role="tablist">
        <Tab active={tab === "reservations"} onClick={() => setTab("reservations")}>Reservas</Tab>
        <Tab active={tab === "catalog"} onClick={() => setTab("catalog")}>Voos, hotéis e transportes</Tab>
        <Tab active={tab === "packages"} onClick={() => setTab("packages")}>Pacotes</Tab>
      </div>

      {tab === "reservations" && (
        <div className="operator-workspace">
          <div className="operator-main-card">
            <h2>Nova reserva para passageiro</h2>
            <p>Pesquise um perfil existente e use os itens selecionados no catálogo.</p>
            <label>Buscar passageiro
              <input value={search} onChange={event => setSearch(event.target.value)}
                placeholder="Nome ou e-mail" />
            </label>
            {users.isFetching && <Loading label="Buscando passageiros..." />}
            {users.isError && <ErrorNotice message={friendlyError(users.error)} />}
            {users.data && (
              <div className="passenger-results">
                {users.data.items.map(profile => (
                  <button key={profile.id} className={selectedPassenger?.id === profile.id ? "selected" : ""}
                    onClick={() => setSelectedPassenger(profile)}>
                    <span className="passenger-avatar">{initials(profile)}</span>
                    <span><strong>{fullName(profile)}</strong><small>{profile.email}</small></span>
                  </button>
                ))}
                {users.data.items.length === 0 && <p>Nenhum perfil encontrado.</p>}
              </div>
            )}

            <DraftSummary />
            <label>Método de pagamento
              <select value={paymentMethodRef} onChange={event => setPaymentMethodRef(event.target.value)}>
                <option value="pm_test_success">Aprovar pagamento</option>
                <option value="pm_test_failure">Recusar pagamento</option>
                <option value="pm_test_refund_failure">Falhar no reembolso</option>
              </select>
            </label>
            {createBooking.isSuccess && <SuccessNotice message="Reserva criada para o passageiro." />}
            {createBooking.isError && <ErrorNotice message={friendlyError(createBooking.error)} />}
            <button className="button button-full" disabled={createBooking.isPending
              || !selectedPassenger || items.length === 0} onClick={() => createBooking.mutate()}>
              {createBooking.isPending ? "Criando reserva..." : "Criar reserva"}
            </button>

            <h2 className="operator-list-title">Reservas cadastradas</h2>
            {bookings.isLoading && <Loading />}
            {bookings.isError && <ErrorNotice message={friendlyError(bookings.error)} />}
            {bookings.data?.items.map(value => (
              <Link className="operator-booking-row" to={`/bookings/${value.id}`} key={value.id}>
                <span><strong>#{value.id.slice(0, 8)}</strong><small>{formatDateTime(value.createdAt)}</small></span>
                <span>{value.items.length} itens</span>
                <StatusBadge status={value.status} />
              </Link>
            ))}
          </div>

          <aside className="passenger-card">
            <p className="eyebrow">Passageiro</p>
            {selectedPassenger ? (
              <>
                <span className="passenger-avatar passenger-avatar-large">{initials(selectedPassenger)}</span>
                <h2>{fullName(selectedPassenger)}</h2>
                <p>{selectedPassenger.email}</p>
                <dl>
                  <div><dt>ID do perfil</dt><dd>{selectedPassenger.id.slice(0, 8)}</dd></div>
                  <div><dt>Itens no rascunho</dt><dd>{items.length}</dd></div>
                  <div><dt>Moeda</dt><dd>{currency ?? "—"}</dd></div>
                </dl>
              </>
            ) : <Empty title="Nenhum passageiro" description="Pesquise e selecione um usuário para iniciar." />}
          </aside>
        </div>
      )}

      {tab === "catalog" && <section className="operator-main-card"><CatalogManagement embedded /></section>}

      {tab === "packages" && (
        <section className="operator-package-grid">
          <div className="operator-main-card">
            <h2>Novo pacote</h2>
            <p>O pacote usa os itens atuais do rascunho e continua sujeito à disponibilidade.</p>
            <label>Nome<input required value={packageForm.name}
              onChange={event => setPackageForm({ ...packageForm, name: event.target.value })}
              placeholder="Fim de semana em Fortaleza" /></label>
            <label>Descrição<textarea rows={4} value={packageForm.description}
              onChange={event => setPackageForm({ ...packageForm, description: event.target.value })}
              placeholder="Voo, hospedagem e traslado em um único pacote." /></label>
            <DraftSummary />
            {createPackage.isSuccess && <SuccessNotice message="Pacote publicado para os viajantes." />}
            {createPackage.isError && <ErrorNotice message={friendlyError(createPackage.error)} />}
            <button className="button button-full" disabled={createPackage.isPending
              || !packageForm.name.trim() || items.length === 0} onClick={() => createPackage.mutate()}>
              {createPackage.isPending ? "Publicando..." : "Publicar pacote"}
            </button>
          </div>
          <div className="operator-main-card">
            <h2>Pacotes publicados</h2>
            {packages.isLoading && <Loading />}
            {packages.data?.items.length === 0 && <Empty title="Nenhum pacote" description="Monte um rascunho e publique o primeiro pacote." />}
            {packages.data?.items.map(value => (
              <article className="operator-package-row" key={value.id}>
                <div><strong>{value.name}</strong><p>{value.description}</p></div>
                <span>{value.items.length} itens</span>
              </article>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function DraftSummary() {
  const { items, currency, removeItem } = useDraft();
  const total = items.reduce((sum, item) => sum + item.price.amountMinor, 0);
  return (
    <div className="operator-draft">
      <div><strong>Itens selecionados</strong><Link to="/catalog/flights">Adicionar pelo catálogo</Link></div>
      {items.length === 0 && <p>Nenhum item no rascunho.</p>}
      {items.map(item => (
        <div className="operator-draft-item" key={item.id}>
          <span><strong>{item.label}</strong><small>{item.detail}</small></span>
          <strong>{formatMoney(item.price)}</strong>
          <button className="icon-button" aria-label={`Remover ${item.label}`}
            onClick={() => removeItem(item.id)}>×</button>
        </div>
      ))}
      {currency && <div className="operator-draft-total"><span>Total estimado</span>
        <strong>{formatMoney({ currency, amountMinor: total })}</strong></div>}
    </div>
  );
}

function Stat({ value, label, tone }: { value: number; label: string; tone: string }) {
  return <article className={`operator-stat ${tone}`}><strong>{value}</strong><span>{label}</span></article>;
}

function Tab({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return <button role="tab" aria-selected={active} className={active ? "active" : ""} onClick={onClick}>{children}</button>;
}

function initials(profile: Profile) {
  return `${profile.firstName?.[0] ?? ""}${profile.lastName?.[0] ?? ""}`.toUpperCase() || "U";
}

function fullName(profile: Profile) {
  return `${profile.firstName} ${profile.lastName}`.trim() || profile.email;
}

function attempt(signature: string): string {
  try {
    const stored = JSON.parse(sessionStorage.getItem(ATTEMPT_KEY) ?? "null") as
      { signature: string; idempotencyKey: string } | null;
    if (stored?.signature === signature) return stored.idempotencyKey;
  } catch { /* create a fresh attempt */ }
  const value = { signature, idempotencyKey: crypto.randomUUID() };
  sessionStorage.setItem(ATTEMPT_KEY, JSON.stringify(value));
  return value.idempotencyKey;
}
