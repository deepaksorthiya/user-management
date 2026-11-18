import { api, query, relationPath, csv } from "./api.js";

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const escape = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const title = (value) => value.charAt(0).toUpperCase() + value.slice(1);
const singular = (kind) =>
  ({
    users: "user",
    groups: "group",
    roles: "role",
    permissions: "permission",
  })[kind];
const icons = { users: "♙", groups: "▦", roles: "◇", permissions: "⌑" };
const descriptions = {
  users:
    "The people behind your workspace. Manage profiles, memberships, and access.",
  groups: "Bring people together. Organize memberships and shared access.",
  roles: "Define responsibilities with reusable sets of permissions.",
  permissions:
    "The building blocks of access. See exactly where each permission is granted.",
};
const relations = {
  users: ["roles", "groups", "permissions", "access"],
  groups: ["members", "roles", "permissions"],
  roles: ["permissions", "users", "groups"],
  permissions: ["roles", "groups", "users"],
};
const state = {
  view: "overview",
  page: 0,
  size: 10,
  q: "",
  active: "",
  sort: "username,asc",
  stats: null,
  data: [],
  request: 0,
  detailRequest: 0,
  selection: null,
  tab: "profile",
  relationPage: 0,
};
const activity = [];
let toastTimer;
function toast(message, error = false) {
  const box = $("#toast");
  box.textContent = message;
  box.classList.toggle("error", error);
  box.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(
    () => {
      box.hidden = true;
    },
    error ? 7500 : 3500,
  );
}
function record(message) {
  activity.unshift({ message, time: new Date() });
  toast(message);
}
function loading() {
  return '<div class="loading" role="status">Loading workspace…</div>';
}
function empty(heading, description, action = "") {
  return `<div class="empty"><div class="empty-symbol" aria-hidden="true">◎</div><h3>${escape(heading)}</h3><p>${escape(description)}</p>${action}</div>`;
}
function failure(error, retry) {
  return empty(
    "Something needs attention",
    error.message,
    `<button class="button" data-action="${retry}">Try again</button>`,
  );
}
function badge(active) {
  return `<span class="badge ${active ? "" : "inactive"}">${active ? "Active" : "Inactive"}</span>`;
}
function initials(item) {
  return item.username
    ? `${item.firstName?.[0] || ""}${item.lastName?.[0] || ""}`.toUpperCase() ||
        item.username.slice(0, 2).toUpperCase()
    : icons[state.view] || "◇";
}
function identity(item, kind) {
  const name =
    kind === "users" ? `${item.firstName} ${item.lastName}` : item.name;
  return `<div class="identity"><span class="avatar tone-${item.id % 4}" aria-hidden="true">${escape(kind === "users" ? initials(item) : icons[kind])}</span><div><strong>${escape(name)}</strong><small>${escape(kind === "users" ? `@${item.username}` : `ID ${item.id}`)}</small></div></div>`;
}
function rows(items, kind, compact = false, relation = false) {
  return items
    .map(
      (item) =>
        `<tr><td>${identity(item, kind)}</td>${kind === "users" ? `<td>${escape(item.email)}</td><td>${badge(item.active)}</td>` : `<td class="description-cell" title="${escape(item.description)}">${escape(item.description || "No description")}</td>`}${compact ? "" : `<td>${escape(new Date(item.createdAt).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" }))}</td>`}<td><button class="row-action" data-action="${relation ? "related-open" : "open"}" data-kind="${kind}" data-id="${item.id}" aria-label="Open ${escape(item.username || item.name)}">View ↗</button>${relation ? `<button class="row-action" data-action="unlink" data-id="${item.id}" aria-label="Remove ${escape(item.username || item.name)} from this relationship">Remove</button>` : ""}</td></tr>`,
    )
    .join("");
}
function table(items, kind, compact = false, relation = false) {
  return `<div class="table-wrap"><table><thead><tr><th>${kind === "users" ? "Person" : singular(kind)}</th>${kind === "users" ? "<th>Email address</th><th>Status</th>" : "<th>Description</th>"}${compact ? "" : "<th>Created</th>"}<th><span class="sr-only">Actions</span></th></tr></thead><tbody>${rows(items, kind, compact, relation)}</tbody></table></div>`;
}
async function stats() {
  const [users, groups, roles, permissions] = await Promise.all([
    api("users?size=1"),
    api("groups"),
    api("roles"),
    api("permissions"),
  ]);
  state.stats = {
    users: users.totalElements,
    groups: groups.length,
    roles: roles.length,
    permissions: permissions.length,
  };
  for (const kind of Object.keys(state.stats))
    $(`#nav-${kind}`).textContent = state.stats[kind];
  return state.stats;
}
async function connection() {
  try {
    const response = await fetch("/actuator/health");
    const health = await response.json();
    if (!response.ok || health.status !== "UP") throw new Error();
    $("#connection").textContent = "All systems connected";
    $("#connection-dot").style.background = "#aacb8b";
  } catch {
    $("#connection").textContent = "Service unavailable";
    $("#connection-dot").style.background = "#dc9c78";
  }
}
function statCards(counts) {
  const captions = {
    users: "People in your workspace",
    groups: "Teams with shared access",
    roles: "Reusable access definitions",
    permissions: "Granular capabilities",
  };
  return `<div class="stats">${Object.entries(counts)
    .map(
      ([kind, count]) =>
        `<a class="stat" href="#${kind}"><div class="stat-label">Total ${kind}<span class="stat-symbol">${icons[kind]}</span></div><div class="stat-number">${count.toLocaleString()}</div><div class="stat-bottom"><span>↗</span> ${captions[kind]}</div></a>`,
    )
    .join("")}</div>`;
}
async function overview(token) {
  const [counts, users] = await Promise.all([
    stats(),
    api("users?size=5&sort=createdAt,desc"),
  ]);
  if (token !== state.request) return;
  $("#page").innerHTML =
    `${statCards(counts)}<section class="hero"><div><div class="eyebrow">CONNECTED BY DESIGN</div><h2>The right access. For every person.</h2><p>A thoughtful workspace starts with clear connections.<br>Organize your people, define roles, and make access simple.</p><a href="#groups">Manage your connections <span aria-hidden="true">↗</span></a></div><div class="hero-art" aria-hidden="true"><div class="orbit"></div><div class="orbit-core">◇</div><span class="orbit-node n1">♙</span><span class="orbit-node n2">⌑</span><span class="orbit-node n3">▦</span></div></section>
  <div class="grid-two"><section class="card"><div class="card-header"><div><h2>Recently added people</h2><p>A growing workspace, one person at a time.</p></div><a href="#users">View all users ↗</a></div>${users.content.length ? table(users.content, "users", true) : empty("Your workspace starts here", "Add your first user to start connecting people and access.")}</section>
  <section class="card"><div class="card-header"><div><h2>How access connects</h2><p>One connected system. Every relationship visible.</p></div></div><div class="connections"><a href="#groups" class="connection-item"><span class="stat-symbol">▦</span><div><strong>Bring people into groups</strong><p>Shared teams, shared possibilities</p></div><span>↗</span></a><a href="#roles" class="connection-item"><span class="stat-symbol">◇</span><div><strong>Give roles a purpose</strong><p>Grant access directly or through groups</p></div><span>↗</span></a><a href="#permissions" class="connection-item"><span class="stat-symbol">⌑</span><div><strong>Make permissions precise</strong><p>See what each person can actually do</p></div><span>↗</span></a></div><div class="mini-note">Open a user’s <strong>Effective access</strong> tab to see direct permissions, direct roles, inherited roles, and combined permissions.</div></section></div>
  <section class="card"><div class="card-header"><div><h2>This session</h2><p>Changes made in this browser session.</p></div><span class="result-count">${activity.length} actions</span></div>${
    activity.length
      ? `<div class="connections">${activity
          .slice(0, 8)
          .map(
            (item) =>
              `<div class="connection-item"><span class="stat-symbol">✓</span><div><strong>${escape(item.message)}</strong><p>${escape(item.time.toLocaleTimeString())}</p></div></div>`,
          )
          .join("")}</div>`
      : '<div class="mini-note" style="margin-top:20px">You’re all caught up. Your workspace changes will appear here as you make them.</div>'
  }</section>`;
}
function filters() {
  const users = state.view === "users";
  return `<div class="toolbar"><div class="search"><span aria-hidden="true">⌕</span><label class="sr-only" for="search">Search ${state.view}</label><input id="search" maxlength="100" placeholder="${users ? "Search people (3+ characters)…" : `Search ${state.view}…`}" value="${escape(state.q)}"></div>${
    users
      ? `<label class="sr-only" for="active-filter">Filter status</label><select id="active-filter"><option value="">All statuses</option><option value="true" ${state.active === "true" ? "selected" : ""}>Active</option><option value="false" ${state.active === "false" ? "selected" : ""}>Inactive</option></select><label class="sr-only" for="sort">Sort users</label><select id="sort">${[
          ["username,asc", "Name A–Z"],
          ["username,desc", "Name Z–A"],
          ["createdAt,desc", "Newest first"],
          ["createdAt,asc", "Oldest first"],
          ["email,asc", "Email A–Z"],
          ["id,asc", "ID ascending"],
        ]
          .map(
            ([value, label]) =>
              `<option value="${value}" ${state.sort === value ? "selected" : ""}>${label}</option>`,
          )
          .join("")}</select>`
      : ""
  }<span class="spacer"></span><label class="sr-only" for="page-size">Rows per page</label><select id="page-size">${[10, 20, 50, 100].map((size) => `<option ${size === state.size ? "selected" : ""} value="${size}">${size} per page</option>`).join("")}</select></div>`;
}
function pagination(page, pages, total, relation = false) {
  return `<div class="pagination"><span>${total.toLocaleString()} ${total === 1 ? "record" : "records"}${total ? ` · Page ${page + 1} of ${Math.max(pages, 1)}` : ""}</span><div class="page-buttons"><button class="button" data-action="${relation ? "relation-prev" : "prev"}" ${page <= 0 ? "disabled" : ""}>← Previous</button><button class="button" data-action="${relation ? "relation-next" : "next"}" ${page >= pages - 1 ? "disabled" : ""}>Next →</button></div></div>`;
}
async function listing(token) {
  const kind = state.view;
  let items, total, pages;
  if (kind === "users") {
    if (state.q && state.q.length < 3) {
      if (token === state.request)
        $("#list-content").innerHTML = empty(
          "Keep typing…",
          "Search needs at least 3 characters. Clear the field to see all people.",
        );
      return;
    }
    const result = await api(
      `users?${query({ page: state.page, size: state.size, q: state.q, active: state.active, sort: state.sort })}`,
    );
    items = result.content;
    total = result.totalElements;
    pages = result.totalPages;
  } else {
    const all = await api(kind);
    const filtered = all.filter((item) =>
      `${item.name} ${item.description || ""}`
        .toLowerCase()
        .includes(state.q.toLowerCase()),
    );
    total = filtered.length;
    pages = Math.ceil(total / state.size);
    items = filtered.slice(
      state.page * state.size,
      (state.page + 1) * state.size,
    );
  }
  if (token !== state.request) return;
  if (!items.length && state.page > 0) {
    state.page--;
    return reloadList();
  }
  state.data = items;
  $("#list-content").innerHTML =
    `${items.length ? table(items, kind) : empty(`No ${kind} found`, state.q || state.active ? "Try another search or clear your filters." : `Create your first ${singular(kind)} to get started.`, `<button class="button primary" data-action="create">+ Add ${singular(kind)}</button>`)}${pagination(state.page, pages, total)}`;
  $("#list-count").textContent = `${total.toLocaleString()} ${kind}`;
}
async function reloadList() {
  const token = ++state.request;
  $("#list-content").innerHTML = loading();
  try {
    await listing(token);
  } catch (error) {
    if (token === state.request)
      $("#list-content").innerHTML = failure(error, "retry-list");
  }
}
async function render() {
  const token = ++state.request;
  const view = state.view;
  $$("[data-nav]").forEach((link) => {
    const active = link.dataset.nav === view;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });
  $("#crumb").textContent = title(view);
  $("#page-title").textContent =
    view === "overview" ? "Workspace overview" : title(view);
  $("#page-description").textContent =
    view === "overview"
      ? "A clear view of your people and everything they can do."
      : descriptions[view];
  $("#heading-actions").innerHTML =
    view === "overview"
      ? '<a href="#users" class="button">Manage users ↗</a><button class="button primary" data-action="create-user">+ Add user</button>'
      : `${view === "users" ? '<button class="button" data-action="export">↓ Export CSV</button>' : ""}<button class="button primary" data-action="create">+ Add ${singular(view)}</button>`;
  if (view === "overview") {
    $("#page").innerHTML = loading();
    try {
      await overview(token);
    } catch (error) {
      if (token === state.request)
        $("#page").innerHTML = failure(error, "refresh");
    }
  } else {
    $("#page").innerHTML =
      `<section class="card"><div class="card-header"><div><h2>${view === "users" ? "People directory" : `${title(view)} directory`}</h2><p>Everything you need, in one place.</p></div><span id="list-count" class="result-count"></span></div>${filters()}<div id="list-content">${loading()}</div></section>`;
    try {
      await listing(token);
    } catch (error) {
      if (token === state.request)
        $("#list-content").innerHTML = failure(error, "retry-list");
    }
  }
}
function field(name, label, value = "", opts = {}) {
  return `<label class="field ${opts.full ? "full" : ""}">${label}<input name="${name}" type="${opts.type || "text"}" value="${escape(value)}" ${opts.required === false ? "" : "required"} ${opts.min ? `minlength="${opts.min}"` : ""} maxlength="${opts.max || 100}" ${opts.pattern ? `pattern="${opts.pattern}"` : ""} ${opts.auto ? `autocomplete="${opts.auto}"` : ""}>${opts.help ? `<small>${opts.help}</small>` : ""}</label>`;
}
function modal(heading, description, body, submitText, onSubmit) {
  const dialog = $("#modal");
  dialog.innerHTML = `<form><div class="modal-head"><div><h2 id="modal-title">${escape(heading)}</h2><p>${escape(description)}</p></div><button type="button" class="icon-button" data-action="close-modal" aria-label="Close dialog">×</button></div><div class="modal-body"><div id="form-error" class="form-error" role="alert" hidden></div>${body}</div><div class="modal-actions"><button type="button" class="button" data-action="close-modal">Cancel</button><button type="submit" class="button primary">${escape(submitText)}</button></div></form>`;
  const form = $("form", dialog);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (form.dataset.busy) return;
    form.dataset.busy = "true";
    const buttons = $$("button", form);
    buttons.forEach((button) => (button.disabled = true));
    $("#form-error").hidden = true;
    try {
      await onSubmit(new FormData(form), form);
      if (dialog.open) dialog.close();
    } catch (error) {
      $("#form-error").textContent = error.message;
      $("#form-error").hidden = false;
    } finally {
      delete form.dataset.busy;
      buttons.forEach((button) => (button.disabled = false));
    }
  });
  dialog.oncancel = (event) => {
    if (form.dataset.busy) event.preventDefault();
  };
  dialog.showModal();
}
async function afterMutation(message) {
  record(message);
  await Promise.allSettled([stats(), connection()]);
  await render();
}
function editor(kind, item = null) {
  const edit = !!item;
  const user = kind === "users";
  let body;
  if (user) {
    body = `<div class="form-grid">${!edit ? field("username", "Username", "", { full: true, min: 3, max: 50, pattern: "[a-zA-Z0-9._-]+", auto: "off", help: "3–50 letters, numbers, dots, underscores, or hyphens." }) : ""}${field("firstName", "First name", item?.firstName)}${field("lastName", "Last name", item?.lastName)}${field("email", "Email address", item?.email, { type: "email", full: true })}${!edit ? field("password", "Password", "", { type: "password", min: 8, max: 72, full: true, auto: "new-password", help: "8–72 characters." }) : ""}<label class="check-field"><input type="checkbox" name="active" ${item?.active !== false ? "checked" : ""}> Active account</label></div>`;
  } else {
    const coded = kind !== "groups";
    body = `<div class="form-grid">${field("name", coded ? "Name (UPPER_SNAKE_CASE)" : "Group name", item?.name, { full: true, max: kind === "roles" ? 50 : 100, pattern: coded ? "[A-Z][A-Z0-9_]*" : null, help: coded ? "Start with a letter. Use uppercase letters, numbers, and underscores." : "" })}<label class="field full">Description<textarea name="description" maxlength="255" placeholder="What is this ${singular(kind)} for?">${escape(item?.description)}</textarea><small>Optional · up to 255 characters</small></label></div>`;
  }
  modal(
    `${edit ? "Edit" : "Add"} ${singular(kind)}`,
    edit ? "Update the details below." : "Make room for a new connection.",
    body,
    edit ? "Save changes" : `Create ${singular(kind)}`,
    async (form) => {
      const payload = Object.fromEntries(form);
      if (user) payload.active = form.has("active");
      const saved = await api(
        `${kind}${edit ? `/${item.id}` : ""}`,
        edit ? "PUT" : "POST",
        payload,
      );
      await afterMutation(
        `${title(singular(kind))} ${edit ? "updated" : "created"}`,
      );
      if (
        edit &&
        state.selection?.kind === kind &&
        state.selection.id === item.id
      )
        await openDetail(kind, saved.id, state.tab);
    },
  );
}
function removeEntity(kind, item) {
  modal(
    `Delete ${singular(kind)}?`,
    `This will permanently delete ${item.username || item.name}.`,
    '<div class="warning">Assignments connected to this record will also be removed. This action cannot be undone.</div>',
    "Delete permanently",
    async () => {
      await api(`${kind}/${item.id}`, "DELETE");
      $("#detail").close();
      state.selection = null;
      ++state.detailRequest;
      await afterMutation(`${title(singular(kind))} deleted`);
    },
  );
  $('#modal button[type="submit"]').classList.remove("primary");
  $('#modal button[type="submit"]').classList.add("danger");
}
function password(item) {
  modal(
    "Change password",
    `Set a new password for @${item.username}.`,
    field("newPassword", "New password", "", {
      type: "password",
      min: 8,
      max: 72,
      auto: "new-password",
      help: "8–72 characters. The password is never displayed or stored in this UI.",
    }),
    "Update password",
    async (form) => {
      await api(`users/${item.id}/password`, "PUT", Object.fromEntries(form));
      record("Password updated");
    },
  );
}
async function openDetail(kind, id, tab = "profile") {
  const token = ++state.detailRequest;
  state.selection = { kind, id: Number(id) };
  state.tab = tab;
  state.relationPage = 0;
  const dialog = $("#detail");
  dialog.innerHTML = loading();
  if (!dialog.open) dialog.showModal();
  try {
    const item = await api(`${kind}/${id}`);
    if (token !== state.detailRequest || !dialog.open) return;
    state.selection.item = item;
    dialog.innerHTML = `<div class="detail-head"><span class="avatar tone-${item.id % 4}">${escape(kind === "users" ? initials(item) : icons[kind])}</span><div><div class="eyebrow">${singular(kind).toUpperCase()} DETAILS</div><h2 id="detail-title">${escape(item.username || item.name)}</h2><p>${escape(kind === "users" ? item.email : item.description || "No description yet")}</p></div><button class="icon-button" data-action="close-detail" aria-label="Close details">×</button></div><div class="detail-actions"><button class="button" data-action="edit">Edit details</button>${kind === "users" ? '<button class="button" data-action="password">Change password</button>' : ""}<button class="button danger" data-action="delete">Delete ${singular(kind)}</button></div><div class="tabs" role="tablist" aria-label="Record details">${["profile", ...relations[kind]].map((tab) => `<button role="tab" id="tab-${tab}" data-action="tab" data-tab="${tab}" aria-controls="tab-panel">${tab === "profile" ? "Details" : tab === "access" ? "Effective access" : kind === "users" && tab === "permissions" ? "Direct permissions" : title(tab)}</button>`).join("")}</div><div class="detail-content" id="tab-panel" role="tabpanel" tabindex="0"></div>`;
    await renderTab();
  } catch (error) {
    if (token === state.detailRequest)
      dialog.innerHTML = `<div class="modal-head"><h2 id="detail-title">Unable to load record</h2><button class="icon-button" data-action="close-detail" aria-label="Close details">×</button></div>${failure(error, "retry-detail")}`;
  }
}
function detailCurrent(token) {
  return token === state.detailRequest && $("#detail").open;
}
async function renderTab() {
  const token = ++state.detailRequest;
  const { kind, id, item } = state.selection;
  const tab = state.tab;
  $$(".tabs button").forEach((button) => {
    const active = button.dataset.tab === tab;
    button.classList.toggle("active", active);
    button.setAttribute("aria-selected", String(active));
    button.tabIndex = active ? 0 : -1;
  });
  const panel = $("#tab-panel");
  panel.setAttribute("aria-labelledby", `tab-${tab}`);
  panel.innerHTML = loading();
  if (tab === "profile") {
    const fields =
      kind === "users"
        ? [
            "id",
            "username",
            "firstName",
            "lastName",
            "email",
            "active",
            "createdAt",
            "updatedAt",
          ]
        : ["id", "name", "description", "createdAt", "updatedAt"];
    const labels = {
      id: "Record ID",
      firstName: "First name",
      lastName: "Last name",
      createdAt: "Created",
      updatedAt: "Last updated",
    };
    panel.innerHTML = `<dl class="metadata">${fields.map((key) => `<div><dt>${labels[key] || title(key)}</dt><dd>${key === "active" ? badge(item[key]) : escape(key.endsWith("At") ? new Date(item[key]).toLocaleString() : item[key] || "—")}</dd></div>`).join("")}</dl>`;
    return;
  }
  try {
    if (tab === "access") {
      const [access, directPermissions] = await Promise.all([
        api(`users/${id}/access`),
        api(`users/${id}/permissions`),
      ]);
      access.directPermissions = directPermissions.map(
        (permission) => permission.name,
      );
      if (!detailCurrent(token)) return;
      panel.innerHTML = `<p class="relation-help">Effective permissions combine direct user grants, direct roles, group roles, and permissions granted directly to groups. Removing a direct grant does not remove access still inherited through roles or groups. Account status: ${access.active ? "active" : "inactive"}.</p><div class="access-flow">${[
        ["groups", "Member of groups"],
        ["directRoles", "Direct roles"],
        ["groupRoles", "Inherited group roles"],
        ["directPermissions", "Direct user permissions"],
        ["permissions", "Effective permissions"],
      ]
        .map(
          ([key, label]) =>
            `<section class="access-section"><h3>${label} · ${access[key].length}</h3><div class="chips">${access[key].length ? access[key].map((name) => `<span class="chip">${escape(name)}</span>`).join("") : '<span class="muted">None assigned</span>'}</div></section>`,
        )
        .join("")}</div>`;
      return;
    }
    const paged = tab === "members" || tab === "users";
    const result = await api(
      `${kind}/${id}/${tab}${paged ? `?page=${state.relationPage}&size=10&sort=username,asc` : ""}`,
    );
    if (!detailCurrent(token)) return;
    const items = paged ? result.content : result;
    if (paged && !items.length && state.relationPage > 0) {
      state.relationPage--;
      return renderTab();
    }
    const target = tab === "members" ? "users" : tab;
    const bulk =
      (kind === "users" && ["roles", "groups", "permissions"].includes(tab)) ||
      (kind === "groups" && ["roles", "permissions"].includes(tab)) ||
      (kind === "roles" && tab === "permissions");
    panel.innerHTML = `<p class="relation-help">${relationHelp(kind, tab)}</p><div class="card"><div class="toolbar"><h2>${title(tab)} <span class="result-count">(${paged ? result.totalElements : items.length})</span></h2><span class="spacer"></span>${bulk ? '<button class="button" data-action="replace">Manage all</button>' : ""}<button class="button primary" data-action="link">+ ${tab === "permissions" ? "Grant permission" : `Add ${singular(target)}`}</button></div>${items.length ? table(items, target, true, true) : empty("No connections yet", `Add ${target} to build this relationship.`)}${paged ? pagination(state.relationPage, result.totalPages, result.totalElements, true) : ""}</div>`;
  } catch (error) {
    if (detailCurrent(token)) panel.innerHTML = failure(error, "retry-tab");
  }
}
function relationHelp(kind, tab) {
  if (kind === "users" && tab === "permissions")
    return "Permissions granted directly to this user, independently of roles and groups. Removing a direct grant preserves permissions inherited through other assignments.";
  if (kind === "permissions" && tab === "users")
    return "Users with this permission granted directly. Add or remove direct grants here; inherited access through roles and groups is unchanged.";
  if (kind === "users" && tab === "roles")
    return "Roles assigned directly to this user. Group roles are shown in Effective access.";
  if (kind === "groups" && tab === "permissions")
    return "Permissions granted directly to this group are inherited by every member.";
  if (kind === "permissions")
    return `These ${tab} grant this permission directly. Changes update the owning ${singular(tab)}.`;
  if (kind === "roles" && tab === "users")
    return "Users with this role assigned directly. Group inheritance is separate.";
  return `Manage ${tab} connected to this ${singular(kind)}. Changes take effect immediately.`;
}
async function unlink(targetId) {
  const { kind, id } = state.selection;
  const tab = state.tab;
  modal(
    "Remove connection?",
    "The record will stay in your workspace. Only this assignment will be removed.",
    "<p>Effective access may change for affected users.</p>",
    "Remove connection",
    async () => {
      await api(relationPath(kind, id, tab, targetId), "DELETE");
      record("Connection removed");
      await renderTab();
    },
  );
}
async function linkPicker(bulk = false) {
  const { kind, id } = state.selection;
  const tab = state.tab;
  const target = tab === "members" ? "users" : tab;
  modal(
    bulk ? "Manage assignments" : `Add ${singular(target)}`,
    bulk
      ? "Choose the complete set. Unchecked assignments will be removed."
      : "Select a record to connect. Existing assignments are safe to select again.",
    `${target === "users" ? '<p class="subheading">Search users by name or email (at least 3 characters).</p>' : ""}<label class="sr-only" for="choice-search">Find ${target}</label><input id="choice-search" class="choice-search" maxlength="100" placeholder="Search ${target}…"><div id="choice-results" class="choice-list">${loading()}</div><div id="choice-pages"></div><p id="choice-count" class="muted"></p>`,
    bulk ? "Save assignments" : "Add connection",
    async (form) => {
      if (pickerLoading)
        throw new Error("Wait for assignments to finish loading.");
      if (bulk) {
        await api(`${kind}/${id}/${tab}`, "PUT", { ids: [...selected] });
      } else {
        const targetId = form.get("target");
        if (!targetId) throw new Error(`Select a ${singular(target)} first.`);
        await api(relationPath(kind, id, tab, Number(targetId)), "PUT");
      }
      record(bulk ? "Assignments updated" : "Connection added");
      await renderTab();
    },
  );
  const selected = new Set();
  let all = [],
    pickerPage = 0,
    pickerToken = 0,
    pickerLoading = true,
    debounce;
  const root = $("#modal");
  const pickerForm = $("form", root);
  const pickerCurrent = () => root.open && $("form", root) === pickerForm;
  const submit = $('button[type="submit"]', root);
  submit.disabled = true;
  const updateCount = () => {
    $("#choice-count").textContent = bulk
      ? `${selected.size} selected · saving replaces all assignments`
      : "Select one record to add";
  };
  async function load() {
    if (!pickerCurrent()) return;
    const token = ++pickerToken;
    pickerLoading = true;
    submit.disabled = true;
    $("#choice-results").innerHTML = loading();
    $("#choice-pages").innerHTML = "";
    try {
      const q = $("#choice-search").value.trim();
      if (target === "users" && q && q.length < 3) {
        $("#choice-results").innerHTML =
          '<p class="muted">Enter at least 3 characters to search.</p>';
        return;
      }
      let items,
        pages = 1;
      if (target === "users") {
        const result = await api(
          `users?${query({ q, page: pickerPage, size: 20, sort: "username,asc" })}`,
        );
        items = result.content;
        pages = result.totalPages;
      } else
        items = all.filter((item) =>
          `${item.name} ${item.description || ""}`
            .toLowerCase()
            .includes(q.toLowerCase()),
        );
      if (token !== pickerToken || !pickerCurrent()) return;
      $("#choice-results").innerHTML = items.length
        ? items
            .map(
              (item) =>
                `<label class="choice"><input type="${bulk ? "checkbox" : "radio"}" name="target" value="${item.id}" ${selected.has(item.id) ? "checked" : ""}><span>${escape(item.username || item.name)}</span><small>${escape(item.email || `#${item.id}`)}</small></label>`,
            )
            .join("")
        : '<p class="muted">No matching records. Create one in its directory first.</p>';
      if (target === "users") {
        $("#choice-pages").innerHTML =
          `<div class="pagination"><span>Page ${pickerPage + 1} of ${Math.max(1, pages)}</span><div class="page-buttons"><button type="button" class="button" id="picker-prev" ${pickerPage === 0 ? "disabled" : ""}>Previous</button><button type="button" class="button" id="picker-next" ${pickerPage >= pages - 1 ? "disabled" : ""}>Next</button></div></div>`;
        $("#picker-prev").onclick = () => {
          pickerPage--;
          void load();
        };
        $("#picker-next").onclick = () => {
          pickerPage++;
          void load();
        };
      }
      pickerLoading = false;
      submit.disabled = false;
      updateCount();
    } catch (error) {
      if (token === pickerToken && pickerCurrent()) {
        $("#choice-results").innerHTML =
          `<p class="form-error">${escape(error.message)}</p><button type="button" id="picker-retry" class="button">Try again</button>`;
        $("#picker-retry").onclick = () => void load();
      }
    }
  }
  $("#choice-search").addEventListener("input", () => {
    clearTimeout(debounce);
    pickerPage = 0;
    debounce = setTimeout(load, 250);
  });
  $("#choice-results").addEventListener("change", (event) => {
    const input = event.target;
    if (input.type === "checkbox") {
      if (input.checked) selected.add(Number(input.value));
      else selected.delete(Number(input.value));
      updateCount();
    }
  });
  try {
    if (target !== "users") all = await api(target);
    if (bulk) {
      const current = await api(`${kind}/${id}/${tab}`);
      current.forEach((item) => selected.add(item.id));
    }
    if (pickerCurrent()) await load();
  } catch (error) {
    if (pickerCurrent())
      $("#choice-results").innerHTML =
        `<p class="form-error">${escape(error.message)} Close this dialog and try again.</p>`;
  }
}
async function exportUsers(button) {
  if (state.q && state.q.length < 3)
    return toast("Search requires at least 3 characters.", true);
  button.disabled = true;
  button.textContent = "Exporting…";
  try {
    let after;
    const all = [];
    do {
      const result = await api(
        `users/scroll?${query({ q: state.q, active: state.active, size: 200, after })}`,
      );
      all.push(...result.content);
      after = result.hasNext ? result.nextCursor : null;
    } while (after);
    const url = URL.createObjectURL(
      new Blob(["\ufeff" + csv(all)], { type: "text/csv;charset=utf-8" }),
    );
    const link = document.createElement("a");
    link.href = url;
    link.download = `forma-users-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    record(`Exported ${all.length} users`);
  } catch (error) {
    toast(error.message, true);
  } finally {
    button.disabled = false;
    button.textContent = "↓ Export CSV";
  }
}
const actions = {
  refresh: async () => {
    await Promise.allSettled([stats(), connection()]);
    await render();
    if ($("#detail").open && state.selection)
      await openDetail(state.selection.kind, state.selection.id, state.tab);
  },
  "retry-list": reloadList,
  "retry-tab": renderTab,
  "retry-detail": () =>
    openDetail(state.selection.kind, state.selection.id, state.tab),
  create: () => editor(state.view),
  "create-user": () => editor("users"),
  open: (button) => openDetail(button.dataset.kind, button.dataset.id),
  "related-open": (button) =>
    openDetail(button.dataset.kind, button.dataset.id),
  "close-modal": () => $("#modal").close(),
  "close-detail": () => {
    ++state.detailRequest;
    $("#detail").close();
  },
  edit: () => editor(state.selection.kind, state.selection.item),
  delete: () => removeEntity(state.selection.kind, state.selection.item),
  password: () => password(state.selection.item),
  tab: (button) => {
    state.tab = button.dataset.tab;
    state.relationPage = 0;
    return renderTab();
  },
  prev: () => {
    state.page--;
    return reloadList();
  },
  next: () => {
    state.page++;
    return reloadList();
  },
  "relation-prev": () => {
    state.relationPage--;
    return renderTab();
  },
  "relation-next": () => {
    state.relationPage++;
    return renderTab();
  },
  link: () => linkPicker(),
  replace: () => linkPicker(true),
  unlink: (button) => unlink(Number(button.dataset.id)),
  export: exportUsers,
};
document.addEventListener("click", (event) => {
  const button = event.target.closest("[data-action]");
  if (button && !button.disabled) {
    const action = actions[button.dataset.action];
    if (action)
      Promise.resolve(action(button)).catch((error) =>
        toast(error.message, true),
      );
  }
});
let searchTimer;
document.addEventListener("input", (event) => {
  if (event.target.id === "search") {
    state.q = event.target.value.trim();
    state.page = 0;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(reloadList, 300);
  }
});
document.addEventListener("change", (event) => {
  const id = event.target.id;
  if (["active-filter", "sort", "page-size"].includes(id)) {
    if (id === "active-filter") state.active = event.target.value;
    if (id === "sort") state.sort = event.target.value;
    if (id === "page-size") state.size = Number(event.target.value);
    state.page = 0;
    void reloadList();
  }
});
document.addEventListener("keydown", (event) => {
  if (
    event.target.getAttribute("role") === "tab" &&
    ["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)
  ) {
    event.preventDefault();
    const tabs = $$(".tabs button");
    const index = tabs.indexOf(event.target);
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? tabs.length - 1
          : (index + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) %
            tabs.length;
    tabs[next].focus();
    tabs[next].click();
  }
});
function route() {
  clearTimeout(searchTimer);
  const view = location.hash.slice(1);
  state.view = ["overview", "users", "groups", "roles", "permissions"].includes(
    view,
  )
    ? view
    : "overview";
  state.page = 0;
  state.q = "";
  state.active = "";
  if ($("#detail").open) $("#detail").close();
  ++state.detailRequest;
  void render();
}
window.addEventListener("hashchange", route);
$("#refresh").onclick = () =>
  actions.refresh().catch((error) => toast(error.message, true));
$("#detail").addEventListener("cancel", () => {
  ++state.detailRequest;
});
route();
void stats().catch(() => {});
void connection();
