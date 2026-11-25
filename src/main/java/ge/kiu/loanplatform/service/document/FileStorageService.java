package ge.kiu.loanplatform.service.document;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// What the rest of the application needs from a file store: save, open and remove a file.
// It says nothing about WHERE the files are kept. Today LocalFileStorageService keeps them in a folder
// on disk; a cloud or database store could be added later as another class that implements this
// interface, and DocumentService / LoanApplicationService would not change (they only know this interface).
public interface FileStorageService {

    // Checks rule 6, saves the file under a new name and returns the path (or key) where it was stored
    String store(MultipartFile file);

    // Opens a stored file for download; ResourceNotFoundException when it no longer exists
    Resource load(String storedFilePath);

    // Removes a stored file; does nothing when it is already gone
    void delete(String storedFilePath);
}
