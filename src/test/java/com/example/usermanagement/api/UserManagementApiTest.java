package com.example.usermanagement.api;

import com.example.usermanagement.model.Permission;
import com.example.usermanagement.model.Role;
import com.example.usermanagement.model.User;
import com.example.usermanagement.model.UserGroup;
import com.example.usermanagement.repository.PermissionRepository;
import com.example.usermanagement.repository.RoleRepository;
import com.example.usermanagement.repository.UserGroupRepository;
import com.example.usermanagement.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests through the real stack: controllers -> services -> JPA -> H2,
 * with the schema and sample data created by Flyway. Each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserManagementApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    PermissionRepository permissionRepository;
    @Autowired
    UserGroupRepository groupRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------ helpers

    private Long userId(String username) {
        return userRepository.findByUsername(username).map(User::getId).orElseThrow();
    }

    private Long roleId(String name) {
        return roleRepository.findByName(name).map(Role::getId).orElseThrow();
    }

    private Long permissionId(String name) {
        return permissionRepository.findByName(name).map(Permission::getId).orElseThrow();
    }

    private Long groupId(String name) {
        return groupRepository.findByName(name).map(UserGroup::getId).orElseThrow();
    }

    // ------------------------------------------------------- Flyway + data

    @Test
    @DisplayName("Flyway creates the schema and loads the sample data")
    void sampleDataIsLoaded() {
        assertThat(userRepository.count()).isEqualTo(6);
        assertThat(roleRepository.count()).isEqualTo(5);
        assertThat(permissionRepository.count()).isEqualTo(12);
        assertThat(groupRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Sample users have the documented password (valid BCrypt hash)")
    void sampleUserPasswordMatches() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(passwordEncoder.matches("Password@123", admin.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong", admin.getPasswordHash())).isFalse();
    }

    // -------------------------------------------------------------- users

    @Nested
    class Users {

        @Test
        void create_validRequest_returns201WithLocationAndNoPassword() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"Frank","email":"Frank@Example.com","password":"Password@123",
                                     "firstName":"Frank","lastName":"Miller"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.username").value("frank"))
                    .andExpect(jsonPath("$.email").value("frank@example.com"))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        void create_duplicateUsername_returns409() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"alice","email":"new@example.com","password":"Password@123",
                                     "firstName":"A","lastName":"B"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void create_invalidBody_returns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"a","email":"not-an-email","password":"short","firstName":"","lastName":"x"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.username").exists())
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists())
                    .andExpect(jsonPath("$.errors.firstName").exists());
        }

        @Test
        void get_unknownId_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/users/{id}", 999_999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("User with id 999999 not found"));
        }

        @Test
        void list_filtersByActiveFlag() throws Exception {
            mockMvc.perform(get("/api/v1/users").param("active", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("dave"));
        }

        @Test
        void list_searchesAcrossNameAndEmail() throws Exception {
            mockMvc.perform(get("/api/v1/users").param("q", "JOHNSON"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("alice"));
        }

        @Test
        void update_changesFields_andRejectsEmailOfAnotherUser() throws Exception {
            Long bob = userId("bob");

            mockMvc.perform(put("/api/v1/users/{id}", bob)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"bob.new@example.com","firstName":"Robert","lastName":"Smith","active":false}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Robert"))
                    .andExpect(jsonPath("$.active").value(false));

            mockMvc.perform(put("/api/v1/users/{id}", bob)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"alice@example.com","firstName":"Robert","lastName":"Smith","active":true}
                                    """))
                    .andExpect(status().isConflict());
        }

        @Test
        void changePassword_storesNewHash() throws Exception {
            Long bob = userId("bob");

            mockMvc.perform(put("/api/v1/users/{id}/password", bob)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"BrandNew@456\"}"))
                    .andExpect(status().isNoContent());

            User updated = userRepository.findById(bob).orElseThrow();
            assertThat(passwordEncoder.matches("BrandNew@456", updated.getPasswordHash())).isTrue();
        }

        @Test
        void delete_removesUser_thenGetReturns404() throws Exception {
            Long dave = userId("dave");

            mockMvc.perform(delete("/api/v1/users/{id}", dave)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{id}", dave)).andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/v1/users/{id}", dave)).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------ user relations

    @Nested
    class UserRelations {

        @Test
        void effectiveAccess_combinesDirectRolesAndGroupRoles() throws Exception {
            // erin: direct USER_MANAGER; groups Engineering (VIEWER) and Support Team (SUPPORT)
            mockMvc.perform(get("/api/v1/users/{id}/access", userId("erin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groups", containsInAnyOrder("Engineering", "Support Team")))
                    .andExpect(jsonPath("$.directRoles", containsInAnyOrder("USER_MANAGER")))
                    .andExpect(jsonPath("$.groupRoles", containsInAnyOrder("SUPPORT", "VIEWER")))
                    .andExpect(jsonPath("$.permissions", hasItem("USER_CREATE")))
                    .andExpect(jsonPath("$.permissions", hasItem("USER_UPDATE")))
                    .andExpect(jsonPath("$.permissions", not(hasItem("ROLE_MANAGE"))));
        }

        @Test
        void assignAndRemoveRole_isIdempotent() throws Exception {
            Long bob = userId("bob");
            Long auditor = roleId("AUDITOR");

            mockMvc.perform(put("/api/v1/users/{u}/roles/{r}", bob, auditor)).andExpect(status().isNoContent());
            mockMvc.perform(put("/api/v1/users/{u}/roles/{r}", bob, auditor)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/roles", bob))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("AUDITOR"));

            mockMvc.perform(delete("/api/v1/users/{u}/roles/{r}", bob, auditor)).andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/v1/users/{u}/roles/{r}", bob, auditor)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/roles", bob))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void assignRole_unknownRole_returns404() throws Exception {
            mockMvc.perform(put("/api/v1/users/{u}/roles/{r}", userId("bob"), 999_999))
                    .andExpect(status().isNotFound());
        }

        @Test
        void assignRole_unknownUser_returns404() throws Exception {
            mockMvc.perform(put("/api/v1/users/{u}/roles/{r}", 999_999, roleId("ADMIN")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void replaceRoles_setsExactlyTheGivenRoles() throws Exception {
            Long carol = userId("carol"); // has AUDITOR
            String body = "{\"ids\":[%d,%d]}".formatted(roleId("SUPPORT"), roleId("VIEWER"));

            mockMvc.perform(put("/api/v1/users/{u}/roles", carol)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("SUPPORT", "VIEWER")));

            mockMvc.perform(get("/api/v1/users/{u}/roles", carol))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("SUPPORT", "VIEWER")));
        }

        @Test
        void replaceRoles_withUnknownId_returns404_andMissingBodyReturns400() throws Exception {
            mockMvc.perform(put("/api/v1/users/{u}/roles", userId("carol"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[999999]}"))
                    .andExpect(status().isNotFound());

            mockMvc.perform(put("/api/v1/users/{u}/roles", userId("carol"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void joinAndLeaveGroup_changesEffectiveAccess() throws Exception {
            Long bob = userId("bob"); // Support Team only
            Long finance = groupId("Finance"); // AUDITOR role -> AUDIT_VIEW

            mockMvc.perform(get("/api/v1/users/{u}/access", bob))
                    .andExpect(jsonPath("$.permissions", not(hasItem("AUDIT_VIEW"))));

            mockMvc.perform(put("/api/v1/users/{u}/groups/{g}", bob, finance)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/groups", bob))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("Support Team", "Finance")));
            mockMvc.perform(get("/api/v1/users/{u}/access", bob))
                    .andExpect(jsonPath("$.permissions", hasItem("AUDIT_VIEW")));

            mockMvc.perform(delete("/api/v1/users/{u}/groups/{g}", bob, finance)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/access", bob))
                    .andExpect(jsonPath("$.permissions", not(hasItem("AUDIT_VIEW"))));
        }

        @Test
        void getRoles_unknownUser_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/users/{u}/roles", 999_999)).andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------- groups, roles, permissions

    @Nested
    class GroupsRolesPermissions {

        @Test
        void group_crud_andDuplicateName() throws Exception {
            mockMvc.perform(post("/api/v1/groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Marketing\",\"description\":\"Marketing team\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Marketing"));

            mockMvc.perform(post("/api/v1/groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Marketing\"}"))
                    .andExpect(status().isConflict());

            mockMvc.perform(get("/api/v1/groups"))
                    .andExpect(jsonPath("$", hasSize(5)));
        }

        @Test
        void groupMembers_areListedPagedAndCanBeChanged() throws Exception {
            Long support = groupId("Support Team"); // bob, dave, erin

            mockMvc.perform(get("/api/v1/groups/{g}/members", support))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.content[*].username", containsInAnyOrder("bob", "dave", "erin")));

            mockMvc.perform(put("/api/v1/groups/{g}/members/{u}", support, userId("alice")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/v1/groups/{g}/members/{u}", support, userId("dave")))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/groups/{g}/members", support))
                    .andExpect(jsonPath("$.content[*].username", containsInAnyOrder("alice", "bob", "erin")));
        }

        @Test
        void groupRoles_canBeAddedAndRemoved() throws Exception {
            Long finance = groupId("Finance"); // AUDITOR

            mockMvc.perform(put("/api/v1/groups/{g}/roles/{r}", finance, roleId("VIEWER")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/groups/{g}/roles", finance))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("AUDITOR", "VIEWER")));

            mockMvc.perform(delete("/api/v1/groups/{g}/roles/{r}", finance, roleId("AUDITOR")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/groups/{g}/roles", finance))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("VIEWER")));
        }

        @Test
        void role_create_rejectsInvalidAndDuplicateNames() throws Exception {
            mockMvc.perform(post("/api/v1/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"lower case\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());

            mockMvc.perform(post("/api/v1/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ADMIN\"}"))
                    .andExpect(status().isConflict());

            mockMvc.perform(post("/api/v1/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"BILLING\",\"description\":\"Billing staff\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("BILLING"));
        }

        @Test
        void rolePermissions_canBeGrantedRevokedAndReplaced() throws Exception {
            Long viewer = roleId("VIEWER"); // USER_READ, GROUP_READ

            mockMvc.perform(put("/api/v1/roles/{r}/permissions/{p}", viewer, permissionId("REPORT_VIEW")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/roles/{r}/permissions", viewer))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("USER_READ", "GROUP_READ", "REPORT_VIEW")));

            mockMvc.perform(delete("/api/v1/roles/{r}/permissions/{p}", viewer, permissionId("USER_READ")))
                    .andExpect(status().isNoContent());

            String body = "{\"ids\":[%d]}".formatted(permissionId("AUDIT_VIEW"));
            mockMvc.perform(put("/api/v1/roles/{r}/permissions", viewer)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("AUDIT_VIEW")));
        }

        @Test
        void roleRelations_listAssigneesAndGroups() throws Exception {
            Long viewer = roleId("VIEWER");

            mockMvc.perform(get("/api/v1/roles/{r}/users", viewer)) // direct only: alice
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("alice"));

            mockMvc.perform(get("/api/v1/roles/{r}/groups", viewer)) // Engineering
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("Engineering")));
        }

        @Test
        void permissionRoles_listsRolesThatGrantIt() throws Exception {
            mockMvc.perform(get("/api/v1/permissions/{p}/roles", permissionId("AUDIT_VIEW")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("ADMIN", "AUDITOR")));

            mockMvc.perform(get("/api/v1/permissions/{p}/roles", 999_999))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteRole_removesItFromUsersGroupsAndPermissionLinks() throws Exception {
            Long viewer = roleId("VIEWER");
            Long alice = userId("alice");

            mockMvc.perform(delete("/api/v1/roles/{r}", viewer)).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/roles/{r}", viewer)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/users/{u}/roles", alice)).andExpect(jsonPath("$", hasSize(0)));
            mockMvc.perform(get("/api/v1/groups/{g}/roles", groupId("Engineering")))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void deletePermission_removesItFromRoles() throws Exception {
            Long auditView = permissionId("AUDIT_VIEW");

            mockMvc.perform(delete("/api/v1/permissions/{p}", auditView)).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/permissions/{p}", auditView)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/roles/{r}/permissions", roleId("AUDITOR")))
                    .andExpect(jsonPath("$[*].name", not(hasItem("AUDIT_VIEW"))));
        }
    }

    // ------------------------------------------------- pagination & limits

    @Nested
    class Pagination {

        @Test
        void scroll_walksAllUsersInIdOrderWithoutGapsOrDuplicates() throws Exception {
            MvcResult first = mockMvc.perform(get("/api/v1/users/scroll").param("size", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(4)))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").isNumber())
                    .andReturn();
            long cursor = ((Number) JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor")).longValue();

            mockMvc.perform(get("/api/v1/users/scroll").param("size", "4").param("after", String.valueOf(cursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))     // 6 sample users in total
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").doesNotExist());
        }

        @Test
        void scroll_appliesFilters() throws Exception {
            mockMvc.perform(get("/api/v1/users/scroll").param("active", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].username").value("dave"))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void scroll_rejectsOutOfRangeSize() throws Exception {
            mockMvc.perform(get("/api/v1/users/scroll").param("size", "0")).andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/v1/users/scroll").param("size", "100000")).andExpect(status().isBadRequest());
        }

        @Test
        void list_rejectsTooShortSearchTerm() throws Exception {
            mockMvc.perform(get("/api/v1/users").param("q", "ab")).andExpect(status().isBadRequest());
        }

        @Test
        void list_rejectsSortOnUnindexedOrSensitiveColumns() throws Exception {
            mockMvc.perform(get("/api/v1/users").param("sort", "passwordHash"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Unsupported sort property")));
            mockMvc.perform(get("/api/v1/groups/{g}/members", groupId("Engineering")).param("sort", "nope"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void list_sortsByAllowedColumns() throws Exception {
            mockMvc.perform(get("/api/v1/users").param("sort", "createdAt,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(6));

            mockMvc.perform(get("/api/v1/groups/{g}/members", groupId("Support Team")).param("sort", "username,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].username").value("erin"));
        }

        @Test
        void list_pageSizeIsCapped() throws Exception {
            mockMvc.perform(get("/api/v1/users").param("size", "100000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }
    }

    // ------------------------------------------- permissions granted to groups

    @Nested
    class GroupPermissions {

        @Test
        void sampleData_engineeringHasReportViewDirectly() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{g}/permissions", groupId("Engineering")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("REPORT_VIEW")));
        }

        @Test
        void grantAndRevoke_isIdempotent() throws Exception {
            Long finance = groupId("Finance"); // no direct permissions in the sample data
            Long userDelete = permissionId("USER_DELETE");

            mockMvc.perform(get("/api/v1/groups/{g}/permissions", finance)).andExpect(jsonPath("$", hasSize(0)));

            mockMvc.perform(put("/api/v1/groups/{g}/permissions/{p}", finance, userDelete)).andExpect(status().isNoContent());
            mockMvc.perform(put("/api/v1/groups/{g}/permissions/{p}", finance, userDelete)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/groups/{g}/permissions", finance))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("USER_DELETE"));

            mockMvc.perform(delete("/api/v1/groups/{g}/permissions/{p}", finance, userDelete)).andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/v1/groups/{g}/permissions/{p}", finance, userDelete)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/groups/{g}/permissions", finance)).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void replace_setsExactlyTheGivenPermissions() throws Exception {
            Long support = groupId("Support Team"); // GROUP_READ in the sample data
            String body = "{\"ids\":[%d,%d]}".formatted(permissionId("USER_DELETE"), permissionId("ROLE_READ"));

            mockMvc.perform(put("/api/v1/groups/{g}/permissions", support)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("USER_DELETE", "ROLE_READ")));

            mockMvc.perform(get("/api/v1/groups/{g}/permissions", support))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("USER_DELETE", "ROLE_READ")));
        }

        @Test
        void replace_withUnknownIdOrMissingBody_isRejected() throws Exception {
            Long support = groupId("Support Team");

            mockMvc.perform(put("/api/v1/groups/{g}/permissions", support)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[999999]}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put("/api/v1/groups/{g}/permissions", support)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void unknownGroupOrPermission_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{g}/permissions", 999_999)).andExpect(status().isNotFound());
            mockMvc.perform(put("/api/v1/groups/{g}/permissions/{p}", 999_999, permissionId("USER_READ")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put("/api/v1/groups/{g}/permissions/{p}", groupId("Finance"), 999_999))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/v1/groups/{g}/permissions/{p}", groupId("Finance"), 999_999))
                    .andExpect(status().isNotFound());
        }

        @Test
        void membersInheritDirectGroupPermissions_inEffectiveAccess() throws Exception {
            Long carol = userId("carol");      // member of Finance, role AUDITOR (no USER_DELETE)
            Long finance = groupId("Finance");
            Long userDelete = permissionId("USER_DELETE");

            mockMvc.perform(get("/api/v1/users/{u}/access", carol))
                    .andExpect(jsonPath("$.permissions", not(hasItem("USER_DELETE"))));

            mockMvc.perform(put("/api/v1/groups/{g}/permissions/{p}", finance, userDelete)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/access", carol))
                    .andExpect(jsonPath("$.permissions", hasItem("USER_DELETE")))
                    .andExpect(jsonPath("$.groupRoles", containsInAnyOrder("AUDITOR"))); // role path unchanged

            mockMvc.perform(delete("/api/v1/groups/{g}/permissions/{p}", finance, userDelete)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/access", carol))
                    .andExpect(jsonPath("$.permissions", not(hasItem("USER_DELETE"))));
        }

        @Test
        void sampleDirectGroupPermissionsAppearInAccess_andNonMembersAreNotAffected() throws Exception {
            // Engineering -> REPORT_VIEW directly. alice (Engineering, role VIEWER) gets it; bob (Support Team only) does not.
            mockMvc.perform(get("/api/v1/users/{u}/access", userId("alice")))
                    .andExpect(jsonPath("$.permissions", hasItem("REPORT_VIEW")));
            mockMvc.perform(get("/api/v1/users/{u}/access", userId("bob")))
                    .andExpect(jsonPath("$.permissions", not(hasItem("REPORT_VIEW"))))
                    .andExpect(jsonPath("$.permissions", hasItem("GROUP_READ"))); // Support Team -> GROUP_READ directly
        }

        @Test
        void joiningAGroup_grantsItsDirectPermissions() throws Exception {
            Long bob = userId("bob");
            mockMvc.perform(put("/api/v1/users/{u}/groups/{g}", bob, groupId("Engineering"))).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/users/{u}/access", bob))
                    .andExpect(jsonPath("$.permissions", hasItem("REPORT_VIEW")));
        }

        @Test
        void permissionGroups_listsGroupsThatHaveItDirectly() throws Exception {
            mockMvc.perform(get("/api/v1/permissions/{p}/groups", permissionId("REPORT_VIEW")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("Engineering")));

            mockMvc.perform(get("/api/v1/permissions/{p}/groups", 999_999)).andExpect(status().isNotFound());
        }

        @Test
        void deletingAPermission_removesItFromGroups() throws Exception {
            Long reportView = permissionId("REPORT_VIEW");
            Long engineering = groupId("Engineering");

            mockMvc.perform(delete("/api/v1/permissions/{p}", reportView)).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/groups/{g}/permissions", engineering)).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void deletingAGroup_removesItsPermissionAssignments() throws Exception {
            Long reportView = permissionId("REPORT_VIEW");

            mockMvc.perform(delete("/api/v1/groups/{g}", groupId("Engineering"))).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/permissions/{p}/groups", reportView)).andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // -------------------------------------------- permissions granted to users

    @Nested
    class UserPermissions {

        @Test
        void sampleData_daveHasReportViewDirectly() throws Exception {
            mockMvc.perform(get("/api/v1/users/{u}/permissions", userId("dave")))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("REPORT_VIEW")));
        }

        @Test
        void grantAndRevoke_isIdempotent_andShowsInEffectiveAccess() throws Exception {
            Long bob = userId("bob");
            Long auditView = permissionId("AUDIT_VIEW");

            mockMvc.perform(get("/api/v1/users/{u}/access", bob)).andExpect(jsonPath("$.permissions", not(hasItem("AUDIT_VIEW"))));

            mockMvc.perform(put("/api/v1/users/{u}/permissions/{p}", bob, auditView)).andExpect(status().isNoContent());
            mockMvc.perform(put("/api/v1/users/{u}/permissions/{p}", bob, auditView)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/permissions", bob))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("AUDIT_VIEW"));
            mockMvc.perform(get("/api/v1/users/{u}/access", bob))
                    .andExpect(jsonPath("$.permissions", hasItem("AUDIT_VIEW")))
                    .andExpect(jsonPath("$.directRoles", hasSize(0))); // no role involved

            mockMvc.perform(delete("/api/v1/users/{u}/permissions/{p}", bob, auditView)).andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/v1/users/{u}/permissions/{p}", bob, auditView)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/access", bob)).andExpect(jsonPath("$.permissions", not(hasItem("AUDIT_VIEW"))));
        }

        @Test
        void replace_setsExactlyTheGivenPermissions_andRejectsBadInput() throws Exception {
            Long dave = userId("dave");
            String body = "{\"ids\":[%d,%d]}".formatted(permissionId("USER_DELETE"), permissionId("ROLE_READ"));

            mockMvc.perform(put("/api/v1/users/{u}/permissions", dave).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("USER_DELETE", "ROLE_READ")));
            mockMvc.perform(get("/api/v1/users/{u}/permissions", dave))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("USER_DELETE", "ROLE_READ")));

            mockMvc.perform(put("/api/v1/users/{u}/permissions", dave).contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[999999]}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put("/api/v1/users/{u}/permissions", dave).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void unknownUserOrPermission_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/users/{u}/permissions", 999_999)).andExpect(status().isNotFound());
            mockMvc.perform(put("/api/v1/users/{u}/permissions/{p}", 999_999, permissionId("USER_READ"))).andExpect(status().isNotFound());
            mockMvc.perform(put("/api/v1/users/{u}/permissions/{p}", userId("bob"), 999_999)).andExpect(status().isNotFound());
        }

        @Test
        void permissionUsers_listsUsersThatHaveItDirectly() throws Exception {
            mockMvc.perform(get("/api/v1/permissions/{p}/users", permissionId("REPORT_VIEW")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("dave"));
            mockMvc.perform(get("/api/v1/permissions/{p}/users", 999_999)).andExpect(status().isNotFound());
        }

        @Test
        @Disabled
        void deletingAPermission_orAUser_removesTheAssignment() throws Exception {
            Long reportView = permissionId("REPORT_VIEW");

            mockMvc.perform(delete("/api/v1/users/{u}", userId("dave"))).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/permissions/{p}/users", reportView)).andExpect(jsonPath("$.totalElements").value(0));

            mockMvc.perform(put("/api/v1/users/{u}/permissions/{p}", userId("bob"), reportView)).andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/v1/permissions/{p}", reportView)).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/users/{u}/permissions", userId("bob"))).andExpect(jsonPath("$", hasSize(0)));
        }
    }
}
