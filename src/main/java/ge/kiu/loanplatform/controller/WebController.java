package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.CustomerRequest;
import ge.kiu.loanplatform.dto.CustomerResponse;
import ge.kiu.loanplatform.dto.DocumentResponse;
import ge.kiu.loanplatform.dto.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.LoanProductRequest;
import ge.kiu.loanplatform.dto.LoanProductResponse;
import ge.kiu.loanplatform.dto.RejectRequest;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.exception.BusinessRuleException;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.service.CustomerService;
import ge.kiu.loanplatform.service.DocumentService;
import ge.kiu.loanplatform.service.LoanApplicationService;
import ge.kiu.loanplatform.service.LoanProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The small test website (Thymeleaf pages) under /ui.
 * Difference from the other controllers:
 * - @Controller (not @RestController): a method returns the NAME of an HTML template
 *   from src/main/resources/templates, not JSON.
 * - It calls the same services as the REST API, so all business rules and
 *   validation annotations are exactly the same.
 * - HTML forms can only send GET and POST, so update/approve/reject/delete are POST here.
 *
 * How a form works here:
 * - valid   -> call the service, then redirect with a green "flash" message.
 * - invalid -> show the SAME page again with what the user typed ("values")
 *              and one message per field ("fieldErrors"), so nothing has to be retyped.
 */
