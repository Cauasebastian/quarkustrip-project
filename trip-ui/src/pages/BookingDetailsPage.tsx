import { useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { tripApi } from "../api";
import { formatDateTime, friendlyError, shouldPollBooking } from "../format";
import { ErrorNotice, Loading, MoneyText, PageHeader, StatusBadge, SuccessNotice } from "../components/Ui";

const steps = ["RESERVING", "PAYMENT_PENDING", "CONFIRMING", "CONFIRMED"];

export function BookingDetailsPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const pollingStartedAt = useRef(Date.now());
  const booking = useQuery({
    queryKey: ["booking", id],
    queryFn: () => tripApi.getBooking(id),
    enabled: Boolean(id),
    refetchInterval: query => shouldPollBooking(query.state.data?.status, pollingStartedAt.current) ? 2_000 : false
  });
  const cancel = useMutation({
    mutationFn: () => tripApi.cancelBooking(id),
    onSuccess: async () => {
      pollingStartedAt.current = Date.now();
      await queryClient.invalidateQueries({ queryKey: ["booking", id] });
      await queryClient.invalidateQueries({ queryKey: ["bookings"] });
    }
  });

  if (booking.isLoading) return <div className="page"><Loading label="Carregando reserva..." /></div>;
  if (booking.isError || !booking.data) return <div className="page"><ErrorNotice message={friendlyError(booking.error)} /></div>;
  const value = booking.data;
  const canCancel = !["CANCELLED", "FAILED", "MANUAL_REVIEW", "COMPENSATING"].includes(value.status);

  return <div className="page page-narrow">
    <PageHeader eyebrow={`Reserva #${value.id.slice(0, 8)}`} title="Acompanhamento da Saga" description={`Criada em ${formatDateTime(value.createdAt)}`}
      action={<button className="button button-secondary" onClick={() => void booking.refetch()}>Atualizar</button>} />
    <section className="booking-hero">
      <div><span>Estado atual</span><StatusBadge status={value.status} /></div>
      <div><span>Valor total</span><MoneyText value={value.total} /></div>
      <div><span>Última atualização</span><strong>{formatDateTime(value.updatedAt)}</strong></div>
    </section>
    <ol className="saga-steps">
      {steps.map((step, index) => {
        const current = steps.indexOf(value.status);
        const done = value.status === "CONFIRMED" || (current >= 0 && index < current);
        const active = step === value.status;
        return <li className={done ? "done" : active ? "active" : ""} key={step}><span>{done ? "✓" : index + 1}</span><div><strong>{label(step)}</strong><small>{step}</small></div></li>;
      })}
    </ol>
    {value.failureCode && <ErrorNotice message={`Motivo: ${value.failureCode}`} />}
    {cancel.isSuccess && <SuccessNotice message="Cancelamento solicitado. A compensação será acompanhada automaticamente." />}
    {cancel.isError && <ErrorNotice message={friendlyError(cancel.error)} />}
    <section><div className="section-heading"><div><p className="eyebrow">Itens</p><h2>Serviços da viagem</h2></div></div>
      <div className="booking-items">{value.items.map(item => <article key={item.id}>
        <span className="item-kind">{item.type === "FLIGHT" ? "✈" : item.type === "HOTEL" ? "⌂" : "⌁"}</span>
        <div><strong>{label(item.type)}</strong><small>{item.resourceId}</small>{item.failureReason && <p className="danger">{item.failureReason}</p>}</div>
        <MoneyText value={item.price} /><StatusBadge status={item.status} />
      </article>)}</div>
    </section>
    <div className="detail-actions">
      {canCancel && <button data-testid="cancel-booking" className="button button-danger" disabled={cancel.isPending} onClick={() => cancel.mutate()}>{cancel.isPending ? "Solicitando..." : "Cancelar reserva"}</button>}
      <Link className="button button-ghost" to="/">Voltar às reservas</Link>
    </div>
  </div>;
}

function label(value: string): string {
  return ({ RESERVING: "Bloqueio dos recursos", PAYMENT_PENDING: "Pagamento", CONFIRMING: "Confirmação", CONFIRMED: "Concluída", FLIGHT: "Voo", HOTEL: "Hotel", TRANSPORT: "Transporte" } as Record<string, string>)[value] ?? value;
}
