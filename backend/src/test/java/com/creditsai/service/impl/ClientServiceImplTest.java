package com.creditsai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.creditsai.dto.ClientRequest;
import com.creditsai.dto.ClientResponse;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.mapper.ClientMapper;
import com.creditsai.model.Client;
import com.creditsai.repository.ClientRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock
    private ClientRepository clientRepository;

    private ClientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClientServiceImpl(clientRepository, Mappers.getMapper(ClientMapper.class));
    }

    private static Client client(String name) {
        Client c = new Client();
        c.setName(name);
        c.setIdentifier("123");
        c.setAddress("Sofia");
        return c;
    }

    private static ClientRequest request() {
        return new ClientRequest("New", "999", "Plovdiv", "note");
    }

    @Test
    void create_validRequest_savesAndReturnsMappedResponse() {
        // given
        given(clientRepository.save(any(Client.class))).willAnswer(i -> i.getArgument(0));

        // when
        ClientResponse response = service.create(request());

        // then
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New");
        assertThat(captor.getValue().isArchived()).isFalse();
        assertThat(response.name()).isEqualTo("New");
        assertThat(response.identifier()).isEqualTo("999");
        assertThat(response.description()).isEqualTo("note");
    }

    @Test
    void list_archivedFalse_queriesRepositoryAndMapsPage() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(clientRepository.findByArchived(false, pageable))
                .willReturn(new PageImpl<>(List.of(client("A")), pageable, 1));

        // when
        Page<ClientResponse> page = service.list(false, pageable);

        // then
        assertThat(page.getContent()).extracting(ClientResponse::name).containsExactly("A");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void list_archivedTrue_queriesArchivedClients() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        Client archived = client("Old");
        archived.archive();
        given(clientRepository.findByArchived(true, pageable))
                .willReturn(new PageImpl<>(List.of(archived), pageable, 1));

        // when
        Page<ClientResponse> page = service.list(true, pageable);

        // then
        assertThat(page.getContent()).singleElement().satisfies(r -> assertThat(r.archived()).isTrue());
    }

    @Test
    void get_includeArchivedFalse_usesActiveOnlyLookup() {
        // given
        given(clientRepository.findByIdAndArchivedFalse(1L)).willReturn(Optional.of(client("A")));

        // when
        ClientResponse response = service.get(1L, false);

        // then
        assertThat(response.name()).isEqualTo("A");
        verify(clientRepository, never()).findById(any());
    }

    @Test
    void get_includeArchivedFalseAndArchivedClient_throwsNotFound() {
        // given
        given(clientRepository.findByIdAndArchivedFalse(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.get(1L, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client 1 was not found.");
    }

    @Test
    void get_includeArchivedTrue_returnsArchivedClient() {
        // given
        Client archived = client("Old");
        archived.archive();
        given(clientRepository.findById(1L)).willReturn(Optional.of(archived));

        // when
        ClientResponse response = service.get(1L, true);

        // then
        assertThat(response.archived()).isTrue();
        assertThat(response.archivedAt()).isNotNull();
        verify(clientRepository, never()).findByIdAndArchivedFalse(any());
    }

    @Test
    void get_includeArchivedTrueAndMissing_throwsNotFound() {
        // given
        given(clientRepository.findById(7L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.get(7L, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client 7 was not found.");
    }

    @Test
    void update_existingClient_replacesAllEditableFieldsAndSaves() {
        // given
        Client existing = client("Old");
        given(clientRepository.findById(1L)).willReturn(Optional.of(existing));
        given(clientRepository.save(existing)).willReturn(existing);

        // when
        ClientResponse response = service.update(1L, request());

        // then
        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getIdentifier()).isEqualTo("999");
        assertThat(existing.getAddress()).isEqualTo("Plovdiv");
        assertThat(existing.getDescription()).isEqualTo("note");
        assertThat(response.name()).isEqualTo("New");
    }

    @Test
    void update_archivedClient_isAllowedAndStaysArchived() {
        // given
        Client existing = client("Old");
        existing.archive();
        given(clientRepository.findById(1L)).willReturn(Optional.of(existing));
        given(clientRepository.save(existing)).willReturn(existing);

        // when
        ClientResponse response = service.update(1L, request());

        // then
        assertThat(response.name()).isEqualTo("New");
        assertThat(response.archived()).isTrue();
    }

    @Test
    void update_missingClient_throwsNotFoundAndDoesNotSave() {
        // given
        given(clientRepository.findById(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.update(1L, request()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(clientRepository, never()).save(any());
    }

    @Test
    void archive_activeClient_marksArchivedAndSaves() {
        // given
        Client existing = client("A");
        given(clientRepository.findById(1L)).willReturn(Optional.of(existing));

        // when
        service.archive(1L);

        // then
        assertThat(existing.isArchived()).isTrue();
        verify(clientRepository).save(existing);
    }

    @Test
    void archive_alreadyArchived_throwsBusinessRuleAndDoesNotSave() {
        // given
        Client existing = client("A");
        existing.archive();
        given(clientRepository.findById(1L)).willReturn(Optional.of(existing));

        // when / then
        assertThatThrownBy(() -> service.archive(1L)).isInstanceOf(BusinessRuleException.class);
        verify(clientRepository, never()).save(any());
    }

    @Test
    void archive_missingClient_throwsNotFound() {
        // given
        given(clientRepository.findById(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.archive(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restore_archivedClient_clearsArchiveAndReturnsResponse() {
        // given
        Client existing = client("A");
        existing.archive();
        given(clientRepository.findById(1L)).willReturn(Optional.of(existing));
        given(clientRepository.save(existing)).willReturn(existing);

        // when
        ClientResponse response = service.restore(1L);

        // then
        assertThat(response.archived()).isFalse();
        assertThat(response.archivedAt()).isNull();
    }

    @Test
    void restore_activeClient_throwsBusinessRuleAndDoesNotSave() {
        // given
        given(clientRepository.findById(1L)).willReturn(Optional.of(client("A")));

        // when / then
        assertThatThrownBy(() -> service.restore(1L)).isInstanceOf(BusinessRuleException.class);
        verify(clientRepository, never()).save(any());
    }

    @Test
    void restore_missingClient_throwsNotFound() {
        // given
        given(clientRepository.findById(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.restore(1L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
