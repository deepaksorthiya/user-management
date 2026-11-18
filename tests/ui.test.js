import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { JSDOM } from "jsdom";
const base = new URL("../src/main/resources/static/", import.meta.url);
const html = await readFile(new URL("index.html", base), "utf8");
const code =
  (await readFile(new URL("api.js", base), "utf8")).replaceAll("export ", "") +
  "\n" +
  (await readFile(new URL("app.js", base), "utf8")).replace(
    /^import .*\n/,
    "",
  ) +
  "\nwindow.__app={state,openDetail,renderTab,editor,removeEntity,password,linkPicker};";
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function until(check) {
  for (let i = 0; i < 80; i++) {
    if (check()) return;
    await wait(5);
  }
  throw new Error("UI did not reach expected state");
}
async function setup(hash = "users") {
  const dom = new JSDOM(html, {
    url: `http://localhost/#${hash}`,
    runScripts: "outside-only",
  });
  const w = dom.window;
  w.HTMLDialogElement.prototype.showModal = function () {
    this.open = true;
  };
  w.HTMLDialogElement.prototype.close = function () {
    this.open = false;
  };
  w.Response = Response;
  const calls = [];
  let fail = null;
  const stamp = "2026-11-16T10:00:00";
  const records = {
    users: [
      {
        id: 1,
        username: "alice",
        firstName: "Alice",
        lastName: "Chen",
        email: "alice@example.com",
        active: true,
        createdAt: stamp,
        updatedAt: stamp,
      },
      {
        id: 2,
        username: "bob",
        firstName: "Bob",
        lastName: "Lane",
        email: "bob@example.com",
        active: false,
        createdAt: stamp,
        updatedAt: stamp,
      },
    ],
    groups: [
      {
        id: 1,
        name: "Engineering",
        description: "Builders",
        createdAt: stamp,
        updatedAt: stamp,
      },
      {
        id: 2,
        name: "Design",
        description: "Creative",
        createdAt: stamp,
        updatedAt: stamp,
      },
    ],
    roles: [
      {
        id: 1,
        name: "ADMIN",
        description: "Admin role",
        createdAt: stamp,
        updatedAt: stamp,
      },
      { id: 2, name: "EDITOR", createdAt: stamp, updatedAt: stamp },
    ],
    permissions: [
      {
        id: 1,
        name: "READ_USERS",
        description: "Read people",
        createdAt: stamp,
        updatedAt: stamp,
      },
      { id: 2, name: "WRITE_USERS", createdAt: stamp, updatedAt: stamp },
    ],
  };
  const json = (value, status = 200) =>
    new Response(value === null ? null : JSON.stringify(value), { status });
  w.fetch = async (url, options = {}) => {
    const method = options.method || "GET";
    const payload = options.body ? JSON.parse(options.body) : undefined;
    calls.push({ url, method, payload });
    if (fail) {
      const problem = fail;
      fail = null;
      return json(problem, problem.status || 500);
    }
    if (url === "/actuator/health") return json({ status: "UP" });
    const parsed = new URL(url, "http://localhost");
    const [kind, id, relation, target] = parsed.pathname
      .replace("/api/v1/", "")
      .split("/");
    const list = records[kind];
    if (!list) return json({ detail: "Not found" }, 404);
    if (method === "POST") {
      const item = { ...payload, id: 3, createdAt: stamp, updatedAt: stamp };
      list.push(item);
      return json(item, 201);
    }
    if (method === "PUT") {
      if (!relation) {
        const index = list.findIndex((item) => item.id === Number(id));
        list[index] = { ...list[index], ...payload };
        return json(list[index]);
      }
      return target || relation === "password" ? json(null, 204) : json([]);
    }
    if (method === "DELETE") {
      if (!relation)
        records[kind] = list.filter((item) => item.id !== Number(id));
      return json(null, 204);
    }
    if (id === "scroll")
      return json({
        content: list,
        size: list.length,
        hasNext: false,
        nextCursor: null,
      });
    if (relation === "access")
      return json({
        userId: Number(id),
        username: "alice",
        active: true,
        groups: ["Engineering"],
        directRoles: ["ADMIN"],
        groupRoles: ["EDITOR"],
        permissions: ["READ_USERS", "WRITE_USERS"],
      });
    if (relation) {
      const items = records[relation === "members" ? "users" : relation];
      return relation === "users" || relation === "members"
        ? json({ content: items, totalElements: items.length, totalPages: 1 })
        : json(items);
    }
    if (id) return json(list.find((item) => item.id === Number(id)));
    if (kind === "users") {
      let items = list;
      const q = parsed.searchParams.get("q");
      const active = parsed.searchParams.get("active");
      if (q)
        items = items.filter((item) =>
          JSON.stringify(item).toLowerCase().includes(q.toLowerCase()),
        );
      if (active)
        items = items.filter((item) => String(item.active) === active);
      return json({
        content: items,
        page: 0,
        totalElements: items.length,
        totalPages: 1,
      });
    }
    return json(list);
  };
  w.eval(code);
  await until(() => w.document.querySelector("table"));
  await wait(15);
  const $ = (selector) => w.document.querySelector(selector);
  const click = (selector) => {
    const item = $(selector);
    assert.ok(item, `Missing control: ${selector}`);
    item.click();
  };
  const submit = async () => {
    $("#modal form").dispatchEvent(
      new w.Event("submit", { bubbles: true, cancelable: true }),
    );
    await wait(30);
  };
  const fill = (values) => {
    for (const [name, value] of Object.entries(values)) {
      $(`#modal [name="${name}"]`).value = value;
    }
  };
  const close = () => {
    w.close();
  };
  return {
    w,
    $,
    click,
    submit,
    fill,
    calls,
    records,
    close,
    failNext: (problem) => {
      fail = problem;
    },
  };
}

