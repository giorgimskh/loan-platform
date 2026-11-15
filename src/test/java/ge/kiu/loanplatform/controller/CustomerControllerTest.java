package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.CustomerRequest;
import ge.kiu.loanplatform.dto.CustomerResponse;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void getById_returns400_whenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/customers/abc"))
                .andExpect(status().isBadRequest());
    }
}
