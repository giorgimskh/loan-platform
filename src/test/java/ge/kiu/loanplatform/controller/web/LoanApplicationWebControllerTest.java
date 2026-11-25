package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.application.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.application.LoanApplicationUpdateRequest;
import ge.kiu.loanplatform.dto.application.RejectRequest;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.calculation.RepaymentScheduleResponse;
import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.file.PersonalDataItem;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.customer.CustomerService;
import ge.kiu.loanplatform.service.document.DocumentTypeService;
import ge.kiu.loanplatform.service.product.LoanProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static ge.kiu.loanplatform.controller.web.WebTestData.application;
import static ge.kiu.loanplatform.controller.web.WebTestData.calculation;
import static ge.kiu.loanplatform.controller.web.WebTestData.customer;
import static ge.kiu.loanplatform.controller.web.WebTestData.decreasingCalculation;
import static ge.kiu.loanplatform.controller.web.WebTestData.documentTypes;
import static ge.kiu.loanplatform.controller.web.WebTestData.pageOf;
import static ge.kiu.loanplatform.controller.web.WebTestData.product;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Web layer test for LoanApplicationWebController. The services are mocks.
// The templates are really rendered, so these tests also catch mistakes in the HTML files.
@WebMvcTest(LoanApplicationWebController.class)
class LoanApplicationWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private LoanProductService loanProductService;
    @MockitoBean
    private LoanApplicationService loanApplicationService;
    @MockitoBean
    private DocumentTypeService documentTypeService;

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
                .andExpect(content().string(not(containsString("Cancel this application"))))
                // memento pattern: a decided application offers undo
                .andExpect(content().string(containsString("/ui/applications/7/undo-decision")));
    }

    @Test
    void applicationDetail_offersNoUndo_whenApplicationIsSubmitted() throws Exception {
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.SUBMITTED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/ui/applications/7/undo-decision"))));
    }

    @Test
    void undoDecision_returnsToTheApplicationWithTheRestoredStatus() throws Exception { // memento pattern
        when(loanApplicationService.undoLastDecision(7L)).thenReturn(application(ApplicationStatus.SUBMITTED));

        mockMvc.perform(post("/ui/applications/7/undo-decision")
                        .header("Referer", "http://localhost:8080/ui/applications/7"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "The last decision was undone: application 7 is submitted again"));
    }

    @Test
    void undoDecision_showsTheReason_whenTheServiceRefuses() throws Exception {
        when(loanApplicationService.undoLastDecision(7L)).thenThrow(
                new ConflictException("Application with id 7 is SUBMITTED and has no decision to undo"));

        mockMvc.perform(post("/ui/applications/7/undo-decision")
                        .header("Referer", "http://localhost:8080/ui/applications/7"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("error", "Application with id 7 is SUBMITTED and has no decision to undo"));
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
    void calculator_showsAnEmptyFormWithoutErrors_whenOpenedByALink() throws Exception {
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/calculator").param("loanProductId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("calculator"))
                // the product from the link is already chosen, and nothing is marked as wrong
                .andExpect(content().string(containsString("selected=\"selected\">Consumer Loan at 14.50% per year, equal monthly payments</option>")))
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
                .andExpect(content().string(not(containsString("First monthly payment")))) // equal payments: one amount
                .andExpect(content().string(containsString(
                        "/ui/applications/new?loanProductId=1&amp;amount=5000&amp;termMonths=12")));
    }

    @Test
    void calculator_showsTheFirstAndLastPayment_whenPaymentsDecrease() throws Exception { // strategy pattern
        when(loanProductService.getAll()).thenReturn(List.of(product()));
        when(loanApplicationService.calculate(1L, new BigDecimal("5000"), 12)).thenReturn(decreasingCalculation());

        mockMvc.perform(get("/ui/calculator")
                        .param("loanProductId", "1").param("amount", "5000").param("termMonths", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("First monthly payment")))
                .andExpect(content().string(containsString("477.09")))
                .andExpect(content().string(containsString("Falls every month to 421.66 in the last month")));
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
                .andExpect(content().string(containsString("/api/applications/7/file"))) // visitor pattern download
                .andExpect(content().string(containsString("Equal monthly payments at 14.50% per year.")))
                .andExpect(content().string(containsString("4,610.31")))
                // documents: a download link for everyone, but no delete button after a decision
                .andExpect(content().string(containsString("/api/documents/10/download")))
                .andExpect(content().string(not(containsString("/ui/documents/10/delete"))));
    }

    @Test
    void applicationDetail_showsThePersonalDataOfTheFile() throws Exception { // visitor pattern
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.SUBMITTED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(loanApplicationService.getPersonalData(7L)).thenReturn(List.of(
                new PersonalDataItem("Customer", "Personal number", "01234567890"),
                new PersonalDataItem("Document 10 (ID Card)", "File name", "ana-passport.pdf")));

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Personal data")))
                .andExpect(content().string(containsString("Customer, personal number")))
                .andExpect(content().string(containsString("01234567890")))
                .andExpect(content().string(containsString("Document 10 (ID Card), file name")))
                .andExpect(content().string(containsString("ana-passport.pdf")));
    }

    @Test
    void applicationDetail_showsTheFirstAndLastPayment_whenTheScheduleDecreases() throws Exception { // strategy pattern
        when(loanApplicationService.getById(7L)).thenReturn(application(ApplicationStatus.APPROVED));
        when(loanProductService.getById(1L)).thenReturn(product());
        when(loanApplicationService.getSchedule(7L)).thenReturn(new RepaymentScheduleResponse(7L, decreasingCalculation(),
                List.of(new ScheduleEntryResponse(1, new BigDecimal("477.09"), new BigDecimal("60.42"),
                        new BigDecimal("416.67"), new BigDecimal("4583.33")))));

        mockMvc.perform(get("/ui/applications/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Decreasing monthly payments at 14.50% per year.")))
                .andExpect(content().string(containsString("First to last payment")))
                .andExpect(content().string(containsString("477.09 to 421.66")));
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
}
