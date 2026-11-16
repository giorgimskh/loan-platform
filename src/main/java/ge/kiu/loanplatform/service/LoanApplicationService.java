package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.ProductTotalResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerRepository customerRepository;
    private final LoanProductRepository loanProductRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public LoanApplicationResponse create(LoanApplicationRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer with id " + request.customerId() + " not found"));
        LoanProduct product = loanProductRepository.findById(request.loanProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan product with id " + request.loanProductId() + " not found"));

        // Rule 1: amount must be within the product's range
        if (request.amount().compareTo(product.getMinAmount()) < 0
                || request.amount().compareTo(product.getMaxAmount()) > 0) {
            throw new BusinessRuleException("Amount must be between " + product.getMinAmount()
                    + " and " + product.getMaxAmount() + " for product " + product.getName());
        }

        // Rule 2: term must be within the product's range
        if (request.termMonths() < product.getMinTermMonths()
                || request.termMonths() > product.getMaxTermMonths()) {
            throw new BusinessRuleException("Term must be between " + product.getMinTermMonths()
                    + " and " + product.getMaxTermMonths() + " months for product " + product.getName());
        }

        LoanApplication application = new LoanApplication();
        application.setCustomer(customer);
        application.setLoanProduct(product);
        application.setAmount(request.amount());
        application.setTermMonths(request.termMonths());
        application.setStatus(ApplicationStatus.SUBMITTED); // Rule 3: new applications start as SUBMITTED

        LoanApplication saved = loanApplicationRepository.save(application);
        return LoanApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<LoanApplicationResponse> getAll(ApplicationStatus status, Pageable pageable) {
        Page<LoanApplication> page = (status == null)
                ? loanApplicationRepository.findAll(pageable)
                : loanApplicationRepository.findByStatus(status, pageable);
        return page.map(LoanApplicationResponse::from);
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse getById(Long id) {
        return LoanApplicationResponse.from(findApplication(id));
    }

    // All applications of one customer that have the given status (method-name query)
    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> getByCustomerAndStatus(Long customerId, ApplicationStatus status) {
        // without this check an unknown customer would get an empty list instead of a clear 404
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer with id " + customerId + " not found");
        }
        return loanApplicationRepository.findByCustomerIdAndStatus(customerId, status).stream()
                .map(LoanApplicationResponse::from)
                .toList();
    }

    // Applications of one product (by name) with an amount above the given value (@Query with JOIN)
    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> searchByProductAndAmount(String productName, BigDecimal amountAbove) {
        return loanApplicationRepository.findByProductNameAndAmountGreaterThan(productName, amountAbove).stream()
                .map(LoanApplicationResponse::from)
                .toList();
    }

    // Report: total approved amount per product (@Query with SUM + GROUP BY)
    @Transactional(readOnly = true)
    public List<ProductTotalResponse> getApprovedTotalsPerProduct() {
        return loanApplicationRepository.findTotalApprovedAmountPerProduct().stream()
                .map(ProductTotalResponse::from)
                .toList();
    }

    @Transactional
    public LoanApplicationResponse approve(Long id) {
        LoanApplication application = findApplication(id);
        requireSubmitted(application, "approved");

        application.setStatus(ApplicationStatus.APPROVED);
        // no save() needed: changes to a managed entity are written on commit
        return LoanApplicationResponse.from(application);
    }

    @Transactional
    public LoanApplicationResponse reject(Long id, RejectRequest request) {
        LoanApplication application = findApplication(id);
        requireSubmitted(application, "rejected");

        // Rule 5: non-blank reason is guaranteed by @NotBlank on RejectRequest
        application.setStatus(ApplicationStatus.REJECTED);
        application.setDecisionComment(request.decisionComment());
        return LoanApplicationResponse.from(application);
    }

    @Transactional
    public void delete(Long id) {
        LoanApplication application = findApplication(id);
        requireSubmitted(application, "cancelled");

        // remove the uploaded files from disk
        for (Document document : application.getDocuments()) {
            fileStorageService.delete(document.getStoredFilePath());
        }
        // the Document rows are deleted too (cascade = ALL, orphanRemoval = true on LoanApplication.documents)
        loanApplicationRepository.delete(application);
    }

    // ---------- helpers ----------

    private LoanApplication findApplication(Long id) {
        return loanApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application with id " + id + " not found"));
    }

    // Rule 4: only SUBMITTED applications can be approved, rejected or cancelled
    private void requireSubmitted(LoanApplication application, String action) {
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ConflictException("Application with id " + application.getId() + " is "
                    + application.getStatus() + " and cannot be " + action);
        }
    }
}
