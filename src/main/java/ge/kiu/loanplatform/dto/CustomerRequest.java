package ge.kiu.loanplatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Personal number is required")
        @Pattern(regexp = "\\d{11}", message = "Personal number must be exactly 11 digits")
        String personalNumber,

        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone
) {
}
