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
import ge.kiu.loanplatform.exception.BusinessRuleException;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.service.CustomerService;
import ge.kiu.loanplatform.service.DocumentService;
import ge.kiu.loanplatform.service.LoanApplicationService;
import ge.kiu.loanplatform.service.LoanProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    // ---------- overview ----------

    @Test
    void home_showsWaitingApplicationsAndTheReport() throws Exception {
        when(loanApplicationService.getAll(eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(pageOf(application(ApplicationStatus.SUBMITTED)));
        when(loanApplicationService.getApprovedTotalsPerProduct())
                .thenReturn(List.of(new ProductTotalResponse("Consumer Loan", new BigDecimal("12000.00"))));

        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("1 application is waiting for a decision")))
                .andExpect(content().string(containsString("12,000.00")));
    }

    @Test
    void home_showsEmptyStates_whenThereIsNothingToShow() throws Exception {
        when(loanApplicationService.getAll(eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(pageOf());
        when(loanApplicationService.getApprovedTotalsPerProduct()).thenReturn(List.of());

        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No applications are waiting for a decision")))
                .andExpect(content().string(containsString("Nothing has been approved yet")));
    }

    // ---------- customers ----------

    @Test
    void customers_listsCustomers() throws Exception {
        when(customerService.getAll()).thenReturn(List.of(customer()));

        mockMvc.perform(get("/ui/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers"))
                .andExpect(content().string(containsString("Ana Beridze")))
                .andExpect(content().string(containsString("/ui/customers/1/applications?status=APPROVED")))
                .andExpect(content().string(containsString("/ui/applications/new?customerId=1")));
    }

    @Test
    void customers_showsAnEmptyState_whenThereAreNoCustomers() throws Exception {
        when(customerService.getAll()).thenReturn(List.of());

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
        when(customerService.getAll()).thenReturn(List.of());

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
        when(customerService.getAll()).thenReturn(List.of());

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
        when(customerService.getAll()).thenReturn(List.of(customer()));
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

    // ---------- products ----------

    @Test
    void products_listsProductsWithEditLinks() throws Exception {
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(content().string(containsString("Consumer Loan")))
                .andExpect(content().string(containsString("ID Card")))
                .andExpect(content().string(containsString("/ui/products/1/edit")));
    }

    @Test
    void newProduct_showsAnEmptyForm() throws Exception {
        mockMvc.perform(get("/ui/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Create product")))
                .andExpect(content().string(containsString("action=\"/ui/products\"")));
    }

    @Test
    void createProduct_acceptsAnEmptyDocumentTypeList() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class))).thenReturn(product());

        mockMvc.perform(productForm(post("/ui/products"), "20000", ""))
                .andExpect(redirectedUrl("/ui/products"))
                .andExpect(flash().attribute("message", "Consumer Loan was created"));

        verify(loanProductService).create(productRequest("20000", Set.of()));
    }

    @Test
    void createProduct_showsTheFormAgain_whenAValueIsNotANumber() throws Exception {
        mockMvc.perform(productForm(post("/ui/products"), "a lot", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Enter a valid value")))
                .andExpect(content().string(containsString("value=\"a lot\"")));

        verifyNoInteractions(loanProductService);
    }

    @Test
    void editProduct_showsTheFormFilledWithTheCurrentValues() throws Exception {
        when(loanProductService.getById(1L)).thenReturn(product());

        mockMvc.perform(get("/ui/products/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Edit product 1")))
                .andExpect(content().string(containsString("action=\"/ui/products/1\"")))
                .andExpect(content().string(containsString("value=\"Consumer Loan\"")))
                .andExpect(content().string(containsString("value=\"14.50\"")))
                .andExpect(content().string(containsString("value=\"1,2\"")))
                .andExpect(content().string(containsString("Save changes")));
    }

    @Test
    void updateProduct_savesAndRedirects() throws Exception {
        when(loanProductService.update(eq(1L), any(LoanProductRequest.class))).thenReturn(product());

        mockMvc.perform(productForm(post("/ui/products/1"), "30000", "1,2"))
                .andExpect(redirectedUrl("/ui/products"))
                .andExpect(flash().attribute("message", "Consumer Loan was saved"));

        verify(loanProductService).update(1L, productRequest("30000", Set.of(1L, 2L)));
    }

    @Test
    void updateProduct_keepsTheEditForm_whenTheServiceRefuses() throws Exception {
        when(loanProductService.update(eq(1L), any(LoanProductRequest.class)))
                .thenThrow(new BusinessRuleException("Minimum amount cannot be greater than maximum amount"));

        mockMvc.perform(productForm(post("/ui/products/1"), "100", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Minimum amount cannot be greater than maximum amount")))
                // still the edit form of product 1, with the typed value
                .andExpect(content().string(containsString("action=\"/ui/products/1\"")))
                .andExpect(content().string(containsString("value=\"100\"")));
    }

    @Test
    void deleteProduct_showsTheErrorPage_whenProductHasApplications() throws Exception {
        doThrow(new ConflictException("Loan product with id 1 has applications and cannot be deleted"))
                .when(loanProductService).delete(1L);

        mockMvc.perform(post("/ui/products/1/delete"))
                .andExpect(view().name("error-message"))
                .andExpect(content().string(containsString("has applications and cannot be deleted")));
    }

    // ---------- applications ----------

    @Test
    void applications_showsOnePageOfApplications() throws Exception {
        when(loanApplicationService.getAll(eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(pageOf(application(ApplicationStatus.SUBMITTED)));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/applications").param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(view().name("applications"))
                .andExpect(content().string(containsString("/ui/applications/7")))
                .andExpect(content().string(containsString("5,000.00")));
    }

    @Test
    void applications_showsAllStatusesAndAnEmptyState_whenStatusIsEmpty() throws Exception {
        // ?status= (empty) must mean "no filter"
        when(loanApplicationService.getAll(isNull(), any(Pageable.class))).thenReturn(pageOf());

        mockMvc.perform(get("/ui/applications").param("status", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No applications yet")))
                // no table header without rows
                .andExpect(content().string(not(containsString("Submitted on"))));
    }

    @Test
    void newApplication_preselectsTheCustomerFromTheUrl() throws Exception {
        when(customerService.getAll()).thenReturn(List.of(customer()));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/applications/new").param("customerId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-form"))
                .andExpect(content().string(containsString("selected=\"selected\">Ana Beridze (customer 1)</option>")));
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
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "Application 7 was submitted"));
    }

    @Test
    void createApplication_showsTheFormAgain_whenFormIsEmpty() throws Exception {
        when(customerService.getAll()).thenReturn(List.of(customer()));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(post("/ui/applications")
                        .param("customerId", "")
                        .param("loanProductId", "")
                        .param("amount", "")
                        .param("termMonths", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("application-form"))
                .andExpect(content().string(containsString("Customer id is required")))
                .andExpect(content().string(containsString("Amount is required")));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void createApplication_keepsTheForm_whenAmountIsOutsideTheProductRange() throws Exception { // rule 1
        when(customerService.getAll()).thenReturn(List.of(customer()));
        when(loanProductService.getAll()).thenReturn(List.of(product()));
        when(loanApplicationService.create(any(LoanApplicationRequest.class)))
                .thenThrow(new BusinessRuleException("Amount must be between 500.00 and 20000.00 for product Consumer Loan"));

        mockMvc.perform(post("/ui/applications")
                        .param("customerId", "1")
                        .param("loanProductId", "1")
                        .param("amount", "50")
                        .param("termMonths", "12"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-form"))
                .andExpect(content().string(containsString("Amount must be between 500.00 and 20000.00")))
                .andExpect(content().string(containsString("value=\"50\"")));
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
                .andExpect(content().string(containsString("Consumer Loan applications above 1000")));
    }

    // ---------- one application ----------

    @Test
    void applicationDetail_offersTheDecision_whenApplicationIsSubmitted() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.SUBMITTED));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-detail"))
                .andExpect(content().string(containsString("id-card.pdf")))
                .andExpect(content().string(containsString("15 Mar 2026, 10:30")))
                .andExpect(content().string(containsString("Approve application")))
                // the upload form offers the documents the product requires
                .andExpect(content().string(containsString("<option value=\"1\">ID Card</option>")));
    }

    @Test
    void applicationDetail_explainsWhyThereIsNoDecision_whenApplicationIsApproved() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.APPROVED));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This application is approved.")))
                .andExpect(content().string(not(containsString("Approve application"))));
    }

    @Test
    void reject_redirectsWithAnError_whenReasonIsEmpty() throws Exception { // rule 5
        mockMvc.perform(post("/ui/applications/7/reject").param("decisionComment", ""))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("error", "Rejection reason is required"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void reject_callsTheService_whenReasonIsGiven() throws Exception {
        mockMvc.perform(post("/ui/applications/7/reject").param("decisionComment", "Income too low"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "Application 7 was rejected"));

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
                .andExpect(flash().attribute("message", "id-card.pdf was uploaded"));
    }

    // ---------- error page ----------

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
                List.of(new DocumentTypeResponse(1L, "ID Card"), new DocumentTypeResponse(2L, "Salary Certificate")));
    }

    private LoanApplicationResponse application(ApplicationStatus status) {
        return new LoanApplicationResponse(7L, new BigDecimal("5000.00"), 12, status,
                LocalDateTime.of(2026, 3, 15, 10, 30),
                null, 1L, "Ana Beridze", 1L, "Consumer Loan",
                List.of(new DocumentResponse(10L, "id-card.pdf", 1L, "ID Card", LocalDateTime.now())));
    }

    private Page<LoanApplicationResponse> pageOf(LoanApplicationResponse... applications) {
        return new PageImpl<>(List.of(applications), PageRequest.of(0, 5), applications.length);
    }

    // the product form with every field filled; only maxAmount and the document ids differ between tests
    private MockHttpServletRequestBuilder productForm(
            MockHttpServletRequestBuilder request,
            String maxAmount, String documentTypeIds) {
        return request
                .param("name", "Consumer Loan")
                .param("interestRate", "14.5")
                .param("minAmount", "500")
                .param("maxAmount", maxAmount)
                .param("minTermMonths", "3")
                .param("maxTermMonths", "48")
                .param("requiredDocumentTypeIds", documentTypeIds);
    }

    private LoanProductRequest productRequest(String maxAmount, Set<Long> documentTypeIds) {
        return new LoanProductRequest("Consumer Loan", new BigDecimal("14.5"), new BigDecimal("500"),
                new BigDecimal(maxAmount), 3, 48, documentTypeIds);
    }
}
