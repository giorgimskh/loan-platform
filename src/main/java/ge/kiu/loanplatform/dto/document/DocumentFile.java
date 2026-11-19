package ge.kiu.loanplatform.dto.document;

import org.springframework.core.io.Resource;

// A stored document ready to be downloaded: the name the customer gave it and the file itself.
// Resource is Spring's type for "something whose bytes can be read", here a file on disk.
public record DocumentFile(
        String originalFileName,
        Resource resource
) {
}
