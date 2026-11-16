package ge.kiu.loanplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RejectRequest(

        // Free text, so we cannot whitelist letters only. Instead we forbid the dangerous characters:
        // < and > (HTML/script tags) and control characters such as line breaks (\p{Cntrl}).
        @NotBlank(message = "Rejection reason is required")
        @Size(max = 255, message = "Rejection reason must be at most 255 characters")
        @Pattern(regexp = "^[^<>\\p{Cntrl}]*$",
                message = "Rejection reason must not contain < or > or control characters")
        String decisionComment
) {
}
