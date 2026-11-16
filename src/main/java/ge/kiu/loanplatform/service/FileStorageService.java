package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.config.AppProperties;
import ge.kiu.loanplatform.exception.BusinessRuleException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB in bytes
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final AppProperties appProperties;

    // Checks rule 6, saves the file under a unique name and returns the path where it was stored
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessRuleException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessRuleException("File is too large. Maximum size is 5MB");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension) || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessRuleException("Only PDF, JPG and PNG files are allowed");
        }

        // unique name: two uploads called "id.pdf" must not overwrite each other
        String storedFileName = UUID.randomUUID() + "." + extension;

        try {
            Path uploadDir = Path.of(appProperties.getUploadDir());
            Files.createDirectories(uploadDir); // does nothing if the folder already exists
            Path target = uploadDir.resolve(storedFileName);
            file.transferTo(target);
            return target.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
    }

    // Opens a stored file for download. Nothing is read here: Spring reads the bytes while it sends the response.
    public Resource load(String storedFilePath) {
        Resource resource = new FileSystemResource(storedFilePath);
        if (!resource.exists()) {
            // the database row exists but the file was removed from the uploads folder by hand
            throw new ResourceNotFoundException("The stored file of this document no longer exists");
        }
        return resource;
    }

    public void delete(String storedFilePath) {
        try {
            Files.deleteIfExists(Path.of(storedFilePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete file", e);
        }
    }

    // "Salary.Certificate.PDF" -> "pdf", no dot -> ""
    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
