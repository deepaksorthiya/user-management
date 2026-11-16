package com.example.usermanagement.api;

import com.example.usermanagement.model.Role;
import com.example.usermanagement.model.User;
import com.example.usermanagement.model.UserGroup;
import com.example.usermanagement.repository.RoleRepository;
import com.example.usermanagement.repository.UserGroupRepository;
import com.example.usermanagement.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards against performance regressions (N+1, accidental entity loading, extra COUNT queries)
 * by asserting how many SQL statements each endpoint prepares.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
class QueryCountTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    UserGroupRepository groupRepository;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private long statementsFor(ThrowingRunnable request) throws Exception {
        statistics.clear();
        request.run();
        return statistics.getPrepareStatementCount();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    @DisplayName("effective access: at most 5 statements, independent of how many roles/groups/permissions exist")
    void access_usesAtMostFiveStatements() throws Exception {
        Long erin = userRepository.findByUsername("erin").map(User::getId).orElseThrow();

        long statements = statementsFor(() ->
                mockMvc.perform(get("/api/v1/users/{id}/access", erin)).andExpect(status().isOk()));

        assertThat(statements).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("keyset scroll: exactly one SELECT, no COUNT")
    void scroll_usesOneStatement() throws Exception {
        long statements = statementsFor(() ->
                mockMvc.perform(get("/api/v1/users/scroll").param("size", "3")).andExpect(status().isOk()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @DisplayName("first page that is not full: COUNT query is skipped")
    void list_firstPartialPage_skipsCountQuery() throws Exception {
        long statements = statementsFor(() ->
                mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @DisplayName("group members: existence check + one projected page query, no per-user lazy loading")
    void groupMembers_noNPlusOne() throws Exception {
        Long support = groupRepository.findByName("Support Team").map(UserGroup::getId).orElseThrow();

        long statements = statementsFor(() ->
                mockMvc.perform(get("/api/v1/groups/{g}/members", support)).andExpect(status().isOk()));

        assertThat(statements).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("role users: existence check + one projected page query")
    void roleUsers_noNPlusOne() throws Exception {
        Long viewer = roleRepository.findByName("VIEWER").map(Role::getId).orElseThrow();

        long statements = statementsFor(() ->
                mockMvc.perform(get("/api/v1/roles/{r}/users", viewer)).andExpect(status().isOk()));

        assertThat(statements).isLessThanOrEqualTo(2);
    }
}
