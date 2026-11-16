# user-management

Spring Boot 4.1 / Java 25 REST API for **users, groups, roles and permissions**
(Spring Data JPA + Hibernate, Flyway, H2 now, PostgreSQL later). Version `0.3.0`.

> `groupId` is the placeholder `com.example`; change it (and the package) to yours.
> If you are on another Boot 4.x release, change the parent version in `pom.xml`.

## Run

```bash
mvn spring-boot:run          # H2 in-memory + schema + sample data
mvn test
```

* API base: `http://localhost:8080/api/v1`
* Health: `http://localhost:8080/actuator/health`
* Every sample user has the password `Password@123` (BCrypt hashed in the DB).

## Domain model

```
User ──< user_roles >── Role ──< role_permissions >── Permission
 │                       │                                  │
 └─< group_members >─ UserGroup ──< group_roles >───────────┤
                         └──────< group_permissions >───────┘
```

Effective access of a user = the union of

1. permissions of the roles assigned **directly to the user**,
2. permissions of the roles assigned to **every group the user is in**,
3. permissions granted **directly to every group the user is in** (`group_permissions`),

flattened and de-duplicated (`GET /users/{id}/access`, one SQL statement for the permissions).

| Entity       | Table         | Owns (JPA `@ManyToMany`)                                 |
|--------------|---------------|----------------------------------------------------------|
| `User`       | `users`       | `roles` (user_roles), `userGroups` (group_members)       |
| `UserGroup`  | `user_groups` | `roles` (group_roles), `permissions` (group_permissions) |
| `Role`       | `roles`       | `permissions` (role_permissions)                         |
| `Permission` | `permissions` | nothing (looked up through queries)                      |

Design notes

* Relations are mapped **unidirectionally from the owning side**. "Who has this role?" is answered
  with repository queries instead of huge lazy inverse collections.
* `UserGroup`/`user_groups` instead of `Group`/`groups`: `GROUPS` is reserved in H2 and HQL.
* Join tables use `ON DELETE CASCADE`, so deleting a role/permission/group cleans up assignments.
* Entities never leave the service layer; the API uses records (no password hash is ever returned).
* `@Version` optimistic locking on every entity (concurrent edit -> `409`).

## Package structure

```
com.example.usermanagement
├── controller/   XController (CRUD)  +  XRelationController (relations), for User, Group, Role, Permission
├── service/      XService            +  XRelationService
├── repository/   Spring Data repositories, UserSpecifications (dynamic search)
├── model/        JPA entities (BaseEntity: id, audit timestamps, version)
├── dto/          request/response records, grouped per entity (+ common)
├── exception/    ResourceNotFound, DuplicateResource, GlobalExceptionHandler (RFC 9457 Problem Details)
└── config/       JPA auditing, PasswordEncoder
```

## REST API

Lists of users / members / role assignees are **paged**: `?page=0&size=20&sort=username,asc`.
`PUT`/`DELETE` on a relation are idempotent and return `204`.

### Users — `UserController`

| Method | Path                                    |                                                                                                    |
|--------|-----------------------------------------|----------------------------------------------------------------------------------------------------|
| GET    | `/users?q=&active=&page=&size=&sort=`   | search + offset page (sort: `id`, `username`, `email`, `createdAt`; `q` >= 3 chars; `size` <= 100) |
| GET    | `/users/scroll?q=&active=&after=&size=` | **keyset** page for big data / exports (`size` <= 200, pass `nextCursor` as `after`)               |
| GET    | `/users/{id}`                           |                                                                                                    |
| POST   | `/users`                                | `201` + `Location`                                                                                 |
| PUT    | `/users/{id}`                           | email, firstName, lastName, active                                                                 |
| PUT    | `/users/{id}/password`                  | `204`                                                                                              |
| DELETE | `/users/{id}`                           | `204`                                                                                              |

### User relations — `UserRelationController`

| Method       | Path                           |                                                          |
|--------------|--------------------------------|----------------------------------------------------------|
| GET / PUT    | `/users/{id}/roles`            | list / replace all (`{"ids":[1,2]}`)                     |
| PUT / DELETE | `/users/{id}/roles/{roleId}`   | assign / remove                                          |
| GET / PUT    | `/users/{id}/groups`           | list / replace all                                       |
| PUT / DELETE | `/users/{id}/groups/{groupId}` | join / leave                                             |
| GET          | `/users/{id}/access`           | groups, direct roles, group roles, effective permissions |

### Groups — `GroupController`, `GroupRelationController`