test("overview shows live counts, recent people and real session activity", async () => {
  const t = await setup("overview");
  try {
    assert.equal(t.$("h1").textContent, "Workspace overview");
    assert.equal(t.w.document.querySelectorAll(".stat").length, 4);
    assert.match(t.$("#page").textContent, /Alice Chen/);
    assert.match(t.$("#page").textContent, /0 actions/);
    assert.equal(t.$("#connection").textContent, "All systems connected");
  } finally {
    t.close();
  }
});
test("create user submits all fields with active status and updates directory", async () => {
  const t = await setup();
  try {
    t.click('[data-action="create"]');
    t.fill({
      username: "charlie",
      firstName: "Charlie",
      lastName: "River",
      email: "charlie@example.com",
      password: "Password@123",
    });
    await t.submit();
    const call = t.calls.find((c) => c.method === "POST");
    assert.deepEqual(call.payload, {
      username: "charlie",
      firstName: "Charlie",
      lastName: "River",
      email: "charlie@example.com",
      password: "Password@123",
      active: true,
    });
    assert.equal(t.$("#modal").open, false);
    assert.match(t.$("#list-content").textContent, /Charlie River/);
  } finally {
    t.close();
  }
});
test("edit user keeps username immutable and password uses dedicated route", async () => {
  const t = await setup();
  try {
    t.click('[data-action="open"]');
    await until(() => t.$('[data-action="edit"]'));
    t.click('[data-action="edit"]');
    assert.equal(t.$('[name="username"]'), null);
    t.fill({ email: "new@example.com" });
    t.$('[name="active"]').checked = false;
    await t.submit();
    assert.ok(
      t.calls.some(
        (c) =>
          c.url === "/api/v1/users/1" &&
          c.method === "PUT" &&
          c.payload.email === "new@example.com" &&
          c.payload.active === false,
      ),
    );
    t.click('[data-action="password"]');
    t.fill({ newPassword: "NewPassword@123" });
    await t.submit();
    assert.ok(
      t.calls.some(
        (c) =>
          c.url === "/api/v1/users/1/password" &&
          c.payload.newPassword === "NewPassword@123",
      ),
    );
  } finally {
    t.close();
  }
});
test("delete requires confirmation and removes the record", async () => {
  const t = await setup();
  try {
    t.click('[data-action="open"]');
    await until(() => t.$('[data-action="delete"]'));
    t.click('[data-action="delete"]');
    assert.equal(t.calls.filter((c) => c.method === "DELETE").length, 0);
    await t.submit();
    assert.ok(
      t.calls.some((c) => c.url === "/api/v1/users/1" && c.method === "DELETE"),
    );
    assert.equal(t.$("#detail").open, false);
    assert.doesNotMatch(t.$("#list-content").textContent, /Alice Chen/);
  } finally {
    t.close();
  }
});
test("validation errors stay in dialog and preserve entered values", async () => {
  const t = await setup();
  try {
    t.click('[data-action="create"]');
    t.fill({
      username: "alice",
      email: "bad@example.com",
      firstName: "Alice",
      lastName: "Test",
      password: "Password@123",
    });
    t.failNext({ status: 409, detail: "Username already exists" });
    await t.submit();
    assert.equal(t.$("#modal").open, true);
    assert.match(t.$("#form-error").textContent, /Username already exists/);
    assert.equal(t.$('[name="username"]').value, "alice");
    assert.equal(t.$('button[type="submit"]').disabled, false);
  } finally {
    t.close();
  }
});
test("short user search is blocked, valid search and status filters call API", async () => {
  const t = await setup();
  try {
    const search = t.$("#search");
    search.value = "al";
    search.dispatchEvent(new t.w.Event("input", { bubbles: true }));
    await wait(350);
    assert.match(t.$("#list-content").textContent, /at least 3/);
    search.value = "alice";
    search.dispatchEvent(new t.w.Event("input", { bubbles: true }));
    await wait(350);
    assert.ok(t.calls.some((c) => c.url.includes("q=alice")));
    t.$("#active-filter").value = "false";
    t.$("#active-filter").dispatchEvent(
      new t.w.Event("change", { bubbles: true }),
    );
    await wait(20);
    assert.ok(t.calls.some((c) => c.url.includes("active=false")));
  } finally {
    t.close();
  }
});
test("effective access renders all direct, inherited and group permissions", async () => {
  const t = await setup();
  try {
    await t.w.__app.openDetail("users", 1, "access");
    assert.match(t.$("#tab-panel").textContent, /Engineering/);
    assert.match(t.$("#tab-panel").textContent, /ADMIN/);
    assert.match(t.$("#tab-panel").textContent, /EDITOR/);
    assert.match(t.$("#tab-panel").textContent, /WRITE_USERS/);
  } finally {
    t.close();
  }
});
for (const [kind, tab, target, endpoint] of [
  ["users", "roles", "roles", "users/1/roles/1"],
  ["users", "groups", "groups", "users/1/groups/1"],
  ["users", "permissions", "permissions", "users/1/permissions/1"],
  ["groups", "members", "users", "groups/1/members/1"],
  ["groups", "roles", "roles", "groups/1/roles/1"],
  ["groups", "permissions", "permissions", "groups/1/permissions/1"],
  ["roles", "permissions", "permissions", "roles/1/permissions/1"],
  ["roles", "users", "users", "users/1/roles/1"],
  ["roles", "groups", "groups", "groups/1/roles/1"],
  ["permissions", "roles", "roles", "roles/1/permissions/1"],
  ["permissions", "groups", "groups", "groups/1/permissions/1"],
  ["permissions", "users", "users", "users/1/permissions/1"],
])
  test(`${kind} → ${tab}: displays records, adds and removes through correct API`, async () => {
    const t = await setup();
    try {
      await t.w.__app.openDetail(kind, 1, tab);
      assert.ok(t.$("#tab-panel table"));
      t.click('[data-action="link"]');
      await until(() => t.$('[name="target"]'));
      t.$('[name="target"]').checked = true;
      await t.submit();
      assert.ok(
        t.calls.some(
          (c) => c.url === `/api/v1/${endpoint}` && c.method === "PUT",
        ),
      );
      t.click('[data-action="unlink"]');
      await t.submit();
      assert.ok(
        t.calls.some(
          (c) => c.url === `/api/v1/${endpoint}` && c.method === "DELETE",
        ),
      );
    } finally {
      t.close();
    }
  });
