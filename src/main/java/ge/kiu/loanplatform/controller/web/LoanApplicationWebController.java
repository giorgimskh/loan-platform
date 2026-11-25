package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.application.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.LoanApplicationUpdateRequest;
import ge.kiu.loanplatform.dto.application.RejectRequest;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.customer.CustomerService;
import ge.kiu.loanplatform.service.document.DocumentTypeService;
import ge.kiu.loanplatform.service.product.LoanProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

// Loan application pages: list, search, submit, edit, the application page with the officer's decision
// (approve, reject, undo), cancel, and the payment calculator (it uses the same rules as a new application).
// Mapped to /ui (not /ui/applications) because the calculator is at /ui/calculator.
// Shared by every website controller: WebControllerAdvice, WebForms, PreviousPage.
@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class LoanApplicationWebController {

    private final CustomerService customerService;
    private final LoanProductService loanProductService;
    private final LoanApplicationService loanApplicationService;
    private final DocumentTypeService documentTypeService;

    // The website version of GET /api/products/{id}/calculate.
    // The form has the same three fields as LoanApplicationUpdateRequest (product, amount, term),
    // so that DTO and its validation annotations are reused here.
    // It is a GET form: calculating changes nothing, and the result page can be bookmarked.
    @GetMapping("/calculator")
    public String calculator(@Valid @ModelAttribute LoanApplicationUpdateRequest form, BindingResult result,
                             @RequestParam Map<String, String> params, Model model) {
        model.addAttribute("products", loanProductService.getAll());
        model.addAttribute("values", params);
        if (!params.containsKey("amount")) {
            // the page was opened by a link, the form was not sent yet: show it empty, without errors
            return "calculator";
        }
        if (result.hasErrors()) {
            WebForms.showFieldErrors(model, params, result);
            return "calculator";
        }
        try {
            model.addAttribute("calculation",
                    loanApplicationService.calculate(form.loanProductId(), form.amount(), form.termMonths()));
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            // rule 1 or 2: this product does not lend that amount or for that long
            WebForms.showFormError(model, params, ex.getMessage());
        }
        return "calculator";
    }

    // Same pagination and sorting as GET /api/applications, e.g. /ui/applications?status=SUBMITTED&page=1
    @GetMapping("/applications")
    public String applications(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 8, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        model.addAttribute("page", loanApplicationService.getAll(status, pageable));
        model.addAttribute("status", status);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("products", loanProductService.getAll()); // for the search form
        return "applications";
    }

    // /ui/applications/new?customerId=1 opens the form with that customer already chosen.
    // The calculator links here with ?loanProductId=..&amount=..&termMonths=.. to fill the other fields.
    @GetMapping("/applications/new")
    public String newApplication(@RequestParam Map<String, String> params, Model model) {
        model.addAttribute("values", params);
        return applicationForm(model);
    }

    @PostMapping("/applications")
    public String createApplication(@Valid @ModelAttribute LoanApplicationRequest form, BindingResult result,
                                    @RequestParam Map<String, String> params,
                                    Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            WebForms.showFieldErrors(model, params, result);
            return applicationForm(model);
        }
        try {
            LoanApplicationResponse created = loanApplicationService.create(form);
            redirect.addFlashAttribute("message", "Application " + created.id() + " was submitted");
            return "redirect:/ui/applications/" + created.id();
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            // e.g. rule 1: the amount is outside the product's range
            WebForms.showFormError(model, params, ex.getMessage());
            return applicationForm(model);
        }
    }

    @GetMapping("/applications/search")
    public String searchApplications(@RequestParam String productName, @RequestParam BigDecimal amountAbove,
                                     Model model) {
        model.addAttribute("title", productName + " applications above " + amountAbove);
        model.addAttribute("applications", loanApplicationService.searchByProductAndAmount(productName, amountAbove));
        return "application-list";
    }

    @GetMapping("/applications/{id}")
    public String applicationDetail(@PathVariable Long id, Model model) {
        LoanApplicationResponse application = loanApplicationService.getById(id);
        // not named "application": that name is reserved by Thymeleaf
        model.addAttribute("loanApplication", application);
        // rule 7: what the product requires and which of those documents are still missing
        model.addAttribute("requiredDocuments", loanProductService.getById(application.loanProductId()).requiredDocuments());
        model.addAttribute("missingDocuments", loanApplicationService.getMissingDocuments(id));
        model.addAttribute("documentTypes", documentTypeService.getAll()); // choices of the upload form
        model.addAttribute("history", loanApplicationService.getHistory(id));
        // only an approved application has a repayment schedule
        if (application.status() == ApplicationStatus.APPROVED) {
            model.addAttribute("schedule", loanApplicationService.getSchedule(id));
        }
        return "application-detail";
    }

    // The edit page is the same template as "new application", filled with the current values
    @GetMapping("/applications/{id}/edit")
    public String editApplication(@PathVariable Long id, Model model) {
        LoanApplicationResponse application = loanApplicationService.getById(id);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("loanProductId", application.loanProductId().toString());
        values.put("amount", application.amount().toPlainString());
        values.put("termMonths", application.termMonths().toString());

        model.addAttribute("values", values);
        return applicationEditForm(model, id);
    }

    // The website version of PUT /api/applications/{id}
    @PostMapping("/applications/{id}")
    public String updateApplication(@PathVariable Long id,
                                    @Valid @ModelAttribute LoanApplicationUpdateRequest form, BindingResult result,
                                    @RequestParam Map<String, String> params,
                                    Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            WebForms.showFieldErrors(model, params, result);
            return applicationEditForm(model, id);
        }
        try {
            loanApplicationService.update(id, form);
            redirect.addFlashAttribute("message", "Application " + id + " was saved");
            return "redirect:/ui/applications/" + id;
        } catch (BusinessRuleException | ConflictException ex) {
            // rule 1 or 2 (amount or term outside the range), or rule 4 (a decided application cannot be modified)
            WebForms.showFormError(model, params, ex.getMessage());
            return applicationEditForm(model, id);
        }
    }

    // approve / reject / cancel can be clicked on the application page or in the "Actions" menu
    // of a list, so afterwards the user is sent back to the page the click came from.

    @PostMapping("/applications/{id}/approve")
    public String approve(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        loanApplicationService.approve(id);
        redirect.addFlashAttribute("message", "Application " + id + " was approved");
        return "redirect:" + PreviousPage.findOr(request, "/ui/applications/" + id);
    }

    @PostMapping("/applications/{id}/reject")
    public String reject(@PathVariable Long id, @Valid @ModelAttribute RejectRequest form, BindingResult result,
                         HttpServletRequest request, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            // only one field, so a short red message after the redirect is enough
            redirect.addFlashAttribute("error", result.getFieldErrors().getFirst().getDefaultMessage());
        } else {
            loanApplicationService.reject(id, form);
            redirect.addFlashAttribute("message", "Application " + id + " was rejected");
        }
        return "redirect:" + PreviousPage.findOr(request, "/ui/applications/" + id);
    }

    // Memento pattern: back to the state the application had before its last decision
    @PostMapping("/applications/{id}/undo-decision")
    public String undoDecision(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        LoanApplicationResponse restored = loanApplicationService.undoLastDecision(id);
        redirect.addFlashAttribute("message", "The last decision was undone: application " + id
                + " is " + restored.status().name().toLowerCase() + " again");
        return "redirect:" + PreviousPage.findOr(request, "/ui/applications/" + id);
    }

    @PostMapping("/applications/{id}/delete")
    public String cancel(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        loanApplicationService.delete(id);
        redirect.addFlashAttribute("message", "Application " + id + " was cancelled");
        // the application's own page no longer exists, so from there go to the list
        String previousPage = PreviousPage.findOr(request, "/ui/applications");
        if (previousPage.startsWith("/ui/applications/" + id)) {
            previousPage = "/ui/applications";
        }
        return "redirect:" + previousPage;
    }

    // The edit form shows which application is edited (its number and customer) and the product dropdown
    private String applicationEditForm(Model model, Long id) {
        model.addAttribute("applicationId", id);
        model.addAttribute("customerName", loanApplicationService.getById(id).customerFullName());
        model.addAttribute("products", loanProductService.getAll());
        return "application-form";
    }

    // The application form needs the customers and products for its two dropdowns
    private String applicationForm(Model model) {
        // unpaged = every customer in one list, sorted by name
        model.addAttribute("customers", customerService.getAll(null, Pageable.unpaged(Sort.by("fullName"))).getContent());
        model.addAttribute("products", loanProductService.getAll());
        return "application-form";
    }
}
