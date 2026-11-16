package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.CustomerRequest;
import ge.kiu.loanplatform.dto.CustomerResponse;
import ge.kiu.loanplatform.dto.DocumentResponse;
import ge.kiu.loanplatform.dto.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.LoanProductRequest;
import ge.kiu.loanplatform.dto.LoanProductResponse;
import ge.kiu.loanplatform.dto.ProductTotalResponse;
import ge.kiu.loanplatform.dto.RejectRequest;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.service.CustomerService;
import ge.kiu.loanplatform.service.DocumentService;
import ge.kiu.loanplatform.service.LoanApplicationService;
import ge.kiu.loanplatform.service.LoanProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Web layer test for the Thymeleaf website. The services are mocks.
// The templates are really rendered, so these tests also catch mistakes in the HTML files.
@WebMvcTest(WebController.class)
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private LoanProductService loanProductService;
    @MockitoBean
    private LoanApplicationService loanApplicationService;
    @MockitoBean
    private DocumentService documentService;

    // ---------- pages ----------

    @Test
    void home_showsTheApprovedTotalsReport() throws Exception {
        when(loanApplicationService.getApprovedTotalsPerProduct())
                .thenReturn(List.of(new ProductTotalResponse("Consumer Loan", new BigDecimal("12000.00"))));

        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("Consumer Loan")))
                .andExpect(content().string(containsString("12000.00")));
    }

    @Test
    void customers_listsCustomers() throws Exception {
        when(customerService.getAll()).thenReturn(List.of(customer()));

        mockMvc.perform(get("/ui/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers"))
                .andExpect(content().string(containsString("Ana Beridze")))
                .andExpect(content().string(containsString("/ui/customers/1/applications?status=APPROVED")));
    }

    @Test
    void products_listsProductsWithRequiredDocuments() throws Exception {
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(content().string(containsString("Consumer Loan")))
                .andExpect(content().string(containsString("1 ID Card")));
    }

    @Test
    void applications_showsOnePageOfApplications() throws Exception {
        when(loanApplicationService.getAll(eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(application(ApplicationStatus.SUBMITTED)), PageRequest.of(0, 5), 1));
        when(customerService.getAll()).thenReturn(List.of(customer()));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/applications").param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(view().name("applications"))
                .andExpect(content().string(containsString("Page 1 of 1 (1 applications)")))
                .andExpect(content().string(containsString("/ui/applications/7")));
    }

    @Test
    void applications_showsAllStatuses_whenStatusIsEmpty() throws Exception {
        // the "All" option of the filter sends ?status= (empty), which must mean "no filter"
        when(loanApplicationService.getAll(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

        mockMvc.perform(get("/ui/applications").param("status", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No applications found.")));
    }

    @Test
    void applicationDetail_showsApplicationDocumentsAndRequiredDocuments() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.SUBMITTED));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-detail"))
                .andExpect(content().string(containsString("id-card.pdf")))
                .andExpect(content().string(containsString("2026-03-15 10:30")))
                .andExpect(content().string(not(containsString("No documents uploaded."))))
                .andExpect(content().string(containsString("This product requires:")));
    }

    @Test
    void searchApplications_showsTheResultList() throws Exception {
        when(loanApplicationService.searchByProductAndAmount("Consumer Loan", new BigDecimal("1000")))
                .thenReturn(List.of(application(ApplicationStatus.APPROVED)));

        mockMvc.perform(get("/ui/applications/search")
                        .param("productName", "Consumer Loan")
                        .param("amountAbove", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-list"))
                .andExpect(content().string(containsString("Applications for Consumer Loan above 1000")));
    }

    @Test
    void customerApplications_showsTheResultList() throws Exception {
        when(loanApplicationService.getByCustomerAndStatus(1L, ApplicationStatus.APPROVED))
                .thenReturn(List.of(application(ApplicationStatus.APPROVED)));

        mockMvc.perform(get("/ui/customers/1/applications").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-list"))
                .andExpect(content().string(containsString("APPROVED applications of customer 1")));
    }

    // ---------- forms ----------

    @Test
    void createCustomer_redirectsWithMessage_andTreatsEmptyPhoneAsNull() throws Exception {
        when(customerService.create(any(CustomerRequest.class))).thenReturn(customer());

        mockMvc.perform(post("/ui/customers")
                        .param("fullName", "Ana Beridze")
                        .param("email", "ana@mail.com")
                        .param("personalNumber", "01234567890")
                        .param("phone", "")) // an empty form field
                .andExpect(redirectedUrl("/ui/customers"))
                .andExpect(flash().attribute("message", "Customer created with id 1"));

        // records compare by value, so this checks every field that reached the service
        verify(customerService).create(new CustomerRequest("Ana Beridze", "ana@mail.com", "01234567890", null));
    }

    @Test
    void createCustomer_redirectsWithErrors_whenFormIsInvalid() throws Exception {
        mockMvc.perform(post("/ui/customers")
                        .param("fullName", "<script>alert(1)</script>")
                        .param("email", "not-an-email")
                        .param("personalNumber", "12"))
                .andExpect(redirectedUrl("/ui/customers"))
                .andExpect(flash().attribute("errors", hasItem("Personal number must be exactly 11 digits")))
                .andExpect(flash().attribute("errors", hasItem("Email must be a valid email address")));

        verifyNoInteractions(customerService);
    }

    @Test
    void createProduct_acceptsAnEmptyDocumentTypeList() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class))).thenReturn(product());

        mockMvc.perform(post("/ui/products")
                        .param("name", "Consumer Loan")
                        .param("interestRate", "14.5")
                        .param("minAmount", "500")
                        .param("maxAmount", "20000")
                        .param("minTermMonths", "3")
                        .param("maxTermMonths", "48")
                        .param("requiredDocumentTypeIds", ""))
                .andExpect(redirectedUrl("/ui/products"))
                .andExpect(flash().attribute("message", "Product created with id 1"));

        verify(loanProductService).create(new LoanProductRequest("Consumer Loan", new BigDecimal("14.5"),
                new BigDecimal("500"), new BigDecimal("20000"), 3, 48, Set.of()));
    }

    @Test
    void createProduct_readsCommaSeparatedDocumentTypeIds() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class))).thenReturn(product());

        mockMvc.perform(post("/ui/products")
                        .param("name", "Consumer Loan")
                        .param("interestRate", "14.5")
                        .param("minAmount", "500")
                        .param("maxAmount", "20000")
                        .param("minTermMonths", "3")
                        .param("maxTermMonths", "48")
                        .param("requiredDocumentTypeIds", "1,2"))
                .andExpect(redirectedUrl("/ui/products"));

        verify(loanProductService).create(new LoanProductRequest("Consumer Loan", new BigDecimal("14.5"),
                new BigDecimal("500"), new BigDecimal("20000"), 3, 48, Set.of(1L, 2L)));
    }

    @Test
    void createApplication_redirectsToTheNewApplication() throws Exception {
        when(loanApplicationService.create(any(LoanApplicationRequest.class)))
                .thenReturn(application(ApplicationStatus.SUBMITTED));

        mockMvc.perform(post("/ui/applications")
                        .param("customerId", "1")
                        .param("loanProductId", "1")
                        .param("amount", "5000")
                        .param("termMonths", "12"))
                .andExpect(redirectedUrl("/ui/applications/7"));
    }

    @Test
    void createApplication_redirectsWithErrors_whenFormIsEmpty() throws Exception {
        mockMvc.perform(post("/ui/applications")
                        .param("customerId", "")
                        .param("loanProductId", "")
                        .param("amount", "")
                        .param("termMonths", ""))
                .andExpect(redirectedUrl("/ui/applications"))
                .andExpect(flash().attribute("errors", hasItem("Amount is required")));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void reject_redirectsWithErrors_whenReasonIsEmpty() throws Exception { // rule 5
        mockMvc.perform(post("/ui/applications/7/reject").param("decisionComment", ""))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("errors", hasItem("Rejection reason is required")));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void reject_callsTheService_whenReasonIsGiven() throws Exception {
        mockMvc.perform(post("/ui/applications/7/reject").param("decisionComment", "Income too low"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "Application rejected"));

        verify(loanApplicationService).reject(7L, new RejectRequest("Income too low"));
    }

    @Test
    void uploadDocument_redirectsBackToTheApplication() throws Exception {
        when(documentService.upload(eq(7L), any(MultipartFile.class), eq(1L)))
                .thenReturn(new DocumentResponse(10L, "id-card.pdf", 1L, "ID Card", LocalDateTime.now()));

        mockMvc.perform(multipart("/ui/applications/7/documents")
                        .file(new MockMultipartFile("file", "id-card.pdf", "application/pdf", "pdf".getBytes()))
                        .param("documentTypeId", "1"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "Uploaded id-card.pdf"));
    }

    // ---------- errors ----------

    @Test
    void approve_showsTheErrorPage_whenApplicationIsNotSubmitted() throws Exception { // rule 4
        when(loanApplicationService.approve(7L))
                .thenThrow(new ConflictException("Application with id 7 is APPROVED and cannot be approved"));

        mockMvc.perform(post("/ui/applications/7/approve"))
                .andExpect(view().name("error-message"))
                .andExpect(content().string(containsString("ConflictException")))
                .andExpect(content().string(containsString("Application with id 7 is APPROVED and cannot be approved")));
    }

    @Test
    void applicationDetail_showsTheErrorPage_whenApplicationDoesNotExist() throws Exception {
        when(loanApplicationService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Loan application with id 99 not found"));

        mockMvc.perform(get("/ui/applications/99"))
                .andExpect(view().name("error-message"))
                .andExpect(content().string(containsString("Loan application with id 99 not found")));
    }

    // ---------- helpers ----------

    private CustomerResponse customer() {
        return new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", "555123456");
    }

    private LoanProductResponse product() {
        return new LoanProductResponse(1L, "Consumer Loan", new BigDecimal("14.50"),
                new BigDecimal("500.00"), new BigDecimal("20000.00"), 3, 48,
                List.of(new DocumentTypeResponse(1L, "ID Card")));
    }

    private LoanApplicationResponse application(ApplicationStatus status) {
        return new LoanApplicationResponse(7L, new BigDecimal("5000.00"), 12, status,
                LocalDateTime.of(2026, 3, 15, 10, 30),
                null, 1L, "Ana Beridze", 1L, "Consumer Loan",
                List.of(new DocumentResponse(10L, "id-card.pdf", 1L, "ID Card", LocalDateTime.now())));
    }
}
