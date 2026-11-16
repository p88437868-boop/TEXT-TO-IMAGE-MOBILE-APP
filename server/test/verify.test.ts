import assert from "node:assert/strict";
import test from "node:test";
import {
  call,
  createCustomer,
  DEVICE_A,
  DEVICE_B,
  makeApp,
  verifyCall,
} from "./helpers.ts";

const DAY = 86_400_000;

test("a new license is pending, then activates on the right phone", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  assert.equal(license.status, "pending");
  assert.match(license.key, /^[2-9A-HJ-NP-Z]{4}(-[2-9A-HJ-NP-Z]{4}){3}$/);
  assert.equal(license.plan, "Unlimited");

  const result = await verifyCall(app, { key: license.key });
  assert.equal(result.verified, true, "reply must be signed with the server key");
  assert.equal(result.payload?.status, "ACTIVE");
  assert.equal(result.payload?.name, "Amina Khan");
  assert.equal(result.payload?.activatedAt, app.now);
  assert.equal(result.payload?.expiresAt, app.now + 30 * DAY);
  assert.equal(result.payload?.serverTime, app.now);
  assert.equal(result.payload?.quota.mode, "unlimited");
  assert.equal(result.payload?.quota.remaining, null);

  const after = await call(app, "GET", `/admin/licenses/${license.id}`);
  assert.equal(after.json.license.status, "active");
  assert.equal(after.json.license.deviceModel, "Pixel 7");
  assert.equal(after.json.license.appVersion, "1.2.0");
  assert.ok(after.json.events.some((event: any) => event.type === "activated"));
});

test("the key only works on the phone it was made for", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);

  const other = await verifyCall(app, { key: license.key, deviceCode: DEVICE_B });
  assert.equal(other.verified, true);
  assert.equal(other.payload?.status, "WRONG_DEVICE");
  assert.equal(other.payload?.name, null, "must not reveal the customer name");

  const detail = await call(app, "GET", `/admin/licenses/${license.id}`);
  const attempts = detail.json.events.filter((event: any) => event.type === "wrong_device_attempt");
  assert.equal(attempts.length, 1);
  assert.match(attempts[0].detail, /TP-9ABC/);

  const right = await verifyCall(app, { key: license.key });
  assert.equal(right.payload?.status, "ACTIVE");
});

test("an unknown or malformed key is INVALID_KEY, never a crash", async () => {
  const app = await makeApp();
  await createCustomer(app);
  const unknown = await verifyCall(app, { key: "2345-6789-ABCD-EFGH" });
  assert.equal(unknown.payload?.status, "INVALID_KEY");
  const garbage = await verifyCall(app, { key: "not-a-key" });
  assert.equal(garbage.payload?.status, "INVALID_KEY");
});

test("verify needs a nonce and a working signing key", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  const noNonce = await call(app, "POST", "/v1/verify", {
    key: license.key,
    deviceCode: DEVICE_A,
  });
  assert.equal(noNonce.status, 400);
  assert.equal(noNonce.json.error, "NONCE_REQUIRED");
});

test("lifetime keys have no expiry", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { durationDays: null });
  const result = await verifyCall(app, { key: license.key });
  assert.equal(result.payload?.status, "ACTIVE");
  assert.equal(result.payload?.expiresAt, null);
  app.advance(500 * DAY);
  const later = await verifyCall(app, { key: license.key });
  assert.equal(later.payload?.status, "ACTIVE");
});

test("expiry counts from activation, not from creation", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { durationDays: 7 });
  app.advance(10 * DAY);
  const result = await verifyCall(app, { key: license.key });
  assert.equal(result.payload?.expiresAt, app.now + 7 * DAY);

  app.advance(7 * DAY + 1000);
  const expired = await verifyCall(app, { key: license.key });
  assert.equal(expired.payload?.status, "EXPIRED");
});

test("block, unblock and permanent cancel", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  await verifyCall(app, { key: license.key });

  const blocked = await call(app, "POST", `/admin/licenses/${license.id}/block`);
  assert.equal(blocked.json.license.status, "blocked");
  assert.equal((await verifyCall(app, { key: license.key })).payload?.status, "BLOCKED");

  const unblocked = await call(app, "POST", `/admin/licenses/${license.id}/unblock`);
  assert.equal(unblocked.json.license.status, "active");
  assert.equal((await verifyCall(app, { key: license.key })).payload?.status, "ACTIVE");

  await call(app, "POST", `/admin/licenses/${license.id}/cancel`);
  assert.equal((await verifyCall(app, { key: license.key })).payload?.status, "CANCELLED");

  const unblockCancelled = await call(app, "POST", `/admin/licenses/${license.id}/unblock`);
  assert.equal(unblockCancelled.status, 409);
});

