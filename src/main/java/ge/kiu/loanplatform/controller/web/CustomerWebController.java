package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.customer.CustomerRequest;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.customer.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

// Customer pages: list with search and pager, register, edit, delete, and a customer's applications by status.
// Shared by every website controller: WebControllerAdvice, WebForms, PreviousPage.
@Controller
@RequestMapping("/ui/customers")
@RequiredArgsConstructor
public class CustomerWebController {

    private final CustomerService customerService;
    private final LoanApplicationService loanApplicationService;

    // the first page of the customers list, newest first (used when the register form is shown again)
    private static final Pageable NEWEST_CUSTOMERS = PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "id"));

    // Same search and pagination as GET /api/customers, e.g. /ui/customers?search=ana&page=1
    @GetMapping
    public String customers(@RequestParam(required = false) String search,
                            @PageableDefault(size = 6, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                            Model model) {
        model.addAttribute("page", customerService.getAll(search, pageable));
        model.addAttribute("search", search);
        return "customers";
    }

    // @ModelAttribute builds the DTO from the form fields; @Valid checks its annotations;
    // BindingResult receives the validation errors instead of an exception being thrown.
    // "params" is the raw text of every form field, used to show the form again unchanged.
    @PostMapping
    public String createCustomer(@Valid @ModelAttribute CustomerRequest form, BindingResult result,
                                 @RequestParam Map<String, String> params,
                                 Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            WebForms.showFieldErrors(model, params, result);
            return customers(null, NEWEST_CUSTOMERS, model);
        }
        try {
            CustomerResponse created = customerService.create(form);
            // flash attribute = survives exactly one redirect, then disappears
            redirect.addFlashAttribute("message", created.fullName() + " was registered as customer " + created.id());
            return "redirect:/ui/customers";
        } catch (ConflictException ex) {
            // e.g. the email is already used: keep the form and show the reason above it
            WebForms.showFormError(model, params, ex.getMessage());
            return customers(null, NEWEST_CUSTOMERS, model);
        }
    }

    // The edit page: the form filled with the customer's current values
    @GetMapping("/{id}/edit")
    public String editCustomer(@PathVariable Long id, Model model) {
        CustomerResponse customer = customerService.getById(id);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("fullName", customer.fullName());
        values.put("email", customer.email());
        values.put("personalNumber", customer.personalNumber());
        values.put("phone", customer.phone());

        model.addAttribute("values", values);
        model.addAttribute("customerId", id);
        return "customer-form";
    }

    // The website version of PUT /api/customers/{id}
    @PostMapping("/{id}")
    public String updateCustomer(@PathVariable Long id,
                                 @Valid @ModelAttribute CustomerRequest form, BindingResult result,
                                 @RequestParam Map<String, String> params,
                                 Model model, RedirectAttributes redirect) {
        model.addAttribute("customerId", id);
        if (result.hasErrors()) {
            WebForms.showFieldErrors(model, params, result);
            return "customer-form";
        }
        try {
            CustomerResponse updated = customerService.update(id, form);
            redirect.addFlashAttribute("message", updated.fullName() + " was saved");
            return "redirect:/ui/customers";
        } catch (ConflictException ex) {
            WebForms.showFormError(model, params, ex.getMessage());
            return "customer-form";
        }
    }

    // The website version of DELETE /api/customers/{id}; a customer with applications is refused (409)
    @PostMapping("/{id}/delete")
    public String deleteCustomer(@PathVariable Long id, RedirectAttributes redirect) {
        customerService.delete(id);
        redirect.addFlashAttribute("message", "Customer " + id + " was deleted");
        return "redirect:/ui/customers";
    }

    @GetMapping("/{id}/applications")
    public String customerApplications(@PathVariable Long id, @RequestParam ApplicationStatus status, Model model) {
        CustomerResponse customer = customerService.getById(id);
        model.addAttribute("title", customer.fullName() + ": " + status.name().toLowerCase() + " applications");
        model.addAttribute("applications", loanApplicationService.getByCustomerAndStatus(id, status));
        return "application-list";
    }
}
