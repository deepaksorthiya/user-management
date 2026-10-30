# user-management

Spring Boot 4.1 / Java 21 REST API for **users, groups, roles and permissions**
(Spring Data JPA + Hibernate, Flyway, H2 now, PostgreSQL later). Version `0.1.0`.

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
 │                       │
 └─< group_members >─ UserGroup ──< group_roles >────┘
```

Effective access of a user = **direct roles + roles of every group the user is in**,
flattened into permissions (`GET /users/{id}/access`).

| Entity      | Table         | Owns (JPA `@ManyToMany`)                          |
|-------------|---------------|---------------------------------------------------|
| `User`      | `users`       | `roles` (user_roles), `userGroups` (group_members) |
| `UserGroup` | `user_groups` | `roles` (group_roles)                             |
| `Role`      | `roles`       | `permissions` (role_permissions)                  |
| `Permission`| `permissions` | nothing (looked up through queries)               |

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
| Method | Path | |
|---|---|---|
| GET | `/users?q=&active=` | search + page |
| GET | `/users/{id}` | |
| POST | `/users` | `201` + `Location` |
| PUT | `/users/{id}` | email, firstName, lastName, active |
| PUT | `/users/{id}/password` | `204` |
| DELETE | `/users/{id}` | `204` |

### User relations — `UserRelationController`
| Method | Path | |
|---|---|---|
| GET / PUT | `/users/{id}/roles` | list / replace all (`{"ids":[1,2]}`) |
| PUT / DELETE | `/users/{id}/roles/{roleId}` | assign / remove |
| GET / PUT | `/users/{id}/groups` | list / replace all |
| PUT / DELETE | `/users/{id}/groups/{groupId}` | join / leave |
| GET | `/users/{id}/access` | groups, direct roles, group roles, effective permissions |

### Groups — `GroupController`, `GroupRelationController`
| Method | Path | |
|---|---|---|
| GET, POST | `/groups` | |
| GET, PUT, DELETE | `/groups/{id}` | |
| GET | `/groups/{id}/members` | paged |
| PUT / DELETE | `/groups/{id}/members/{userId}` | add / remove member |
| GET / PUT | `/groups/{id}/roles` | list / replace all |
| PUT / DELETE | `/groups/{id}/roles/{roleId}` | add / remove |

### Roles — `RoleController`, `RoleRelationController`
| Method | Path | |
|---|---|---|
| GET, POST | `/roles` | names are `UPPER_SNAKE_CASE` |
| GET, PUT, DELETE | `/roles/{id}` | |
| GET / PUT | `/roles/{id}/permissions` | list / replace all |
| PUT / DELETE | `/roles/{id}/permissions/{permissionId}` | grant / revoke |
| GET | `/roles/{id}/users` | users with the role directly (paged) |
| GET | `/roles/{id}/groups` | groups with the role |

### Permissions — `PermissionController`, `PermissionRelationController`
| Method | Path | |
|---|---|---|
| GET, POST | `/permissions` | |
| GET, PUT, DELETE | `/permissions/{id}` | |
| GET | `/permissions/{id}/roles` | roles that grant it (grant/revoke is done on the role) |

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
├── migration/    V1 core tables, V2 join tables      <- every environment
└── sample-data/  V1000__sample_data.sql               <- default (H2) profile only
```

* Hibernate runs with `ddl-auto: validate`; **Flyway owns the schema**.
* The SQL is portable (`GENERATED BY DEFAULT AS IDENTITY`, `TIMESTAMP`, `BOOLEAN`), and H2 runs in
  `MODE=PostgreSQL`, so the same migrations run on both databases.
* Sample data is inserted without hard-coded ids (rows are linked by natural keys), so it never
  desynchronizes identity sequences.
* New schema changes: add `V3__...sql` to `db/migration`. Never edit an applied migration.

## Moving to PostgreSQL

The PostgreSQL driver and `flyway-database-postgresql` are already on the classpath.

```bash
docker compose up -d                         # local Postgres 17
DB_PASSWORD=usermgmt mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

The `postgres` profile (`application-postgres.yaml`) reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
from the environment and loads **only** `db/migration` (no sample data). Nothing else changes.

Before production also consider: authentication/authorization for this API (it is open right now),
a sequence-based id strategy if you need JDBC insert batching (`IDENTITY` disables it), and
case-insensitive uniqueness for group names (`citext` or a lower(name) unique index).

## Tests

* `UserServiceTest` – Mockito unit tests (success + conflict + not found).
* `UserManagementApiTest` – full-stack MockMvc tests against H2 with the real Flyway migrations and
  sample data (CRUD, validation errors, relations, effective access, cascade deletes). Rolled back per test.