test("extending a live license adds to its end date", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { durationDays: 10 });
  const first = await verifyCall(app, { key: license.key });
  const originalExpiry = first.payload?.expiresAt;

  app.advance(3 * DAY);
  const extended = await call(app, "POST", `/admin/licenses/${license.id}/extend`, { days: 5 });
  assert.equal(extended.json.license.expiresAt, originalExpiry + 5 * DAY);

  const after = await verifyCall(app, { key: license.key });
  assert.equal(after.payload?.status, "ACTIVE");
  assert.equal(after.payload?.expiresAt, originalExpiry + 5 * DAY);
});

test("extending an expired license revives the same key", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { durationDays: 2 });
  await verifyCall(app, { key: license.key });
  app.advance(5 * DAY);
  assert.equal((await verifyCall(app, { key: license.key })).payload?.status, "EXPIRED");

  const extended = await call(app, "POST", `/admin/licenses/${license.id}/extend`, { days: 30 });
  assert.equal(extended.json.license.expiresAt, app.now + 30 * DAY);
  const revived = await verifyCall(app, { key: license.key });
  assert.equal(revived.payload?.status, "ACTIVE");
  assert.equal(revived.payload?.expiresAt, app.now + 30 * DAY);
});

test("moving to a new phone locks the old one", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  await verifyCall(app, { key: license.key });

  const moved = await call(app, "POST", `/admin/licenses/${license.id}/move-phone`, {
    deviceCode: DEVICE_B,
  });
  assert.equal(moved.json.license.deviceCode, DEVICE_B);

  assert.equal((await verifyCall(app, { key: license.key })).payload?.status, "WRONG_DEVICE");
  const newPhone = await verifyCall(app, { key: license.key, deviceCode: DEVICE_B });
  assert.equal(newPhone.payload?.status, "ACTIVE");
});

test("a phone cannot hold two live licenses", async () => {
  const app = await makeApp();
  await createCustomer(app);
  const second = await call(app, "POST", "/admin/licenses", {
    name: "Second Person",
    deviceCode: DEVICE_A,
    durationDays: 30,
  });
  assert.equal(second.status, 409);
  assert.equal(second.json.error, "DEVICE_ALREADY_LICENSED");
  assert.match(second.json.message, /Extend that one instead/);
});

test("bad input is rejected with a clear message", async () => {
  const app = await makeApp();
  const badDevice = await call(app, "POST", "/admin/licenses", {
    name: "X",
    deviceCode: "TP-1234",
    durationDays: 30,
  });
  assert.equal(badDevice.status, 400);
  assert.equal(badDevice.json.error, "INVALID_DEVICE_CODE");

  const noName = await call(app, "POST", "/admin/licenses", {
    deviceCode: DEVICE_A,
    durationDays: 30,
  });
  assert.equal(noName.status, 400);
  assert.equal(noName.json.error, "NAME_REQUIRED");
});

test("failed attempts are rate limited per phone and hour", async () => {
  const app = await makeApp();
  const statuses: number[] = [];
  for (let attempt = 0; attempt < 11; attempt++) {
    const result = await verifyCall(app, { key: "2345-6789-ABCD-EFGH", deviceCode: DEVICE_B });
    statuses.push(result.status);
  }
  assert.deepEqual(statuses.slice(0, 10), Array(10).fill(200));
  assert.equal(statuses[10], 429, "the 11th failed attempt is blocked");

  app.advance(3_600_001);
  const afterAnHour = await verifyCall(app, { key: "2345-6789-ABCD-EFGH", deviceCode: DEVICE_B });
  assert.equal(afterAnHour.status, 200);
});

test("a good license is never rate limited by other phones", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  for (let attempt = 0; attempt < 12; attempt++) {
    await verifyCall(app, { key: "2345-6789-ABCD-EFGH", deviceCode: DEVICE_B });
  }
  const good = await verifyCall(app, { key: license.key });
  assert.equal(good.status, 200);
  assert.equal(good.payload?.status, "ACTIVE");
});

test("last seen is updated without logging every check", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  await verifyCall(app, { key: license.key });
  app.advance(DAY);
  await verifyCall(app, { key: license.key });
  const detail = await call(app, "GET", `/admin/licenses/${license.id}`);
  assert.equal(detail.json.license.lastSeenAt, app.now);
  const checkEvents = detail.json.events.filter((event: any) =>
    ["verify", "checked", "active"].includes(event.type),
  );
  assert.equal(checkEvents.length, 0);
});
