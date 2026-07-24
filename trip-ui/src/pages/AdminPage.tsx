import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { tripApi } from "../api";
import { friendlyError, localDateTimeToIso } from "../format";
import { ErrorNotice, PageHeader, SuccessNotice } from "../components/Ui";

type Tab = "flight" | "hotel" | "room" | "transport";

export function AdminPage() {
  return <CatalogManagement />;
}

export function CatalogManagement({ embedded = false }: { embedded?: boolean }) {
  const [tab, setTab] = useState<Tab>("flight");
  return <div className="page">
    {!embedded && <PageHeader eyebrow="Administração" title="Gerenciar catálogo" description="Cadastre ofertas para testar a jornada completa da plataforma." />}
    <div className="tabs" role="tablist">
      {(["flight", "hotel", "room", "transport"] as Tab[]).map(value => <button role="tab" aria-selected={tab === value} className={tab === value ? "active" : ""} key={value} onClick={() => setTab(value)}>{tabLabel(value)}</button>)}
    </div>
    {tab === "flight" && <FlightForm />}{tab === "hotel" && <HotelForm />}{tab === "room" && <RoomForm />}{tab === "transport" && <TransportForm />}
  </div>;
}

function FlightForm() {
  const [form, set] = useState({ flightNumber: "TP100", origin: "FOR", destination: "GRU", departureTime: "", arrivalTime: "", totalSeats: 12, currency: "BRL", amountMinor: 45990 });
  const mutation = useMutation({ mutationFn: () => tripApi.createFlight({ ...form, departureTime: localDateTimeToIso(form.departureTime), arrivalTime: localDateTimeToIso(form.arrivalTime), seatPrice: { currency: form.currency, amountMinor: form.amountMinor } }) });
  return <AdminForm title="Novo voo" description="Os assentos serão gerados automaticamente a partir da capacidade." mutation={mutation} onSubmit={() => mutation.mutate()}>
    <label>Número<input required value={form.flightNumber} onChange={e => set({ ...form, flightNumber: e.target.value })} /></label>
    <label>Origem<input required minLength={3} maxLength={3} value={form.origin} onChange={e => set({ ...form, origin: e.target.value })} /></label>
    <label>Destino<input required minLength={3} maxLength={3} value={form.destination} onChange={e => set({ ...form, destination: e.target.value })} /></label>
    <label>Partida<input required type="datetime-local" value={form.departureTime} onChange={e => set({ ...form, departureTime: e.target.value })} /></label>
    <label>Chegada<input required type="datetime-local" value={form.arrivalTime} onChange={e => set({ ...form, arrivalTime: e.target.value })} /></label>
    <label>Assentos<input required min={1} type="number" value={form.totalSeats} onChange={e => set({ ...form, totalSeats: Number(e.target.value) })} /></label>
    <MoneyFields currency={form.currency} amount={form.amountMinor} setCurrency={currency => set({ ...form, currency })} setAmount={amountMinor => set({ ...form, amountMinor })} />
  </AdminForm>;
}

function HotelForm() {
  const [form, set] = useState({ name: "Hotel Atlântico", address: "Av. Beira Mar, 1000", city: "Fortaleza", country: "BR", rating: 4 });
  const mutation = useMutation({ mutationFn: () => tripApi.createHotel(form) });
  return <AdminForm title="Novo hotel" description="Após criar, copie o ID retornado para cadastrar quartos." mutation={mutation} onSubmit={() => mutation.mutate()}>
    <label>Nome<input required value={form.name} onChange={e => set({ ...form, name: e.target.value })} /></label>
    <label>Endereço<input required value={form.address} onChange={e => set({ ...form, address: e.target.value })} /></label>
    <label>Cidade<input required value={form.city} onChange={e => set({ ...form, city: e.target.value })} /></label>
    <label>País<input required minLength={2} maxLength={2} value={form.country} onChange={e => set({ ...form, country: e.target.value })} /></label>
    <label>Classificação<input type="number" min={0} max={5} value={form.rating} onChange={e => set({ ...form, rating: Number(e.target.value) })} /></label>
  </AdminForm>;
}

