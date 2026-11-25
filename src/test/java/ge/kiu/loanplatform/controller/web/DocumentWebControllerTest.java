package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.service.document.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// Web layer test for DocumentWebController. The services are mocks.
// The templates are really rendered, so these tests also catch mistakes in the HTML files.
@WebMvcTest(DocumentWebController.class)
class DocumentWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void uploadDocument_redirectsBackToTheApplication() throws Exception {
        when(documentService.upload(eq(7L), any(MultipartFile.class), eq(1L)))
                .thenReturn(new DocumentResponse(10L, "id-card.pdf", 1L, "ID Card", LocalDateTime.now()));

        mockMvc.perform(multipart("/ui/applications/7/documents")
                        .file(new MockMultipartFile("file", "id-card.pdf", "application/pdf", "pdf".getBytes()))
                        .param("documentTypeId", "1"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "id-card.pdf was uploaded"));
    }

    @Test
    void deleteDocument_returnsToTheApplicationPage() throws Exception {
        mockMvc.perform(post("/ui/documents/10/delete").header("Referer", "http://localhost/ui/applications/7"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("message", "Document 10 was deleted"));

        verify(documentService).delete(10L);
    }
}
