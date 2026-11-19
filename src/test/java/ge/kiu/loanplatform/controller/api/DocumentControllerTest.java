package ge.kiu.loanplatform.controller.api;

import ge.kiu.loanplatform.dto.document.DocumentFile;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.service.document.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web layer test: starts only this controller + GlobalExceptionHandler (no database, no real files).
// The service is a mock, MockMvc sends fake multipart/form-data requests.
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    // a fake uploaded file; the first argument "file" must match the @RequestParam name
    private final MockMultipartFile pdf =
            new MockMultipartFile("file", "id-card.pdf", "application/pdf", "fake pdf content".getBytes());

    @Test
    void upload_returns201_whenRequestIsValid() throws Exception {
        when(documentService.upload(eq(1L), any(MultipartFile.class), eq(2L)))
                .thenReturn(new DocumentResponse(10L, "id-card.pdf", 2L, "ID Card", LocalDateTime.now()));

        mockMvc.perform(multipart("/api/applications/1/documents")
                        .file(pdf)
                        .param("documentTypeId", "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.originalFileName").value("id-card.pdf"))
                .andExpect(jsonPath("$.documentTypeName").value("ID Card"));
    }

    @Test
    void upload_returns400_whenApplicationIdIsNegative() throws Exception {
        mockMvc.perform(multipart("/api/applications/-1/documents")
                        .file(pdf)
                        .param("documentTypeId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));

        verifyNoInteractions(documentService);
    }

    @Test
    void upload_returns400_whenDocumentTypeIdIsNegative() throws Exception {
        mockMvc.perform(multipart("/api/applications/1/documents")
                        .file(pdf)
                        .param("documentTypeId", "-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.documentTypeId").value("Document type id must be a positive number"));

        verifyNoInteractions(documentService);
    }

    @Test
    void upload_returns400_whenDocumentTypeIdIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/applications/1/documents")
                        .file(pdf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'documentTypeId'"));

        verifyNoInteractions(documentService);
    }

    @Test
    void upload_returns400_whenDocumentTypeIdIsNotANumber() throws Exception {
        mockMvc.perform(multipart("/api/applications/1/documents")
                        .file(pdf)
                        .param("documentTypeId", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(documentService);
    }

    @Test
    void upload_returns400_whenFileIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/applications/1/documents")
                        .param("documentTypeId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required part 'file'"));

        verifyNoInteractions(documentService);
    }

    @Test
    void upload_returns400_whenFileTypeIsNotAllowed() throws Exception { // rule 6
        when(documentService.upload(eq(1L), any(MultipartFile.class), eq(2L)))
                .thenThrow(new BusinessRuleException("Only PDF, JPG and PNG files are allowed"));

        MockMultipartFile txt = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/applications/1/documents")
                        .file(txt)
                        .param("documentTypeId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PDF, JPG and PNG files are allowed"));
    }

    @Test
    void upload_returns404_whenApplicationDoesNotExist() throws Exception {
        when(documentService.upload(eq(99L), any(MultipartFile.class), eq(2L)))
                .thenThrow(new ResourceNotFoundException("Application with id 99 not found"));

        mockMvc.perform(multipart("/api/applications/99/documents")
                        .file(pdf)
                        .param("documentTypeId", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Application with id 99 not found"));
    }

    @Test
    void upload_returns409_whenServiceReportsAConflict() throws Exception {
        when(documentService.upload(eq(1L), any(MultipartFile.class), eq(2L)))
                .thenThrow(new ConflictException("Application with id 1 is APPROVED and cannot accept documents"));

        mockMvc.perform(multipart("/api/applications/1/documents")
                        .file(pdf)
                        .param("documentTypeId", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---------- download ----------

    @Test
    void download_returnsTheFileWithItsNameAndType() throws Exception {
        // ByteArrayResource = a "file" that only exists in memory, enough for a test
        when(documentService.download(10L))
                .thenReturn(new DocumentFile("id-card.pdf", new ByteArrayResource("fake pdf content".getBytes())));

        mockMvc.perform(get("/api/documents/10/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("id-card.pdf")))
                .andExpect(content().bytes("fake pdf content".getBytes()));
    }

    @Test
    void download_returns404_whenDocumentDoesNotExist() throws Exception {
        when(documentService.download(99L)).thenThrow(new ResourceNotFoundException("Document with id 99 not found"));

        mockMvc.perform(get("/api/documents/99/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Document with id 99 not found"));
    }

    // ---------- delete ----------

    @Test
    void delete_returns204_whenDocumentIsDeleted() throws Exception {
        mockMvc.perform(delete("/api/documents/10"))
                .andExpect(status().isNoContent());

        verify(documentService).delete(10L);
    }

    @Test
    void delete_returns409_whenApplicationIsAlreadyDecided() throws Exception {
        doThrow(new ConflictException("Document with id 10 belongs to an application that is APPROVED and cannot be deleted"))
                .when(documentService).delete(10L);

        mockMvc.perform(delete("/api/documents/10"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns400_whenIdIsNegative() throws Exception {
        mockMvc.perform(delete("/api/documents/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));
    }
}
