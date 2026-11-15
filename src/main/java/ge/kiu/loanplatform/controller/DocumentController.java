package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.DocumentResponse;
import ge.kiu.loanplatform.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // multipart/form-data with two parts: "file" and "documentTypeId"
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@PathVariable Long id,
                                   @RequestParam MultipartFile file,
                                   @RequestParam Long documentTypeId) {
        return documentService.upload(id, file, documentTypeId);
    }
}
