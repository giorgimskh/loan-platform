package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.DocumentTypeRequest;
import ge.kiu.loanplatform.dto.DocumentTypeResponse;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.service.DocumentTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web layer test: starts only this controller + GlobalExceptionHandler (no database).
// The service is a mock, MockMvc sends fake HTTP requests.
@WebMvcTest(DocumentTypeController.class)
class DocumentTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentTypeService documentTypeService;

    @Test
    void getAll_returns200WithDocumentTypes() throws Exception {
        when(documentTypeService.getAll()).thenReturn(List.of(new DocumentTypeResponse(1L, "ID Card")));

        mockMvc.perform(get("/api/document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("ID Card"));
    }

    @Test
    void create_returns201_whenBodyIsValid() throws Exception {
        when(documentTypeService.create(new DocumentTypeRequest("Passport")))
                .thenReturn(new DocumentTypeResponse(2L, "Passport"));

        mockMvc.perform(post("/api/document-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Passport\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Passport"));
    }

    @Test
    void create_returns400_whenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/document-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));

        verifyNoInteractions(documentTypeService);
    }

    @Test
    void create_returns400_whenNameContainsHtml() throws Exception {
        mockMvc.perform(post("/api/document-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void create_returns409_whenNameAlreadyExists() throws Exception {
        when(documentTypeService.create(new DocumentTypeRequest("ID Card")))
                .thenThrow(new ConflictException("Document type with name ID Card already exists"));

        mockMvc.perform(post("/api/document-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ID Card\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Document type with name ID Card already exists"));
    }

    @Test
    void delete_returns204_whenDocumentTypeIsDeleted() throws Exception {
        mockMvc.perform(delete("/api/document-types/2"))
                .andExpect(status().isNoContent());

        verify(documentTypeService).delete(2L);
    }

    @Test
    void delete_returns409_whenAProductRequiresTheType() throws Exception {
        doThrow(new ConflictException("Document type with id 1 is required by a loan product and cannot be deleted"))
                .when(documentTypeService).delete(1L);

        mockMvc.perform(delete("/api/document-types/1"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns404_whenDocumentTypeDoesNotExist() throws Exception {
        doThrow(new ResourceNotFoundException("Document type with id 99 not found"))
                .when(documentTypeService).delete(99L);

        mockMvc.perform(delete("/api/document-types/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns400_whenIdIsNegative() throws Exception {
        mockMvc.perform(delete("/api/document-types/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));
    }
}
