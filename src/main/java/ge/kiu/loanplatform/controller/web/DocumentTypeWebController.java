package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.document.DocumentTypeRequest;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.service.document.DocumentTypeService;
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

import java.util.Map;

// Document type page: list, add and delete document types.
// Shared by every website controller: WebControllerAdvice, WebForms, PreviousPage.
@Controller
@RequestMapping("/ui/document-types")
@RequiredArgsConstructor
public class DocumentTypeWebController {

    private final DocumentTypeService documentTypeService;

    @GetMapping
    public String documentTypes(Model model) {
        model.addAttribute("documentTypes", documentTypeService.getAll());
        return "document-types";
    }

    @PostMapping
    public String createDocumentType(@Valid @ModelAttribute DocumentTypeRequest form, BindingResult result,
                                     @RequestParam Map<String, String> params,
                                     Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            WebForms.showFieldErrors(model, params, result);
            return documentTypes(model);
        }
        try {
            DocumentTypeResponse created = documentTypeService.create(form);
            redirect.addFlashAttribute("message", created.name() + " was added");
            return "redirect:/ui/document-types";
        } catch (ConflictException ex) {
            WebForms.showFormError(model, params, ex.getMessage());
            return documentTypes(model);
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteDocumentType(@PathVariable Long id, RedirectAttributes redirect) {
        documentTypeService.delete(id);
        redirect.addFlashAttribute("message", "Document type " + id + " was deleted");
        return "redirect:/ui/document-types";
    }
}
