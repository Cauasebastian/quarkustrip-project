import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { tripApi } from "../api";
import { formatDateTime, formatDuration, friendlyError, shouldPollBooking } from "../format";
import { jaegerDependenciesUrl, jaegerTraceUrl } from "../observability";
import type { BookingObservability, ObservabilitySignals } from "../types";
import { ErrorNotice, Loading, MoneyText, PageHeader, StatusBadge, SuccessNotice } from "../components/Ui";

const steps = ["RESERVING", "PAYMENT_PENDING", "CONFIRMING", "CONFIRMED"];

export function BookingDetailsPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const pollingStartedAt = useRef(Date.now());
  const [showCommunications, setShowCommunications] = useState(false);
  const booking = useQuery({
    queryKey: ["booking", id],
    queryFn: () => tripApi.getBooking(id),
    enabled: Boolean(id),
    refetchInterval: query => shouldPollBooking(query.state.data?.status, pollingStartedAt.current) ? 2_000 : false
  });
  const observability = useQuery({
    queryKey: ["booking-observability", id],
    queryFn: () => tripApi.getBookingObservability(id),
    enabled: Boolean(id),
    refetchInterval: () => shouldPollBooking(booking.data?.status, pollingStartedAt.current) ? 5_000 : false,
    retry: false
  });
  const cancel = useMutation({
    mutationFn: () => tripApi.cancelBooking(id),
    onSuccess: async () => {
      pollingStartedAt.current = Date.now();
      await queryClient.invalidateQueries({ queryKey: ["booking", id] });
      await queryClient.invalidateQueries({ queryKey: ["booking-observability", id] });
      await queryClient.invalidateQueries({ queryKey: ["bookings"] });
    }
  });

  if (booking.isLoading) return <div className="page"><Loading label="Carregando reserva..." /></div>;
  if (booking.isError || !booking.data) return <div className="page"><ErrorNotice message={friendlyError(booking.error)} /></div>;
  const value = booking.data;
  const canCancel = !["CANCELLED", "FAILED", "MANUAL_REVIEW", "COMPENSATING"].includes(value.status);
  const trace = observability.data;

  const refresh = async () => {
    await Promise.all([booking.refetch(), observability.refetch()]);
  };

  return <div className="page page-narrow">
    <PageHeader
      eyebrow={`Reserva #${value.id.slice(0, 8)}`}
      title="Acompanhamento da Saga"
      description={`Criada em ${formatDateTime(value.createdAt)}`}
      action={<button className="button button-secondary" onClick={() => void refresh()}>Atualizar</button>}
    />
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
        return <li className={done ? "done" : active ? "active" : ""} key={step}>
          <span>{done ? "✓" : index + 1}</span><div><strong>{label(step)}</strong><small>{step}</small></div>
        </li>;
      })}
    </ol>
    {value.failureCode && <ErrorNotice message={`Motivo: ${value.failureCode}`} />}
    {cancel.isSuccess && <SuccessNotice message="Cancelamento solicitado. A compensação será acompanhada automaticamente." />}
    {cancel.isError && <ErrorNotice message={friendlyError(cancel.error)} />}

    <ObservabilityPanel
      trace={trace}
      loading={observability.isLoading}
      error={observability.isError ? friendlyError(observability.error) : null}
      expanded={showCommunications}
      onToggle={() => setShowCommunications(current => !current)}
    />

    <section>
      <div className="section-heading"><div><p className="eyebrow">Itens</p><h2>Serviços da viagem</h2></div></div>
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

interface ObservabilityPanelProps {
  trace: BookingObservability | undefined;
  loading: boolean;
  error: string | null;
  expanded: boolean;
  onToggle: () => void;
}

