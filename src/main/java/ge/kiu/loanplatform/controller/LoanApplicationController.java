package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.ProductTotalResponse;
import ge.kiu.loanplatform.dto.RejectRequest;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.service.LoanApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
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

    @GetMapping("/{id}")
    public LoanApplicationResponse getById(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        return loanApplicationService.getById(id);
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
