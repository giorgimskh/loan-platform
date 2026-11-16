package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.CustomerRequest;
import ge.kiu.loanplatform.dto.CustomerResponse;
import ge.kiu.loanplatform.dto.DocumentResponse;
import ge.kiu.loanplatform.dto.DocumentTypeRequest;
import ge.kiu.loanplatform.dto.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.LoanApplicationUpdateRequest;
import ge.kiu.loanplatform.dto.LoanProductRequest;
import ge.kiu.loanplatform.dto.LoanProductResponse;
import ge.kiu.loanplatform.dto.RejectRequest;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.exception.BusinessRuleException;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.service.CustomerService;
import ge.kiu.loanplatform.service.DocumentService;
import ge.kiu.loanplatform.service.DocumentTypeService;
import ge.kiu.loanplatform.service.LoanApplicationService;
import ge.kiu.loanplatform.service.LoanProductService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.net.URI;
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
    private final DocumentTypeService documentTypeService;

    // the first page of the customers list, newest first (used when the register form is shown again)
    private static final Pageable NEWEST_CUSTOMERS = PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "id"));

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
        model.addAttribute("stats", loanApplicationService.getStatistics()); // the numbers and charts at the top
        return "home"; // templates/home.html
    }

    // ---------- customers ----------

    // Same search and pagination as GET /api/customers, e.g. /ui/customers?search=ana&page=1
    @GetMapping("/customers")
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
    @PostMapping("/customers")
    public String createCustomer(@Valid @ModelAttribute CustomerRequest form, BindingResult result,
                                 @RequestParam Map<String, String> params,
                                 Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return customers(null, NEWEST_CUSTOMERS, model);
        }
        try {
            CustomerResponse created = customerService.create(form);
            // flash attribute = survives exactly one redirect, then disappears
            redirect.addFlashAttribute("message", created.fullName() + " was registered as customer " + created.id());
            return "redirect:/ui/customers";
        } catch (ConflictException ex) {
            // e.g. the email is already used: keep the form and show the reason above it
            showFormError(model, params, ex.getMessage());
            return customers(null, NEWEST_CUSTOMERS, model);
        }
    }

    // The edit page: the form filled with the customer's current values
    @GetMapping("/customers/{id}/edit")
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
    @PostMapping("/customers/{id}")
    public String updateCustomer(@PathVariable Long id,
                                 @Valid @ModelAttribute CustomerRequest form, BindingResult result,
                                 @RequestParam Map<String, String> params,
                                 Model model, RedirectAttributes redirect) {
        model.addAttribute("customerId", id);
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return "customer-form";
        }
        try {
            CustomerResponse updated = customerService.update(id, form);
            redirect.addFlashAttribute("message", updated.fullName() + " was saved");
            return "redirect:/ui/customers";
        } catch (ConflictException ex) {
            showFormError(model, params, ex.getMessage());
            return "customer-form";
        }
    }

    // The website version of DELETE /api/customers/{id}; a customer with applications is refused (409)
    @PostMapping("/customers/{id}/delete")
    public String deleteCustomer(@PathVariable Long id, RedirectAttributes redirect) {
        customerService.delete(id);
        redirect.addFlashAttribute("message", "Customer " + id + " was deleted");
        return "redirect:/ui/customers";
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
    public String newProduct(Model model) {
        // no "productId" in the model = the template shows "New product"; no checkbox is ticked
        return productForm(model, List.of());
    }

    @PostMapping("/products")
    public String createProduct(@Valid @ModelAttribute LoanProductRequest form, BindingResult result,
                                @RequestParam Map<String, String> params,
                                @RequestParam(name = "requiredDocumentTypeIds", required = false) List<String> checkedIds,
                                Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return productForm(model, checkedIds);
        }
        try {
            LoanProductResponse created = loanProductService.create(form);
            redirect.addFlashAttribute("message", created.name() + " was created");
            return "redirect:/ui/products";
        } catch (BusinessRuleException | ConflictException | ResourceNotFoundException ex) {
            showFormError(model, params, ex.getMessage());
            return productForm(model, checkedIds);
        }
    }

    // The edit page is the same template as "new product", filled with the current values
    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model) {
        LoanProductResponse product = loanProductService.getById(id);

        // the ids of the documents this product requires now: their checkboxes start ticked
        List<String> checkedIds = new ArrayList<>();
        for (DocumentTypeResponse documentType : product.requiredDocuments()) {
            checkedIds.add(documentType.id().toString());
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", product.name());
        values.put("interestRate", product.interestRate().toPlainString());
        values.put("minAmount", product.minAmount().toPlainString());
        values.put("maxAmount", product.maxAmount().toPlainString());
        values.put("minTermMonths", product.minTermMonths().toString());
        values.put("maxTermMonths", product.maxTermMonths().toString());

        model.addAttribute("values", values);
        model.addAttribute("productId", id);
        return productForm(model, checkedIds);
    }

    // The website version of PUT /api/products/{id}
    @PostMapping("/products/{id}")
    public String updateProduct(@PathVariable Long id,
                                @Valid @ModelAttribute LoanProductRequest form, BindingResult result,
                                @RequestParam Map<String, String> params,
                                @RequestParam(name = "requiredDocumentTypeIds", required = false) List<String> checkedIds,
                                Model model, RedirectAttributes redirect) {
        model.addAttribute("productId", id);
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return productForm(model, checkedIds);
        }
        try {
            LoanProductResponse updated = loanProductService.update(id, form);
            redirect.addFlashAttribute("message", updated.name() + " was saved");
            return "redirect:/ui/products";
        } catch (BusinessRuleException | ConflictException | ResourceNotFoundException ex) {
            showFormError(model, params, ex.getMessage());
            return productForm(model, checkedIds);
        }
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirect) {
        loanProductService.delete(id);
        redirect.addFlashAttribute("message", "Product " + id + " was deleted");
        return "redirect:/ui/products";
    }

    // ---------- document types ----------

    @GetMapping("/document-types")
    public String documentTypes(Model model) {
        model.addAttribute("documentTypes", documentTypeService.getAll());
        return "document-types";
    }

    @PostMapping("/document-types")
    public String createDocumentType(@Valid @ModelAttribute DocumentTypeRequest form, BindingResult result,
                                     @RequestParam Map<String, String> params,
                                     Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            showFieldErrors(model, params, result);
            return documentTypes(model);
        }
        try {
            DocumentTypeResponse created = documentTypeService.create(form);
            redirect.addFlashAttribute("message", created.name() + " was added");
            return "redirect:/ui/document-types";
        } catch (ConflictException ex) {
            showFormError(model, params, ex.getMessage());
            return documentTypes(model);
        }
    }

    @PostMapping("/document-types/{id}/delete")
    public String deleteDocumentType(@PathVariable Long id, RedirectAttributes redirect) {
        documentTypeService.delete(id);
        redirect.addFlashAttribute("message", "Document type " + id + " was deleted");
        return "redirect:/ui/document-types";
    }

    // ---------- payment calculator ----------

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
            showFieldErrors(model, params, result);
            return "calculator";
        }
        try {
            model.addAttribute("calculation",
                    loanApplicationService.calculate(form.loanProductId(), form.amount(), form.termMonths()));
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            // rule 1 or 2: this product does not lend that amount or for that long
            showFormError(model, params, ex.getMessage());
        }
        return "calculator";
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
            showFieldErrors(model, params, result);
            return applicationEditForm(model, id);
        }
        try {
            loanApplicationService.update(id, form);
            redirect.addFlashAttribute("message", "Application " + id + " was saved");
            return "redirect:/ui/applications/" + id;
        } catch (BusinessRuleException | ConflictException ex) {
            // rule 1 or 2 (amount or term outside the range), or rule 4 (a decided application cannot be modified)
            showFormError(model, params, ex.getMessage());
            return applicationEditForm(model, id);
        }
    }

    // approve / reject / cancel can be clicked on the application page or in the "Actions" menu
    // of a list, so afterwards the user is sent back to the page the click came from.

    @PostMapping("/applications/{id}/approve")
    public String approve(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        loanApplicationService.approve(id);
        redirect.addFlashAttribute("message", "Application " + id + " was approved");
        return "redirect:" + previousUiPageOr(request, "/ui/applications/" + id);
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
        return "redirect:" + previousUiPageOr(request, "/ui/applications/" + id);
    }

    @PostMapping("/applications/{id}/delete")
    public String cancel(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        loanApplicationService.delete(id);
        redirect.addFlashAttribute("message", "Application " + id + " was cancelled");
        // the application's own page no longer exists, so from there go to the list
        String previousPage = previousUiPageOr(request, "/ui/applications");
        if (previousPage.startsWith("/ui/applications/" + id)) {
            previousPage = "/ui/applications";
        }
        return "redirect:" + previousPage;
    }

    // The form uses enctype="multipart/form-data", the same as the REST upload endpoint
    @PostMapping("/applications/{id}/documents")
    public String uploadDocument(@PathVariable Long id, @RequestParam MultipartFile file,
                                 @RequestParam Long documentTypeId, RedirectAttributes redirect) {
        DocumentResponse uploaded = documentService.upload(id, file, documentTypeId);
        redirect.addFlashAttribute("message", uploaded.originalFileName() + " was uploaded");
        return "redirect:/ui/applications/" + id;
    }

    // The website version of DELETE /api/documents/{id}; afterwards back to the application's page
    @PostMapping("/documents/{id}/delete")
    public String deleteDocument(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        documentService.delete(id);
        redirect.addFlashAttribute("message", "Document " + id + " was deleted");
        return "redirect:" + previousUiPageOr(request, "/ui/applications");
    }

    // ---------- errors ----------

    // When a service throws one of our custom exceptions on a /ui page (e.g. rule 4: cancelling an
    // APPROVED application, or approving it a second time), send the user back to the page they came from and show the reason there.
    // An @ExceptionHandler inside a controller wins over the global @RestControllerAdvice (which returns JSON).
    @ExceptionHandler({ResourceNotFoundException.class, BusinessRuleException.class, ConflictException.class})
    public String handleServiceError(RuntimeException ex, HttpServletRequest request,
                                     Model model, RedirectAttributes redirect) {
        String previousPage = previousUiPage(request);
        if (previousPage != null) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + previousPage;
        }
        // no page to go back to (e.g. the address was typed by hand): show a separate error page
        model.addAttribute("errorType", ex.getClass().getSimpleName());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error-message";
    }

    // The page the user came from, or "fallback" when it is unknown
    private String previousUiPageOr(HttpServletRequest request, String fallback) {
        String previousPage = previousUiPage(request);
        return previousPage != null ? previousPage : fallback;
    }

    // The browser sends the address of the page the user came from in the "Referer" header.
    // We use only its path, and only if it is one of our own /ui pages, so nobody can use
    // this to redirect a user to another website.
    private String previousUiPage(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getPath();
            // going back to the page that just failed would fail again, so that does not count
            if (path == null || !path.startsWith("/ui") || path.equals(request.getRequestURI())) {
                return null;
            }
            return uri.getQuery() == null ? path : path + "?" + uri.getQuery();
        } catch (IllegalArgumentException invalidAddress) {
            return null;
        }
    }

    // ---------- helpers ----------

    // The product form needs every document type (its checkboxes) and which of them are ticked
    private String productForm(Model model, List<String> checkedIds) {
        model.addAttribute("documentTypes", documentTypeService.getAll());
        // no checkbox ticked = the browser sends nothing, so checkedIds can be null
        model.addAttribute("checkedIds", checkedIds == null ? List.of() : checkedIds);
        return "product-form";
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
