import assert from "node:assert/strict";
import test from "node:test";
import { call, createCustomer, DEVICE_B, makeApp, verifyCall } from "./helpers.ts";

const DAY = 86_400_000;

test("admin endpoints reject a missing or wrong token", async () => {
  const app = await makeApp();
  const anonymous = await call(app, "GET", "/admin/licenses", undefined, { token: null });
  assert.equal(anonymous.status, 401);
  const wrong = await call(app, "GET", "/admin/licenses", undefined, { token: "nope" });
  assert.equal(wrong.status, 401);
  const ok = await call(app, "GET", "/admin/licenses");
  assert.equal(ok.status, 200);
});

test("list, search and status filters", async () => {
  const app = await makeApp();
  const amina = await createCustomer(app);
  await createCustomer(app, { name: "Bilal Ahmed", deviceCode: DEVICE_B, durationDays: null });

  const all = await call(app, "GET", "/admin/licenses");
  assert.equal(all.json.licenses.length, 2);

  const search = await call(app, "GET", "/admin/licenses?search=Bilal");
  assert.equal(search.json.licenses.length, 1);
  assert.equal(search.json.licenses[0].name, "Bilal Ahmed");

  const byKey = await call(app, "GET", `/admin/licenses?search=${amina.key}`);
  assert.equal(byKey.json.licenses.length, 1);

  const pending = await call(app, "GET", "/admin/licenses?status=pending");
  assert.equal(pending.json.licenses.length, 2);

  await verifyCall(app, { key: amina.key });
  const active = await call(app, "GET", "/admin/licenses?status=active");
  assert.equal(active.json.licenses.length, 1);
  assert.equal(active.json.licenses[0].id, amina.id);
});

test("display status covers expired, blocked and cancelled", async () => {
  const app = await makeApp();
  const a = await createCustomer(app, { durationDays: 1 });
  const b = await createCustomer(app, { name: "B", deviceCode: DEVICE_B });
  await verifyCall(app, { key: a.key });
  await call(app, "POST", `/admin/licenses/${b.id}/block`);

  app.advance(2 * DAY);
  const expired = await call(app, "GET", "/admin/licenses?status=expired");
  assert.equal(expired.json.licenses.length, 1);
  assert.equal(expired.json.licenses[0].id, a.id);
  const blocked = await call(app, "GET", "/admin/licenses?status=blocked");
  assert.equal(blocked.json.licenses.length, 1);

  await call(app, "POST", `/admin/licenses/${b.id}/cancel`);
  const cancelled = await call(app, "GET", "/admin/licenses?status=cancelled");
  assert.equal(cancelled.json.licenses.length, 1);
});

test("rename and notes are recorded", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  const renamed = await call(app, "PATCH", `/admin/licenses/${license.id}`, {
    name: "Amina K.",
    note: "paid cash",
  });
  assert.equal(renamed.json.license.name, "Amina K.");
  assert.equal(renamed.json.license.note, "paid cash");

  const detail = await call(app, "GET", `/admin/licenses/${license.id}`);
  assert.ok(detail.json.events.some((event: any) => event.type === "name_changed"));

  const empty = await call(app, "PATCH", `/admin/licenses/${license.id}`, { name: "  " });
  assert.equal(empty.status, 400);
});

test("delete only works for cancelled licenses", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  const refused = await call(app, "DELETE", `/admin/licenses/${license.id}`);
  assert.equal(refused.status, 409);
  assert.equal(refused.json.error, "NOT_CANCELLED");

  await call(app, "POST", `/admin/licenses/${license.id}/cancel`);
  const deleted = await call(app, "DELETE", `/admin/licenses/${license.id}`);
  assert.equal(deleted.status, 200);
  const gone = await call(app, "GET", `/admin/licenses/${license.id}`);
  assert.equal(gone.status, 404);
});

test("stats count every state and recent activity", async () => {
  const app = await makeApp();
  const a = await createCustomer(app);
  const b = await createCustomer(app, { name: "B", deviceCode: DEVICE_B });
  await verifyCall(app, { key: a.key });
  await call(app, "POST", `/admin/licenses/${b.id}/block`);

  const stats = await call(app, "GET", "/admin/stats");
  assert.equal(stats.json.total, 2);
  assert.equal(stats.json.active, 1);
  assert.equal(stats.json.blocked, 1);
  assert.equal(stats.json.pending, 0);
  assert.equal(stats.json.seenRecently, 1);
  assert.equal(stats.json.expiringSoon, 0, "a 30 day license is not expiring this week");
});

test("stats flag licenses that expire within 7 days", async () => {
  const app = await makeApp();
  const soon = await createCustomer(app, { durationDays: 3 });
  await verifyCall(app, { key: soon.key });
  const stats = await call(app, "GET", "/admin/stats");
  assert.equal(stats.json.expiringSoon, 1);

  app.advance(8 * DAY);
  const later = await call(app, "GET", "/admin/stats");
  assert.equal(later.json.expired, 1);
  assert.equal(later.json.expiringSoon, 0);
});

test("CSV export includes a header and one row per license", async () => {
  const app = await makeApp();
  await createCustomer(app, { name: "Name, with comma", note: "said \"hi\"" });
  const csv = await call(app, "GET", "/admin/export.csv");
  assert.equal(csv.status, 200);
  const lines = csv.text.trim().split("\n");
  assert.equal(lines.length, 2);
  assert.match(lines[0], /^id,name,key,status,plan,device_code/);
  assert.match(csv.text, /"Name, with comma"/);
  assert.match(csv.text, /"said ""hi"""/);
});

test("the day time zone setting is validated and stored", async () => {
  const app = await makeApp();
  const bad = await call(app, "PUT", "/admin/settings", { quotaTz: "Mars/Olympus" });
  assert.equal(bad.status, 400);
  assert.equal(bad.json.error, "INVALID_TIME_ZONE");

  const good = await call(app, "PUT", "/admin/settings", { quotaTz: "Europe/Paris" });
  assert.equal(good.status, 200);
  const read = await call(app, "GET", "/admin/settings");
  assert.equal(read.json.quotaTz, "Europe/Paris");
});

test("health check needs no token", async () => {
  const app = await makeApp();
  const health = await call(app, "GET", "/health", undefined, { token: null });
  assert.equal(health.status, 200);
  assert.equal(health.json.ok, true);
});
