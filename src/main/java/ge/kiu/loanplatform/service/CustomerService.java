package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.CustomerRequest;
import ge.kiu.loanplatform.dto.CustomerResponse;
import ge.kiu.loanplatform.entity.Customer;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new ConflictException("Customer with email " + request.email() + " already exists");
        }
        if (customerRepository.existsByPersonalNumber(request.personalNumber())) {
            throw new ConflictException("Customer with personal number " + request.personalNumber() + " already exists");
        }

        Customer customer = new Customer();
        customer.setFullName(request.fullName());
        customer.setEmail(request.email());
        customer.setPersonalNumber(request.personalNumber());
        customer.setPhone(request.phone());

        Customer saved = customerRepository.save(customer);
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer with id " + id + " not found"));
        return CustomerResponse.from(customer);
    }
}
