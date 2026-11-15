package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.CustomerRequest;
import ge.kiu.loanplatform.dto.CustomerResponse;
import ge.kiu.loanplatform.entity.Customer;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
