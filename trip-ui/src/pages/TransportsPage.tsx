import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { tripApi } from "../api";
import { useDraft } from "../draft";
import { friendlyError, localDateTimeToIso } from "../format";
import { ErrorNotice, Loading, MoneyText, PageHeader, SuccessNotice } from "../components/Ui";
import type { Transport } from "../types";

interface Search { type: string; startsAt: string; endsAt: string }

export function TransportsPage() {
  const { addItem, items } = useDraft();
  const [type, setType] = useState("TRANSFER");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [search, setSearch] = useState<Search | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const result = useQuery({
    queryKey: ["transports", search],
    queryFn: () => tripApi.searchTransports(search!.type, search!.startsAt, search!.endsAt),
    enabled: search !== null
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (endsAt <= startsAt) { setNotice("O término precisa ocorrer depois do início."); return; }
    setNotice(null);
    setSearch({ type, startsAt: localDateTimeToIso(startsAt), endsAt: localDateTimeToIso(endsAt) });
  }

  function select(value: Transport) {
    const added = addItem({
      id: `TRANSPORT:${value.id}:${search!.startsAt}:${search!.endsAt}`,
      label: `${value.transportType} · ${value.providerName}`,
      detail: `${new Date(search!.startsAt).toLocaleString("pt-BR")} até ${new Date(search!.endsAt).toLocaleString("pt-BR")}`,
      price: value.price,
      request: { type: "TRANSPORT", resourceId: value.id, startsAt: search!.startsAt, endsAt: search!.endsAt }
    });
    setNotice(added ? "Transporte adicionado ao rascunho." : "A moeda deste transporte é diferente dos outros itens do rascunho.");
  }

  return <div className="page">
    <PageHeader eyebrow="Catálogo" title="Transportes" description="Escolha o tipo e o período de utilização."
      action={items.length > 0 && <Link className="button button-secondary" to="/bookings/new">Ver rascunho ({items.length})</Link>} />
    <form className="search-panel" onSubmit={submit}>
      <label>Tipo<select value={type} onChange={event => setType(event.target.value)}><option>TRANSFER</option><option>CAR_RENTAL</option><option>SHUTTLE</option></select></label>
      <label>Início<input required type="datetime-local" value={startsAt} onChange={event => setStartsAt(event.target.value)} /></label>
      <label>Término<input required type="datetime-local" value={endsAt} onChange={event => setEndsAt(event.target.value)} /></label>
      <button className="button" type="submit">Buscar transportes</button>
    </form>
    {notice && (notice.includes("adicionado") ? <SuccessNotice message={notice} /> : <ErrorNotice message={notice} />)}
    {result.isLoading && <Loading label="Consultando transportes..." />}
    {result.isError && <ErrorNotice message={friendlyError(result.error)} />}
    <section className="catalog-grid">{result.data?.items.map(value => <article className={`catalog-card ${!value.available ? "unavailable" : ""}`} key={value.id}>
      <div className="catalog-card-top"><span className="catalog-icon">⌁</span><span className="muted">{value.transportType}</span></div>
      <h2>{value.providerName}</h2><p>{details(value.vehicleDetailsJson)}</p><div className="price-row"><MoneyText value={value.price} /><small>pelo período</small></div>
      <button disabled={!value.available} className="button button-full" onClick={() => select(value)}>{value.available ? "Adicionar ao rascunho" : "Indisponível"}</button>
    </article>)}</section>
  </div>;
}

function details(json: string): string {
  try { return Object.values(JSON.parse(json) as Record<string, unknown>).join(" · "); }
  catch { return json; }
}
