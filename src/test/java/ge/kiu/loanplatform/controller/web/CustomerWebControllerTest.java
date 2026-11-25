package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.customer.CustomerRequest;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static ge.kiu.loanplatform.controller.web.WebTestData.application;
import static ge.kiu.loanplatform.controller.web.WebTestData.customer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Web layer test for CustomerWebController. The services are mocks.
// The templates are really rendered, so these tests also catch mistakes in the HTML files.
@WebMvcTest(CustomerWebController.class)
class CustomerWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private LoanApplicationService loanApplicationService;

    @Test
    void customers_listsCustomers() throws Exception {
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer())));

        mockMvc.perform(get("/ui/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers"))
                .andExpect(content().string(containsString("Ana Beridze")))
                .andExpect(content().string(containsString("/ui/customers/1/applications?status=APPROVED")))
                .andExpect(content().string(containsString("/ui/applications/new?customerId=1")));
    }

    @Test
    void customers_showsAnEmptyState_whenThereAreNoCustomers() throws Exception {
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/ui/customers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No customers yet")));
    }

    @Test
    void createCustomer_redirectsWithMessage_andTreatsEmptyPhoneAsNull() throws Exception {
        when(customerService.create(any(CustomerRequest.class))).thenReturn(customer());

        mockMvc.perform(post("/ui/customers")
                        .param("fullName", "Ana Beridze")
                        .param("email", "ana@mail.com")
                        .param("personalNumber", "01234567890")
                        .param("phone", "")) // an empty form field
                .andExpect(redirectedUrl("/ui/customers"))
                .andExpect(flash().attribute("message", "Ana Beridze was registered as customer 1"));

        // records compare by value, so this checks every field that reached the service
        verify(customerService).create(new CustomerRequest("Ana Beridze", "ana@mail.com", "01234567890", null));
    }

    @Test
    void createCustomer_showsTheFormAgainWithErrorsAndTypedValues_whenFormIsInvalid() throws Exception {
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(post("/ui/customers")
                        .param("fullName", "Ana Beridze")
                        .param("email", "not-an-email")
                        .param("personalNumber", "12")
                        .param("phone", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("customers"))
                // one message per invalid field
                .andExpect(content().string(containsString("Email must be a valid email address")))
                .andExpect(content().string(containsString("Personal number must be exactly 11 digits")))
                // what the user typed is still in the form
                .andExpect(content().string(containsString("value=\"Ana Beridze\"")))
                .andExpect(content().string(containsString("value=\"not-an-email\"")));

        verify(customerService, never()).create(any());
    }

    @Test
    void createCustomer_escapesHtmlInTheTypedValue() throws Exception {
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(post("/ui/customers")
                        .param("fullName", "<script>alert(1)</script>")
                        .param("email", "ana@mail.com")
                        .param("personalNumber", "01234567890"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Full name may contain only letters")))
                // Thymeleaf escapes the value, so the script is shown as text and never runs
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void createCustomer_keepsTheForm_whenEmailAlreadyExists() throws Exception {
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer())));
        when(customerService.create(any(CustomerRequest.class)))
                .thenThrow(new ConflictException("Customer with email ana@mail.com already exists"));

        mockMvc.perform(post("/ui/customers")
                        .param("fullName", "Ana Beridze")
                        .param("email", "ana@mail.com")
                        .param("personalNumber", "01234567890"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers"))
                .andExpect(content().string(containsString("Customer with email ana@mail.com already exists")))
                .andExpect(content().string(containsString("value=\"01234567890\"")));
    }

    @Test
    void customerApplications_showsTheResultList() throws Exception {
        when(customerService.getById(1L)).thenReturn(customer());
        when(loanApplicationService.getByCustomerAndStatus(1L, ApplicationStatus.APPROVED))
                .thenReturn(List.of(application(ApplicationStatus.APPROVED)));

        mockMvc.perform(get("/ui/customers/1/applications").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-list"))
                .andExpect(content().string(containsString("Ana Beridze: approved applications")));
    }

    @Test
    void customers_passesTheSearchTextToTheServiceAndKeepsItInThePager() throws Exception {
        // 13 matches with 6 per page = 3 pages, we are on the first one
        when(customerService.getAll(eq("ana"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer()), PageRequest.of(0, 6), 13));

        mockMvc.perform(get("/ui/customers").param("search", "ana"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"ana\"")))
                .andExpect(content().string(containsString("Page 1 of 3")))
                .andExpect(content().string(containsString("/ui/customers?search=ana&amp;page=1")));
    }

    @Test
    void customers_saysNothingMatches_whenTheSearchFindsNothing() throws Exception {
        when(customerService.getAll(eq("zzz"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/ui/customers").param("search", "zzz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No customer matches")));
    }

    @Test
    void editCustomer_showsTheFormFilledWithTheCurrentValues() throws Exception {
        when(customerService.getById(1L)).thenReturn(customer());

        mockMvc.perform(get("/ui/customers/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-form"))
                .andExpect(content().string(containsString("Edit customer 1")))
                .andExpect(content().string(containsString("value=\"ana@mail.com\"")))
                .andExpect(content().string(containsString("action=\"/ui/customers/1\"")));
    }

    @Test
    void updateCustomer_savesAndRedirects() throws Exception {
        CustomerRequest expected = new CustomerRequest("Ana Beridze", "ana@mail.com", "01234567890", null);
        when(customerService.update(1L, expected)).thenReturn(customer());

        mockMvc.perform(post("/ui/customers/1")
                        .param("fullName", "Ana Beridze")
                        .param("email", "ana@mail.com")
                        .param("personalNumber", "01234567890")
                        .param("phone", ""))
                .andExpect(redirectedUrl("/ui/customers"))
                .andExpect(flash().attribute("message", "Ana Beridze was saved"));
    }

    @Test
    void updateCustomer_keepsTheEditForm_whenEmailBelongsToAnotherCustomer() throws Exception {
        when(customerService.update(eq(1L), any(CustomerRequest.class)))
                .thenThrow(new ConflictException("Customer with email nino@mail.com already exists"));

        mockMvc.perform(post("/ui/customers/1")
                        .param("fullName", "Ana Beridze")
                        .param("email", "nino@mail.com")
                        .param("personalNumber", "01234567890"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-form"))
                .andExpect(content().string(containsString("Customer with email nino@mail.com already exists")))
                .andExpect(content().string(containsString("value=\"nino@mail.com\"")));
    }

    @Test
    void deleteCustomer_goesBackWithTheReason_whenCustomerHasApplications() throws Exception {
        doThrow(new ConflictException("Customer with id 1 has applications and cannot be deleted"))
                .when(customerService).delete(1L);

        mockMvc.perform(post("/ui/customers/1/delete").header("Referer", "http://localhost/ui/customers"))
                .andExpect(redirectedUrl("/ui/customers"))
                .andExpect(flash().attribute("error", "Customer with id 1 has applications and cannot be deleted"));
    }
}
