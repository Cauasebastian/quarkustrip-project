import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { tripApi } from "../api";
import { useDraft } from "../draft";
import { friendlyError } from "../format";
import { ErrorNotice, Loading, MoneyText, PageHeader, SuccessNotice } from "../components/Ui";
import type { Hotel, Room } from "../types";

interface Search { city: string; country: string; checkIn: string; checkOut: string }

export function HotelsPage() {
  const { addItem, items } = useDraft();
  const [city, setCity] = useState("Fortaleza");
  const [country, setCountry] = useState("BR");
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const [search, setSearch] = useState<Search | null>(null);
  const [selected, setSelected] = useState<Hotel | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const hotels = useQuery({
    queryKey: ["hotels", search],
    queryFn: () => tripApi.searchHotels(search!.city, search!.country, search!.checkIn, search!.checkOut),
    enabled: search !== null
  });
  const rooms = useQuery({
    queryKey: ["rooms", selected?.id, search?.checkIn, search?.checkOut],
    queryFn: () => tripApi.listRooms(selected!.id, search!.checkIn, search!.checkOut),
    enabled: selected !== null && search !== null
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (checkOut <= checkIn) { setNotice("A saída precisa ocorrer depois da entrada."); return; }
    setNotice(null); setSelected(null);
    setSearch({ city, country: country.toUpperCase(), checkIn, checkOut });
  }

  function selectRoom(room: Room) {
    const added = addItem({
      id: `HOTEL:${room.id}:${search!.checkIn}:${search!.checkOut}`,
      label: `${selected!.name} · quarto ${room.roomNumber}`,
      detail: `${room.roomType} · ${search!.checkIn} a ${search!.checkOut}`,
      price: room.nightlyPrice,
      request: { type: "HOTEL", resourceId: room.id, checkIn: search!.checkIn, checkOut: search!.checkOut }
    });
    setNotice(added ? "Quarto adicionado ao rascunho." : "A moeda deste quarto é diferente dos outros itens do rascunho.");
  }

  return (
    <div className="page">
      <PageHeader eyebrow="Catálogo" title="Hotéis e quartos" description="Escolha o período para consultar disponibilidade real."
        action={items.length > 0 && <Link className="button button-secondary" to="/bookings/new">Ver rascunho ({items.length})</Link>} />
      <form className="search-panel search-panel-wide" onSubmit={submit}>
        <label>Cidade<input required value={city} onChange={event => setCity(event.target.value)} /></label>
        <label>País<input required minLength={2} maxLength={2} value={country} onChange={event => setCountry(event.target.value)} /></label>
        <label>Entrada<input required type="date" value={checkIn} onChange={event => setCheckIn(event.target.value)} /></label>
        <label>Saída<input required type="date" value={checkOut} onChange={event => setCheckOut(event.target.value)} /></label>
        <button className="button" type="submit">Buscar hotéis</button>
      </form>
      {notice && (notice.includes("adicionado") ? <SuccessNotice message={notice} /> : <ErrorNotice message={notice} />)}
      {hotels.isLoading && <Loading label="Buscando hotéis..." />}
      {hotels.isError && <ErrorNotice message={friendlyError(hotels.error)} />}
      <section className="catalog-grid">
        {hotels.data?.items.map(hotel => <HotelCard key={hotel.id} hotel={hotel} selected={selected?.id === hotel.id} onSelect={() => setSelected(hotel)} />)}
      </section>
      {selected && <section className="room-section">
        <div className="section-heading"><div><p className="eyebrow">{selected.city}</p><h2>Quartos em {selected.name}</h2></div><button className="button button-ghost" onClick={() => setSelected(null)}>Fechar</button></div>
        {rooms.isLoading && <Loading label="Verificando quartos..." />}
        {rooms.isError && <ErrorNotice message={friendlyError(rooms.error)} />}
        <div className="room-list">{rooms.data?.items.map(room => <article className={`room-row ${!room.available ? "unavailable" : ""}`} key={room.id}>
          <div><strong>Quarto {room.roomNumber}</strong><span>{room.roomType}</span></div><div><MoneyText value={room.nightlyPrice} /><small>por noite</small></div>
          <button disabled={!room.available} className="button button-small" onClick={() => selectRoom(room)}>{room.available ? "Adicionar" : "Indisponível"}</button>
        </article>)}</div>
      </section>}
    </div>
  );
}

function HotelCard({ hotel, selected, onSelect }: { hotel: Hotel; selected: boolean; onSelect: () => void }) {
  return <article className={`catalog-card ${selected ? "selected" : ""}`}>
    <div className="catalog-card-top"><span className="catalog-icon">⌂</span><span className="rating">{"★".repeat(hotel.rating)}{"☆".repeat(5 - hotel.rating)}</span></div>
    <h2>{hotel.name}</h2><p>{hotel.address}</p><p className="muted">{hotel.city}, {hotel.country}</p>
    <button className="button button-secondary button-full" onClick={onSelect}>Ver quartos</button>
  </article>;
}
