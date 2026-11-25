package ge.kiu.loanplatform.service.storage;

import ge.kiu.loanplatform.config.properties.AppProperties;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Tests rule 6 with real files, written to a temporary folder that JUnit deletes afterwards.
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUploadDir(tempDir.toString());
        fileStorageService = new LocalFileStorageService(appProperties);
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
    void store_neverUsesTheOriginalNameAsPath_evenWhenItContainsFolders() {
        // a client can send any text as the file name, also one that tries to leave the upload folder
        MockMultipartFile file = new MockMultipartFile("file", "../../evil.pdf", "application/pdf", "content".getBytes());

        Path stored = Path.of(fileStorageService.store(file));

        assertThat(stored.getParent()).isEqualTo(tempDir);                        // still in the upload folder
        assertThat(stored.getFileName().toString()).matches("[0-9a-f-]{36}\\.pdf"); // UUID + ".pdf"
    }

    @Test
    void store_acceptsUpperCaseExtension() throws IOException {
        // a real 1 x 1 pixel PNG, because images are now read (their header) before they are stored
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        MockMultipartFile file = new MockMultipartFile("file", "PHOTO.PNG", "image/png", png.toByteArray());

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
