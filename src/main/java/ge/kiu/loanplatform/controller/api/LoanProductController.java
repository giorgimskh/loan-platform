package ge.kiu.loanplatform.controller.api;

import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.product.LoanProductRequest;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;
import ge.kiu.loanplatform.dto.validation.ValidAmount;
import ge.kiu.loanplatform.dto.validation.ValidTerm;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.product.LoanProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
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

@Tag(name = "Loan products", description = "Manage loan products and calculate what a loan would cost")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;
    private final LoanApplicationService loanApplicationService;

    @GetMapping
    public List<LoanProductResponse> getAll() {
        return loanProductService.getAll();
    }

    @GetMapping("/{id}")
    public LoanProductResponse getById(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        return loanProductService.getById(id);
    }

    // GET /api/products/1/calculate?amount=5000&termMonths=12
    // Payment calculator: monthly payment, total to pay back and total interest. Nothing is saved.
    // The parameters have the same limits as the fields of LoanApplicationRequest.
    @GetMapping("/{id}/calculate")
    public PaymentCalculationResponse calculate(
            @PathVariable @Positive(message = "Id must be a positive number") Long id,
            @RequestParam
            @ValidAmount(label = "Amount")
            BigDecimal amount,
            @RequestParam
            @ValidTerm(label = "Term")
            Integer termMonths) {
        return loanApplicationService.calculate(id, amount, termMonths);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanProductResponse create(@Valid @RequestBody LoanProductRequest request) {
        return loanProductService.create(request);
    }

    @PutMapping("/{id}")
    public LoanProductResponse update(@PathVariable @Positive(message = "Id must be a positive number") Long id, @Valid @RequestBody LoanProductRequest request) {
        return loanProductService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        loanProductService.delete(id);
    }
}
