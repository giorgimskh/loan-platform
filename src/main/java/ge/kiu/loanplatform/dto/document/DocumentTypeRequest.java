package ge.kiu.loanplatform.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DocumentTypeRequest(
        // Whitelist: letters, digits, spaces and a few punctuation marks. Blocks < > " ; etc.
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        @Pattern(regexp = "^[\\p{L}\\p{N} .,&()'-]*$",
                message = "Name may contain only letters, digits, spaces and . , & ( ) ' -")
        String name
) {
}