function RoomForm() {
  const [form, set] = useState({ hotelId: "", roomNumber: "101", roomType: "STANDARD", currency: "BRL", amountMinor: 29990 });
  const mutation = useMutation({ mutationFn: () => tripApi.createRoom({ hotelId: form.hotelId, roomNumber: form.roomNumber, roomType: form.roomType, nightlyPrice: { currency: form.currency, amountMinor: form.amountMinor } }) });
  return <AdminForm title="Novo quarto" description="Vincule o quarto ao UUID de um hotel existente." mutation={mutation} onSubmit={() => mutation.mutate()}>
    <label>ID do hotel<input required value={form.hotelId} onChange={e => set({ ...form, hotelId: e.target.value })} /></label>
    <label>Número<input required value={form.roomNumber} onChange={e => set({ ...form, roomNumber: e.target.value })} /></label>
    <label>Tipo<input required value={form.roomType} onChange={e => set({ ...form, roomType: e.target.value })} /></label>
    <MoneyFields currency={form.currency} amount={form.amountMinor} setCurrency={currency => set({ ...form, currency })} setAmount={amountMinor => set({ ...form, amountMinor })} />
  </AdminForm>;
}

function TransportForm() {
  const [form, set] = useState({ transportType: "TRANSFER", providerName: "Trip Transfer", vehicleDetailsJson: "{\"vehicle\":\"Sedan\",\"capacity\":4}", currency: "BRL", amountMinor: 15990 });
  const mutation = useMutation({ mutationFn: () => tripApi.createTransport({ transportType: form.transportType, providerName: form.providerName, vehicleDetailsJson: form.vehicleDetailsJson, price: { currency: form.currency, amountMinor: form.amountMinor } }) });
  return <AdminForm title="Novo transporte" description="Detalhes do veículo são armazenados como JSON." mutation={mutation} onSubmit={() => mutation.mutate()}>
    <label>Tipo<select value={form.transportType} onChange={e => set({ ...form, transportType: e.target.value })}><option>TRANSFER</option><option>CAR_RENTAL</option><option>SHUTTLE</option></select></label>
    <label>Fornecedor<input required value={form.providerName} onChange={e => set({ ...form, providerName: e.target.value })} /></label>
    <label className="full-column">Detalhes JSON<textarea required rows={5} value={form.vehicleDetailsJson} onChange={e => set({ ...form, vehicleDetailsJson: e.target.value })} /></label>
    <MoneyFields currency={form.currency} amount={form.amountMinor} setCurrency={currency => set({ ...form, currency })} setAmount={amountMinor => set({ ...form, amountMinor })} />
  </AdminForm>;
}

function AdminForm({ title, description, mutation, onSubmit, children }: { title: string; description: string; mutation: { isPending: boolean; isSuccess: boolean; isError: boolean; error: unknown; data?: unknown }; onSubmit: () => void; children: React.ReactNode }) {
  function submit(event: FormEvent) { event.preventDefault(); onSubmit(); }
  return <form className="form-card admin-form" onSubmit={submit}><div className="full-column"><h2>{title}</h2><p>{description}</p></div>{children}
    <div className="full-column">{mutation.isSuccess && <><SuccessNotice message="Item criado no catálogo." /><pre className="result-json">{JSON.stringify(mutation.data, null, 2)}</pre></>}{mutation.isError && <ErrorNotice message={friendlyError(mutation.error)} />}</div>
    <button className="button" disabled={mutation.isPending}>{mutation.isPending ? "Salvando..." : "Cadastrar"}</button>
  </form>;
}

function MoneyFields({ currency, amount, setCurrency, setAmount }: { currency: string; amount: number; setCurrency: (value: string) => void; setAmount: (value: number) => void }) {
  return <><label>Moeda<input required minLength={3} maxLength={3} value={currency} onChange={e => setCurrency(e.target.value.toUpperCase())} /></label><label>Valor em unidades mínimas<input required min={0} type="number" value={amount} onChange={e => setAmount(Number(e.target.value))} /></label></>;
}

function tabLabel(value: Tab) { return ({ flight: "Voos", hotel: "Hotéis", room: "Quartos", transport: "Transportes" })[value]; }