| Method           | Path                                      |                                                                                     |
|------------------|-------------------------------------------|-------------------------------------------------------------------------------------|
| GET, POST        | `/groups`                                 |                                                                                     |
| GET, PUT, DELETE | `/groups/{id}`                            |                                                                                     |
| GET              | `/groups/{id}/members`                    | paged                                                                               |
| PUT / DELETE     | `/groups/{id}/members/{userId}`           | add / remove member                                                                 |
| GET / PUT        | `/groups/{id}/roles`                      | list / replace all                                                                  |
| PUT / DELETE     | `/groups/{id}/roles/{roleId}`             | add / remove                                                                        |
| GET / PUT        | `/groups/{id}/permissions`                | permissions granted **directly** to the group: list / replace all (`{"ids":[1,2]}`) |
| PUT / DELETE     | `/groups/{id}/permissions/{permissionId}` | grant / revoke (idempotent, `204`)                                                  |

### Roles — `RoleController`, `RoleRelationController`

| Method           | Path                                     |                                      |
|------------------|------------------------------------------|--------------------------------------|
| GET, POST        | `/roles`                                 | names are `UPPER_SNAKE_CASE`         |
| GET, PUT, DELETE | `/roles/{id}`                            |                                      |
| GET / PUT        | `/roles/{id}/permissions`                | list / replace all                   |
| PUT / DELETE     | `/roles/{id}/permissions/{permissionId}` | grant / revoke                       |
| GET              | `/roles/{id}/users`                      | users with the role directly (paged) |
| GET              | `/roles/{id}/groups`                     | groups with the role                 |

### Permissions — `PermissionController`, `PermissionRelationController`

| Method           | Path                       |                                                                          |
|------------------|----------------------------|--------------------------------------------------------------------------|
| GET, POST        | `/permissions`             |                                                                          |
| GET, PUT, DELETE | `/permissions/{id}`        |                                                                          |
| GET              | `/permissions/{id}/roles`  | roles that grant it (grant/revoke is done on the role)                   |
| GET              | `/permissions/{id}/groups` | groups that have it granted directly (grant/revoke is done on the group) |

Errors are Problem Details JSON: `404` unknown id, `409` duplicate / concurrent edit,
`400` validation (with an `errors` map of field -> message).

### Quick try

```bash
curl localhost:8080/api/v1/users/2/access
curl -X POST localhost:8080/api/v1/users -H "Content-Type: application/json" \
  -d "{\"username\":\"frank\",\"email\":\"frank@example.com\",\"password\":\"Password@123\",\"firstName\":\"Frank\",\"lastName\":\"Miller\"}"
curl -X PUT localhost:8080/api/v1/users/3/roles/4
```

## Database & Flyway

```
src/main/resources/db
├── migration/    V1 core tables, V2 join tables, V3 indexes, V6 group_permissions  <- every environment
└── sample-data/  V1000 sample data, V1001 sample group permissions   <- default (H2) profile only
```

* Hibernate runs with `ddl-auto: validate`; **Flyway owns the schema**.
* The SQL is portable (`GENERATED BY DEFAULT AS IDENTITY`, `TIMESTAMP`, `BOOLEAN`), and H2 runs in
  `MODE=PostgreSQL`, so the same migrations run on both databases.
* Sample data is inserted without hard-coded ids (rows are linked by natural keys), so it never
  desynchronizes identity sequences.
* New schema changes: add `V3__...sql` to `db/migration`. Never edit an applied migration.

## Performance & scalability (millions of rows)

| Area             | What is done                                                                                                                                   | Where                                                                       |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| Pagination       | Offset pages for UI, **keyset (seek) pagination** for deep / bulk reads; COUNT skipped when not needed; page size capped at 100                | `UserSearchRepositoryImpl`, `GET /users/scroll`, `spring.data.web.pageable` |
| Sorting          | Only indexed columns may be sorted on (`id`, `username`, `email`, `createdAt`) -> `400` otherwise                                              | `service/Sorts`                                                             |
| Projections      | List endpoints select only the needed columns straight into records (no entity, no `password_hash`, no dirty checking)                         | `UserSearchRepositoryImpl`, `UserRepository`                                |
| Effective access | 5 light statements returning strings; permissions via one `UNION` SQL                                                                          | `UserRelationService#getAccess`, `PermissionRepository`                     |
| Indexes          | `(active,id)`, `(created_at,id)`; join-table PKs + reverse indexes; PostgreSQL GIN trigram indexes for `contains` search, built `CONCURRENTLY` | `V3`, `db/postgres/V4,V5`                                                   |
| N+1 / batching   | `default_batch_fetch_size=50`, JDBC `batch_size=50`, `order_inserts/updates`, `fail_on_pagination_over_collection_fetch`                       | `application.yaml`                                                          |
| Connections      | Fixed-size Hikari pool, 3 s acquire timeout, `auto-commit=false` + `provider_disables_autocommit`, leak detection (postgres)                   | `application*.yaml`                                                         |
| Safety nets      | Slow-query log (500 ms), Hibernate query timeout 10 s and `reWriteBatchedInserts` (postgres)                                                   | `application*.yaml`                                                         |
| Caching          | Roles / permissions / groups lists cached (Caffeine, 5 min, evicted **after commit**)                                                          | `CacheConfig`, services                                                     |
| Regression tests | Assert SQL statement counts per endpoint (no N+1, no stray COUNT)                                                                              | `QueryCountTest`                                                            |

