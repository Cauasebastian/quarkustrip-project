import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { tripApi } from "../api";
import { useAuth } from "../auth";
import { useDraft } from "../draft";
import { friendlyError, localDateTimeToIso } from "../format";
import { Empty, ErrorNotice, Loading, MoneyText, PageHeader, SuccessNotice } from "../components/Ui";
import type { Transport } from "../types";

interface Search {
  type: string;
  startsAt: string;
  endsAt: string;
}

const initialSearch: Search = { type: "CAR_RENTAL", startsAt: "", endsAt: "" };

export function TransportsPage() {
  const { addItem, items } = useDraft();
  const { isOperator } = useAuth();
  const [type, setType] = useState("CAR_RENTAL");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [search, setSearch] = useState<Search>(initialSearch);
  const [notice, setNotice] = useState<string | null>(null);

  const result = useQuery({
    queryKey: ["transports", search],
    queryFn: () => tripApi.searchTransports(search.type, search.startsAt, search.endsAt)
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if ((startsAt && !endsAt) || (!startsAt && endsAt)) {
      setNotice("Preencha início e término juntos ou deixe os dois horários vazios.");
      return;
    }
    if (startsAt && endsAt <= startsAt) {
      setNotice("O término precisa ocorrer depois do início.");
      return;
    }
    setNotice(null);
    setSearch({
      type,
      startsAt: startsAt ? localDateTimeToIso(startsAt) : "",
      endsAt: endsAt ? localDateTimeToIso(endsAt) : ""
    });
  }

  function select(value: Transport) {
    if (!result.data) return;
    const { startsAt: resolvedStart, endsAt: resolvedEnd } = result.data;
    const added = addItem({
      id: `TRANSPORT:${value.id}:${resolvedStart}:${resolvedEnd}`,
      label: `${transportLabel(value.transportType)} · ${value.providerName}`,
      detail: `${formatDateTime(resolvedStart)} até ${formatDateTime(resolvedEnd)}`,
      price: value.price,
      request: {
        type: "TRANSPORT",
        resourceId: value.id,
        startsAt: resolvedStart,
        endsAt: resolvedEnd
      }
    });
    setNotice(added
      ? "Transporte adicionado ao rascunho."
      : "A moeda deste transporte é diferente dos outros itens do rascunho.");
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Catálogo"
        title="Carros e transportes"
        description="Os carros disponíveis para os próximos dias aparecem automaticamente. Informe horários apenas quando precisar de outro período."
        action={items.length > 0 && (
          <Link className="button button-secondary" to={isOperator ? "/operator" : "/bookings/new"}>
            {isOperator ? "Voltar ao operador" : "Ver rascunho"} ({items.length})
          </Link>
        )}
      />

      <form className="search-panel transport-search-panel" onSubmit={submit}>
        <label>
          Tipo
          <select value={type} onChange={event => setType(event.target.value)}>
            <option value="CAR_RENTAL">Carro</option>
            <option value="TRANSFER">Transfer</option>
            <option value="SHUTTLE">Van compartilhada</option>
            <option value="">Todos</option>
          </select>
        </label>
        <label>
          Início <span className="field-optional">Opcional</span>
          <input type="datetime-local" value={startsAt} onChange={event => setStartsAt(event.target.value)} />
        </label>
        <label>
          Término <span className="field-optional">Opcional</span>
          <input type="datetime-local" value={endsAt} onChange={event => setEndsAt(event.target.value)} />
        </label>
        <button className="button" type="submit">Atualizar busca</button>
      </form>

      {notice && (notice.includes("adicionado")
        ? <SuccessNotice message={notice} />
        : <ErrorNotice message={notice} />)}
      {result.isLoading && <Loading label="Verificando carros para os próximos dias..." />}
      {result.isError && <ErrorNotice message={friendlyError(result.error)} />}

      {result.data && (
        <div className="availability-summary" role="status">
          <span aria-hidden="true">✓</span>
          <div>
            <strong>{result.data.defaultPeriod ? "Período sugerido" : "Período selecionado"}</strong>
            <small>{formatDateTime(result.data.startsAt)} até {formatDateTime(result.data.endsAt)}</small>
          </div>
          <small>{result.data.items.filter(value => value.available).length} de {result.data.items.length} opções disponíveis</small>
        </div>
      )}

      {result.data?.items.length === 0 && (
        <Empty
          title="Nenhum transporte encontrado"
          description="Tente selecionar “Todos” para visualizar as outras opções cadastradas."
        />
      )}

      <section className="catalog-grid">
        {result.data?.items.map(value => (
          <article className={`catalog-card ${!value.available ? "unavailable" : ""}`} key={value.id}>
            <div className="catalog-card-top">
              <span className="catalog-icon">⌁</span>
              <span className="muted">{transportLabel(value.transportType)}</span>
            </div>
            <span className={`availability-badge ${value.available ? "available" : "unavailable"}`}>
              {value.available ? "Disponível nos próximos dias" : "Ocupado no período"}
            </span>
            <h2>{value.providerName}</h2>
            <p>{details(value.vehicleDetailsJson)}</p>
            <div className="price-row"><MoneyText value={value.price} /><small>pelo período</small></div>
            <button disabled={!value.available} className="button button-full" onClick={() => select(value)}>
              {value.available ? "Adicionar ao rascunho" : "Indisponível"}
            </button>
          </article>
        ))}
      </section>
    </div>
  );
}

function details(json: string): string {
  try {
    return Object.values(JSON.parse(json) as Record<string, unknown>).join(" · ");
  } catch {
    return json;
  }
}

function transportLabel(value: string): string {
  return {
    CAR_RENTAL: "Carro",
    TRANSFER: "Transfer",
    SHUTTLE: "Van compartilhada"
  }[value] ?? value;
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}
