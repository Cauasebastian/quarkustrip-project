import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { tripApi } from "../api";
import { formatDateTime, friendlyError } from "../format";
import { Empty, ErrorNotice, Loading, MoneyText, PageHeader, StatusBadge } from "../components/Ui";

export function HomePage() {
  const bookings = useQuery({
    queryKey: ["bookings", 0, 10],
    queryFn: () => tripApi.listBookings(0, 10)
  });

  return (
    <div className="page">
      <PageHeader eyebrow="Visão geral" title="Suas reservas" description="Acompanhe reservas recentes e comece uma nova viagem."
        action={<Link className="button" to="/catalog/flights">Planejar viagem</Link>} />
      <section className="quick-actions" aria-label="Atalhos">
        <Link to="/catalog/flights"><span>✈</span><strong>Buscar voos</strong><small>Escolha voo e assento</small></Link>
        <Link to="/catalog/hotels"><span>⌂</span><strong>Buscar hotéis</strong><small>Consulte quartos disponíveis</small></Link>
        <Link to="/catalog/transports"><span>⌁</span><strong>Buscar transporte</strong><small>Reserve por período</small></Link>
      </section>
      {bookings.isLoading && <Loading label="Buscando suas reservas..." />}
      {bookings.isError && <ErrorNotice message={friendlyError(bookings.error)} />}
      {bookings.data?.items.length === 0 && <Empty title="Nenhuma reserva ainda" description="Pesquise um serviço e monte a sua primeira viagem." actionTo="/catalog/flights" actionLabel="Explorar catálogo" />}
      {bookings.data && bookings.data.items.length > 0 && (
        <section className="booking-list">
          {bookings.data.items.map(booking => (
            <Link className="booking-row" to={`/bookings/${booking.id}`} key={booking.id}>
              <div><small>Reserva</small><strong>#{booking.id.slice(0, 8)}</strong><span>{formatDateTime(booking.createdAt)}</span></div>
              <div className="booking-meta"><span>{booking.items.length} {booking.items.length === 1 ? "item" : "itens"}</span><MoneyText value={booking.total} /></div>
              <StatusBadge status={booking.status} />
              <span className="row-arrow">→</span>
            </Link>
          ))}
        </section>
      )}
    </div>
  );
}