for (const [kind, tab] of [
  ["users", "roles"],
  ["users", "groups"],
  ["users", "permissions"],
  ["groups", "roles"],
  ["groups", "permissions"],
  ["roles", "permissions"],
])
  test(`${kind} → ${tab}: bulk replace supports clearing every assignment`, async () => {
    const t = await setup();
    try {
      await t.w.__app.openDetail(kind, 1, tab);
      t.click('[data-action="replace"]');
      await until(() => t.$('[name="target"]'));
      for (const input of t.w.document.querySelectorAll('[name="target"]')) {
        input.checked = false;
        input.dispatchEvent(new t.w.Event("change", { bubbles: true }));
      }
      await t.submit();
      assert.ok(
        t.calls.some(
          (c) =>
            c.url === `/api/v1/${kind}/1/${tab}` &&
            c.method === "PUT" &&
            c.payload.ids.length === 0,
        ),
      );
    } finally {
      t.close();
    }
  });
for (const kind of ["groups", "roles", "permissions"])
  test(`${kind}: create, edit, inspect and delete`, async () => {
    const t = await setup(kind);
    try {
      t.click('[data-action="create"]');
      t.fill({
        name: kind === "groups" ? "Research" : "RESEARCH",
        description: "New record",
      });
      await t.submit();
      assert.ok(
        t.calls.some((c) => c.url === `/api/v1/${kind}` && c.method === "POST"),
      );
      await t.w.__app.openDetail(kind, 3);
      t.click('[data-action="edit"]');
      t.fill({ description: "Updated record" });
      await t.submit();
      assert.ok(
        t.calls.some(
          (c) =>
            c.url === `/api/v1/${kind}/3` &&
            c.method === "PUT" &&
            c.payload.description === "Updated record",
        ),
      );
      t.click('[data-action="delete"]');
      await t.submit();
      assert.ok(
        t.calls.some(
          (c) => c.url === `/api/v1/${kind}/3` && c.method === "DELETE",
        ),
      );
    } finally {
      t.close();
    }
  });
