import { test } from "node:test";
import assert from "node:assert/strict";
import {
  api,
  ApiError,
  query,
  relationPath,
  csv,
} from "../src/main/resources/static/api.js";

test("API handles JSON, empty mutations, validation errors and connection failures", async () => {
  const original = globalThis.fetch;
  try {
    let received;
    globalThis.fetch = async (...args) => {
      received = args;
      return new Response(JSON.stringify({ id: 1 }), { status: 201 });
    };
    assert.deepEqual(await api("users", "POST", { username: "alice" }), {
      id: 1,
    });
    assert.equal(received[0], "/api/v1/users");
    assert.equal(received[1].headers["Content-Type"], "application/json");
    assert.deepEqual(JSON.parse(received[1].body), { username: "alice" });
    globalThis.fetch = async () => new Response(null, { status: 204 });
    assert.equal(await api("users/1", "DELETE"), null);
    globalThis.fetch = async () =>
      new Response(
        JSON.stringify({
          detail: "Validation failed",
          errors: { email: "Invalid email" },
        }),
        { status: 400 },
      );
    await assert.rejects(
      api("users"),
      (error) =>
        error instanceof ApiError &&
        error.status === 400 &&
        error.message.includes("email: Invalid email"),
    );
    globalThis.fetch = async () => {
      throw new Error("offline");
    };
    await assert.rejects(api("users"), /Cannot connect/);
  } finally {
    globalThis.fetch = original;
  }
});
test("query encodes filters and preserves false/zero", () => {
  assert.equal(
    query({
      q: "a & b",
      page: 0,
      active: false,
      empty: "",
      missing: undefined,
    }),
    "q=a+%26+b&page=0&active=false",
  );
});
test("all forward and reverse assignment routes use their owning API", () => {
  for (const [kind, relation, path] of [
    ["users", "roles", "users/1/roles/2"],
    ["users", "groups", "users/1/groups/2"],
    ["groups", "members", "groups/1/members/2"],
    ["groups", "roles", "groups/1/roles/2"],
    ["groups", "permissions", "groups/1/permissions/2"],
    ["roles", "permissions", "roles/1/permissions/2"],
    ["roles", "users", "users/2/roles/1"],
    ["roles", "groups", "groups/2/roles/1"],
    ["permissions", "roles", "roles/2/permissions/1"],
    ["permissions", "groups", "groups/2/permissions/1"],
  ])
    assert.equal(relationPath(kind, 1, relation, 2), path);
});
test("CSV escapes delimiters, quotes and formulas", () => {
  const result = csv([
    {
      id: 1,
      username: "=SUM(1,2)",
      email: 'a"b@example.com',
      firstName: "a,b",
      lastName: "<script>",
      active: true,
    },
  ]);
  assert.match(result, /"'=SUM\(1,2\)"/);
  assert.match(result, /"a""b@example.com"/);
  assert.match(result, /"a,b"/);
  assert.equal(result.split("\r\n").length, 2);
});
