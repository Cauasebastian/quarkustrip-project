import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { tripApi } from "../api";
import { useDraft } from "../draft";
import { formatMoney, friendlyError } from "../format";
import type { TravelPackage } from "../types";
import { Empty, ErrorNotice, Loading, PageHeader, SuccessNotice } from "../components/Ui";

export function PackagesPage() {
  const { addItems } = useDraft();
  const [added, setAdded] = useState<string | null>(null);
  const [currencyError, setCurrencyError] = useState(false);
  const packages = useQuery({
    queryKey: ["packages", 0, 20],
    queryFn: () => tripApi.listPackages(0, 20)
  });

  function add(value: TravelPackage) {
    const accepted = addItems(value.items.map(item => ({
      id: `package:${value.id}:${item.id}`,
      label: item.label,
      detail: item.detail,
      price: item.displayPrice,
      request: item.item
    })));
    setCurrencyError(!accepted);
    if (accepted) setAdded(value.id);
  }

  return (
    <div className="page">
      <PageHeader eyebrow="Ofertas da companhia" title="Pacotes de viagem"
        description="Combine voo, hospedagem e transporte em uma única seleção." />
      {packages.isLoading && <Loading label="Buscando pacotes..." />}
      {packages.isError && <ErrorNotice message={friendlyError(packages.error)} />}
      {packages.data?.items.length === 0 && <Empty title="Nenhum pacote disponível"
        description="A companhia ainda não publicou pacotes." />}
      {currencyError && <ErrorNotice message="O rascunho atual usa outra moeda. Limpe-o antes de adicionar este pacote." />}
      {added && <SuccessNotice message="Pacote adicionado ao rascunho." />}
      <section className="package-cards">
        {packages.data?.items.map(value => {
          const total = value.items.reduce((sum, item) => sum + item.displayPrice.amountMinor, 0);
          return <article className="package-card" key={value.id}>
            <div className="package-card-banner"><span>Trip</span><strong>{value.items.length} serviços</strong></div>
            <div className="package-card-body">
              <h2>{value.name}</h2>
              <p>{value.description}</p>
              <ul>{value.items.map(item => <li key={item.id}><span>{item.type}</span>{item.label}</li>)}</ul>
              <div className="package-card-footer">
                <span><small>Total estimado</small><strong>{formatMoney({ currency: value.currency, amountMinor: total })}</strong></span>
                <button className="button" onClick={() => add(value)}>Adicionar pacote</button>
              </div>
              {added === value.id && <Link className="text-link" to="/bookings/new">Revisar rascunho</Link>}
            </div>
          </article>;
        })}
      </section>
    </div>
  );
}
