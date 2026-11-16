export class ApiError extends Error {
  constructor(status, problem) {
    const fields = problem.errors
      ? Object.entries(problem.errors)
          .map(([key, value]) => `${key}: ${value}`)
          .join("\n")
      : "";
    super(
      [problem.detail || problem.title || `Request failed (${status})`, fields]
        .filter(Boolean)
        .join("\n"),
    );
    this.status = status;
  }
}
export async function api(path, method = "GET", body) {
  let response;
  try {
    response = await fetch(`/api/v1/${path}`, {
      method,
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new Error(
      "Cannot connect to the workspace. Check the server and try again.",
    );
  }
  if (!response.ok) {
    let problem;
    try {
      problem = await response.json();
    } catch {
      problem = { title: response.statusText };
    }
    throw new ApiError(response.status, problem);
  }
  return response.status === 204 ? null : response.json();
}
export function query(values) {
  return new URLSearchParams(
    Object.entries(values).filter(([, value]) => value !== "" && value != null),
  ).toString();
}
// Reverse relationships are always changed through their owning API.
export function relationPath(kind, id, relation, targetId) {
  if (kind === "roles" && relation === "users")
    return `users/${targetId}/roles/${id}`;
  if (kind === "roles" && relation === "groups")
    return `groups/${targetId}/roles/${id}`;
  if (kind === "permissions")
    return `${relation}/${targetId}/permissions/${id}`;
  return `${kind}/${id}/${relation}/${targetId}`;
}
export function csvCell(value) {
  return `"${String(value ?? "").replace(/"/g, '""')}"`;
}
export function csv(rows) {
  const keys = [
    "id",
    "username",
    "email",
    "firstName",
    "lastName",
    "active",
    "createdAt",
    "updatedAt",
  ];
  // Neutralize spreadsheet formulas in exported user-controlled text.
  const safe = (value) =>
    typeof value === "string" && /^[=+\-@\t\r]/.test(value)
      ? `'${value}`
      : value;
  return [
    keys.map(csvCell).join(","),
    ...rows.map((row) => keys.map((key) => csvCell(safe(row[key]))).join(",")),
  ].join("\r\n");
}
