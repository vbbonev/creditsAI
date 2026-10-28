package com.creditsai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.creditsai.dto.AttachmentUpload;
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

    @Mock
    private AttachmentIngestor attachmentIngestor;

    private ClientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClientServiceImpl(clientRepository, Mappers.getMapper(ClientMapper.class), attachmentIngestor);
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
    void list_noSearch_matchesEverythingAndMapsPage() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(clientRepository.search("%", false, pageable))
                .willReturn(new PageImpl<>(List.of(client("A")), pageable, 1));

        // when
        Page<ClientResponse> page = service.list(null, false, pageable);

        // then
        assertThat(page.getContent()).extracting(ClientResponse::name).containsExactly("A");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void list_blankSearch_matchesEverything() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(clientRepository.search("%", false, pageable)).willReturn(Page.empty(pageable));

        // when
        service.list("   ", false, pageable);

        // then
        verify(clientRepository).search("%", false, pageable);
    }

    @Test
    void list_search_isTrimmedLowerCasedAndWrappedInWildcards() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(clientRepository.search("%acme иван%", false, pageable)).willReturn(Page.empty(pageable));

        // when
        service.list("  ACME Иван ", false, pageable);

        // then
        verify(clientRepository).search("%acme иван%", false, pageable);
    }

    @Test
    void list_searchWithLikeWildcards_escapesThem() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(clientRepository.search("%100\\%\\_a\\\\b%", false, pageable)).willReturn(Page.empty(pageable));

        // when
        service.list("100%_a\\b", false, pageable);

        // then
        verify(clientRepository).search("%100\\%\\_a\\\\b%", false, pageable);
    }

    @Test
    void list_includeArchived_passesFlagAndMapsArchivedClients() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        Client archived = client("Old");
        archived.archive();
        given(clientRepository.search("%", true, pageable))
                .willReturn(new PageImpl<>(List.of(client("A"), archived), pageable, 2));

        // when
        Page<ClientResponse> page = service.list(null, true, pageable);

        // then
        assertThat(page.getContent()).extracting(ClientResponse::archived).containsExactly(false, true);
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

    @Test
    void createWithFiles_validRequest_savesClientFirstThenAttachesFiles() {
        // given
        given(clientRepository.save(any(Client.class))).willAnswer(i -> i.getArgument(0));
        given(clientRepository.saveAndFlush(any(Client.class))).willAnswer(i -> i.getArgument(0));
        List<AttachmentUpload> uploads = List.of(new AttachmentUpload("a.txt", 1, null));

        // when
        ClientResponse response = service.create(request(), uploads, "user1");

        // then
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(clientRepository, attachmentIngestor);
        order.verify(clientRepository).save(any(Client.class));
        order.verify(attachmentIngestor).attach(any(Client.class), org.mockito.ArgumentMatchers.eq(uploads),
                org.mockito.ArgumentMatchers.eq("user1"));
        assertThat(response.name()).isEqualTo("New");
    }

    @Test
    void createWithFiles_fileRejected_propagatesAndDoesNotFlushTheClient() {
        // given
        given(clientRepository.save(any(Client.class))).willAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.doThrow(new BusinessRuleException("A client can have at most 30 files."))
                .when(attachmentIngestor).attach(any(Client.class), any(), any());

        // when / then: the exception rolls the transaction back (client row and files)
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(request(), List.of(), "user1"))
                .isInstanceOf(BusinessRuleException.class);
        verify(clientRepository, never()).saveAndFlush(any(Client.class));
    }
}
