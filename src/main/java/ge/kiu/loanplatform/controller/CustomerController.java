package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.CustomerRequest;
import ge.kiu.loanplatform.dto.CustomerResponse;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.service.CustomerService;
import ge.kiu.loanplatform.service.LoanApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import java.util.List;

@Tag(name = "Customers", description = "Register, find, change and delete customers")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final LoanApplicationService loanApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    // GET /api/customers?search=ana&page=0&size=10&sort=fullName,asc
    // search is optional: it looks in the name, the email and the personal number.
    @GetMapping
    public Page<CustomerResponse> getAll(
            @RequestParam(required = false)
            @Size(max = 100, message = "Search text must be at most 100 characters")
            String search,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return customerService.getAll(search, pageable);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        return customerService.getById(id);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable @Positive(message = "Id must be a positive number") Long id,
                                   @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    // Refused with 409 while the customer has applications
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        customerService.delete(id);
    }

    // GET /api/customers/1/applications?status=SUBMITTED
    // All applications of one customer that have the given status. status is required.
    @GetMapping("/{id}/applications")
    public List<LoanApplicationResponse> getApplications(
            @PathVariable @Positive(message = "Id must be a positive number") Long id,
            @RequestParam ApplicationStatus status) {
        return loanApplicationService.getByCustomerAndStatus(id, status);
    }
}
