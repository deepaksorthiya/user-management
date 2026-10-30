package com.example.usermanagement.api;

import com.example.usermanagement.model.Permission;
import com.example.usermanagement.model.Role;
import com.example.usermanagement.model.User;
import com.example.usermanagement.model.UserGroup;
import com.example.usermanagement.repository.PermissionRepository;
import com.example.usermanagement.repository.RoleRepository;
import com.example.usermanagement.repository.UserGroupRepository;
import com.example.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
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
}
