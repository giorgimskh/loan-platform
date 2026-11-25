package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.dto.application.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.LoanApplicationUpdateRequest;
import ge.kiu.loanplatform.dto.application.RejectRequest;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.calculation.RepaymentScheduleResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.report.ProductTotalResponse;
import ge.kiu.loanplatform.dto.report.StatisticsResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.entity.application.StatusChange;
import ge.kiu.loanplatform.entity.customer.Customer;
import ge.kiu.loanplatform.entity.document.Document;
import ge.kiu.loanplatform.entity.document.DocumentType;
import ge.kiu.loanplatform.entity.product.LoanProduct;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.application.LoanApplicationRepository;
import ge.kiu.loanplatform.repository.application.ProductTotalAmount;
import ge.kiu.loanplatform.repository.customer.CustomerRepository;
import ge.kiu.loanplatform.repository.product.LoanProductRepository;
import ge.kiu.loanplatform.service.application.event.ApplicationStatusChangedEvent;
import ge.kiu.loanplatform.service.application.rule.RequiredDocumentsRule;
import ge.kiu.loanplatform.service.calculation.LoanCalculatorService;
import ge.kiu.loanplatform.service.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test for the business rules. Repositories and file storage are Mockito mocks.
@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private LoanProductRepository loanProductRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    // @Spy = the real object, not a fake: the calculator is pure maths, so the tests use it as it is
    @Spy
    private LoanCalculatorService loanCalculatorService = new LoanCalculatorService();

    // Built by hand in setUp: @InjectMocks cannot fill the List<ApprovalRule>.
    // The real RequiredDocumentsRule is used (plain logic, no database), so rule 7 is really tested.
    private LoanApplicationService loanApplicationService;

    private Customer customer;
    private LoanProduct product; // amount 500..20000, term 3..48 months

    @BeforeEach
    void setUp() {
        loanApplicationService = new LoanApplicationService(loanApplicationRepository, customerRepository,
                loanProductRepository, fileStorageService, loanCalculatorService, eventPublisher,
                List.of(new RequiredDocumentsRule()));

        customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Ana Beridze");
        customer.setEmail("ana@example.com");

        product = new LoanProduct();
        product.setId(1L);
        product.setName("Consumer Loan");
        product.setInterestRate(new BigDecimal("14.50"));
        product.setMinAmount(new BigDecimal("500.00"));
        product.setMaxAmount(new BigDecimal("20000.00"));
        product.setMinTermMonths(3);
        product.setMaxTermMonths(48);
    }

    // ---------- create ----------

    @Test
    void create_savesApplicationAsSubmitted_whenAmountAndTermAreInRange() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));
        when(loanApplicationRepository.save(any(LoanApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LoanApplicationResponse response = loanApplicationService.create(
                new LoanApplicationRequest(1L, 1L, new BigDecimal("5000"), 12));

        assertThat(response.status()).isEqualTo(ApplicationStatus.SUBMITTED); // rule 3
        assertThat(response.amount()).isEqualByComparingTo("5000");
        assertThat(response.customerFullName()).isEqualTo("Ana Beridze");
        assertThat(response.loanProductName()).isEqualTo("Consumer Loan");
    }

    @Test
    void create_acceptsAmountExactlyOnTheLimits() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));
        when(loanApplicationRepository.save(any(LoanApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // "500" and "500.00" are the same value for compareTo, so the minimum itself is allowed
        loanApplicationService.create(new LoanApplicationRequest(1L, 1L, new BigDecimal("500"), 3));
        loanApplicationService.create(new LoanApplicationRequest(1L, 1L, new BigDecimal("20000"), 48));
    }

    @Test
    void create_throwsBusinessRule_whenAmountIsBelowMinimum() { // rule 1
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> loanApplicationService.create(
                new LoanApplicationRequest(1L, 1L, new BigDecimal("499.99"), 12)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Amount");
        verify(loanApplicationRepository, never()).save(any());
    }

    @Test
    void create_throwsBusinessRule_whenAmountIsAboveMaximum() { // rule 1
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> loanApplicationService.create(
                new LoanApplicationRequest(1L, 1L, new BigDecimal("20000.01"), 12)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_throwsBusinessRule_whenTermIsOutOfRange() { // rule 2
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> loanApplicationService.create(
                new LoanApplicationRequest(1L, 1L, new BigDecimal("5000"), 49)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Term");
    }

    @Test
    void create_throwsNotFound_whenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanApplicationService.create(
                new LoanApplicationRequest(99L, 1L, new BigDecimal("5000"), 12)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- approve / reject / delete (rule 4) ----------

    @Test
    void approve_setsStatusApproved_whenApplicationIsSubmitted() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.SUBMITTED)));

        LoanApplicationResponse response = loanApplicationService.approve(1L);

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void approve_throwsConflict_whenApplicationIsAlreadyApproved() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.APPROVED)));

        assertThatThrownBy(() -> loanApplicationService.approve(1L))
                .isInstanceOf(ConflictException.class);
        verify(eventPublisher, never()).publishEvent(any()); // a refused decision is not announced
    }

    @Test
    void reject_setsStatusRejectedAndSavesReason() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.SUBMITTED)));

        LoanApplicationResponse response = loanApplicationService.reject(1L, new RejectRequest("Income too low"));

        assertThat(response.status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(response.decisionComment()).isEqualTo("Income too low");
    }

    @Test
    void approve_changesTheDecisionAndRemovesTheReason_whenApplicationWasRejected() { // rule 4
        LoanApplication rejected = application(ApplicationStatus.REJECTED);
        rejected.setDecisionComment("Income too low");
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(rejected));

        LoanApplicationResponse response = loanApplicationService.approve(1L);

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(response.decisionComment()).isNull();
    }

    @Test
    void reject_changesTheDecision_whenApplicationWasApproved() { // rule 4
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.APPROVED)));

        LoanApplicationResponse response = loanApplicationService.reject(1L, new RejectRequest("Approved by mistake"));

        assertThat(response.status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(response.decisionComment()).isEqualTo("Approved by mistake");
    }

    @Test
    void reject_throwsConflict_whenApplicationIsAlreadyRejected() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.REJECTED)));

        assertThatThrownBy(() -> loanApplicationService.reject(1L, new RejectRequest("Again")))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- observer pattern: status change events ----------

    @Test
    void create_publishesStatusChangedEvent_withNoPreviousStatus() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));
        when(loanApplicationRepository.save(any(LoanApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        loanApplicationService.create(new LoanApplicationRequest(1L, 1L, new BigDecimal("5000"), 12));

        ApplicationStatusChangedEvent event = capturePublishedEvent();
        assertThat(event.previousStatus()).isNull();
        assertThat(event.newStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(event.customerEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void approve_publishesStatusChangedEvent() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.SUBMITTED)));

        loanApplicationService.approve(1L);

        ApplicationStatusChangedEvent event = capturePublishedEvent();
        assertThat(event.applicationId()).isEqualTo(1L);
        assertThat(event.previousStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(event.newStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void reject_publishesStatusChangedEventWithTheReason() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.APPROVED)));

        loanApplicationService.reject(1L, new RejectRequest("Income too low"));

        ApplicationStatusChangedEvent event = capturePublishedEvent();
        assertThat(event.previousStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(event.newStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(event.decisionComment()).isEqualTo("Income too low");
    }

    // ---------- memento pattern: undo the last decision ----------

    @Test
    void undoLastDecision_goesBackToSubmitted_andKeepsTheHistory() {
        LoanApplication application = withHistory(application(ApplicationStatus.APPROVED),
                ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        LoanApplicationResponse response = loanApplicationService.undoLastDecision(1L);

        assertThat(response.status()).isEqualTo(ApplicationStatus.SUBMITTED);
        // nothing is deleted: SUBMITTED -> APPROVED -> SUBMITTED
        assertThat(application.getStatusHistory()).extracting(StatusChange::getStatus).containsExactly(
                ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED, ApplicationStatus.SUBMITTED);
        ApplicationStatusChangedEvent event = capturePublishedEvent();
        assertThat(event.previousStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(event.newStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    void undoLastDecision_bringsBackTheEarlierRejectionReason() {
        LoanApplication application = withHistory(application(ApplicationStatus.APPROVED),
                ApplicationStatus.SUBMITTED, ApplicationStatus.REJECTED, ApplicationStatus.APPROVED);
        application.getStatusHistory().get(1).setDecisionComment("Income too low");
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        LoanApplicationResponse response = loanApplicationService.undoLastDecision(1L);

        assertThat(response.status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(response.decisionComment()).isEqualTo("Income too low");
    }

    @Test
    void undoLastDecision_throwsConflict_whenApplicationIsSubmitted() {
        LoanApplication application = withHistory(application(ApplicationStatus.SUBMITTED), ApplicationStatus.SUBMITTED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> loanApplicationService.undoLastDecision(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no decision to undo");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void undoLastDecision_throwsConflict_whenGoingBackToApprovedWithMissingDocuments() { // rule 7
        product.setRequiredDocuments(Set.of(documentType(1L, "ID Card")));
        LoanApplication application = withHistory(application(ApplicationStatus.REJECTED),
                ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> loanApplicationService.undoLastDecision(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ID Card");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void undoLastDecision_throwsConflict_whenThereIsNoEarlierState() {
        LoanApplication application = withHistory(application(ApplicationStatus.APPROVED), ApplicationStatus.APPROVED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> loanApplicationService.undoLastDecision(1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void undoLastDecision_throwsNotFound_whenApplicationDoesNotExist() {
        when(loanApplicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanApplicationService.undoLastDecision(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesApplicationAndItsFiles_whenSubmitted() {
        LoanApplication application = application(ApplicationStatus.SUBMITTED);
        Document document = new Document();
        document.setStoredFilePath("uploads/abc.pdf");
        application.getDocuments().add(document);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        loanApplicationService.delete(1L);

        verify(fileStorageService).delete("uploads/abc.pdf");
        verify(loanApplicationRepository).delete(application);
    }

    @Test
    void delete_throwsConflict_whenApplicationIsApproved() {
        LoanApplication application = application(ApplicationStatus.APPROVED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> loanApplicationService.delete(1L))
                .isInstanceOf(ConflictException.class);
        verify(loanApplicationRepository, never()).delete(any());
    }

    @Test
    void getById_throwsNotFound_whenApplicationDoesNotExist() {
        when(loanApplicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanApplicationService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void update_changesAmountAndTerm_whenApplicationIsSubmitted() {
        LoanApplication application = application(ApplicationStatus.SUBMITTED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));

        LoanApplicationResponse response = loanApplicationService.update(1L,
                new LoanApplicationUpdateRequest(1L, new BigDecimal("6000"), 24));

        assertThat(response.amount()).isEqualByComparingTo("6000");
        assertThat(response.termMonths()).isEqualTo(24);
        assertThat(response.status()).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    void update_throwsConflict_whenApplicationIsAlreadyApproved() { // rule 4
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.APPROVED)));

        assertThatThrownBy(() -> loanApplicationService.update(1L,
                new LoanApplicationUpdateRequest(1L, new BigDecimal("6000"), 24)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be modified");
    }

    @Test
    void update_throwsBusinessRule_andKeepsOldValues_whenAmountIsOutsideProductRange() { // rule 1
        LoanApplication application = application(ApplicationStatus.SUBMITTED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> loanApplicationService.update(1L,
                new LoanApplicationUpdateRequest(1L, new BigDecimal("999999"), 24)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(application.getAmount()).isEqualByComparingTo("5000"); // nothing was changed
    }

    @Test
    void update_throwsNotFound_whenProductDoesNotExist() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.SUBMITTED)));
        when(loanProductRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanApplicationService.update(1L,
                new LoanApplicationUpdateRequest(99L, new BigDecimal("6000"), 24)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- queries ----------

    @Test
    void getByCustomerAndStatus_returnsApplicationsOfTheCustomer() {
        when(customerRepository.existsById(1L)).thenReturn(true);
        when(loanApplicationRepository.findByCustomerIdAndStatus(1L, ApplicationStatus.SUBMITTED))
                .thenReturn(List.of(application(ApplicationStatus.SUBMITTED)));

        List<LoanApplicationResponse> result =
                loanApplicationService.getByCustomerAndStatus(1L, ApplicationStatus.SUBMITTED);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().customerFullName()).isEqualTo("Ana Beridze");
    }

    @Test
    void getByCustomerAndStatus_throwsNotFound_whenCustomerDoesNotExist() {
        when(customerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> loanApplicationService.getByCustomerAndStatus(99L, ApplicationStatus.SUBMITTED))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(loanApplicationRepository, never()).findByCustomerIdAndStatus(any(), any());
    }

    @Test
    void searchByProductAndAmount_returnsMatchingApplications() {
        when(loanApplicationRepository.findByProductNameAndAmountGreaterThan("Consumer Loan", new BigDecimal("1000")))
                .thenReturn(List.of(application(ApplicationStatus.APPROVED)));

        List<LoanApplicationResponse> result =
                loanApplicationService.searchByProductAndAmount("Consumer Loan", new BigDecimal("1000"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().loanProductName()).isEqualTo("Consumer Loan");
    }

    @Test
    void getApprovedTotalsPerProduct_mapsQueryRowsToResponses() {
        // ProductTotalAmount is an interface (Spring Data fills it), so in a unit test we mock one row
        ProductTotalAmount row = mock(ProductTotalAmount.class);
        when(row.getProductName()).thenReturn("Consumer Loan");
        when(row.getTotalAmount()).thenReturn(new BigDecimal("12000.00"));
        when(loanApplicationRepository.findTotalApprovedAmountPerProduct()).thenReturn(List.of(row));

        List<ProductTotalResponse> result = loanApplicationService.getApprovedTotalsPerProduct();

        assertThat(result).containsExactly(new ProductTotalResponse("Consumer Loan", new BigDecimal("12000.00")));
    }

    // ---------- required documents (rule 7) ----------

    @Test
    void approve_throwsConflict_whenARequiredDocumentIsMissing() { // rule 7
        product.setRequiredDocuments(Set.of(documentType(1L, "ID Card")));
        LoanApplication application = application(ApplicationStatus.SUBMITTED); // nothing uploaded
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> loanApplicationService.approve(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ID Card");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    void approve_succeeds_whenEveryRequiredDocumentIsUploaded() { // rule 7
        DocumentType idCard = documentType(1L, "ID Card");
        product.setRequiredDocuments(Set.of(idCard));
        LoanApplication application = application(ApplicationStatus.SUBMITTED);
        application.getDocuments().add(document(idCard));
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        LoanApplicationResponse response = loanApplicationService.approve(1L);

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void getMissingDocuments_returnsOnlyTheRequiredTypesThatAreNotUploaded() {
        DocumentType idCard = documentType(1L, "ID Card");
        DocumentType passport = documentType(2L, "Passport");
        product.setRequiredDocuments(Set.of(idCard, passport));
        LoanApplication application = application(ApplicationStatus.SUBMITTED);
        application.getDocuments().add(document(idCard));
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThat(loanApplicationService.getMissingDocuments(1L))
                .containsExactly(new DocumentTypeResponse(2L, "Passport"));
    }

    // ---------- history ----------

    @Test
    void history_getsOneLineForEveryStatusChange() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));
        // keep the application the service creates, so the next calls can find it
        LoanApplication[] saved = new LoanApplication[1];
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            saved[0].setId(1L);
            return saved[0];
        });
        when(loanApplicationRepository.findById(1L)).thenAnswer(invocation -> Optional.of(saved[0]));

        loanApplicationService.create(new LoanApplicationRequest(1L, 1L, new BigDecimal("5000"), 12));
        loanApplicationService.reject(1L, new RejectRequest("Income too low"));
        loanApplicationService.approve(1L);

        List<StatusChangeResponse> history = loanApplicationService.getHistory(1L);
        assertThat(history).extracting(StatusChangeResponse::status).containsExactly(
                ApplicationStatus.SUBMITTED, ApplicationStatus.REJECTED, ApplicationStatus.APPROVED);
        assertThat(history.get(1).decisionComment()).isEqualTo("Income too low");
        assertThat(history.get(2).decisionComment()).isNull();
    }

    // ---------- calculator and repayment schedule ----------

    @Test
    void calculate_returnsThePaymentForTheProductsInterestRate() {
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));

        PaymentCalculationResponse result = loanApplicationService.calculate(1L, new BigDecimal("5000"), 12);

        assertThat(result.interestRate()).isEqualByComparingTo("14.50");
        assertThat(result.monthlyPayment()).isEqualByComparingTo("450.11");
        assertThat(result.totalInterest()).isEqualByComparingTo("401.36");
    }

    @Test
    void calculate_throwsBusinessRule_whenAmountIsOutsideTheProductRange() { // rule 1
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> loanApplicationService.calculate(1L, new BigDecimal("99"), 12))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void calculate_throwsNotFound_whenProductDoesNotExist() {
        when(loanProductRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanApplicationService.calculate(99L, new BigDecimal("5000"), 12))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSchedule_returnsOneLinePerMonth_whenApplicationIsApproved() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.APPROVED)));

        RepaymentScheduleResponse schedule = loanApplicationService.getSchedule(1L);

        assertThat(schedule.payments()).hasSize(12);
        assertThat(schedule.summary().monthlyPayment()).isEqualByComparingTo("450.11");
        assertThat(schedule.payments().getLast().remainingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void getSchedule_throwsConflict_whenApplicationIsNotApproved() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.SUBMITTED)));

        assertThatThrownBy(() -> loanApplicationService.getSchedule(1L))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- statistics ----------

    @Test
    void getStatistics_countsPerStatusAndCalculatesRateAndAverage() {
        when(loanApplicationRepository.countByStatus(ApplicationStatus.SUBMITTED)).thenReturn(1L);
        when(loanApplicationRepository.countByStatus(ApplicationStatus.APPROVED)).thenReturn(2L);
        when(loanApplicationRepository.countByStatus(ApplicationStatus.REJECTED)).thenReturn(1L);
        when(loanApplicationRepository.sumAllAmounts()).thenReturn(new BigDecimal("18000.00"));
        when(loanApplicationRepository.sumAmountsByStatus(ApplicationStatus.APPROVED)).thenReturn(new BigDecimal("12000.00"));

        StatisticsResponse statistics = loanApplicationService.getStatistics();

        assertThat(statistics.totalApplications()).isEqualTo(4);
        assertThat(statistics.approvalRate()).isEqualByComparingTo("66.7");     // 2 of the 3 decided
        assertThat(statistics.averageAmount()).isEqualByComparingTo("4500.00"); // 18000 / 4
        assertThat(statistics.totalApprovedAmount()).isEqualByComparingTo("12000.00");
    }

    @Test
    void getStatistics_returnsZeros_whenThereAreNoApplications() { // must not divide by zero
        when(loanApplicationRepository.sumAmountsByStatus(ApplicationStatus.APPROVED)).thenReturn(BigDecimal.ZERO);

        StatisticsResponse statistics = loanApplicationService.getStatistics();

        assertThat(statistics.totalApplications()).isZero();
        assertThat(statistics.approvalRate()).isEqualByComparingTo("0");
        assertThat(statistics.averageAmount()).isEqualByComparingTo("0");
    }

    // ---------- helper ----------

    private DocumentType documentType(Long id, String name) {
        DocumentType documentType = new DocumentType();
        documentType.setId(id);
        documentType.setName(name);
        return documentType;
    }

    private Document document(DocumentType documentType) {
        Document document = new Document();
        document.setOriginalFileName("file.pdf");
        document.setDocumentType(documentType);
        return document;
    }

    // ArgumentCaptor catches the object the service passed to the mock, so the test can look inside it
    private ApplicationStatusChangedEvent capturePublishedEvent() {
        ArgumentCaptor<ApplicationStatusChangedEvent> captor = ArgumentCaptor.forClass(ApplicationStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    // adds one history line per status, oldest first (like the real history)
    private LoanApplication withHistory(LoanApplication application, ApplicationStatus... statuses) {
        for (ApplicationStatus status : statuses) {
            StatusChange change = new StatusChange();
            change.setLoanApplication(application);
            change.setStatus(status);
            application.getStatusHistory().add(change);
        }
        return application;
    }

    private LoanApplication application(ApplicationStatus status) {
        LoanApplication application = new LoanApplication();
        application.setId(1L);
        application.setAmount(new BigDecimal("5000"));
        application.setTermMonths(12);
        application.setStatus(status);
        application.setCustomer(customer);
        application.setLoanProduct(product);
        return application;
    }
}
