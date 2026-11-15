package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.RejectRequest;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.entity.Customer;
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

@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerRepository customerRepository;
    private final LoanProductRepository loanProductRepository;

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

        // its Document rows are deleted too (cascade = ALL, orphanRemoval = true on LoanApplication.documents)
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