Notes

* Trigram search needs `CREATE EXTENSION pg_trgm` (V4). On managed PostgreSQL the migration user must be allowed
  to create extensions. Four GIN indexes cost write throughput and disk - drop the ones you do not search.
* `V5` must contain only `CREATE INDEX CONCURRENTLY` statements (they cannot run inside a transaction).
* The pool size is a **database** capacity decision. With virtual threads enabled, thousands of requests can wait on
  a pool of 20, so keep `connection-timeout` short and scale the pool by what PostgreSQL can serve.
* IDs use `IDENTITY`, which disables JDBC insert batching. For bulk loading use `JdbcTemplate.batchUpdate` or
  `COPY`, or switch to pooled sequences (`allocationSize = 50`) in a new migration.
* The cache is per instance; staleness is bounded by the 5-minute TTL. Use Redis if you need exact invalidation.

## Moving to PostgreSQL

The PostgreSQL driver and `flyway-database-postgresql` are already on the classpath.

```bash
docker compose up -d                         # local Postgres 17
DB_PASSWORD=usermgmt mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

The `postgres` profile (`application-postgres.yaml`) reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
(and optionally `DB_POOL_SIZE`) from the environment and loads `db/migration` + `db/postgres`
(PostgreSQL-only index migrations), **never** the sample data.

Before production also consider: authentication/authorization for this API (it is open right now)
and case-insensitive uniqueness for group names (`citext` or a lower (name) unique index).

## Tests

* `UserServiceTest` – Mockito unit tests (success + conflict + not found).
* `QueryCountTest` – asserts the number of SQL statements per endpoint (N+1 / COUNT regressions).
* `UserManagementApiTest` – full-stack MockMvc tests against H2 with the real Flyway migrations and
  sample data (CRUD, validation errors, relations, effective access, cascade deletes). Rolled back per test.

## Forma web workspace

A responsive, dependency-free management UI is bundled into the Spring Boot JAR.
Start the application and open **http://localhost:8080/**. Java **25** is required
by `pom.xml`.

The UI uses the existing `/api/v1` endpoints on the same origin. No separate
frontend server, build, external font, CDN, or CORS configuration is required.

- Overview: live record counts, recent users, API health, and activity from this browser session.
- Users: create/read/update/delete, active status, password changes, server search
  (three-character minimum), status filters, sorting, pagination, and filtered CSV
  export using the keyset endpoint. Export includes all matching users, in ID order.
- Groups, roles, and permissions: CRUD, local search, and pagination.
- Relationships: assign/remove user roles and groups; group members, roles, and
  direct permissions; role permissions, users, and groups; permission roles and
  groups. Reverse views change assignments through the owning API.
- “Manage all” replaces complete assignment sets for user roles/groups, group
  roles/permissions, and role permissions. Empty selection clears assignments.
- Effective access shows groups, direct roles, inherited roles, and the combined
  permission set. This reflects the existing API; it does not add authentication
  or change the backend's authorization behavior.
- Forms retain input on API errors, destructive actions require confirmation, and
  relationship and user pickers handle large user directories with server pagination.
  Dialogs support Escape and tab navigation; detail tabs support arrow/Home/End keys.

### Verification

```bash
bash mvnw clean verify    # Java tests and executable JAR (requires JDK 25)
npm ci                   # Development-only DOM test dependency
npm test                 # API client and UI interaction regression tests
# With a disposable application instance running:
python tests/api-smoke.py http://127.0.0.1:8080
node tests/live-ui.mjs http://127.0.0.1:8080 # Actual UI code against live API
```

The DOM tests exercise rendered forms, API payloads, errors, all ten relationship
views, bulk clearing, keyboard tabs, and untrusted text escaping. They run without
a browser and do not establish visual rendering. The live UI test executes the actual frontend code against the running backend
using a DOM environment. It checks form submissions, assignment changes, inherited
permissions, and backend validation without a browser engine. The live smoke test creates
uniquely named records, exercises actual API operations, and deletes its records
in a `finally` block. Run it against a disposable development/test database.
The backend's default H2 database is in memory; use the existing PostgreSQL profile
when durable storage is needed. Frontend test dependencies are not packaged in the JAR.
