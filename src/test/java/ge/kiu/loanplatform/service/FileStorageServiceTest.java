package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.config.AppProperties;
import ge.kiu.loanplatform.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Tests rule 6 with real files, written to a temporary folder that JUnit deletes afterwards.
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUploadDir(tempDir.toString());
        fileStorageService = new FileStorageService(appProperties);
    }

    @Test
    void store_savesFileUnderUniqueName_whenFileIsValidPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "content".getBytes());

        String firstPath = fileStorageService.store(file);
        String secondPath = fileStorageService.store(file);

        assertThat(Files.exists(Path.of(firstPath))).isTrue();
        assertThat(firstPath).endsWith(".pdf");
        assertThat(firstPath).isNotEqualTo(secondPath);        // same file name, two different stored names
        assertThat(firstPath).doesNotContain("id.pdf");        // the original name is not used on disk
    }

    @Test
    void store_acceptsUpperCaseExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "PHOTO.PNG", "image/png", "content".getBytes());

        assertThat(fileStorageService.store(file)).endsWith(".png");
    }

    @Test
    void store_throwsBusinessRule_whenFileTypeIsNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> fileStorageService.store(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PDF, JPG and PNG");
    }

    @Test
    void store_throwsBusinessRule_whenExtensionIsPdfButContentTypeIsNot() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> fileStorageService.store(file))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void store_throwsBusinessRule_whenFileIsLargerThan5Mb() {
        byte[] sixMegabytes = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", sixMegabytes);

        assertThatThrownBy(() -> fileStorageService.store(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void store_throwsBusinessRule_whenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.store(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void delete_removesTheStoredFile() {
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "content".getBytes());
        String path = fileStorageService.store(file);

        fileStorageService.delete(path);

        assertThat(Files.exists(Path.of(path))).isFalse();
    }
}