@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class WebController {

    private final CustomerService customerService;
    private final LoanProductService loanProductService;
    private final LoanApplicationService loanApplicationService;
    private final DocumentService documentService;

    // An empty form field arrives as "" (empty text). This turns "" into null,
    // so an empty optional field (phone) is treated as "not sent", like in JSON.
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    // ---------- home ----------

    // both /ui and /ui/ open the overview
    @GetMapping({"", "/"})
    public String home(Model model) {
        // Model = the data the template can read, e.g. ${totals}
        // the 5 newest applications that still need a decision
        Pageable newestFive = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("waiting", loanApplicationService.getAll(ApplicationStatus.SUBMITTED, newestFive));
        model.addAttribute("totals", loanApplicationService.getApprovedTotalsPerProduct());
        return "home"; // templates/home.html
    }

    // ---------- customers ----------

    @GetMapping("/customers")
    public String customers(Model model) {
        model.addAttribute("customers", customerService.getAll());
        return "customers";
    }

    // @ModelAttribute builds the DTO from the form fields; @Valid checks its annotations;
    // BindingResult receives the validation errors instead of an exception being thrown.
    // "params" is the raw text of every form field, used to show the form again unchanged.
    @PostMapping("/customers")
    public String createCustomer(@Valid @ModelAttribute CustomerRequest form, BindingResult result,
                                 @RequestParam Map<String, String> params,
                                 Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return customers(model);
        }
        try {
            CustomerResponse created = customerService.create(form);
            // flash attribute = survives exactly one redirect, then disappears
            redirect.addFlashAttribute("message", created.fullName() + " was registered as customer " + created.id());
            return "redirect:/ui/customers";
        } catch (ConflictException ex) {
            // e.g. the email is already used: keep the form and show the reason above it
            showFormError(model, params, ex.getMessage());
            return customers(model);
        }
    }

    @GetMapping("/customers/{id}/applications")
    public String customerApplications(@PathVariable Long id, @RequestParam ApplicationStatus status, Model model) {
        CustomerResponse customer = customerService.getById(id);
        model.addAttribute("title", customer.fullName() + ": " + status.name().toLowerCase() + " applications");
        model.addAttribute("applications", loanApplicationService.getByCustomerAndStatus(id, status));
        return "application-list";
    }

    // ---------- products ----------

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", loanProductService.getAll());
        return "products";
    }

    @GetMapping("/products/new")
    public String newProduct() {
        return "product-form"; // no "productId" in the model = the template shows "New product"
    }

    @PostMapping("/products")
    public String createProduct(@Valid @ModelAttribute LoanProductRequest form, BindingResult result,
                                @RequestParam Map<String, String> params,
                                Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return "product-form";
        }
        try {
            LoanProductResponse created = loanProductService.create(form);
            redirect.addFlashAttribute("message", created.name() + " was created");
            return "redirect:/ui/products";
        } catch (BusinessRuleException | ConflictException | ResourceNotFoundException ex) {
            showFormError(model, params, ex.getMessage());
            return "product-form";
        }
    }

    // The edit page is the same template as "new product", filled with the current values
    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model) {
        LoanProductResponse product = loanProductService.getById(id);

        List<String> documentTypeIds = new ArrayList<>();
        for (DocumentTypeResponse documentType : product.requiredDocuments()) {
            documentTypeIds.add(documentType.id().toString());
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", product.name());
        values.put("interestRate", product.interestRate().toPlainString());
        values.put("minAmount", product.minAmount().toPlainString());
        values.put("maxAmount", product.maxAmount().toPlainString());
        values.put("minTermMonths", product.minTermMonths().toString());
        values.put("maxTermMonths", product.maxTermMonths().toString());
        values.put("requiredDocumentTypeIds", String.join(",", documentTypeIds)); // e.g. "1,2"

        model.addAttribute("values", values);
        model.addAttribute("productId", id);
        return "product-form";
    }

    // The website version of PUT /api/products/{id}
    @PostMapping("/products/{id}")
    public String updateProduct(@PathVariable Long id,
                                @Valid @ModelAttribute LoanProductRequest form, BindingResult result,
                                @RequestParam Map<String, String> params,
                                Model model, RedirectAttributes redirect) {
        model.addAttribute("productId", id);
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return "product-form";
        }
        try {
            LoanProductResponse updated = loanProductService.update(id, form);
            redirect.addFlashAttribute("message", updated.name() + " was saved");
            return "redirect:/ui/products";
        } catch (BusinessRuleException | ConflictException | ResourceNotFoundException ex) {
            showFormError(model, params, ex.getMessage());
            return "product-form";
        }
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirect) {
        loanProductService.delete(id);
        redirect.addFlashAttribute("message", "Product " + id + " was deleted");
        return "redirect:/ui/products";
    }

    // ---------- applications ----------

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

    // /ui/applications/new?customerId=1 opens the form with that customer already chosen
    @GetMapping("/applications/new")
    public String newApplication(@RequestParam(required = false) Long customerId, Model model) {
        if (customerId != null) {
            model.addAttribute("values", Map.of("customerId", customerId.toString()));
        }
        return applicationForm(model);
    }

    @PostMapping("/applications")
    public String createApplication(@Valid @ModelAttribute LoanApplicationRequest form, BindingResult result,
                                    @RequestParam Map<String, String> params,
                                    Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return applicationForm(model);
        }
        try {
            LoanApplicationResponse created = loanApplicationService.create(form);
            redirect.addFlashAttribute("message", "Application " + created.id() + " was submitted");
            return "redirect:/ui/applications/" + created.id();
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            // e.g. rule 1: the amount is outside the product's range
            showFormError(model, params, ex.getMessage());
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
        // the documents this application's product requires (choices of the upload form)
        for (LoanProductResponse product : loanProductService.getAll()) {
            if (product.id().equals(application.loanProductId())) {
                model.addAttribute("requiredDocuments", product.requiredDocuments());
            }
        }
        return "application-detail";
    }

    @PostMapping("/applications/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirect) {
        loanApplicationService.approve(id);
        redirect.addFlashAttribute("message", "Application " + id + " was approved");
        return "redirect:/ui/applications/" + id;
    }

    @PostMapping("/applications/{id}/reject")
    public String reject(@PathVariable Long id, @Valid @ModelAttribute RejectRequest form, BindingResult result,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            // only one field, so a short red message after the redirect is enough
            redirect.addFlashAttribute("error", result.getFieldErrors().getFirst().getDefaultMessage());
            return "redirect:/ui/applications/" + id;
        }
        loanApplicationService.reject(id, form);
        redirect.addFlashAttribute("message", "Application " + id + " was rejected");
        return "redirect:/ui/applications/" + id;
    }

    @PostMapping("/applications/{id}/delete")
    public String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        loanApplicationService.delete(id);
        redirect.addFlashAttribute("message", "Application " + id + " was cancelled");
        return "redirect:/ui/applications";
    }

    // The form uses enctype="multipart/form-data", the same as the REST upload endpoint
    @PostMapping("/applications/{id}/documents")
    public String uploadDocument(@PathVariable Long id, @RequestParam MultipartFile file,
                                 @RequestParam Long documentTypeId, RedirectAttributes redirect) {
        DocumentResponse uploaded = documentService.upload(id, file, documentTypeId);
        redirect.addFlashAttribute("message", uploaded.originalFileName() + " was uploaded");
        return "redirect:/ui/applications/" + id;
    }

    // ---------- errors ----------

    // When a service throws one of our custom exceptions on a /ui page, show an HTML page with the message.
    // An @ExceptionHandler inside a controller wins over the global @RestControllerAdvice (which returns JSON).
    @ExceptionHandler({ResourceNotFoundException.class, BusinessRuleException.class, ConflictException.class})
    public String handleServiceError(RuntimeException ex, Model model) {
        model.addAttribute("errorType", ex.getClass().getSimpleName());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error-message";
    }

    // ---------- helpers ----------

    // The application form needs the customers and products for its two dropdowns
    private String applicationForm(Model model) {
        model.addAttribute("customers", customerService.getAll());
        model.addAttribute("products", loanProductService.getAll());
        return "application-form";
    }

    // Validation failed: give the template the typed values and one message per field
    private void showFieldErrors(Model model, Map<String, String> params, BindingResult result) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : result.getFieldErrors()) {
            // an invalid element of a Set is reported as "requiredDocumentTypeIds[]"
            String field = error.getField().replace("[]", "");
            // isBindingFailure = the text could not be converted at all, e.g. "abc" for a number
            String message = error.isBindingFailure() ? "Enter a valid value" : error.getDefaultMessage();
            fieldErrors.putIfAbsent(field, message);
        }
        model.addAttribute("values", params);
        model.addAttribute("fieldErrors", fieldErrors);
    }

    // The service refused the request (business rule or conflict): keep the form, show the reason above it
    private void showFormError(Model model, Map<String, String> params, String message) {
        model.addAttribute("values", params);
        model.addAttribute("formError", message);
    }
}
