import { expect, test, type Page } from "@playwright/test";

test.describe.configure({ mode: "serial" });

const suffix = Date.now().toString().slice(-6);
const flightNumber = `UI${suffix}`;

test("admin cadastra catálogos", async ({ page }) => {
  await login(page, "admin", "admin");
  await page.goto("/admin");
  await page.getByLabel("Número").fill(flightNumber);
  await page.getByLabel("Origem").fill("FOR");
  await page.getByLabel("Destino").fill("GRU");
  await page.getByLabel("Partida").fill(localInput(24));
  await page.getByLabel("Chegada").fill(localInput(27));
  await page.getByRole("button", { name: "Cadastrar" }).click();
  await expect(page.getByText("Item criado no catálogo.")).toBeVisible();

  await page.getByRole("tab", { name: "Hotéis" }).click();
  await page.getByLabel("Nome").fill(`Hotel UI ${suffix}`);
  await page.getByRole("button", { name: "Cadastrar" }).click();
  await expect(page.getByText("Item criado no catálogo.")).toBeVisible();
  const hotelResult = JSON.parse(await page.locator(".result-json").textContent() ?? "{}") as { id: string };

  await page.getByRole("tab", { name: "Quartos" }).click();
  await page.getByLabel("ID do hotel").fill(hotelResult.id);
  await page.getByRole("button", { name: "Cadastrar" }).click();
  await expect(page.getByText("Item criado no catálogo.")).toBeVisible();

  await page.getByRole("tab", { name: "Transportes" }).click();
  await page.getByRole("button", { name: "Cadastrar" }).click();
  await expect(page.getByText("Item criado no catálogo.")).toBeVisible();
});

test("usuário cria, acompanha e cancela uma reserva", async ({ page }) => {
  await login(page, "demo", "demo");
  await expect(page.getByRole("link", { name: "Admin" })).toHaveCount(0);
  await page.goto("/catalog/flights");
  await page.getByLabel("Origem").fill("FOR");
  await page.getByLabel("Destino").fill("GRU");
  await page.getByRole("button", { name: "Buscar voos" }).click();
  await expect(page.getByText(flightNumber)).toBeVisible();
  await page.locator(".seat").first().click();
  await page.goto("/bookings/new");
  await page.getByTestId("submit-booking").click();
  await expect(page).toHaveURL(/\/bookings\/[0-9a-f-]+/);
  await expect(page.getByText("Confirmada", { exact: true })).toBeVisible({ timeout: 90_000 });
  await page.getByTestId("cancel-booking").click();
  await expect(page.getByText("Cancelada", { exact: true })).toBeVisible({ timeout: 90_000 });
});

test("pagamento recusado termina em falha", async ({ page }) => {
  await login(page, "demo", "demo");
  await page.goto("/catalog/flights");
  await page.getByRole("button", { name: "Buscar voos" }).click();
  await page.locator(".seat").first().click();
  await page.goto("/bookings/new");
  await page.getByLabel("Método de pagamento").selectOption("pm_test_failure");
  await page.getByTestId("submit-booking").click();
  await expect(page.getByText("Falhou", { exact: true })).toBeVisible({ timeout: 90_000 });
});

async function login(page: Page, username: string, password: string) {
  await page.goto("/");
  if (await page.getByTestId("login-button").isVisible().catch(() => false)) {
    await page.getByTestId("login-button").click();
    await page.locator("#username").fill(username);
    await page.locator("#password").fill(password);
    await page.locator("#kc-login").click();
  }
  await expect(page).toHaveURL(/localhost:3000/);
}

function localInput(hoursAhead: number): string {
  const date = new Date(Date.now() + hoursAhead * 3_600_000);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
