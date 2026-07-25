import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { tripApi } from "../api";
import { friendlyError } from "../format";
import { ErrorNotice, Loading, PageHeader, SuccessNotice } from "../components/Ui";
import { useAuth } from "../auth";
import type { Profile } from "../types";

type ProfileForm = {
  email: string;
  firstName: string;
  lastName: string;
  language: string;
  seatPreference: string;
  travelNotes: string;
  needsAssistance: boolean;
};

const emptyForm: ProfileForm = {
  email: "",
  firstName: "",
  lastName: "",
  language: "pt-BR",
  seatPreference: "",
  travelNotes: "",
  needsAssistance: false
};

export function ProfilePage() {
  const { token } = useAuth();
  const tokenEmail = claim(token?.email);
  const tokenFirstName = claim(token?.given_name);
  const tokenLastName = claim(token?.family_name);
  const tokenUsername = claim(token?.preferred_username);
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ["profile"], queryFn: tripApi.getProfile, retry: false });
  const [form, setForm] = useState<ProfileForm>(emptyForm);

  useEffect(() => {
    if (profile.data) {
      setForm(toForm(profile.data));
    } else if (profile.data === null) {
      setForm({
        ...emptyForm,
        email: tokenEmail,
        firstName: tokenFirstName,
        lastName: tokenLastName
      });
    }
  }, [profile.data, tokenEmail, tokenFirstName, tokenLastName]);

  const save = useMutation({
    mutationFn: () => tripApi.updateProfile({
      email: form.email,
      firstName: form.firstName,
      lastName: form.lastName,
      preferencesJson: JSON.stringify({
        language: form.language,
        seatPreference: form.seatPreference,
        travelNotes: form.travelNotes,
        needsAssistance: form.needsAssistance
      })
    }),
    onSuccess: value => {
      setForm(toForm(value));
      queryClient.setQueryData(["profile"], value);
    }
  });

  const username = profile.data?.username || tokenUsername || "viajante";
  const completedFields = useMemo(() =>
    [form.firstName, form.lastName, form.email].filter(value => value.trim()).length, [form]);
  const completion = Math.round((completedFields / 3) * 100);

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate();
  }

  if (profile.isLoading) {
    return <div className="page"><Loading label="Carregando perfil..." /></div>;
  }

  return (
    <div className="page profile-page">
      <PageHeader
        eyebrow="Sua conta"
        title="Perfil do viajante"
        description="Mantenha seus dados atualizados para que companhias e operadores possam localizar você e montar uma reserva assistida."
      />
      {profile.isError && <ErrorNotice message={friendlyError(profile.error)} />}

      <div className="profile-layout">
        <aside className="profile-summary-card">
          <span className="profile-avatar">{initials(form, username)}</span>
          <p className="eyebrow">Identificação na plataforma</p>
          <h2>{displayName(form, username)}</h2>
          <strong className="profile-username">@{username}</strong>
          <p>Este é o nome que o operador usa para encontrar seu perfil.</p>

          <div className="profile-completion">
            <div><span>Perfil preenchido</span><strong>{completion}%</strong></div>
            <span className="profile-progress"><i style={{ width: `${completion}%` }} /></span>
          </div>
          <div className="profile-visibility">
            <span aria-hidden="true">✓</span>
            <p><strong>Pronto para reservas assistidas</strong>
              <small>Nome, username e e-mail ficam disponíveis somente para operadores autorizados.</small></p>
          </div>
        </aside>

        <form className="profile-form-card" onSubmit={submit}>
          <section>
            <div className="profile-section-heading">
              <span>1</span>
              <div><h2>Dados pessoais</h2><p>Informações usadas para identificar o passageiro.</p></div>
            </div>
            <label>Nome de usuário
              <div className="readonly-field">@{username}</div>
              <small>O nome de usuário é definido na autenticação e não pode ser alterado aqui.</small>
            </label>
            <div className="form-grid two-columns">
              <label>Nome<input required value={form.firstName}
                onChange={event => setForm({ ...form, firstName: event.target.value })} /></label>
              <label>Sobrenome<input required value={form.lastName}
                onChange={event => setForm({ ...form, lastName: event.target.value })} /></label>
            </div>
            <label>E-mail<input required type="email" value={form.email}
              onChange={event => setForm({ ...form, email: event.target.value })} /></label>
          </section>

          <section>
            <div className="profile-section-heading">
              <span>2</span>
              <div><h2>Preferências de viagem</h2><p>Opcional. Ajuda o operador a preparar opções mais adequadas.</p></div>
            </div>
            <div className="form-grid two-columns">
              <label>Idioma
                <select value={form.language}
                  onChange={event => setForm({ ...form, language: event.target.value })}>
                  <option value="pt-BR">Português (Brasil)</option>
                  <option value="en">English</option>
                  <option value="es">Español</option>
                </select>
              </label>
              <label>Preferência de assento
                <select value={form.seatPreference}
                  onChange={event => setForm({ ...form, seatPreference: event.target.value })}>
                  <option value="">Sem preferência</option>
                  <option value="WINDOW">Janela</option>
                  <option value="AISLE">Corredor</option>
                </select>
              </label>
            </div>
            <label>Observações para a viagem
              <textarea rows={4} maxLength={500} value={form.travelNotes}
                onChange={event => setForm({ ...form, travelNotes: event.target.value })}
                placeholder="Ex.: viajo com criança, prefiro quartos silenciosos..." />
              <small>{form.travelNotes.length}/500 caracteres</small>
            </label>
            <label className="profile-checkbox">
              <input type="checkbox" checked={form.needsAssistance}
                onChange={event => setForm({ ...form, needsAssistance: event.target.checked })} />
              <span><strong>Preciso de assistência durante a viagem</strong>
                <small>O operador verá este aviso ao montar sua reserva.</small></span>
            </label>
          </section>

          {save.isSuccess && <SuccessNotice message="Perfil atualizado. Operadores já podem localizar você pelo username." />}
          {save.isError && <ErrorNotice message={friendlyError(save.error)} />}
          <div className="profile-form-actions">
            <p>Credenciais e senha continuam protegidas pelo Keycloak.</p>
            <button className="button" disabled={save.isPending}>
              {save.isPending ? "Salvando..." : "Salvar alterações"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function claim(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function toForm(profile: Profile): ProfileForm {
  const preferences = parsePreferences(profile.preferencesJson);
  return {
    email: profile.email,
    firstName: profile.firstName,
    lastName: profile.lastName,
    language: stringPreference(preferences.language, "pt-BR"),
    seatPreference: stringPreference(preferences.seatPreference, ""),
    travelNotes: stringPreference(preferences.travelNotes, ""),
    needsAssistance: preferences.needsAssistance === true
  };
}

function parsePreferences(value: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(value || "{}");
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

function stringPreference(value: unknown, fallback: string): string {
  return typeof value === "string" ? value : fallback;
}

function displayName(form: ProfileForm, username: string): string {
  return `${form.firstName} ${form.lastName}`.trim() || username;
}

function initials(form: ProfileForm, username: string): string {
  return `${form.firstName[0] ?? ""}${form.lastName[0] ?? ""}`.toUpperCase()
    || username.slice(0, 2).toUpperCase();
}
