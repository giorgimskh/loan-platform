package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.LoanApplicationUpdateRequest;
import ge.kiu.loanplatform.dto.ProductTotalResponse;
import ge.kiu.loanplatform.dto.RejectRequest;
import ge.kiu.loanplatform.dto.RepaymentScheduleResponse;
import ge.kiu.loanplatform.dto.StatisticsResponse;
import ge.kiu.loanplatform.dto.StatusChangeResponse;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.service.LoanApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Applications", description = "Submit, change, decide and report on loan applications")
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanApplicationResponse create(@Valid @RequestBody LoanApplicationRequest request) {
        return loanApplicationService.create(request);
    }

    // GET /api/applications?status=SUBMITTED&page=0&size=10&sort=createdAt,desc
    @GetMapping
    public Page<LoanApplicationResponse> getAll(
            @RequestParam(required = false) ApplicationStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return loanApplicationService.getAll(status, pageable);
    }

    // GET /api/applications/search?productName=Consumer Loan&amountAbove=1000
    // Applications of one product with an amount greater than amountAbove. Both parameters are required.
    @GetMapping("/search")
    public List<LoanApplicationResponse> search(
            @RequestParam
            @NotBlank(message = "Product name is required")
            @Size(max = 100, message = "Product name must be at most 100 characters")
            String productName,
            @RequestParam
            @DecimalMin(value = "0.0", message = "Amount cannot be negative")
            BigDecimal amountAbove) {
        return loanApplicationService.searchByProductAndAmount(productName, amountAbove);
    }

    // GET /api/applications/reports/approved-totals
    // Report: for every product, the sum of all APPROVED application amounts.
    @GetMapping("/reports/approved-totals")
    public List<ProductTotalResponse> approvedTotals() {
        return loanApplicationService.getApprovedTotalsPerProduct();
    }

    // GET /api/applications/reports/statistics
    // How many applications per status, the approval rate, the average amount and the approved total.
    @GetMapping("/reports/statistics")
    public StatisticsResponse statistics() {
        return loanApplicationService.getStatistics();
    }

    @GetMapping("/{id}")
    public LoanApplicationResponse getById(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        return loanApplicationService.getById(id);
    }

    // Every status the application has had, oldest first
    @GetMapping("/{id}/history")
    public List<StatusChangeResponse> history(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        return loanApplicationService.getHistory(id);
    }

    // Rule 7: the required documents that are not uploaded yet. An empty list means it can be approved.
    @GetMapping("/{id}/missing-documents")
    public List<DocumentTypeResponse> missingDocuments(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        return loanApplicationService.getMissingDocuments(id);
    }

    // The month-by-month repayment plan of an APPROVED application (409 for other statuses)
    @GetMapping("/{id}/schedule")
    public RepaymentScheduleResponse schedule(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        return loanApplicationService.getSchedule(id);
    }

    // Change the product, amount or term of a SUBMITTED application
    @PutMapping("/{id}")
    public LoanApplicationResponse update(@PathVariable @Positive(message = "Id must be a positive number") Long id,
                                          @Valid @RequestBody LoanApplicationUpdateRequest request) {
        return loanApplicationService.update(id, request);
    }

    @PutMapping("/{id}/approve")
    public LoanApplicationResponse approve(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        return loanApplicationService.approve(id);
    }

    @PutMapping("/{id}/reject")
    public LoanApplicationResponse reject(@PathVariable @Positive(message = "Id must be a positive number") Long id, @Valid @RequestBody RejectRequest request) {
        return loanApplicationService.reject(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        loanApplicationService.delete(id);
    }
}
