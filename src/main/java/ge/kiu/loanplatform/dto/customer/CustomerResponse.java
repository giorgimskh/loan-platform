package ge.kiu.loanplatform.dto.customer;

import ge.kiu.loanplatform.entity.customer.Customer;

public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        String personalNumber,
        String phone
) {

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
