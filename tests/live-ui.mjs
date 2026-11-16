/** Render the actual UI code against a live disposable Spring Boot instance.
 * No browser engine is involved: layout/paint and native focus are not verified.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { JSDOM } from "jsdom";
const origin = process.argv[2] || "http://127.0.0.1:8080";
const root = new URL("../src/main/resources/static/", import.meta.url);
const html = await readFile(new URL("index.html", root), "utf8");
const script =
  (await readFile(new URL("api.js", root), "utf8")).replaceAll("export ", "") +
  "\n" +
  (await readFile(new URL("app.js", root), "utf8")).replace(
    /^import .*\n/,
    "",
  ) +
  "\nwindow.__app={state,openDetail,renderTab};";
const dom = new JSDOM(html, {
  url: `${origin}/#users`,
  runScripts: "outside-only",
});
const w = dom.window;
const $ = (selector) => w.document.querySelector(selector);
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function until(check, label = "expected UI state") {
  for (let i = 0; i < 400; i++) {
    if (check()) return;
    await wait(10);
  }
  throw new Error(
    `Timed out: ${label}. Dialog error: ${$("#form-error")?.textContent}`,
  );
}
function click(selector) {
  assert.ok($(selector), `Control missing: ${selector}`);
  $(selector).click();
}
function fill(values) {
  for (const [name, value] of Object.entries(values))
    $(`#modal [name="${name}"]`).value = value;
}
async function submit() {
  $("#modal form").dispatchEvent(
    new w.Event("submit", { bubbles: true, cancelable: true }),
  );
  await until(() => !$("#modal form")?.dataset.busy, "form settled");
}
const requests = [];
w.HTMLDialogElement.prototype.showModal = function () {
  this.open = true;
};
w.HTMLDialogElement.prototype.close = function () {
  this.open = false;
};
w.fetch = async (path, options) => {
  const response = await fetch(new URL(path, origin), options);
  requests.push({
    path,
    method: options?.method || "GET",
    status: response.status,
  });
  return response;
};
async function api(path, method = "GET", body) {
  const response = await fetch(`${origin}/api/v1/${path}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  assert.ok(response.ok, `${method} ${path}: ${response.status}`);
  return response.status === 204 ? null : response.json();
}
const tag = Date.now();
const created = [];
try {
  w.eval(script);
  await until(() => $("#list-content table"));
  // Create all four types through their actual rendered forms.
  for (const [kind, values] of [
    [
      "users",
      {
        username: `live_${tag}`,
        email: `live_${tag}@example.com`,
        firstName: "Live",
        lastName: "UI Test",
        password: "Password@123",
      },
    ],
    ["groups", { name: `Live UI ${tag}`, description: "Created through UI" }],
    ["roles", { name: `LIVE_${tag}`, description: "Created through UI" }],
    [
      "permissions",
      { name: `LIVE_READ_${tag}`, description: "Created through UI" },
    ],
  ]) {
    if (w.location.hash !== `#${kind}`) {
      w.location.hash = kind;
      await until(
        () =>
          $("h1").textContent === kind.charAt(0).toUpperCase() + kind.slice(1),
      );
      await until(() => $("#list-content table"));
    }
    click('[data-action="create"]');
    fill(values);
    await submit();
    assert.equal(
      $("#modal").open,
      false,
      "Successful create should close form",
    );
    const result = await api(kind === "users" ? `users?q=live_${tag}` : kind);
    const item = (Array.isArray(result) ? result : result.content).find(
      (item) =>
        (item.username || item.name) === (values.username || values.name),
    );
    assert.ok(item, `${kind} record created via UI`);
    created.push([kind, item.id]);
  }
  const u = created.find(([kind]) => kind === "users")[1];
  const g = created.find(([kind]) => kind === "groups")[1];
  const r = created.find(([kind]) => kind === "roles")[1];
  const p = created.find(([kind]) => kind === "permissions")[1];
  async function add(kind, id, tab, targetId) {
    await w.__app.openDetail(kind, id, tab);
    click('[data-action="link"]');
    await until(() => $('[name="target"]'));
    // Search the user directory rather than relying on its first page.
    if (tab === "users" || tab === "members") {
      $("#choice-search").value = `live_${tag}`;
      $("#choice-search").dispatchEvent(
        new w.Event("input", { bubbles: true }),
      );
      await until(() => $(`[name="target"][value="${targetId}"]`));
    }
    const input = $(`[name="target"][value="${targetId}"]`);
    assert.ok(input, "Assignment target is discoverable");
    input.checked = true;
    await submit();
    assert.equal($("#modal").open, false, "Assignment should succeed");
  }
  await add("groups", g, "members", u);
  await add("roles", r, "permissions", p);
  await add("groups", g, "roles", r);
  await w.__app.openDetail("users", u, "access");
  assert.match(
    $("#tab-panel").textContent,
    new RegExp(`LIVE_READ_${tag}`),
    "UI should show inherited permission",
  );
  // Reverse permission view must call the owning group endpoint.
  await add("permissions", p, "groups", g);
  assert.ok(
    requests.some(
      (item) =>
        item.path === `/api/v1/groups/${g}/permissions/${p}` &&
        item.method === "PUT" &&
        item.status === 204,
    ),
  );
  await w.__app.openDetail("users", u, "roles");
  click('[data-action="replace"]');
  await until(() => $(`[name="target"][value="${r}"]`));
  const checkbox = $(`[name="target"][value="${r}"]`);
  checkbox.checked = true;
  checkbox.dispatchEvent(new w.Event("change", { bubbles: true }));
  await submit();
  assert.ok(
    (await api(`users/${u}/roles`)).some((item) => item.id === r),
    "Bulk assignment persisted",
  );
  await w.__app.openDetail("users", u, "profile");
  click('[data-action="edit"]');
  fill({ firstName: "Edited", email: `edited_${tag}@example.com` });
  await submit();
  assert.equal((await api(`users/${u}`)).firstName, "Edited", "Edit persisted");
  click('[data-action="password"]');
  fill({ newPassword: "NewPassword@123" });
  await submit();
  assert.equal($("#modal").open, false);
  // Confirm a backend conflict appears in the actual form and retains input.
  $("#detail").close();
  w.location.hash = "roles";
  await until(() => $("h1").textContent === "Roles");
  await until(() => $("#list-content table"));
  click('[data-action="create"]');
  fill({ name: `LIVE_${tag}`, description: "Duplicate" });
  await submit();
  assert.equal($("#modal").open, true);
  assert.match($("#form-error").textContent, /exist|duplicate/i);
  assert.equal($('#modal [name="name"]').value, `LIVE_${tag}`);
  $("#modal").close();
  // Delete a UI-created permission through the confirmation dialog.
  await w.__app.openDetail("permissions", p);
  click('[data-action="delete"]');
  await submit();
  assert.equal($("#detail").open, false);
  created.splice(
    created.findIndex(([kind, id]) => kind === "permissions" && id === p),
    1,
  );
  assert.equal(
    (await api(`users/${u}/access`)).permissions.includes(`LIVE_READ_${tag}`),
    false,
    "Deletion updates effective access",
  );
  console.log(
    `PASS: live UI → Spring Boot workflows (${requests.length} actual HTTP requests): CRUD, password, membership, grants, bulk replacement, reverse relationships, effective access, validation, deletion.`,
  );
} finally {
  w.close();
  for (const [kind, id] of created.reverse())
    await api(`${kind}/${id}`, "DELETE");
}
