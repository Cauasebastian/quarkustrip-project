import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { tripApi } from "../api";
import { friendlyError } from "../format";
import { ErrorNotice, Loading, PageHeader, SuccessNotice } from "../components/Ui";
import { useAuth } from "../auth";

export function ProfilePage() {
  const { token } = useAuth();
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ["profile"], queryFn: tripApi.getProfile, retry: false });
  const [form, setForm] = useState({ email: "", firstName: "", lastName: "", preferencesJson: "{}" });
  useEffect(() => {
    if (profile.data) setForm({ email: profile.data.email, firstName: profile.data.firstName,
      lastName: profile.data.lastName, preferencesJson: profile.data.preferencesJson || "{}" });
    else if (profile.data === null) setForm({
      email: typeof token?.email === "string" ? token.email : "",
      firstName: typeof token?.given_name === "string" ? token.given_name : "",
      lastName: typeof token?.family_name === "string" ? token.family_name : "",
      preferencesJson: "{}"
    });
  }, [profile.data, token]);
  const save = useMutation({
    mutationFn: () => tripApi.updateProfile(form),
    onSuccess: async value => { setForm(value); await queryClient.invalidateQueries({ queryKey: ["profile"] }); }
  });

  function submit(event: FormEvent) { event.preventDefault(); save.mutate(); }
  if (profile.isLoading) return <div className="page"><Loading label="Carregando perfil..." /></div>;

  return <div className="page page-narrow">
    <PageHeader eyebrow="Conta" title="Seu perfil" description="Credenciais e roles permanecem no Keycloak; aqui ficam apenas perfil e preferências." />
    {profile.isError && <ErrorNotice message={friendlyError(profile.error)} />}
    <form className="form-card" onSubmit={submit}>
      <div className="form-grid two-columns">
        <label>Nome<input value={form.firstName} onChange={event => setForm({ ...form, firstName: event.target.value })} /></label>
        <label>Sobrenome<input value={form.lastName} onChange={event => setForm({ ...form, lastName: event.target.value })} /></label>
      </div>
      <label>Email<input required type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} /></label>
      <label>Preferências em JSON<textarea rows={7} value={form.preferencesJson} onChange={event => setForm({ ...form, preferencesJson: event.target.value })} /></label>
      {save.isSuccess && <SuccessNotice message="Perfil salvo." />}
      {save.isError && <ErrorNotice message={friendlyError(save.error)} />}
      <button className="button" disabled={save.isPending}>{save.isPending ? "Salvando..." : "Salvar perfil"}</button>
    </form>
  </div>;
}