test("untrusted record text is escaped in tables and dialogs", async () => {
  const t = await setup("groups");
  try {
    t.records.groups[0].name = "<img src=x onerror=alert(1)>";
    await t.w.__app.openDetail("groups", 1);
    assert.equal(t.$("#detail img"), null);
    assert.match(t.$("#detail-title").textContent, /<img/);
  } finally {
    t.close();
  }
});
test("keyboard arrows activate accessible detail tabs", async () => {
  const t = await setup();
  try {
    await t.w.__app.openDetail("users", 1);
    t.$("#tab-profile").dispatchEvent(
      new t.w.KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }),
    );
    await wait(20);
    assert.equal(t.$("#tab-roles").getAttribute("aria-selected"), "true");
    assert.equal(
      t.$("#tab-panel").getAttribute("aria-labelledby"),
      "tab-roles",
    );
  } finally {
    t.close();
  }
});
test("CSV export follows the keyset endpoint and creates a downloadable file", async () => {
  const t = await setup();
  try {
    let blob, download;
    t.w.URL.createObjectURL = (value) => {
      blob = value;
      return "blob:test";
    };
    t.w.URL.revokeObjectURL = () => {};
    t.w.HTMLAnchorElement.prototype.click = function () {
      download = this.download;
    };
    t.click('[data-action="export"]');
    await until(() => download);
    assert.ok(
      t.calls.some(
        (c) =>
          c.url.startsWith("/api/v1/users/scroll?") &&
          c.url.includes("size=200"),
      ),
    );
    assert.ok(blob.size > 0);
    assert.match(download, /forma-users-.*\.csv/);
    assert.match(t.$("#toast").textContent, /Exported 2 users/);
  } finally {
    t.close();
  }
});
test("directory failure exposes a working retry action", async () => {
  const t = await setup();
  try {
    t.failNext({ status: 503, detail: "Temporary outage" });
    t.$("#sort").value = "createdAt,desc";
    t.$("#sort").dispatchEvent(new t.w.Event("change", { bubbles: true }));
    await until(() => t.$('[data-action="retry-list"]'));
    assert.match(t.$("#list-content").textContent, /Temporary outage/);
    t.click('[data-action="retry-list"]');
    await until(() => t.$("#list-content table"));
    assert.match(t.$("#list-content").textContent, /Alice Chen/);
  } finally {
    t.close();
  }
});
test("loading an old record cannot overwrite a more recently opened record", async () => {
  const t = await setup();
  try {
    const fetch = t.w.fetch;
    let release;
    t.w.fetch = async (url, options) => {
      if (url === "/api/v1/users/1")
        await new Promise((resolve) => {
          release = resolve;
        });
      return fetch(url, options);
    };
    const old = t.w.__app.openDetail("users", 1);
    await t.w.__app.openDetail("groups", 1);
    release();
    await old;
    assert.equal(t.$("#detail-title").textContent, "Engineering");
  } finally {
    t.close();
  }
});
test("closing a loading picker cannot overwrite a newer form", async () => {
  const t = await setup();
  try {
    await t.w.__app.openDetail("users", 1, "roles");
    const fetch = t.w.fetch;
    let release;
    t.w.fetch = async (url, options) => {
      if (url === "/api/v1/roles")
        await new Promise((resolve) => {
          release = resolve;
        });
      return fetch(url, options);
    };
    const pending = t.w.__app.linkPicker();
    t.click('[data-action="close-modal"]');
    t.w.__app.editor("users");
    release();
    await pending;
    assert.ok(t.$('#modal [name="username"]'));
    assert.equal(t.$("#choice-results"), null);
  } finally {
    t.close();
  }
});

