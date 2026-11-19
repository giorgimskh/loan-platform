package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.application.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.LoanApplicationUpdateRequest;
import ge.kiu.loanplatform.dto.application.RejectRequest;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.calculation.RepaymentScheduleResponse;
import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.dto.customer.CustomerRequest;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeRequest;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.product.LoanProductRequest;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;
import ge.kiu.loanplatform.dto.report.ProductTotalResponse;
import ge.kiu.loanplatform.dto.report.StatisticsResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.customer.CustomerService;
import ge.kiu.loanplatform.service.document.DocumentService;
import ge.kiu.loanplatform.service.document.DocumentTypeService;
import ge.kiu.loanplatform.service.product.LoanProductService;
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
    @MockitoBean
    private DocumentTypeService documentTypeService;

    // ---------- overview ----------

    @Test
    void home_showsWaitingApplicationsAndTheReport() throws Exception {
        when(loanApplicationService.getAll(eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(pageOf(application(ApplicationStatus.SUBMITTED)));
        when(loanApplicationService.getApprovedTotalsPerProduct())
                .thenReturn(List.of(new ProductTotalResponse("Consumer Loan", new BigDecimal("12000.00"))));
        when(loanApplicationService.getStatistics()).thenReturn(new StatisticsResponse(
                4, 1, 2, 1, new BigDecimal("66.7"), new BigDecimal("4500.00"), new BigDecimal("12000.00")));

        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("See all waiting applications")))
                .andExpect(content().string(containsString("12,000.00")))
                // the statistics: approval rate, average amount and the status chart
                .andExpect(content().string(containsString("Approval rate 66.7%")))
                .andExpect(content().string(containsString("Average amount 4,500.00")))
                .andExpect(content().string(containsString("1 submitted, 2 approved, 1 rejected")));
    }

    @Test
    void home_showsEmptyStates_whenThereIsNothingToShow() throws Exception {
        when(loanApplicationService.getAll(eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(pageOf());
        when(loanApplicationService.getApprovedTotalsPerProduct()).thenReturn(List.of());
        when(loanApplicationService.getStatistics()).thenReturn(new StatisticsResponse(
                0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No applications are waiting for a decision")))
                .andExpect(content().string(containsString("No applications yet. The chart appears with the first one.")))
                .andExpect(content().string(containsString("Nothing has been approved yet")));
    }

    // ---------- customers ----------

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
    void newProduct_showsAnEmptyFormWithOneCheckboxPerDocumentType() throws Exception {
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Create product")))
                .andExpect(content().string(containsString("ID Card")))
                .andExpect(content().string(containsString("Bank Statement")))
                .andExpect(content().string(not(containsString("checked=\"checked\""))))
                .andExpect(content().string(containsString("action=\"/ui/products\"")));
    }

    @Test
    void createProduct_sendsAnEmptySet_whenNoCheckboxIsTicked() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class))).thenReturn(product());

        mockMvc.perform(productForm(post("/ui/products"), "20000"))
                .andExpect(redirectedUrl("/ui/products"))
                .andExpect(flash().attribute("message", "Consumer Loan was created"));

        verify(loanProductService).create(productRequest("20000", Set.of()));
    }

    @Test
    void createProduct_showsTheFormAgain_whenAValueIsNotANumber() throws Exception {
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(productForm(post("/ui/products"), "a lot", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Enter a valid value")))
                .andExpect(content().string(containsString("value=\"a lot\"")))
                // the ticked checkbox is still ticked, the other one is not
                .andExpect(content().string(containsString("value=\"2\" checked=\"checked\"")))
                .andExpect(content().string(not(containsString("value=\"1\" checked=\"checked\""))));

        verify(loanProductService, never()).create(any());
    }

    @Test
    void editProduct_showsTheFormFilledWithTheCurrentValues() throws Exception {
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/products/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Edit product 1")))
                .andExpect(content().string(containsString("action=\"/ui/products/1\"")))
                .andExpect(content().string(containsString("value=\"Consumer Loan\"")))
                .andExpect(content().string(containsString("value=\"14.50\"")))
                // the product requires document types 1 and 2, but not 3
                .andExpect(content().string(containsString("value=\"1\" checked=\"checked\"")))
                .andExpect(content().string(containsString("value=\"2\" checked=\"checked\"")))
                .andExpect(content().string(not(containsString("value=\"3\" checked=\"checked\""))))
                .andExpect(content().string(containsString("Save changes")));
    }

    @Test
    void updateProduct_savesAndRedirects() throws Exception {
        when(loanProductService.update(eq(1L), any(LoanProductRequest.class))).thenReturn(product());

        mockMvc.perform(productForm(post("/ui/products/1"), "30000", "1", "2"))
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
                .andExpect(content().string(containsString("5,000.00")))
                // the Actions menu of a SUBMITTED application offers everything
                .andExpect(content().string(containsString("/ui/applications/7/edit")))
                .andExpect(content().string(containsString("/ui/applications/7/approve")))
                .andExpect(content().string(containsString("/ui/applications/7/reject")))
                .andExpect(content().string(containsString("/ui/applications/7/delete")));
    }

    @Test
    void applications_offersOnlyRejectInTheMenu_whenApplicationIsApproved() throws Exception {
        when(loanApplicationService.getAll(isNull(), any(Pageable.class)))
                .thenReturn(pageOf(application(ApplicationStatus.APPROVED)));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/applications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/ui/applications/7/reject")))
                .andExpect(content().string(not(containsString("/ui/applications/7/approve"))))
                .andExpect(content().string(not(containsString("/ui/applications/7/edit"))))
                .andExpect(content().string(not(containsString("/ui/applications/7/delete"))));
    }

    @Test
    void approve_returnsToTheListItWasClickedOn() throws Exception {
        mockMvc.perform(post("/ui/applications/7/approve")
                        .header("Referer", "http://localhost:8080/ui/applications?status=SUBMITTED"))
                .andExpect(redirectedUrl("/ui/applications?status=SUBMITTED"))
                .andExpect(flash().attribute("message", "Application 7 was approved"));

        verify(loanApplicationService).approve(7L);
    }

    @Test
    void reject_returnsToTheListWithAnError_whenReasonIsEmpty() throws Exception { // rule 5
        mockMvc.perform(post("/ui/applications/7/reject")
                        .param("decisionComment", "")
                        .header("Referer", "http://localhost:8080/ui/applications"))
                .andExpect(redirectedUrl("/ui/applications"))
                .andExpect(flash().attribute("error", "Rejection reason is required"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void cancel_goesToTheList_whenClickedOnTheApplicationPage() throws Exception {
        // the application's own page no longer exists after cancelling
        mockMvc.perform(post("/ui/applications/7/delete")
                        .header("Referer", "http://localhost:8080/ui/applications/7"))
                .andExpect(redirectedUrl("/ui/applications"))
                .andExpect(flash().attribute("message", "Application 7 was cancelled"));

        verify(loanApplicationService).delete(7L);
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
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer())));
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
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer())));
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
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer())));
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
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-detail"))
                .andExpect(content().string(containsString("id-card.pdf")))
                .andExpect(content().string(containsString("15 Mar 2026, 10:30")))
                .andExpect(content().string(containsString("Approve application")))
                // the upload form offers every document type
                .andExpect(content().string(containsString("<option value=\"1\">ID Card</option>")));
    }

    @Test
    void applicationDetail_offersOnlyReject_whenApplicationIsApproved() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.APPROVED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This application is approved.")))
                .andExpect(content().string(containsString("Reject application")))
                .andExpect(content().string(not(containsString("Approve application"))))
                .andExpect(content().string(not(containsString("Cancel this application"))));
    }

    @Test
    void applicationDetail_offersEditOnlyWhileSubmitted() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.REJECTED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/ui/applications/7/edit"))));
    }

    @Test
    void editApplication_showsTheFormFilledWithTheCurrentValues() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.SUBMITTED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/applications/7/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-form"))
                .andExpect(content().string(containsString("Edit application No. 7")))
                .andExpect(content().string(containsString("action=\"/ui/applications/7\"")))
                .andExpect(content().string(containsString("value=\"5000.00\"")))
                .andExpect(content().string(containsString("value=\"12\"")))
                // the customer is shown as text, there is no customer dropdown
                .andExpect(content().string(containsString("Ana Beridze")))
                .andExpect(content().string(not(containsString("name=\"customerId\""))));
    }

    @Test
    void updateApplication_savesAndRedirects() throws Exception {
        mockMvc.perform(post("/ui/applications/7")
                        .param("loanProductId", "1")
                        .param("amount", "6000")
                        .param("termMonths", "24"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "Application 7 was saved"));

        verify(loanApplicationService).update(7L, new LoanApplicationUpdateRequest(1L, new BigDecimal("6000"), 24));
    }

    @Test
    void updateApplication_keepsTheEditForm_whenApplicationIsAlreadyDecided() throws Exception { // rule 4
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.APPROVED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());
        when(loanApplicationService.update(eq(7L), any(LoanApplicationUpdateRequest.class)))
                .thenThrow(new ConflictException("Application with id 7 is APPROVED and cannot be modified"));

        mockMvc.perform(post("/ui/applications/7")
                        .param("loanProductId", "1")
                        .param("amount", "6000")
                        .param("termMonths", "24"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-form"))
                .andExpect(content().string(containsString("Application with id 7 is APPROVED and cannot be modified")))
                .andExpect(content().string(containsString("value=\"6000\"")));
    }

    @Test
    void updateApplication_showsFieldErrors_whenFormIsInvalid() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.SUBMITTED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(post("/ui/applications/7")
                        .param("loanProductId", "1")
                        .param("amount", "")
                        .param("termMonths", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("application-form"))
                .andExpect(content().string(containsString("Amount is required")))
                .andExpect(content().string(containsString("Term must be at least 1 month")));

        verify(loanApplicationService, never()).update(any(), any());
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

    // ---------- a service refuses the request ----------

    @Test
    void cancel_goesBackToTheApplicationWithTheReason_whenApplicationIsNotSubmitted() throws Exception { // rule 4
        doThrow(new ConflictException("Application with id 7 is APPROVED and cannot be cancelled"))
                .when(loanApplicationService).delete(7L);

        // the browser sends the page the user clicked on in the Referer header
        mockMvc.perform(post("/ui/applications/7/delete")
                        .header("Referer", "http://localhost:8080/ui/applications/7"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("error", "Application with id 7 is APPROVED and cannot be cancelled"));
    }

    @Test
    void deleteProduct_goesBackToTheProductListWithTheReason_whenProductHasApplications() throws Exception {
        doThrow(new ConflictException("Loan product with id 1 has applications and cannot be deleted"))
                .when(loanProductService).delete(1L);

        mockMvc.perform(post("/ui/products/1/delete")
                        .header("Referer", "http://localhost:8080/ui/products?sort=name"))
                .andExpect(redirectedUrl("/ui/products?sort=name"))
                .andExpect(flash().attribute("error", "Loan product with id 1 has applications and cannot be deleted"));
    }

    @Test
    void approve_doesNotRedirectToAnotherWebsite() throws Exception {
        when(loanApplicationService.approve(7L))
                .thenThrow(new ConflictException("Application with id 7 is APPROVED and cannot be approved"));

        // a Referer that is not one of our /ui pages is ignored: the error page is shown
        mockMvc.perform(post("/ui/applications/7/approve")
                        .header("Referer", "http://evil.example/steal"))
                .andExpect(status().isOk())
                .andExpect(view().name("error-message"));
    }

    // ---------- error page (shown when there is no page to go back to) ----------

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

    // ---------- customers: search, edit, delete ----------

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

    // ---------- document types ----------

    @Test
    void documentTypes_listsTheTypesWithTheAddForm() throws Exception {
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/document-types"))
                .andExpect(status().isOk())
                .andExpect(view().name("document-types"))
                .andExpect(content().string(containsString("Bank Statement")))
                .andExpect(content().string(containsString("action=\"/ui/document-types/3/delete\"")))
                .andExpect(content().string(containsString("Add document type")));
    }

    @Test
    void createDocumentType_savesAndRedirects() throws Exception {
        when(documentTypeService.create(new DocumentTypeRequest("Passport")))
                .thenReturn(new DocumentTypeResponse(4L, "Passport"));

        mockMvc.perform(post("/ui/document-types").param("name", "Passport"))
                .andExpect(redirectedUrl("/ui/document-types"))
                .andExpect(flash().attribute("message", "Passport was added"));
    }

    @Test
    void createDocumentType_showsTheFieldError_whenNameIsEmpty() throws Exception {
        mockMvc.perform(post("/ui/document-types").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("document-types"))
                .andExpect(content().string(containsString("Name is required")));

        verify(documentTypeService, never()).create(any());
    }

    @Test
    void deleteDocumentType_goesBackWithTheReason_whenAProductRequiresIt() throws Exception {
        doThrow(new ConflictException("Document type with id 1 is required by a loan product and cannot be deleted"))
                .when(documentTypeService).delete(1L);

        mockMvc.perform(post("/ui/document-types/1/delete").header("Referer", "http://localhost/ui/document-types"))
                .andExpect(redirectedUrl("/ui/document-types"))
                .andExpect(flash().attribute("error",
                        "Document type with id 1 is required by a loan product and cannot be deleted"));
    }

    // ---------- payment calculator ----------

    @Test
    void calculator_showsAnEmptyFormWithoutErrors_whenOpenedByALink() throws Exception {
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/calculator").param("loanProductId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("calculator"))
                // the product from the link is already chosen, and nothing is marked as wrong
                .andExpect(content().string(containsString("selected=\"selected\">Consumer Loan at 14.50% per year</option>")))
                .andExpect(content().string(not(containsString("Amount is required"))));

        verify(loanApplicationService, never()).calculate(any(), any(), any());
    }

    @Test
    void calculator_showsTheResult() throws Exception {
        when(loanProductService.getAll()).thenReturn(List.of(product()));
        when(loanApplicationService.calculate(1L, new BigDecimal("5000"), 12)).thenReturn(calculation());

        mockMvc.perform(get("/ui/calculator")
                        .param("loanProductId", "1").param("amount", "5000").param("termMonths", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("450.11")))
                .andExpect(content().string(containsString("5,401.36")))
                .andExpect(content().string(containsString(
                        "/ui/applications/new?loanProductId=1&amp;amount=5000&amp;termMonths=12")));
    }

    @Test
    void calculator_showsTheReason_whenAmountIsOutsideTheProductRange() throws Exception { // rule 1
        when(loanProductService.getAll()).thenReturn(List.of(product()));
        when(loanApplicationService.calculate(1L, new BigDecimal("99"), 12))
                .thenThrow(new BusinessRuleException("Amount must be between 500.00 and 20000.00 for product Consumer Loan"));

        mockMvc.perform(get("/ui/calculator")
                        .param("loanProductId", "1").param("amount", "99").param("termMonths", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Amount must be between 500.00 and 20000.00")))
                .andExpect(content().string(containsString("value=\"99\"")));
    }

    @Test
    void calculator_showsFieldErrors_whenTheFormIsInvalid() throws Exception {
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/calculator")
                        .param("loanProductId", "").param("amount", "0").param("termMonths", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Loan product id is required")))
                .andExpect(content().string(containsString("Amount must be greater than 0")))
                .andExpect(content().string(containsString("Term must be at least 1 month")));

        verify(loanApplicationService, never()).calculate(any(), any(), any());
    }

    // ---------- one application: required documents, history, schedule, documents ----------

    @Test
    void applicationDetail_showsWhichRequiredDocumentsAreMissing() throws Exception { // rule 7
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.SUBMITTED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(loanApplicationService.getMissingDocuments(7L))
                .thenReturn(List.of(new DocumentTypeResponse(2L, "Salary Certificate")));

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"done\"")))    // ID Card is uploaded
                .andExpect(content().string(containsString("class=\"missing\""))) // Salary Certificate is not
                .andExpect(content().string(containsString("Approval is refused until the missing documents are uploaded")));
    }

    @Test
    void applicationDetail_showsTheHistoryWithTheRejectionReason() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.REJECTED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(loanApplicationService.getHistory(7L)).thenReturn(List.of(
                new StatusChangeResponse(ApplicationStatus.SUBMITTED, null, LocalDateTime.of(2026, 3, 15, 10, 30)),
                new StatusChangeResponse(ApplicationStatus.REJECTED, "Income too low", LocalDateTime.of(2026, 3, 16, 9, 0))));

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("16 Mar 2026, 09:00")))
                .andExpect(content().string(containsString("Income too low")))
                // a rejected application has no repayment schedule, and the service is not asked for one
                .andExpect(content().string(not(containsString("Repayment schedule"))));

        verify(loanApplicationService, never()).getSchedule(any());
    }

    @Test
    void applicationDetail_showsTheRepaymentSchedule_whenApplicationIsApproved() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.APPROVED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(loanApplicationService.getSchedule(7L)).thenReturn(new RepaymentScheduleResponse(7L, calculation(),
                List.of(new ScheduleEntryResponse(1, new BigDecimal("450.11"), new BigDecimal("60.42"),
                        new BigDecimal("389.69"), new BigDecimal("4610.31")))));

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Repayment schedule")))
                .andExpect(content().string(containsString("4,610.31")))
                // documents: a download link for everyone, but no delete button after a decision
                .andExpect(content().string(containsString("/api/documents/10/download")))
                .andExpect(content().string(not(containsString("/ui/documents/10/delete"))));
    }

    @Test
    void deleteDocument_returnsToTheApplicationPage() throws Exception {
        mockMvc.perform(post("/ui/documents/10/delete").header("Referer", "http://localhost/ui/applications/7"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "Document 10 was deleted"));

        verify(documentService).delete(10L);
    }

    @Test
    void newApplication_fillsTheFormWithTheNumbersFromTheCalculator() throws Exception {
        when(customerService.getAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer())));
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/applications/new")
                        .param("loanProductId", "1").param("amount", "5000").param("termMonths", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"5000\"")))
                .andExpect(content().string(containsString("value=\"12\"")));
    }

    // ---------- helpers ----------

    private PaymentCalculationResponse calculation() {
        return new PaymentCalculationResponse(new BigDecimal("5000"), new BigDecimal("14.50"), 12,
                new BigDecimal("450.11"), new BigDecimal("5401.36"), new BigDecimal("401.36"));
    }

    private CustomerResponse customer() {
        return new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", "555123456");
    }

    private LoanProductResponse product() {
        return new LoanProductResponse(1L, "Consumer Loan", new BigDecimal("14.50"),
                new BigDecimal("500.00"), new BigDecimal("20000.00"), 3, 48,
                List.of(new DocumentTypeResponse(1L, "ID Card"), new DocumentTypeResponse(2L, "Salary Certificate")));
    }

    private List<DocumentTypeResponse> documentTypes() {
        return List.of(new DocumentTypeResponse(1L, "ID Card"),
                new DocumentTypeResponse(2L, "Salary Certificate"),
                new DocumentTypeResponse(3L, "Bank Statement"));
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

    // the product form with every field filled; only maxAmount and the ticked documents differ between tests
    private MockHttpServletRequestBuilder productForm(MockHttpServletRequestBuilder request,
                                                      String maxAmount, String... tickedDocumentTypeIds) {
        request.param("name", "Consumer Loan")
                .param("interestRate", "14.5")
                .param("minAmount", "500")
                .param("maxAmount", maxAmount)
                .param("minTermMonths", "3")
                .param("maxTermMonths", "48")
                .param("_requiredDocumentTypeIds", "on"); // the hidden marker field of the checkbox group
        for (String id : tickedDocumentTypeIds) {
            request.param("requiredDocumentTypeIds", id); // one parameter per ticked checkbox
        }
        return request;
    }

    private LoanProductRequest productRequest(String maxAmount, Set<Long> documentTypeIds) {
        return new LoanProductRequest("Consumer Loan", new BigDecimal("14.5"), new BigDecimal("500"),
                new BigDecimal(maxAmount), 3, 48, documentTypeIds);
    }
}
