package ge.kiu.loanplatform.controller.api;

import ge.kiu.loanplatform.dto.document.DocumentFile;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.validation.ValidFile;
import ge.kiu.loanplatform.service.document.DocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Tag(name = "Documents", description = "Upload, download and delete the documents of an application")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // multipart/form-data with two parts: "file" and "documentTypeId"
    // @ValidFile checks the file (present, at most 5MB, PDF/JPG/PNG) before the service is called.
    // required = false: a missing file arrives as null, so @ValidFile reports it with the same
    // clean 400 "Validation failed" response as an empty or wrong file.
    @PostMapping(value = "/applications/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            @PathVariable @Positive(message = "Id must be a positive number") Long id,
            @RequestParam(required = false) @ValidFile MultipartFile file,
            @RequestParam @Positive(message = "Document type id must be a positive number") Long documentTypeId) {
        return documentService.upload(id, file, documentTypeId);
    }

    // GET /api/documents/1/download
    // ResponseEntity lets us set headers. The body is the file itself, not JSON.
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        DocumentFile file = documentService.download(id);

        // Content-Type from the file extension (.pdf -> application/pdf); unknown -> "just bytes"
        MediaType contentType = MediaTypeFactory.getMediaType(file.originalFileName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        // Content-Disposition "attachment" tells the browser to save the file under its original name
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.resource());
    }

    // Only possible while the application is SUBMITTED (409 afterwards)
    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "Id must be a positive number") Long id) {
        documentService.delete(id);
    }
}
