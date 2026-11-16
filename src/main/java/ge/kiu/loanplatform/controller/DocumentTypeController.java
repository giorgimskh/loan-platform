package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.DocumentTypeRequest;
import ge.kiu.loanplatform.dto.DocumentTypeResponse;
import ge.kiu.loanplatform.service.DocumentTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Document types", description = "The kinds of documents a loan product can require")
@RestController
@RequestMapping("/api/document-types")
@RequiredArgsConstructor
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    @GetMapping
    public List<DocumentTypeResponse> getAll() {
        return documentTypeService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTypeResponse create(@Valid @RequestBody DocumentTypeRequest request) {
        return documentTypeService.create(request);
    }

    // Refused with 409 while a product requires the type or a document was uploaded with it
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        documentTypeService.delete(id);
    }
}
