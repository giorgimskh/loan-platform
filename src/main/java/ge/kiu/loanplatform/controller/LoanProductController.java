package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.LoanProductRequest;
import ge.kiu.loanplatform.dto.LoanProductResponse;
import ge.kiu.loanplatform.service.LoanProductService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;

    @GetMapping
    public List<LoanProductResponse> getAll() {
        return loanProductService.getAll();
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
