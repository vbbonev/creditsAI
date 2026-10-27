package com.creditsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditsai.repository.ClientRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Full-stack flow for clients: controller, security, service, JPA and Liquibase on real Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ClientApiIntegrationTest {

    private static final RequestPostProcessor USER = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    private static final String BODY =
            "{\"name\":\"Acme\",\"identifier\":\"123\",\"address\":\"Sofia\",\"description\":\"note\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private long createClient() throws Exception {
        String location = mockMvc.perform(post("/api/clients").with(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");
        Matcher m = Pattern.compile("/api/clients/(\\d+)$").matcher(location);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    @Test
    void clientLifecycle_createListArchiveRestore_behavesPerRole() throws Exception {
        // given: a client created by a USER
        long id = createClient();

        // then: it is listed with the stable page shape
        mockMvc.perform(get("/api/clients").with(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));

        // when: ADMIN archives it
        mockMvc.perform(delete("/api/clients/" + id).with(ADMIN)).andExpect(status().isNoContent());

        // then: row still exists in the database
        assertThat(repository.findById(id)).isPresent().get().satisfies(c -> {
            assertThat(c.isArchived()).isTrue();
            assertThat(c.getArchivedAt()).isNotNull();
        });

        // and: gone from lists and 404 for USER
        mockMvc.perform(get("/api/clients").with(USER))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/api/clients/" + id).with(USER)).andExpect(status().isNotFound());

        // and: ADMIN sees it by id and in the archived list
        mockMvc.perform(get("/api/clients/" + id).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get("/api/clients?archived=true").with(ADMIN))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mockMvc.perform(get("/api/clients?archived=true").with(USER)).andExpect(status().isForbidden());

        // and: archiving again is a readable 409
        mockMvc.perform(delete("/api/clients/" + id).with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This client is already archived."));

        // when: ADMIN restores it
        mockMvc.perform(post("/api/clients/" + id + "/restore").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").doesNotExist());

        // then: visible to USER again; restoring twice is 409
        mockMvc.perform(get("/api/clients/" + id).with(USER)).andExpect(status().isOk());
        mockMvc.perform(post("/api/clients/" + id + "/restore").with(ADMIN)).andExpect(status().isConflict());
    }

    @Test
    void update_archivedClientAsAdmin_editsFieldsAndKeepsArchived() throws Exception {
        // given
        long id = createClient();
        mockMvc.perform(delete("/api/clients/" + id).with(ADMIN)).andExpect(status().isNoContent());

        // when / then
        mockMvc.perform(put("/api/clients/" + id).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"identifier\":\"9\",\"address\":\"Varna\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.address").value("Varna"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void update_asUser_returns403AndLeavesDataUnchanged() throws Exception {
        // given
        long id = createClient();

        // when / then
        mockMvc.perform(put("/api/clients/" + id).with(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("Acme", "Hacked")))
                .andExpect(status().isForbidden());
        assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("Acme");
    }

    @Test
    void list_default_isSortedByName() throws Exception {
        // given
        for (String name : new String[] {"Charlie", "Alpha", "Bravo"}) {
            mockMvc.perform(post("/api/clients").with(USER).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.replace("Acme", name))).andExpect(status().isCreated());
        }

        // when / then
        mockMvc.perform(get("/api/clients").with(USER))
                .andExpect(jsonPath("$.content[0].name").value("Alpha"))
                .andExpect(jsonPath("$.content[1].name").value("Bravo"))
                .andExpect(jsonPath("$.content[2].name").value("Charlie"));
    }

    @Test
    void missingClient_operations_return404() throws Exception {
        // when / then
        mockMvc.perform(get("/api/clients/999999").with(ADMIN)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/clients/999999").with(ADMIN)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/clients/999999/restore").with(ADMIN)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/clients/999999").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
    }

    @Test
    void create_invalidBody_returns400AndNothingIsStored() throws Exception {
        // when / then
        mockMvc.perform(post("/api/clients").with(USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"identifier\":\"1\",\"address\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name is required."));
        assertThat(repository.count()).isZero();
    }
}
