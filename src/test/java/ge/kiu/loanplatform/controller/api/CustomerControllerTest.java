package ge.kiu.loanplatform.controller.api;

import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.customer.CustomerRequest;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web layer test: starts only this controller + GlobalExceptionHandler (no database).
// The service is a mock, MockMvc sends fake HTTP requests.
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    // the controller also needs this service (for /api/customers/{id}/applications)
    @MockitoBean
    private LoanApplicationService loanApplicationService;

    @Test
    void getApplications_returns200WithApplicationsOfTheCustomer() throws Exception {
        when(loanApplicationService.getByCustomerAndStatus(1L, ApplicationStatus.SUBMITTED))
                .thenReturn(List.of(new LoanApplicationResponse(7L, new BigDecimal("5000"), 12,
                        ApplicationStatus.SUBMITTED, LocalDateTime.now(), null,
                        1L, "Ana Beridze", 1L, "Consumer Loan", List.of())));

        mockMvc.perform(get("/api/customers/1/applications").param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].customerFullName").value("Ana Beridze"));
    }

    @Test
    void getApplications_returns400_whenStatusIsMissing() throws Exception {
        mockMvc.perform(get("/api/customers/1/applications"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'status'"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void getApplications_returns400_whenStatusIsNotAValidEnumValue() throws Exception {
        mockMvc.perform(get("/api/customers/1/applications").param("status", "WRONG"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void getApplications_returns404_whenCustomerDoesNotExist() throws Exception {
        when(loanApplicationService.getByCustomerAndStatus(99L, ApplicationStatus.APPROVED))
                .thenThrow(new ResourceNotFoundException("Customer with id 99 not found"));

        mockMvc.perform(get("/api/customers/99/applications").param("status", "APPROVED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Customer with id 99 not found"));
    }

    @Test
    void create_returns201_whenBodyIsValid() throws Exception {
        when(customerService.create(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", "555123456"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890","phone":"555123456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("ana@mail.com"));
    }

    @Test
    void create_returns400WithFieldErrors_whenBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","email":"not-an-email","personalNumber":"12"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.personalNumber").value("Personal number must be exactly 11 digits"));

        // validation failed before the controller method ran, so the service was never called
        verifyNoInteractions(customerService);
    }

    @Test
    void create_returns400_whenFullNameContainsHtml() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"<script>alert(1)</script>","email":"ana@mail.com","personalNumber":"01234567890"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName")
                        .value("Full name may contain only letters, spaces, dots, apostrophes and hyphens"));

        verifyNoInteractions(customerService);
    }

    @Test
    void create_returns400_whenEmailHasNoDomainEnding() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Beridze","email":"ana@mail","personalNumber":"01234567890"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address"));

        verifyNoInteractions(customerService);
    }

    @Test
    void create_returns400_whenPhoneIsNotANumber() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890","phone":"call me"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone")
                        .value("Phone must be 9 to 15 digits, optionally starting with +"));

        verifyNoInteractions(customerService);
    }

    @Test
    void create_returns201_whenPhoneIsMissing() throws Exception { // phone is optional
        when(customerService.create(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", null));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void create_returns409_whenEmailAlreadyExists() throws Exception {
        when(customerService.create(any(CustomerRequest.class)))
                .thenThrow(new ConflictException("Customer with email ana@mail.com already exists"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Customer with email ana@mail.com already exists"));
    }

    @Test
    void create_returns400_whenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200_whenCustomerExists() throws Exception {
        when(customerService.getById(1L))
                .thenReturn(new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", null));

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Beridze"));
    }

    @Test
    void getById_returns404_whenCustomerDoesNotExist() throws Exception {
        when(customerService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Customer with id 99 not found"));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer with id 99 not found"))
                .andExpect(jsonPath("$.path").value("/api/customers/99"));
    }

    @Test
    void getById_returns400_whenIdIsNegative() throws Exception {
        mockMvc.perform(get("/api/customers/-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));

        verifyNoInteractions(customerService);
    }

    @Test
    void getById_returns400_whenIdIsZero() throws Exception {
        mockMvc.perform(get("/api/customers/0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns400_whenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/customers/abc"))
                .andExpect(status().isBadRequest());
    }

    // ---------- list with search and pagination ----------

    @Test
    void getAll_returns200WithOnePageOfCustomers() throws Exception {
        when(customerService.getAll(eq("ana"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/customers").param("search", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Ana Beridze"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void getAll_returns400_whenSearchTextIsTooLong() throws Exception {
        mockMvc.perform(get("/api/customers").param("search", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.search").value("Search text must be at most 100 characters"));
    }

    // ---------- update ----------

    @Test
    void update_returns200_whenBodyIsValid() throws Exception {
        when(customerService.update(eq(1L), any(CustomerRequest.class))).thenReturn(customer());

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update_returns400WithFieldErrors_whenBodyIsInvalid() throws Exception {
        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Beridze","email":"not-an-email","personalNumber":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.personalNumber").value("Personal number must be exactly 11 digits"));
    }

    @Test
    void update_returns409_whenEmailBelongsToAnotherCustomer() throws Exception {
        when(customerService.update(eq(1L), any(CustomerRequest.class)))
                .thenThrow(new ConflictException("Customer with email ana@mail.com already exists"));

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890"}
                                """))
                .andExpect(status().isConflict());
    }

    // ---------- delete ----------

    @Test
    void delete_returns204_whenCustomerIsDeleted() throws Exception {
        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isNoContent());

        verify(customerService).delete(1L);
    }

    @Test
    void delete_returns409_whenCustomerHasApplications() throws Exception {
        doThrow(new ConflictException("Customer with id 1 has applications and cannot be deleted"))
                .when(customerService).delete(1L);

        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Customer with id 1 has applications and cannot be deleted"));
    }

    private CustomerResponse customer() {
        return new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", "555123456");
    }
}