test("user permissions tab is discoverable and explains direct-only revocation", async () => {
  const t = await setup();
  try {
    await t.w.__app.openDetail("users", 1);
    assert.equal(t.$("#tab-permissions").textContent, "Direct permissions");
    t.click("#tab-permissions");
    await until(() => t.$("#tab-panel table"));
    assert.match(
      t.$("#tab-panel").textContent,
      /preserves permissions inherited/,
    );
    assert.ok(t.$('[data-action="replace"]'));
  } finally {
    t.close();
  }
});

test("effective access distinguishes direct grants from the combined permission set", async () => {
  const t = await setup();
  try {
    const fetch = t.w.fetch;
    t.w.fetch = (url, options) =>
      url === "/api/v1/users/1/permissions"
        ? Promise.resolve(
            new Response(JSON.stringify([{ id: 3, name: "DIRECT_ONLY" }])),
          )
        : fetch(url, options);
    await t.w.__app.openDetail("users", 1, "access");
    const sections = [...t.w.document.querySelectorAll(".access-section")];
    const direct = sections.find((section) =>
      section.textContent.includes("Direct user permissions"),
    );
    const effective = sections.find((section) =>
      section.textContent.includes("Effective permissions"),
    );
    assert.match(direct.textContent, /DIRECT_ONLY/);
    assert.doesNotMatch(direct.textContent, /READ_USERS/);
    assert.match(effective.textContent, /READ_USERS/);
    assert.match(t.$("#tab-panel").textContent, /direct user grants/);
  } finally {
    t.close();
  }
});

test("direct permission load failure offers retry instead of showing incomplete access", async () => {
  const t = await setup();
  try {
    const fetch = t.w.fetch;
    let fail = true;
    t.w.fetch = (url, options) => {
      if (url === "/api/v1/users/1/permissions" && fail) {
        fail = false;
        return Promise.resolve(
          new Response(
            JSON.stringify({ detail: "Permission lookup unavailable" }),
            { status: 503 },
          ),
        );
      }
      return fetch(url, options);
    };
    await t.w.__app.openDetail("users", 1, "access");
    assert.match(
      t.$("#tab-panel").textContent,
      /Permission lookup unavailable/,
    );
    t.click('[data-action="retry-tab"]');
    await until(() => t.$(".access-flow"));
    assert.match(t.$("#tab-panel").textContent, /Direct user permissions/);
  } finally {
    t.close();
  }
});
