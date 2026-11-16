package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.CustomerRequest;
import ge.kiu.loanplatform.dto.CustomerResponse;
import ge.kiu.loanplatform.entity.Customer;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.CustomerRepository;
import ge.kiu.loanplatform.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test: no Spring, no database. The repository is replaced by a Mockito mock.
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @InjectMocks
    private CustomerService customerService;

    private final CustomerRequest request =
            new CustomerRequest("Ana Beridze", "ana@mail.com", "01234567890", "555123456");

    @Test
    void create_savesCustomer_whenEmailAndPersonalNumberAreFree() {
        when(customerRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(customerRepository.existsByPersonalNumber("01234567890")).thenReturn(false);
        // save() returns the same object it was given
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(request);

        assertThat(response.fullName()).isEqualTo("Ana Beridze");
        assertThat(response.email()).isEqualTo("ana@mail.com");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void create_throwsConflict_whenEmailAlreadyExists() {
        when(customerRepository.existsByEmail("ana@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ana@mail.com");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_throwsConflict_whenPersonalNumberAlreadyExists() {
        when(customerRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(customerRepository.existsByPersonalNumber("01234567890")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(request))
                .isInstanceOf(ConflictException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void getById_throwsNotFound_whenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---------- list and search ----------

    @Test
    void getAll_returnsEveryCustomer_whenSearchIsEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        when(customerRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(existingCustomer())));

        Page<CustomerResponse> page = customerService.getAll("  ", pageable);

        assertThat(page.getContent()).extracting(CustomerResponse::fullName).containsExactly("Ana Beridze");
        verify(customerRepository, never()).search(any(), any());
    }

    @Test
    void getAll_usesTheSearchQuery_andTrimsTheText() {
        Pageable pageable = PageRequest.of(0, 10);
        when(customerRepository.search("ana", pageable)).thenReturn(new PageImpl<>(List.of(existingCustomer())));

        Page<CustomerResponse> page = customerService.getAll(" ana ", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    // ---------- update ----------

    @Test
    void update_changesTheFields_andAllowsKeepingTheSameEmail() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer()));

        CustomerResponse response = customerService.update(1L,
                new CustomerRequest("Ana Kapanadze", "ana@mail.com", "01234567890", null));

        assertThat(response.fullName()).isEqualTo("Ana Kapanadze");
        assertThat(response.phone()).isNull();
        // email and personal number did not change, so the "already exists" checks must not run
        verify(customerRepository, never()).existsByEmail(any());
        verify(customerRepository, never()).existsByPersonalNumber(any());
    }

    @Test
    void update_throwsConflict_whenNewEmailBelongsToAnotherCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer()));
        when(customerRepository.existsByEmail("nino@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.update(1L,
                new CustomerRequest("Ana Beridze", "nino@mail.com", "01234567890", null)))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- delete ----------

    @Test
    void delete_removesTheCustomer_whenThereAreNoApplications() {
        Customer customer = existingCustomer();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(loanApplicationRepository.existsByCustomerId(1L)).thenReturn(false);

        customerService.delete(1L);

        verify(customerRepository).delete(customer);
    }

    @Test
    void delete_throwsConflict_whenCustomerHasApplications() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer()));
        when(loanApplicationRepository.existsByCustomerId(1L)).thenReturn(true);

        assertThatThrownBy(() -> customerService.delete(1L))
                .isInstanceOf(ConflictException.class);
        verify(customerRepository, never()).delete(any());
    }

    // ---------- helper ----------

    private Customer existingCustomer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Ana Beridze");
        customer.setEmail("ana@mail.com");
        customer.setPersonalNumber("01234567890");
        customer.setPhone("555123456");
        return customer;
    }
}
