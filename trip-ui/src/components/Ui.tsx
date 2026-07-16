import { Link } from "react-router-dom";
import { formatMoney } from "../format";
import type { BookingStatus, Money } from "../types";

const statusLabels: Record<string, string> = {
  RESERVING: "Reservando",
  PAYMENT_PENDING: "Aguardando pagamento",
  CONFIRMING: "Confirmando",
  CONFIRMED: "Confirmada",
  COMPENSATING: "Desfazendo reserva",
  CANCELLED: "Cancelada",
  FAILED: "Falhou",
  MANUAL_REVIEW: "Revisão manual",
  PENDING: "Pendente",
  HELD: "Bloqueado",
  ITEM_CONFIRMED: "Confirmado",
  ITEM_FAILED: "Falhou",
  ITEM_CANCELLED: "Cancelado"
};

export function StatusBadge({ status }: { status: BookingStatus | string }) {
  return <span className={`status status-${status.toLowerCase()}`}>{statusLabels[status] ?? status}</span>;
}

export function MoneyText({ value }: { value: Money }) {
  return <span className="money">{formatMoney(value)}</span>;
}

export function PageHeader({ eyebrow, title, description, action }: {
  eyebrow?: string; title: string; description?: string; action?: React.ReactNode;
}) {
  return (
    <div className="page-header">
      <div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1>{description && <p>{description}</p>}</div>
      {action && <div>{action}</div>}
    </div>
  );
}

export function Loading({ label = "Carregando..." }: { label?: string }) {
  return <div className="state-card"><span className="spinner" aria-hidden="true" /><p>{label}</p></div>;
}

export function Empty({ title, description, actionTo, actionLabel }: {
  title: string; description: string; actionTo?: string; actionLabel?: string;
}) {
  return <div className="state-card"><div className="state-icon">◇</div><h2>{title}</h2><p>{description}</p>{actionTo && <Link className="button" to={actionTo}>{actionLabel}</Link>}</div>;
}

export function ErrorNotice({ message }: { message: string }) {
  return <div className="notice notice-error" role="alert"><strong>Não foi possível concluir.</strong><span>{message}</span></div>;
}

export function SuccessNotice({ message }: { message: string }) {
  return <div className="notice notice-success" role="status"><strong>Pronto.</strong><span>{message}</span></div>;
}
