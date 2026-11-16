package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.RejectRequest;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.entity.Customer;
import ge.kiu.loanplatform.entity.Document;
import ge.kiu.loanplatform.entity.LoanApplication;
import ge.kiu.loanplatform.entity.LoanProduct;
import ge.kiu.loanplatform.exception.BusinessRuleException;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.CustomerRepository;
import ge.kiu.loanplatform.repository.LoanApplicationRepository;
import ge.kiu.loanplatform.repository.LoanProductRepository;
import ge.kiu.loanplatform.repository.ProductTotalAmount;
import ge.kiu.loanplatform.dto.ProductTotalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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

    @InjectMocks
    private LoanApplicationService loanApplicationService;

    private Customer customer;
    private LoanProduct product; // amount 500..20000, term 3..48 months

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Ana Beridze");

        product = new LoanProduct();
        product.setId(1L);
        product.setName("Consumer Loan");
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
    }

    @Test
    void reject_setsStatusRejectedAndSavesReason() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.SUBMITTED)));

        LoanApplicationResponse response = loanApplicationService.reject(1L, new RejectRequest("Income too low"));

        assertThat(response.status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(response.decisionComment()).isEqualTo("Income too low");
    }

    @Test
    void reject_throwsConflict_whenApplicationIsNotSubmitted() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(application(ApplicationStatus.REJECTED)));

        assertThatThrownBy(() -> loanApplicationService.reject(1L, new RejectRequest("Again")))
                .isInstanceOf(ConflictException.class);
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

    // ---------- helper ----------

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