function ObservabilityPanel({ trace, loading, error, expanded, onToggle }: ObservabilityPanelProps) {
  const available = Boolean(trace?.available && trace.primaryTraceId);
  return <section className="observability-card" aria-label="Observabilidade da reserva">
    <div className="observability-heading">
      <div><p className="eyebrow">Observabilidade</p><h2>Comunicação da reserva</h2></div>
      <div className="observability-actions">
        {available
          ? <a className="button button-secondary button-small" href={jaegerTraceUrl(trace!.primaryTraceId!)} target="_blank" rel="noreferrer">Abrir trace no Jaeger</a>
          : <button className="button button-secondary button-small" disabled title="O trace ainda não está disponível">Abrir trace no Jaeger</button>}
        <button className="button button-ghost button-small" onClick={onToggle} disabled={!trace?.available} aria-expanded={expanded}>
          {expanded ? "Ocultar comunicação" : "Ver comunicação entre serviços"}
        </button>
      </div>
    </div>

    {loading && <div className="observability-message"><span className="spinner" /> Consultando o Jaeger...</div>}
    {error && <p className="observability-message danger">Não foi possível consultar a observabilidade: {error}</p>}
    {trace && !trace.available && <p className="observability-message">
      {trace.unavailableReason ?? "O trace não está mais disponível."} O Jaeger local usa armazenamento efêmero e perde os traces quando reinicia.
    </p>}
    {trace?.available && <>
      <div className="observability-summary">
        <div><small>Tempo observado</small><strong>{formatDuration(trace.totalDurationMs)}</strong></div>
        <div><small>Serviços conectados</small><strong>{trace.observedServices.length}</strong></div>
        <div><small>Traces relacionados</small><strong>{trace.traceIds.length}</strong></div>
      </div>
      <div className={trace.complete ? "trace-completeness trace-complete" : "trace-completeness trace-partial"}>
        <strong>{trace.complete ? "Trace completo" : "Trace parcial"}</strong>
        <span>{trace.complete
          ? "Todos os serviços esperados da Saga apareceram no Jaeger."
          : `Serviços ainda não observados: ${trace.missingServices.join(", ") || "nenhum identificado"}.`}</span>
      </div>
      <div className="trace-services" aria-label="Serviços esperados no trace">
        {trace.expectedSagaServices.map(service =>
          <span className={trace.observedServices.includes(service) ? "trace-service observed" : "trace-service missing"}
            key={service}>{service}</span>)}
      </div>
      <div className="protocol-list" aria-label="Protocolos observados">
        {[...new Set(trace.communications.map(item => item.protocol))].map(protocol =>
          <span className={`protocol protocol-${protocol.toLowerCase()}`} key={protocol}>{protocol === "GRPC" ? "gRPC" : protocol}</span>)}
      </div>
      <SignalList signals={trace.signals} />
      {trace.stages.length > 0 && <div className="trace-stages">
        <h3>Tempo por etapa</h3>
        {trace.stages.map((stage, index) => <div className="trace-stage" key={`${stage.status}-${index}`}>
          <span className={stage.active ? "stage-dot active" : "stage-dot"} />
          <div><strong>{label(stage.status)}</strong><small>{stage.startedAt ? formatDateTime(stage.startedAt) : "Início não registrado"}</small></div>
          <b>{formatDuration(stage.durationMs)}</b>
        </div>)}
      </div>}
      {expanded && <div className="communication-panel">
        {trace.communications.length === 0
          ? <p>Nenhuma conexão foi reconhecida nos spans disponíveis.</p>
          : trace.communications.map((communication, index) => <article key={`${communication.source}-${communication.target}-${communication.protocol}-${index}`}>
            <div className="communication-route">
              <strong>{communication.source}</strong><span>→</span><strong>{communication.target}</strong>
            </div>
            <span className={`protocol protocol-${communication.protocol.toLowerCase()}`}>{communication.protocol === "GRPC" ? "gRPC" : communication.protocol}</span>
            <code>{communication.destination}</code>
            <small>{communication.count} chamada(s) · {formatDuration(communication.totalDurationMs)} · {communication.errorCount} erro(s)</small>
          </article>)}
      </div>}
      <div className="jaeger-links">
        <a href={jaegerDependenciesUrl()} target="_blank" rel="noreferrer">Ver grafo global de dependências no Jaeger ↗</a>
        {trace.traceIds.slice(1).map((traceId, index) => <a key={traceId} href={jaegerTraceUrl(traceId)} target="_blank" rel="noreferrer">Trace relacionado {index + 1} ↗</a>)}
      </div>
    </>}
  </section>;
}

function SignalList({ signals }: { signals: ObservabilitySignals }) {
  const values = [
    ["Retries", signals.retryCount, signals.retryCount > 0],
    ["Duplicações", signals.duplicateCount, signals.duplicateCount > 0],
    ["DLQ", signals.dlqCount, signals.dlqCount > 0],
    ["Spans com falha", signals.failedSpanCount, signals.failedSpanCount > 0],
    ["Compensação", signals.compensationStarted ? "iniciada" : "não", signals.compensationStarted],
    ["Reembolso", signals.refundRequested ? "solicitado" : "não", signals.refundRequested]
  ] as const;
  return <div className="signal-list">
    {values.map(([name, value, alert]) => <span className={alert ? "signal signal-alert" : "signal"} key={name}><small>{name}</small><strong>{value}</strong></span>)}
    {signals.notificationStatus && <span className="signal"><small>Notificação</small><strong>{signals.notificationStatus}</strong></span>}
  </div>;
}

function label(value: string): string {
  return ({ RESERVING: "Bloqueio dos recursos", PAYMENT_PENDING: "Pagamento", CONFIRMING: "Confirmação", CONFIRMED: "Concluída", COMPENSATING: "Compensação", CANCELLED: "Cancelada", FAILED: "Falhou", MANUAL_REVIEW: "Revisão manual", FLIGHT: "Voo", HOTEL: "Hotel", TRANSPORT: "Transporte" } as Record<string, string>)[value] ?? value;
}
