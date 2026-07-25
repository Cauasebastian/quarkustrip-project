import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { tripApi } from "../api";
import { useAuth } from "../auth";
import { useDraft } from "../draft";
import { friendlyError } from "../format";
import { Empty, ErrorNotice, Loading, MoneyText, PageHeader, SuccessNotice } from "../components/Ui";
import type { Hotel, Room } from "../types";

interface Search {
  city: string;
  country: string;
  checkIn: string;
  checkOut: string;
}

const initialSearch: Search = { city: "", country: "", checkIn: "", checkOut: "" };

export function HotelsPage() {
  const { addItem, items } = useDraft();
  const { isOperator } = useAuth();
  const [city, setCity] = useState("");
  const [country, setCountry] = useState("");
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const [search, setSearch] = useState<Search>(initialSearch);
  const [selected, setSelected] = useState<Hotel | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const hotels = useQuery({
    queryKey: ["hotels", search],
    queryFn: () => tripApi.searchHotels(search.city, search.country, search.checkIn, search.checkOut)
  });
  const rooms = useQuery({
    queryKey: ["rooms", selected?.id, hotels.data?.checkIn, hotels.data?.checkOut],
    queryFn: () => tripApi.listRooms(selected!.id, hotels.data!.checkIn, hotels.data!.checkOut),
    enabled: selected !== null && hotels.data !== undefined
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if ((checkIn && !checkOut) || (!checkIn && checkOut)) {
      setNotice("Preencha entrada e saída juntas ou deixe as duas datas vazias.");
      return;
    }
    if (checkIn && checkOut <= checkIn) {
      setNotice("A saída precisa ocorrer depois da entrada.");
      return;
    }
    if (country.trim() && !/^[a-z]{2}$/i.test(country.trim())) {
      setNotice("Use a sigla do país com duas letras ou deixe o campo vazio.");
      return;
    }
    setNotice(null);
    setSelected(null);
    setSearch({
      city: city.trim(),
      country: country.trim().toUpperCase(),
      checkIn,
      checkOut
    });
  }

  function selectRoom(room: Room) {
    if (!selected || !hotels.data) return;
    const { checkIn: resolvedCheckIn, checkOut: resolvedCheckOut } = hotels.data;
    const added = addItem({
      id: `HOTEL:${room.id}:${resolvedCheckIn}:${resolvedCheckOut}`,
      label: `${selected.name} · quarto ${room.roomNumber}`,
      detail: `${room.roomType} · ${formatDate(resolvedCheckIn)} a ${formatDate(resolvedCheckOut)}`,
      price: room.nightlyPrice,
      request: {
        type: "HOTEL",
        resourceId: room.id,
        checkIn: resolvedCheckIn,
        checkOut: resolvedCheckOut
      }
    });
    setNotice(added
      ? "Quarto adicionado ao rascunho."
      : "A moeda deste quarto é diferente dos outros itens do rascunho.");
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Catálogo"
        title="Hotéis e quartos"
        description="Veja todos os hotéis agora. As datas são opcionais e, quando vazias, verificamos automaticamente os próximos três dias."
        action={items.length > 0 && (
          <Link className="button button-secondary" to={isOperator ? "/operator" : "/bookings/new"}>
            {isOperator ? "Voltar ao operador" : "Ver rascunho"} ({items.length})
          </Link>
        )}
      />

      <form className="search-panel search-panel-wide" onSubmit={submit}>
        <label>
          Cidade <span className="field-optional">Opcional</span>
          <input value={city} placeholder="Todas as cidades" onChange={event => setCity(event.target.value)} />
        </label>
        <label>
          País <span className="field-optional">Opcional</span>
          <input minLength={2} maxLength={2} value={country} placeholder="Todos" onChange={event => setCountry(event.target.value)} />
        </label>
        <label>
          Entrada <span className="field-optional">Opcional</span>
          <input type="date" value={checkIn} onChange={event => setCheckIn(event.target.value)} />
        </label>
        <label>
          Saída <span className="field-optional">Opcional</span>
          <input type="date" value={checkOut} onChange={event => setCheckOut(event.target.value)} />
        </label>
        <button className="button" type="submit">Atualizar busca</button>
      </form>

      {notice && (notice.includes("adicionado")
        ? <SuccessNotice message={notice} />
        : <ErrorNotice message={notice} />)}
      {hotels.isLoading && <Loading label="Verificando hotéis para os próximos dias..." />}
      {hotels.isError && <ErrorNotice message={friendlyError(hotels.error)} />}

      {hotels.data && (
        <div className="availability-summary" role="status">
          <span aria-hidden="true">✓</span>
          <div>
            <strong>{hotels.data.defaultPeriod ? "Período sugerido" : "Período selecionado"}</strong>
            <small>{formatDate(hotels.data.checkIn)} até {formatDate(hotels.data.checkOut)}</small>
          </div>
          <small>{hotels.data.items.filter(hotel => hotel.available).length} de {hotels.data.items.length} hotéis com quartos disponíveis</small>
        </div>
      )}

      {hotels.data?.items.length === 0 && (
        <Empty
          title="Nenhum hotel encontrado"
          description="Tente remover os filtros de cidade e país para visualizar todo o catálogo."
        />
      )}

      <section className="catalog-grid">
        {hotels.data?.items.map(hotel => (
          <HotelCard
            key={hotel.id}
            hotel={hotel}
            selected={selected?.id === hotel.id}
            onSelect={() => setSelected(hotel)}
          />
        ))}
      </section>

      {selected && (
        <section className="room-section">
          <div className="section-heading">
            <div>
              <p className="eyebrow">{selected.city}</p>
              <h2>Quartos em {selected.name}</h2>
            </div>
            <button className="button button-ghost" onClick={() => setSelected(null)}>Fechar</button>
          </div>
          {rooms.isLoading && <Loading label="Verificando quartos..." />}
          {rooms.isError && <ErrorNotice message={friendlyError(rooms.error)} />}
          {rooms.data?.items.length === 0 && (
            <Empty
              title="Nenhum quarto cadastrado"
              description="Este hotel ainda não possui quartos ativos no catálogo."
            />
          )}
          <div className="room-list">
            {rooms.data?.items.map(room => (
              <article className={`room-row ${!room.available ? "unavailable" : ""}`} key={room.id}>
                <div><strong>Quarto {room.roomNumber}</strong><span>{room.roomType}</span></div>
                <div><MoneyText value={room.nightlyPrice} /><small>por noite</small></div>
                <button disabled={!room.available} className="button button-small" onClick={() => selectRoom(room)}>
                  {room.available ? "Adicionar" : "Indisponível"}
                </button>
              </article>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function HotelCard({ hotel, selected, onSelect }: {
  hotel: Hotel;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <article className={`catalog-card ${selected ? "selected" : ""}`}>
      <div className="catalog-card-top">
        <span className="catalog-icon">⌂</span>
        <span className="rating">{"★".repeat(hotel.rating)}{"☆".repeat(5 - hotel.rating)}</span>
      </div>
      <span className={`availability-badge ${hotel.available ? "available" : "unavailable"}`}>
        {hotel.available ? "Disponível nos próximos dias" : "Sem quartos no período"}
      </span>
      <h2>{hotel.name}</h2>
      <p>{hotel.address}</p>
      <p className="muted">{hotel.city}, {hotel.country}</p>
      <button className="button button-secondary button-full" onClick={onSelect}>Ver quartos</button>
    </article>
  );
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short" })
    .format(new Date(`${value}T12:00:00`));
}
