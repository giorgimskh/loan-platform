package ge.kiu.loanplatform.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(

        // Whitelist: only letters (any language, \p{L}), spaces, dots, apostrophes and hyphens.
        // This blocks characters like < > " ; that are used in HTML/script injection.
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        @Pattern(regexp = "^[\\p{L} .'-]*$",
                message = "Full name may contain only letters, spaces, dots, apostrophes and hyphens")
        String fullName,

        // @Email alone accepts addresses like "a@b"; the regexp also requires a real domain (name.tld)
        // and allows only safe characters. 254 is the maximum length of an email address.
        @NotBlank(message = "Email is required")
        @Size(max = 254, message = "Email must be at most 254 characters")
        @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Personal number is required")
        @Pattern(regexp = "^\\d{11}$", message = "Personal number must be exactly 11 digits")
        String personalNumber,

        // Optional (null is allowed), but if sent it must look like a phone number: 9-15 digits, optional leading +
        @Pattern(regexp = "^\\+?\\d{9,15}$",
                message = "Phone must be 9 to 15 digits, optionally starting with +")
        String phone
) {
}
