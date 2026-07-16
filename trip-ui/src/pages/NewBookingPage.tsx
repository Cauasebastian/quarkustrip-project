import { useMutation } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { useState } from "react";
import { tripApi } from "../api";
import { useDraft } from "../draft";
import { friendlyError, formatMoney } from "../format";
import { Empty, ErrorNotice, PageHeader } from "../components/Ui";

const ATTEMPT_KEY = "trip.booking.attempt";

interface StoredAttempt { signature: string; idempotencyKey: string }

export function NewBookingPage() {
  const navigate = useNavigate();
  const { items, currency, removeItem, clear } = useDraft();
  const [paymentMethodRef, setPaymentMethodRef] = useState("pm_test_success");
  const mutation = useMutation({
    mutationFn: async () => {
      const body = { currency: currency!, paymentMethodRef, items: items.map(item => item.request) };
      const signature = JSON.stringify(body);
      const attempt = getAttempt(signature);
      return tripApi.createBooking(body, attempt.idempotencyKey);
    },
    onSuccess: result => {
      sessionStorage.removeItem(ATTEMPT_KEY);
      clear();
      navigate(`/bookings/${result.bookingId}`);
    }
  });

  const baseTotal = items.reduce((total, item) => total + item.price.amountMinor, 0);

  return <div className="page page-narrow">
    <PageHeader eyebrow="Reserva" title="Revise seu rascunho" description="Os recursos serão bloqueados antes do pagamento e confirmados somente após a aprovação." />
    {items.length === 0 ? <Empty title="Seu rascunho está vazio" description="Adicione um voo, quarto ou transporte antes de continuar." actionTo="/catalog/flights" actionLabel="Explorar catálogo" /> : <>
      <section className="draft-list">
        {items.map(item => <article className="draft-item" key={item.id}>
          <span className="item-kind">{icon(item.request.type)}</span>
          <div><strong>{item.label}</strong><p>{item.detail}</p><small>{item.request.type}</small></div>
          <strong>{formatMoney(item.price)}</strong>
          <button className="icon-button" aria-label={`Remover ${item.label}`} onClick={() => removeItem(item.id)}>×</button>
        </article>)}
      </section>
      <section className="checkout-panel">
        <div className="checkout-copy"><p className="eyebrow">Pagamento de teste</p><h2>Como a Saga deve responder?</h2><p>Use os cenários determinísticos para testar sucesso, recusa ou falha de reembolso.</p></div>
        <label>Método de pagamento<select value={paymentMethodRef} onChange={event => setPaymentMethodRef(event.target.value)}>
          <option value="pm_test_success">pm_test_success — aprovar</option>
          <option value="pm_test_failure">pm_test_failure — recusar</option>
          <option value="pm_test_refund_failure">pm_test_refund_failure — falhar no reembolso</option>
        </select></label>
        <div className="checkout-total"><span>Preço base dos itens</span><strong>{formatMoney({ currency: currency!, amountMinor: baseTotal })}</strong><small>O total definitivo, incluindo as noites do hotel, é calculado pelos serviços.</small></div>
        {mutation.isError && <ErrorNotice message={friendlyError(mutation.error)} />}
        <button data-testid="submit-booking" disabled={mutation.isPending} className="button button-full button-large" onClick={() => mutation.mutate()}>
          {mutation.isPending ? "Criando reserva..." : "Confirmar e iniciar Saga"}
        </button>
        <Link className="text-link" to="/catalog/flights">Continuar adicionando itens</Link>
      </section>
    </>}
  </div>;
}

function getAttempt(signature: string): StoredAttempt {
  try {
    const current = JSON.parse(sessionStorage.getItem(ATTEMPT_KEY) ?? "null") as StoredAttempt | null;
    if (current?.signature === signature) return current;
  } catch { /* create a new attempt below */ }
  const attempt = { signature, idempotencyKey: crypto.randomUUID() };
  sessionStorage.setItem(ATTEMPT_KEY, JSON.stringify(attempt));
  return attempt;
}

function icon(type: string) {
  return type === "FLIGHT" ? "✈" : type === "HOTEL" ? "⌂" : "⌁";
}
