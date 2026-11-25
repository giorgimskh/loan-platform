package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.dto.application.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.LoanApplicationUpdateRequest;
import ge.kiu.loanplatform.dto.application.RejectRequest;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.calculation.RepaymentScheduleResponse;
import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.report.ProductTotalResponse;
import ge.kiu.loanplatform.dto.report.StatisticsResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStateMemento;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.entity.application.StatusChange;
import ge.kiu.loanplatform.entity.customer.Customer;
import ge.kiu.loanplatform.entity.document.Document;
import ge.kiu.loanplatform.entity.product.LoanProduct;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.application.LoanApplicationRepository;
import ge.kiu.loanplatform.repository.customer.CustomerRepository;
import ge.kiu.loanplatform.repository.product.LoanProductRepository;
import ge.kiu.loanplatform.service.document.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerRepository customerRepository;
    private final LoanProductRepository loanProductRepository;
    private final FileStorageService fileStorageService;
    private final LoanCalculatorService loanCalculatorService;
    // Observer pattern: the service is the subject. It only announces status changes;
    // Spring hands each event to every @EventListener / @TransactionalEventListener that accepts it.
    private final ApplicationEventPublisher eventPublisher;
    // Chain of responsibility: every check an application must pass before it can be approved.
    // Spring puts every bean that implements ApprovalRule into this list, so a new rule is
    // just a new @Component class; this service does not change.
    private final List<ApprovalRule> approvalRules;

    @Transactional
    public LoanApplicationResponse create(LoanApplicationRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer with id " + request.customerId() + " not found"));
        LoanProduct product = loanProductRepository.findById(request.loanProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan product with id " + request.loanProductId() + " not found"));

        validateAmountAndTerm(product, request.amount(), request.termMonths());

        LoanApplication application = new LoanApplication();
        application.setCustomer(customer);
        application.setLoanProduct(product);
        application.setAmount(request.amount());
        application.setTermMonths(request.termMonths());
        application.setStatus(ApplicationStatus.SUBMITTED); // Rule 3: new applications start as SUBMITTED
        recordStatusChange(application); // the first line of its history

        LoanApplication saved = loanApplicationRepository.save(application);
        // published after save(), because only then does the application have an id
        publishStatusChanged(saved, null);
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

    // Numbers about all applications: how many per status, approval rate, average and approved amount
    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics() {
        long submitted = loanApplicationRepository.countByStatus(ApplicationStatus.SUBMITTED);
        long approved = loanApplicationRepository.countByStatus(ApplicationStatus.APPROVED);
        long rejected = loanApplicationRepository.countByStatus(ApplicationStatus.REJECTED);
        long total = submitted + approved + rejected;
        long decided = approved + rejected;

        // "if" guards: dividing by zero would throw an ArithmeticException when there is nothing to count yet
        BigDecimal approvalRate = BigDecimal.ZERO;
        if (decided > 0) {
            approvalRate = BigDecimal.valueOf(approved * 100)
                    .divide(BigDecimal.valueOf(decided), 1, RoundingMode.HALF_UP);
        }
        BigDecimal averageAmount = BigDecimal.ZERO;
        if (total > 0) {
            averageAmount = loanApplicationRepository.sumAllAmounts()
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        }
        BigDecimal totalApprovedAmount = loanApplicationRepository.sumAmountsByStatus(ApplicationStatus.APPROVED);

        return new StatisticsResponse(total, submitted, approved, rejected,
                approvalRate, averageAmount, totalApprovedAmount);
    }

    // Every status the application has had, oldest first
    @Transactional(readOnly = true)
    public List<StatusChangeResponse> getHistory(Long id) {
        return findApplication(id).getStatusHistory().stream()
                .map(StatusChangeResponse::from)
                .toList();
    }

    // Rule 7: the documents the product requires that are not uploaded yet (empty list = nothing is missing)
    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> getMissingDocuments(Long id) {
        return findApplication(id).findMissingDocumentTypes().stream()
                .map(DocumentTypeResponse::from)
                .toList();
    }

    // Payment calculator: what would a loan of this product cost? Nothing is saved.
    @Transactional(readOnly = true)
    public PaymentCalculationResponse calculate(Long loanProductId, BigDecimal amount, Integer termMonths) {
        LoanProduct product = loanProductRepository.findById(loanProductId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product with id " + loanProductId + " not found"));
        // the same rules 1 and 2 as for a real application: no offer for a loan the product does not allow
        validateAmountAndTerm(product, amount, termMonths);

        List<ScheduleEntryResponse> schedule =
                loanCalculatorService.buildSchedule(amount, product.getInterestRate(), termMonths);
        return loanCalculatorService.summarize(amount, product.getInterestRate(), termMonths, schedule);
    }

    // The month-by-month repayment plan. Only an APPROVED application becomes a loan, so only it has a plan.
    @Transactional(readOnly = true)
    public RepaymentScheduleResponse getSchedule(Long id) {
        LoanApplication application = findApplication(id);
        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new ConflictException("Application with id " + id + " is " + application.getStatus()
                    + "; only an APPROVED application has a repayment schedule");
        }

        BigDecimal interestRate = application.getLoanProduct().getInterestRate();
        List<ScheduleEntryResponse> schedule = loanCalculatorService.buildSchedule(
                application.getAmount(), interestRate, application.getTermMonths());
        PaymentCalculationResponse summary = loanCalculatorService.summarize(
                application.getAmount(), interestRate, application.getTermMonths(), schedule);
        return new RepaymentScheduleResponse(id, summary, schedule);
    }

    // Change the product, amount or term of an application that is still waiting for a decision
    @Transactional
    public LoanApplicationResponse update(Long id, LoanApplicationUpdateRequest request) {
        LoanApplication application = findApplication(id);
        requireSubmitted(application, "modified");

        LoanProduct product = loanProductRepository.findById(request.loanProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan product with id " + request.loanProductId() + " not found"));
        // the same rules 1 and 2 as for a new application
        validateAmountAndTerm(product, request.amount(), request.termMonths());

        application.setLoanProduct(product);
        application.setAmount(request.amount());
        application.setTermMonths(request.termMonths());
        // no save() needed: changes to a managed entity are written on commit
        return LoanApplicationResponse.from(application);
    }

    @Transactional
    public LoanApplicationResponse approve(Long id) {
        LoanApplication application = findApplication(id);
        // Command pattern: the command carries what it needs, here the problems the approval rules found
        return executeDecision(application, new ApproveCommand(findApprovalProblems(application)));
    }

    @Transactional
    public LoanApplicationResponse reject(Long id, RejectRequest request) {
        LoanApplication application = findApplication(id);
        // Command pattern: the rejection reason travels inside the command
        return executeDecision(application, new RejectCommand(request.decisionComment()));
    }

    // Memento pattern: go back to the state the application had before its last decision.
    // History is never deleted: the undo is added as a new line, so the full story stays visible.
    @Transactional
    public LoanApplicationResponse undoLastDecision(Long id) {
        LoanApplication application = findApplication(id);
        if (application.getStatus() == ApplicationStatus.SUBMITTED) {
            throw new ConflictException("Application with id " + id + " is SUBMITTED and has no decision to undo");
        }
        // the history is the caretaker: the last line is the current state,
        // the line before it is the snapshot saved before the last decision
        List<StatusChange> history = application.getStatusHistory();
        if (history.size() < 2) {
            throw new ConflictException("Application with id " + id + " has no earlier state to go back to");
        }
        ApplicationStateMemento before = history.get(history.size() - 2).toMemento();

        // The approval rules still apply: undo must not approve an application that could not be approved
        if (before.status() == ApplicationStatus.APPROVED) {
            List<String> problems = findApprovalProblems(application);
            if (!problems.isEmpty()) {
                throw new ConflictException("Application with id " + id
                        + " cannot go back to APPROVED, " + String.join("; ", problems));
            }
        }

        ApplicationStatus previousStatus = application.getStatus();
        application.restore(before);
        recordStatusChange(application);
        publishStatusChanged(application, previousStatus);
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

    // Rules 1 and 2, used when an application is created and when it is modified
    private void validateAmountAndTerm(LoanProduct product, BigDecimal amount, Integer termMonths) {
        // Rule 1: amount must be within the product's range
        if (amount.compareTo(product.getMinAmount()) < 0 || amount.compareTo(product.getMaxAmount()) > 0) {
            throw new BusinessRuleException("Amount must be between " + product.getMinAmount()
                    + " and " + product.getMaxAmount() + " for product " + product.getName());
        }
        // Rule 2: term must be within the product's range
        if (termMonths < product.getMinTermMonths() || termMonths > product.getMaxTermMonths()) {
            throw new BusinessRuleException("Term must be between " + product.getMinTermMonths()
                    + " and " + product.getMaxTermMonths() + " months for product " + product.getName());
        }
    }

    // Command pattern: the steps every decision shares, written once.
    // Only command.execute() is different for approve and reject.
    private LoanApplicationResponse executeDecision(LoanApplication application, DecisionCommand command) {
        // Rule 4: a decision can be changed (APPROVED <-> REJECTED), but not repeated.
        // The action word is the status in lower case: APPROVED -> "approved", REJECTED -> "rejected".
        requireNotAlready(application, command.targetStatus(), command.targetStatus().name().toLowerCase());

        ApplicationStatus previousStatus = application.getStatus();
        command.execute(application);
        recordStatusChange(application);
        publishStatusChanged(application, previousStatus);
        // no save() needed: changes to a managed entity are written on commit
        return LoanApplicationResponse.from(application);
    }

    // Adds one line to the history: the status (and rejection reason) the application has right now.
    // No repository call is needed: the list has cascade = ALL, so the new line is saved with the application.
    // Memento pattern: the line is made from the application's own snapshot (createMemento).
    private void recordStatusChange(LoanApplication application) {
        ApplicationStateMemento snapshot = application.createMemento();
        StatusChange change = new StatusChange();
        change.setLoanApplication(application);
        change.setStatus(snapshot.status());
        change.setDecisionComment(snapshot.decisionComment());
        application.getStatusHistory().add(change);
    }

    // Observer pattern: tells every listener that the application now has a new status.
    // The service does not know who listens; new listeners can be added without changing this class.
    private void publishStatusChanged(LoanApplication application, ApplicationStatus previousStatus) {
        Customer customer = application.getCustomer();
        eventPublisher.publishEvent(new ApplicationStatusChangedEvent(
                application.getId(),
                customer.getFullName(),
                customer.getEmail(),
                previousStatus,
                application.getStatus(),
                application.getDecisionComment()));
    }

    // Chain of responsibility: asks every approval rule in turn and collects what they object to
    // (empty list = every rule is satisfied, the application may be approved)
    private List<String> findApprovalProblems(LoanApplication application) {
        List<String> problems = new ArrayList<>();
        for (ApprovalRule rule : approvalRules) {
            String problem = rule.findProblem(application);
            if (problem != null) {
                problems.add(problem);
            }
        }
        return problems;
    }

    // Rule 4, first half: an application cannot get the decision it already has
    private void requireNotAlready(LoanApplication application, ApplicationStatus status, String action) {
        if (application.getStatus() == status) {
            throw new ConflictException("Application with id " + application.getId() + " is already "
                    + status + " and cannot be " + action + " again");
        }
    }

    // Rule 4, second half: only SUBMITTED applications can be cancelled or modified
    private void requireSubmitted(LoanApplication application, String action) {
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ConflictException("Application with id " + application.getId() + " is "
                    + application.getStatus() + " and cannot be " + action);
        }
    }
}
