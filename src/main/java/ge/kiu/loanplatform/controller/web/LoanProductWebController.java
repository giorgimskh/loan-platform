package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.product.LoanProductRequest;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.service.document.DocumentTypeService;
import ge.kiu.loanplatform.service.product.LoanProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Loan product pages: list, create, edit (with the required documents as checkboxes) and delete.
// Shared by every website controller: WebControllerAdvice, WebForms, PreviousPage.
@Controller
@RequestMapping("/ui/products")
@RequiredArgsConstructor
public class LoanProductWebController {

    private final LoanProductService loanProductService;
    private final DocumentTypeService documentTypeService;

    @GetMapping
    public String products(Model model) {
        model.addAttribute("products", loanProductService.getAll());
        return "products";
    }

    @GetMapping("/new")
    public String newProduct(Model model) {
        // no "productId" in the model = the template shows "New product"; no checkbox is ticked
        return productForm(model, List.of());
    }

    @PostMapping
    public String createProduct(@Valid @ModelAttribute LoanProductRequest form, BindingResult result,
                                @RequestParam Map<String, String> params,
                                @RequestParam(name = "requiredDocumentTypeIds", required = false) List<String> checkedIds,
                                Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            WebForms.showFieldErrors(model, params, result);
            return productForm(model, checkedIds);
        }
        try {
            LoanProductResponse created = loanProductService.create(form);
            redirect.addFlashAttribute("message", created.name() + " was created");
            return "redirect:/ui/products";
        } catch (BusinessRuleException | ConflictException | ResourceNotFoundException ex) {
            WebForms.showFormError(model, params, ex.getMessage());
            return productForm(model, checkedIds);
        }
    }

    // The edit page is the same template as "new product", filled with the current values
    @GetMapping("/{id}/edit")
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
    @PostMapping("/{id}")
    public String updateProduct(@PathVariable Long id,
                                @Valid @ModelAttribute LoanProductRequest form, BindingResult result,
                                @RequestParam Map<String, String> params,
                                @RequestParam(name = "requiredDocumentTypeIds", required = false) List<String> checkedIds,
                                Model model, RedirectAttributes redirect) {
        model.addAttribute("productId", id);
        if (result.hasErrors()) {
            WebForms.showFieldErrors(model, params, result);
            return productForm(model, checkedIds);
        }
        try {
            LoanProductResponse updated = loanProductService.update(id, form);
            redirect.addFlashAttribute("message", updated.name() + " was saved");
            return "redirect:/ui/products";
        } catch (BusinessRuleException | ConflictException | ResourceNotFoundException ex) {
            WebForms.showFormError(model, params, ex.getMessage());
            return productForm(model, checkedIds);
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirect) {
        loanProductService.delete(id);
        redirect.addFlashAttribute("message", "Product " + id + " was deleted");
        return "redirect:/ui/products";
    }

    // The product form needs every document type (its checkboxes) and which of them are ticked
    private String productForm(Model model, List<String> checkedIds) {
        model.addAttribute("documentTypes", documentTypeService.getAll());
        // no checkbox ticked = the browser sends nothing, so checkedIds can be null
        model.addAttribute("checkedIds", checkedIds == null ? List.of() : checkedIds);
        return "product-form";
    }
}
