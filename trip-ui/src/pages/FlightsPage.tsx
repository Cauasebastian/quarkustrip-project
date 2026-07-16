import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { tripApi } from "../api";
import { useDraft } from "../draft";
import { formatDateTime, friendlyError, localDateTimeToIso } from "../format";
import { ErrorNotice, Loading, MoneyText, PageHeader, SuccessNotice } from "../components/Ui";
import type { Flight } from "../types";

interface Search { origin: string; destination: string; departsAfter: string }

export function FlightsPage() {
  const { addItem, items } = useDraft();
  const [origin, setOrigin] = useState("FOR");
  const [destination, setDestination] = useState("GRU");
  const [departsAfter, setDepartsAfter] = useState("");
  const [search, setSearch] = useState<Search | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const result = useQuery({
    queryKey: ["flights", search],
    queryFn: () => tripApi.searchFlights(search!.origin, search!.destination, search!.departsAfter),
    enabled: search !== null
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setSearch({ origin: origin.toUpperCase(), destination: destination.toUpperCase(),
      departsAfter: departsAfter ? localDateTimeToIso(departsAfter) : new Date().toISOString() });
  }

  function select(flight: Flight, seat: string) {
    const added = addItem({
      id: `FLIGHT:${flight.id}:${seat}`,
      label: `${flight.origin} → ${flight.destination}`,
      detail: `${flight.flightNumber} · assento ${seat} · ${formatDateTime(flight.departureTime)}`,
      price: flight.seatPrice,
      request: { type: "FLIGHT", resourceId: flight.id, seatNumber: seat }
    });
    setNotice(added ? "Voo adicionado ao rascunho." : "A moeda deste voo é diferente dos outros itens do rascunho.");
  }

  return (
    <div className="page">
      <PageHeader eyebrow="Catálogo" title="Encontre seu voo" description="Consulte assentos disponíveis e adicione um ao rascunho."
        action={items.length > 0 && <Link className="button button-secondary" to="/bookings/new">Ver rascunho ({items.length})</Link>} />
      <form className="search-panel" onSubmit={submit}>
        <label>Origem<input required minLength={3} maxLength={3} value={origin} onChange={event => setOrigin(event.target.value)} placeholder="FOR" /></label>
        <span className="search-separator">→</span>
        <label>Destino<input required minLength={3} maxLength={3} value={destination} onChange={event => setDestination(event.target.value)} placeholder="GRU" /></label>
        <label>Partindo após<input type="datetime-local" value={departsAfter} onChange={event => setDepartsAfter(event.target.value)} /></label>
        <button className="button" type="submit">Buscar voos</button>
      </form>
      {notice && (notice.includes("diferente") ? <ErrorNotice message={notice} /> : <SuccessNotice message={notice} />)}
      {result.isLoading && <Loading label="Consultando voos e assentos..." />}
      {result.isError && <ErrorNotice message={friendlyError(result.error)} />}
      {result.data?.items.length === 0 && <div className="state-card"><h2>Nenhum voo encontrado</h2><p>Tente alterar origem, destino ou horário.</p></div>}
      <section className="catalog-grid">
        {result.data?.items.map(flight => (
          <article className="catalog-card" key={flight.id}>
            <div className="catalog-card-top"><span className="catalog-icon">✈</span><span className="muted">{flight.flightNumber}</span></div>
            <div className="route"><strong>{flight.origin}</strong><span>—→</span><strong>{flight.destination}</strong></div>
            <p>{formatDateTime(flight.departureTime)} até {formatDateTime(flight.arrivalTime)}</p>
            <div className="price-row"><MoneyText value={flight.seatPrice} /><small>por assento</small></div>
            <div className="seat-list" aria-label="Assentos disponíveis">
              {flight.availableSeats.slice(0, 12).map(seat => <button key={seat} className="seat" onClick={() => select(flight, seat)}>{seat}</button>)}
            </div>
            {flight.availableSeats.length > 12 && <small className="muted">+ {flight.availableSeats.length - 12} assentos disponíveis</small>}
          </article>
        ))}
      </section>
    </div>
  );
}
