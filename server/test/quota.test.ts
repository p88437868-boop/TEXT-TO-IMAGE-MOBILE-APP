import assert from "node:assert/strict";
import test from "node:test";
import { call, createCustomer, makeApp, verifyCall } from "./helpers.ts";

const DAY = 86_400_000;

test("unlimited plans never run out", async () => {
  const app = await makeApp();
  const license = await createCustomer(app);
  await verifyCall(app, { key: license.key });
  for (let i = 0; i < 5; i++) {
    const result = await verifyCall(app, {
      key: license.key,
      usageCount: 500,
      reportId: `unlimited-${i}`,
    });
    assert.equal(result.payload?.quota.remaining, null);
    assert.equal(result.payload?.quota.usedTotal, (i + 1) * 500);
  }
});

test("a daily plan is used up and refills at the next local midnight", async () => {
  const app = await makeApp(Date.UTC(2026, 0, 15, 12, 0, 0));
  const license = await createCustomer(app, { quotaMode: "daily", quotaLimit: 2 });
  assert.equal(license.plan, "2/day");

  const first = await verifyCall(app, { key: license.key, usageCount: 2, reportId: "d-1" });
  assert.equal(first.payload?.quota.remaining, 0);
  assert.equal(first.payload?.quota.usedToday, 2);
  assert.equal(first.payload?.reportedId, "d-1");
  assert.equal(first.payload?.quota.dayEndsAt, Date.UTC(2026, 0, 16, 0, 0, 0));

  // Same day, another render must be blocked by the app (server reports 0 left).
  const sameDay = await verifyCall(app, { key: license.key });
  assert.equal(sameDay.payload?.quota.remaining, 0);

  app.advance(DAY);
  const nextDay = await verifyCall(app, { key: license.key });
  assert.equal(nextDay.payload?.quota.remaining, 2);
  assert.equal(nextDay.payload?.quota.usedToday, 0);
  assert.equal(nextDay.payload?.quota.usedTotal, 2);
});

test("a whole-period plan counts every image until the total", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { quotaMode: "total", quotaLimit: 3 });
  assert.equal(license.plan, "3 total");

  const first = await verifyCall(app, { key: license.key, usageCount: 2, reportId: "t-1" });
  assert.equal(first.payload?.quota.remaining, 1);

  app.advance(30 * DAY);
  const second = await verifyCall(app, { key: license.key, usageCount: 2, reportId: "t-2" });
  assert.equal(second.payload?.quota.usedTotal, 4);
  assert.equal(second.payload?.quota.remaining, 0, "remaining never goes negative");

  // Extending the duration does not give images back.
  await call(app, "POST", `/admin/licenses/${license.id}/extend`, { days: 30 });
  const extended = await verifyCall(app, { key: license.key });
  assert.equal(extended.payload?.quota.remaining, 0);
});

test("a retried report is never counted twice", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { quotaMode: "total", quotaLimit: 100 });
  await verifyCall(app, { key: license.key });
  const once = await verifyCall(app, { key: license.key, usageCount: 5, reportId: "same-id" });
  const twice = await verifyCall(app, { key: license.key, usageCount: 5, reportId: "same-id" });
  assert.equal(once.payload?.quota.usedTotal, 5);
  assert.equal(twice.payload?.quota.usedTotal, 5);
  assert.equal(twice.payload?.reportedId, "same-id", "the app still learns it was processed");
});

test("one report is capped at 10000 images", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { quotaMode: "total", quotaLimit: 1_000_000 });
  await verifyCall(app, { key: license.key });
  const result = await verifyCall(app, {
    key: license.key,
    usageCount: 999_999,
    reportId: "huge",
  });
  assert.equal(result.payload?.quota.usedTotal, 10_000);
});

test("the day rolls over in the configured time zone", async () => {
  // 20:00 UTC on 15 Jan is 01:30 on 16 Jan in Kolkata.
  const app = await makeApp(Date.UTC(2026, 0, 15, 20, 0, 0));
  const license = await createCustomer(app, { quotaMode: "daily", quotaLimit: 5 });
  await call(app, "PUT", "/admin/settings", { quotaTz: "Asia/Kolkata" });

  const result = await verifyCall(app, { key: license.key, usageCount: 3, reportId: "tz-1" });
  assert.equal(result.payload?.quota.usedToday, 3);
  assert.equal(
    result.payload?.quota.dayEndsAt,
    Date.UTC(2026, 0, 16, 18, 30, 0),
    "day ends at midnight IST",
  );

  // 18:29 UTC is still 23:59 IST on the 16th: same day.
  app.advance(Date.UTC(2026, 0, 16, 18, 29, 0) - app.now);
  const stillSameDay = await verifyCall(app, { key: license.key });
  assert.equal(stillSameDay.payload?.quota.usedToday, 3);

  // 18:31 UTC is 00:01 IST on the 17th: new day.
  app.advance(2 * 60_000);
  const newDay = await verifyCall(app, { key: license.key });
  assert.equal(newDay.payload?.quota.usedToday, 0);
  assert.equal(newDay.payload?.quota.remaining, 5);
});

test("the owner can change the plan, add images and reset usage", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { quotaMode: "daily", quotaLimit: 10 });
  await verifyCall(app, { key: license.key, usageCount: 10, reportId: "r-1" });

  const changed = await call(app, "POST", `/admin/licenses/${license.id}/quota`, {
    mode: "total",
    limit: 50,
  });
  assert.equal(changed.json.license.plan, "50 total");
  assert.equal(changed.json.license.remaining, 40);

  const added = await call(app, "POST", `/admin/licenses/${license.id}/usage/add`, { count: 25 });
  assert.equal(added.json.license.usedTotal, 35);
  assert.equal(added.json.license.remaining, 15);

  const resetToday = await call(app, "POST", `/admin/licenses/${license.id}/usage/reset`, {
    scope: "today",
  });
  assert.equal(resetToday.json.license.usedToday, 0);
  assert.equal(resetToday.json.license.usedTotal, 35);

  const resetTotal = await call(app, "POST", `/admin/licenses/${license.id}/usage/reset`, {
    scope: "total",
  });
  assert.equal(resetTotal.json.license.usedTotal, 0);
  assert.equal(resetTotal.json.license.remaining, 50);

  const events = await call(app, "GET", `/admin/licenses/${license.id}`);
  const types = events.json.events.map((event: any) => event.type);
  assert.ok(types.includes("quota_changed"));
  assert.ok(types.includes("usage_reset"));
});

test("reinstalling the app cannot reset usage because the server remembers", async () => {
  const app = await makeApp();
  const license = await createCustomer(app, { quotaMode: "total", quotaLimit: 5 });
  await verifyCall(app, { key: license.key, usageCount: 5, reportId: "install-1" });
  // New install: no report id, no local count, same key.
  const freshInstall = await verifyCall(app, { key: license.key, usageCount: 0 });
  assert.equal(freshInstall.payload?.quota.usedTotal, 5);
  assert.equal(freshInstall.payload?.quota.remaining, 0);
});
