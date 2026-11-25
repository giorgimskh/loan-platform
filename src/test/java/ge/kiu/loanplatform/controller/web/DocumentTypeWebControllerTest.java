package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.document.DocumentTypeRequest;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.service.document.DocumentTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static ge.kiu.loanplatform.controller.web.WebTestData.documentTypes;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Web layer test for DocumentTypeWebController. The services are mocks.
// The templates are really rendered, so these tests also catch mistakes in the HTML files.
@WebMvcTest(DocumentTypeWebController.class)
class DocumentTypeWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentTypeService documentTypeService;

    @Test
    void documentTypes_listsTheTypesWithTheAddForm() throws Exception {
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/document-types"))
                .andExpect(status().isOk())
                .andExpect(view().name("document-types"))
                .andExpect(content().string(containsString("Bank Statement")))
                .andExpect(content().string(containsString("action=\"/ui/document-types/3/delete\"")))
                .andExpect(content().string(containsString("Add document type")));
    }

    @Test
    void createDocumentType_savesAndRedirects() throws Exception {
        when(documentTypeService.create(new DocumentTypeRequest("Passport")))
                .thenReturn(new DocumentTypeResponse(4L, "Passport"));

        mockMvc.perform(post("/ui/document-types").param("name", "Passport"))
                .andExpect(redirectedUrl("/ui/document-types"))
                .andExpect(flash().attribute("message", "Passport was added"));
    }

    @Test
    void createDocumentType_showsTheFieldError_whenNameIsEmpty() throws Exception {
        mockMvc.perform(post("/ui/document-types").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("document-types"))
                .andExpect(content().string(containsString("Name is required")));

        verify(documentTypeService, never()).create(any());
    }

    @Test
    void deleteDocumentType_goesBackWithTheReason_whenAProductRequiresIt() throws Exception {
        doThrow(new ConflictException("Document type with id 1 is required by a loan product and cannot be deleted"))
                .when(documentTypeService).delete(1L);

        mockMvc.perform(post("/ui/document-types/1/delete").header("Referer", "http://localhost/ui/document-types"))
                .andExpect(redirectedUrl("/ui/document-types"))
                .andExpect(flash().attribute("error",
                        "Document type with id 1 is required by a loan product and cannot be deleted"));
    }
}
