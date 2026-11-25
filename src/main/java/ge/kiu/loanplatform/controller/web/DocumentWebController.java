package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.service.document.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Documents of an application: upload (multipart form on the application page) and delete.
// Mapped to /ui because the two addresses start differently: /ui/applications/{id}/documents and /ui/documents/{id}.
// Shared by every website controller: WebControllerAdvice, WebForms, PreviousPage.
@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class DocumentWebController {

    private final DocumentService documentService;

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
        return "redirect:" + PreviousPage.findOr(request, "/ui/applications");
    }
}
