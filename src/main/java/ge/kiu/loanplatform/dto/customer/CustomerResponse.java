package ge.kiu.loanplatform.dto.customer;

import ge.kiu.loanplatform.dto.file.ApplicationPart;
import ge.kiu.loanplatform.dto.file.ApplicationPartVisitor;
import ge.kiu.loanplatform.entity.customer.Customer;

public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        String personalNumber,
        String phone
) implements ApplicationPart {

    // Visitor pattern: send the visitor to the method for this type of part (double dispatch)
    @Override
    public void accept(ApplicationPartVisitor visitor) {
        visitor.visitCustomer(this);
    }

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPersonalNumber(),
                customer.getPhone()
        );
    }
}
