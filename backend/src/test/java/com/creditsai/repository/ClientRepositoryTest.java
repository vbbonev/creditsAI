package com.creditsai.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditsai.TestcontainersConfiguration;
import com.creditsai.model.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Runs against the Testcontainers Postgres with the real Liquibase schema validated by Hibernate. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ClientRepositoryTest {

    @Autowired
    private ClientRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private Client save(String name, boolean archived) {
        Client c = new Client();
        c.setName(name);
        c.setIdentifier("id-" + name);
        c.setAddress("Sofia");
        if (archived) {
            c.archive();
        }
        return repository.saveAndFlush(c);
    }

    @Test
    void save_newClient_populatesIdAndAuditTimestamps() {
        // when
        Client saved = save("A", false);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.isArchived()).isFalse();
        assertThat(saved.getArchivedAt()).isNull();
    }

    @Test
    void save_changedClient_updatesUpdatedAtButNotCreatedAt() throws Exception {
        // given
        Client saved = save("A", false);
        var created = saved.getCreatedAt();
        var updated = saved.getUpdatedAt();
        Thread.sleep(10);

        // when
        saved.setName("B");
        repository.saveAndFlush(saved);

        // then
        assertThat(saved.getCreatedAt()).isEqualTo(created);
        assertThat(saved.getUpdatedAt()).isAfter(updated);
    }

    @Test
    void save_archivedClient_persistsArchivedFlagAndTimestamp() {
        // when
        Client saved = save("A", true);

        // then
        Client reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isArchived()).isTrue();
        assertThat(reloaded.getArchivedAt()).isNotNull();
    }

    @Test
    void search_excludingArchived_returnsOnlyActiveClients() {
        // given
        save("Active", false);
        save("Old", true);

        // when
        Page<Client> page = repository.search("%", false, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).extracting(Client::getName).containsExactly("Active");
    }

    @Test
    void search_includingArchived_returnsActiveAndArchivedClients() {
        // given
        save("Active", false);
        save("Old", true);

        // when
        Page<Client> page = repository.search("%", true, PageRequest.of(0, 20, Sort.by("name")));

        // then
        assertThat(page.getContent()).extracting(Client::getName).containsExactly("Active", "Old");
    }

    @Test
    void search_patternMatchesNameOrIdentifierCaseInsensitively() {
        // given: names are stored with mixed case, identifiers are "id-" + name
        save("Acme Ltd", false);
        save("Beta", false);
        save("Иван Петров", false);

        // when / then
        assertThat(repository.search("%acme%", false, PageRequest.of(0, 20)).getContent())
                .extracting(Client::getName).containsExactly("Acme Ltd");
        assertThat(repository.search("%id-beta%", false, PageRequest.of(0, 20)).getContent())
                .extracting(Client::getName).containsExactly("Beta");
        assertThat(repository.search("%иван%", false, PageRequest.of(0, 20)).getContent())
                .extracting(Client::getName).containsExactly("Иван Петров");
    }

    @Test
    void search_escapedWildcard_matchesItLiterally() {
        // given
        save("100% Ltd", false);
        save("100 Ltd", false);

        // when
        Page<Client> page = repository.search("%100\\%%", false, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).extracting(Client::getName).containsExactly("100% Ltd");
    }

    @Test
    void search_withPagingAndSort_returnsSortedSlicesWithTotals() {
        // given
        save("C", false);
        save("A", false);
        save("B", false);

        // when
        Page<Client> first = repository.search("%", false, PageRequest.of(0, 2, Sort.by("name")));
        Page<Client> second = repository.search("%", false, PageRequest.of(1, 2, Sort.by("name")));

        // then
        assertThat(first.getContent()).extracting(Client::getName).containsExactly("A", "B");
        assertThat(second.getContent()).extracting(Client::getName).containsExactly("C");
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
    }

    @Test
    void search_noMatches_returnsEmptyPage() {
        // given
        save("Acme", false);

        // when
        Page<Client> page = repository.search("%zzz%", true, PageRequest.of(0, 20));

        // then
        assertThat(page).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void findByIdAndArchivedFalse_activeClient_returnsIt() {
        // given
        Client saved = save("A", false);

        // when / then
        assertThat(repository.findByIdAndArchivedFalse(saved.getId())).isPresent();
    }

    @Test
    void findByIdAndArchivedFalse_archivedClient_returnsEmpty() {
        // given
        Client saved = save("A", true);

        // when / then
        assertThat(repository.findByIdAndArchivedFalse(saved.getId())).isEmpty();
    }

    @Test
    void findByIdAndArchivedFalse_unknownId_returnsEmpty() {
        // when / then
        assertThat(repository.findByIdAndArchivedFalse(-1L)).isEmpty();
    }
}
