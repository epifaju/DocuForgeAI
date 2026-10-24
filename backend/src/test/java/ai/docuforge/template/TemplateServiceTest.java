package ai.docuforge.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateOrigin;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import ai.docuforge.template.dto.TemplateCreateRequest;
import ai.docuforge.template.dto.TemplateDuplicateRequest;
import ai.docuforge.template.dto.TemplateUpdateRequest;
import ai.docuforge.template.parser.DetectedVariable;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    private static final byte[] DOCX_MAGIC = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00};

    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateVersionRepository templateVersionRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock private StorageProvider storageProvider;
    @Mock private AuditService auditService;
    @Mock private TemplateVariableService templateVariableService;

    private TemplateService service;
    private DocuForgePrincipal principal;
    private final UUID companyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TemplateService(
                templateRepository,
                templateVersionRepository,
                companyRepository,
                generatedDocumentRepository,
                storageProvider,
                auditService,
                templateVariableService
        );
        principal = new DocuForgePrincipal(
                userId, companyId, "demo", "admin@demo.local", "h", true, Set.of("ADMIN")
        );
    }

    @Test
    void create_persistsDraftAndAudits() {
        when(templateRepository.existsByCompanyIdAndCode(companyId, "LETTER")).thenReturn(false);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company()));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            t.setId(templateId);
            return t;
        });

        var response = service.create(
                principal, new TemplateCreateRequest("LETTER", "Lettre", "desc", "cat")
        );

        assertThat(response.id()).isEqualTo(templateId);
        assertThat(response.code()).isEqualTo("LETTER");
        assertThat(response.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(response.origin()).isEqualTo(TemplateOrigin.USER);
        verify(auditService).recordSuccess(eq(principal), anyString(), eq("TEMPLATE"), eq(templateId));
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(templateRepository.existsByCompanyIdAndCode(companyId, "LETTER")).thenReturn(true);
        assertThatThrownBy(() -> service.create(
                principal, new TemplateCreateRequest("LETTER", "Lettre", null, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_rejectsMissingCompany() {
        when(templateRepository.existsByCompanyIdAndCode(companyId, "LETTER")).thenReturn(false);
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(
                principal, new TemplateCreateRequest("LETTER", "Lettre", null, null)
        ))
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void list_clampsPageAndSize() {
        Template template = userTemplate(TemplateStatus.DRAFT, null);
        when(templateRepository.search(eq(companyId), eq(null), eq("x"), any()))
                .thenReturn(new PageImpl<>(List.of(template), PageRequest.of(0, 100), 1));

        var page = service.list(principal, null, "x", -5, 999);

        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.items()).hasSize(1);
        verify(templateRepository).search(eq(companyId), eq(null), eq("x"), eq(PageRequest.of(
                0, 100, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"
                )
        )));
    }

    @Test
    void update_rejectsArchivedAndPackTemplates() {
        Template archived = userTemplate(TemplateStatus.ARCHIVED, null);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(archived));
        assertThatThrownBy(() -> service.update(
                principal, templateId, new TemplateUpdateRequest("LETTER", "N", null, null)
        )).isInstanceOf(ResponseStatusException.class);

        Template pack = userTemplate(TemplateStatus.DRAFT, null);
        pack.setOrigin(TemplateOrigin.PACK);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(pack));
        assertThatThrownBy(() -> service.update(
                principal, templateId, new TemplateUpdateRequest("LETTER", "N", null, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pack_immutable");
    }

    @Test
    void update_persistsMetadata() {
        Template template = userTemplate(TemplateStatus.DRAFT, null);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(template));
        when(templateRepository.existsByCompanyIdAndCodeAndIdNot(companyId, "LETTER2", templateId))
                .thenReturn(false);
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.update(
                principal, templateId, new TemplateUpdateRequest("LETTER2", "Nouveau", "d", "cat")
        );

        assertThat(response.code()).isEqualTo("LETTER2");
        assertThat(response.name()).isEqualTo("Nouveau");
        verify(auditService).recordSuccess(eq(principal), anyString(), eq("TEMPLATE"), eq(templateId));
    }

    @Test
    void update_rejectsConflictingCode() {
        Template template = userTemplate(TemplateStatus.DRAFT, null);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(template));
        when(templateRepository.existsByCompanyIdAndCodeAndIdNot(companyId, "OTHER", templateId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(
                principal, templateId, new TemplateUpdateRequest("OTHER", "N", null, null)
        )).hasMessageContaining("code_exists");
    }

    @Test
    void duplicate_requiresCurrentVersionAndStoredDocx() {
        Template noVersion = userTemplate(TemplateStatus.ACTIVE, null);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(noVersion));
        assertThatThrownBy(() -> service.duplicate(
                principal, templateId, new TemplateDuplicateRequest("Copy")
        )).hasMessageContaining("no_current_version");

        TemplateVersion version = version(1, "key.docx");
        Template withVersion = userTemplate(TemplateStatus.ACTIVE, version);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(withVersion));
        when(storageProvider.exists("key.docx")).thenReturn(false);
        assertThatThrownBy(() -> service.duplicate(
                principal, templateId, new TemplateDuplicateRequest("Copy")
        )).hasMessageContaining("current_docx_missing");
    }

    @Test
    void duplicate_createsUserCopyWithUniqueCodeAndCompensatesOnFailure() throws Exception {
        TemplateVersion sourceVersion = version(1, "src.docx");
        Template source = userTemplate(TemplateStatus.ACTIVE, sourceVersion);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(source));
        when(storageProvider.exists("src.docx")).thenReturn(true);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company()));
        when(storageProvider.read("src.docx")).thenReturn(new ByteArrayInputStream(DOCX_MAGIC));
        when(templateRepository.existsByCompanyIdAndCode(companyId, "LETTER_COPY")).thenReturn(true);
        when(templateRepository.existsByCompanyIdAndCode(companyId, "LETTER_COPY2")).thenReturn(false);
        when(storageProvider.store(eq(StorageCategory.TEMPLATES), anyString(), anyString(), any(), anyLong()))
                .thenReturn(new StoredFile(
                        "copy-key", "src.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "docx", DOCX_MAGIC.length, StorageCategory.TEMPLATES
                ));

        AtomicInteger saves = new AtomicInteger();
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            if (saves.incrementAndGet() == 1) {
                throw new IllegalStateException("db down");
            }
            return t;
        });
        when(storageProvider.exists("copy-key")).thenReturn(true);

        assertThatThrownBy(() -> service.duplicate(
                principal, templateId, new TemplateDuplicateRequest("  Copie  ")
        )).isInstanceOf(IllegalStateException.class);
        verify(storageProvider).delete("copy-key");

        // happy path
        saves.set(0);
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> {
            TemplateVersion v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        var response = service.duplicate(principal, templateId, new TemplateDuplicateRequest("Copie"));
        assertThat(response.code()).isEqualTo("LETTER_COPY2");
        assertThat(response.origin()).isEqualTo(TemplateOrigin.USER);
        assertThat(response.status()).isEqualTo(TemplateStatus.DRAFT);
        verify(templateVariableService).copyFromVersion(eq(sourceVersion.getId()), any(TemplateVersion.class));
    }

    @Test
    void delete_enforcesDraftOnlyNoDocumentsAndPackImmutable() {
        Template active = userTemplate(TemplateStatus.ACTIVE, null);
        when(templateRepository.findByIdAndCompanyId(templateId, companyId)).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> service.delete(principal, templateId))
                .hasMessageContaining("delete_draft_only");

        Template draft = userTemplate(TemplateStatus.DRAFT, null);
        when(templateRepository.findByIdAndCompanyId(templateId, companyId)).thenReturn(Optional.of(draft));
        when(generatedDocumentRepository.existsByTemplateId(templateId)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(principal, templateId))
                .hasMessageContaining("has_documents");

        Template pack = userTemplate(TemplateStatus.DRAFT, null);
        pack.setOrigin(TemplateOrigin.PACK);
        when(templateRepository.findByIdAndCompanyId(templateId, companyId)).thenReturn(Optional.of(pack));
        assertThatThrownBy(() -> service.delete(principal, templateId))
                .hasMessageContaining("pack_immutable");
    }

    @Test
    void delete_removesVersionsStorageAndTemplate() {
        TemplateVersion v1 = version(1, "v1.docx");
        Template draft = userTemplate(TemplateStatus.DRAFT, v1);
        when(templateRepository.findByIdAndCompanyId(templateId, companyId)).thenReturn(Optional.of(draft));
        when(generatedDocumentRepository.existsByTemplateId(templateId)).thenReturn(false);
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
                .thenReturn(List.of(v1));
        when(templateRepository.saveAndFlush(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(principal, templateId);

        verify(storageProvider).delete("v1.docx");
        verify(templateVersionRepository).delete(v1);
        verify(templateRepository).delete(draft);
        verify(auditService).recordSuccess(eq(principal), anyString(), eq("TEMPLATE"), eq(templateId));
    }

    @Test
    void addVersion_rejectsArchivedTemplate() {
        Template archived = userTemplate(TemplateStatus.ARCHIVED, version(1, "ok.docx"));
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(archived));
        MockMultipartFile ok = new MockMultipartFile("file", "a.docx", "application/octet-stream", DOCX_MAGIC);
        assertThatThrownBy(() -> service.addVersion(principal, templateId, ok, true))
                .hasMessageContaining("version_archived");
    }

    @Test
    void addVersion_validatesFileAndCanSetCurrent() throws Exception {
        Template template = userTemplate(TemplateStatus.DRAFT, null);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.addVersion(principal, templateId, null, true))
                .hasMessageContaining("docx_required");

        MockMultipartFile empty = new MockMultipartFile("file", "a.docx", "application/octet-stream", new byte[0]);
        assertThatThrownBy(() -> service.addVersion(principal, templateId, empty, true))
                .hasMessageContaining("docx_required");

        MockMultipartFile badExt = new MockMultipartFile("file", "a.doc", "application/msword", DOCX_MAGIC);
        assertThatThrownBy(() -> service.addVersion(principal, templateId, badExt, true))
                .hasMessageContaining("docx_extension");

        MockMultipartFile badMagic = new MockMultipartFile(
                "file", "a.docx", "application/octet-stream", new byte[] {1, 2, 3, 4, 5}
        );
        assertThatThrownBy(() -> service.addVersion(principal, templateId, badMagic, true))
                .hasMessageContaining("docx_invalid");

        when(templateVariableService.createFromDocxPreview(any())).thenReturn(List.of(
                new DetectedVariable(
                        "client.name", "Client name", ai.docuforge.domain.template.VariableType.TEXT, 0
                )
        ));
        when(storageProvider.store(eq(StorageCategory.TEMPLATES), anyString(), anyString(), any(), anyLong()))
                .thenReturn(new StoredFile(
                        "new-key", "a.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "docx", DOCX_MAGIC.length, StorageCategory.TEMPLATES
                ));
        when(templateVersionRepository.findMaxVersionNumber(templateId)).thenReturn(0);
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> {
            TemplateVersion v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile ok = new MockMultipartFile("file", "a.docx", "application/octet-stream", DOCX_MAGIC);
        var response = service.addVersion(principal, templateId, ok, true);

        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(template.getCurrentVersion()).isNotNull();
        verify(templateVariableService).persistDetected(any(TemplateVersion.class), any());
    }

    @Test
    void activate_requiresExistingCurrentDocx() {
        Template noFile = userTemplate(TemplateStatus.DRAFT, version(1, "missing.docx"));
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(noFile));
        when(storageProvider.exists("missing.docx")).thenReturn(false);
        assertThatThrownBy(() -> service.activate(principal, templateId))
                .isInstanceOf(ResponseStatusException.class);

        TemplateVersion version = version(1, "ok.docx");
        Template ready = userTemplate(TemplateStatus.DRAFT, version);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(ready));
        when(storageProvider.exists("ok.docx")).thenReturn(true);
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.activate(principal, templateId);
        assertThat(response.status()).isEqualTo(TemplateStatus.ACTIVE);
    }

    @Test
    void activate_rejectsMissingCurrentVersion() {
        Template template = userTemplate(TemplateStatus.DRAFT, null);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(template));
        assertThatThrownBy(() -> service.activate(principal, templateId))
                .hasMessageContaining("sans version DOCX");
    }

    @Test
    void archive_setsStatus() {
        Template template = userTemplate(TemplateStatus.ACTIVE, version(1, "ok.docx"));
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.archive(principal, templateId);
        assertThat(response.status()).isEqualTo(TemplateStatus.ARCHIVED);
        verify(auditService).recordSuccess(eq(principal), anyString(), eq("TEMPLATE"), eq(templateId));
    }

    @Test
    void getAndGetVersion_notFound() {
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(principal, templateId))
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(templateRepository.findByIdAndCompanyId(templateId, companyId))
                .thenReturn(Optional.of(userTemplate(TemplateStatus.DRAFT, null)));
        when(templateVersionRepository.findByTemplateIdAndVersionNumber(templateId, 9))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getVersion(principal, templateId, 9))
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listVersions_mapsDescending() {
        TemplateVersion v2 = version(2, "v2.docx");
        TemplateVersion v1 = version(1, "v1.docx");
        when(templateRepository.findByIdAndCompanyId(templateId, companyId))
                .thenReturn(Optional.of(userTemplate(TemplateStatus.ACTIVE, v2)));
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
                .thenReturn(List.of(v2, v1));

        var versions = service.listVersions(principal, templateId);
        assertThat(versions).extracting(v -> v.versionNumber()).containsExactly(2, 1);
    }

    @Test
    void delete_notFound() {
        when(templateRepository.findByIdAndCompanyId(templateId, companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(principal, templateId))
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void duplicate_usesFallbackCodeWhenSourceCodeBlank() throws Exception {
        TemplateVersion sourceVersion = version(1, "src.docx");
        Template source = userTemplate(TemplateStatus.ACTIVE, sourceVersion);
        source.setCode("   ");
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(source));
        when(storageProvider.exists("src.docx")).thenReturn(true);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company()));
        when(storageProvider.read("src.docx")).thenReturn(new ByteArrayInputStream(DOCX_MAGIC));
        when(templateRepository.existsByCompanyIdAndCode(companyId, "TEMPLATE_COPY")).thenReturn(false);
        when(storageProvider.store(eq(StorageCategory.TEMPLATES), anyString(), anyString(), any(), anyLong()))
                .thenReturn(new StoredFile(
                        "copy-key", "src.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "docx", DOCX_MAGIC.length, StorageCategory.TEMPLATES
                ));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> {
            TemplateVersion v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        var response = service.duplicate(principal, templateId, new TemplateDuplicateRequest("Copie"));
        assertThat(response.code()).isEqualTo("TEMPLATE_COPY");
    }

    private Company company() {
        Company company = new Company();
        company.setId(companyId);
        return company;
    }

    private Template userTemplate(TemplateStatus status, TemplateVersion current) {
        Template template = new Template();
        template.setId(templateId);
        template.setCompany(company());
        template.setCode("LETTER");
        template.setName("Lettre");
        template.setStatus(status);
        template.setOrigin(TemplateOrigin.USER);
        template.setCurrentVersion(current);
        return template;
    }

    private TemplateVersion version(int number, String storageKey) {
        TemplateVersion version = new TemplateVersion();
        version.setId(UUID.randomUUID());
        version.setVersionNumber(number);
        version.setStorageKey(storageKey);
        version.setOriginalFilename(storageKey);
        version.setChecksum("abc");
        return version;
    }
}
