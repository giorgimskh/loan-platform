package ge.kiu.loanplatform.service.customer;

import ge.kiu.loanplatform.dto.customer.CustomerRequest;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.entity.customer.Customer;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.application.LoanApplicationRepository;
import ge.kiu.loanplatform.repository.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final LoanApplicationRepository loanApplicationRepository;

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

    // One page of customers. With a search text: only those whose name, email or personal number contains it.
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAll(String search, Pageable pageable) {
        Page<Customer> page = (search == null || search.isBlank())
                ? customerRepository.findAll(pageable)
                : customerRepository.search(search.trim(), pageable);
        return page.map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return CustomerResponse.from(findCustomer(id));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = findCustomer(id);

        // email and personal number must stay unique, but keeping the customer's own values is fine
        boolean emailChanged = !customer.getEmail().equals(request.email());
        if (emailChanged && customerRepository.existsByEmail(request.email())) {
            throw new ConflictException("Customer with email " + request.email() + " already exists");
        }
        boolean personalNumberChanged = !customer.getPersonalNumber().equals(request.personalNumber());
        if (personalNumberChanged && customerRepository.existsByPersonalNumber(request.personalNumber())) {
            throw new ConflictException("Customer with personal number " + request.personalNumber() + " already exists");
        }

        customer.setFullName(request.fullName());
        customer.setEmail(request.email());
        customer.setPersonalNumber(request.personalNumber());
        customer.setPhone(request.phone());
        // no save() needed: customer is managed by JPA, changes are written automatically on commit
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = findCustomer(id);
        // a customer with applications stays: deleting them would also delete the bank's records
        if (loanApplicationRepository.existsByCustomerId(id)) {
            throw new ConflictException("Customer with id " + id + " has applications and cannot be deleted");
        }
        customerRepository.delete(customer);
    }

    // ---------- helpers ----------

    private Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer with id " + id + " not found"));
    }
}
