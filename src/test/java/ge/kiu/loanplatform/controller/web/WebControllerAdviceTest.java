package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.customer.CustomerService;
import ge.kiu.loanplatform.service.document.DocumentTypeService;
import ge.kiu.loanplatform.service.product.LoanProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Web layer test for WebControllerAdvice: a service refuses the request on a website page.
// With a page to go back to (Referer) the user returns there with the reason; without one the error page is shown.
// Two real controllers are loaded so the advice is tested the way it really runs.
@WebMvcTest({LoanApplicationWebController.class, LoanProductWebController.class})
class WebControllerAdviceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private LoanProductService loanProductService;
    @MockitoBean
    private LoanApplicationService loanApplicationService;
    @MockitoBean
    private DocumentTypeService documentTypeService;

    @Test
    void cancel_goesBackToTheApplicationWithTheReason_whenApplicationIsNotSubmitted() throws Exception { // rule 4
        doThrow(new ConflictException("Application with id 7 is APPROVED and cannot be cancelled"))
                .when(loanApplicationService).delete(7L);

        // the browser sends the page the user clicked on in the Referer header
        mockMvc.perform(post("/ui/applications/7/delete")
                        .header("Referer", "http://localhost:8080/ui/applications/7"))
                .andExpect(redirectedUrl("/ui/applications/7"))
                .andExpect(flash().attribute("error", "Application with id 7 is APPROVED and cannot be cancelled"));
    }

    @Test
    void deleteProduct_goesBackToTheProductListWithTheReason_whenProductHasApplications() throws Exception {
        doThrow(new ConflictException("Loan product with id 1 has applications and cannot be deleted"))
                .when(loanProductService).delete(1L);

        mockMvc.perform(post("/ui/products/1/delete")
                        .header("Referer", "http://localhost:8080/ui/products?sort=name"))
                .andExpect(redirectedUrl("/ui/products?sort=name"))
                .andExpect(flash().attribute("error", "Loan product with id 1 has applications and cannot be deleted"));
    }

    @Test
    void approve_doesNotRedirectToAnotherWebsite() throws Exception {
        when(loanApplicationService.approve(7L))
                .thenThrow(new ConflictException("Application with id 7 is APPROVED and cannot be approved"));

        // a Referer that is not one of our /ui pages is ignored: the error page is shown
        mockMvc.perform(post("/ui/applications/7/approve")
                        .header("Referer", "http://evil.example/steal"))
                .andExpect(status().isOk())
                .andExpect(view().name("error-message"));
    }

    @Test
    void approve_showsTheErrorPage_whenApplicationIsNotSubmitted() throws Exception { // rule 4
        when(loanApplicationService.approve(7L))
                .thenThrow(new ConflictException("Application with id 7 is APPROVED and cannot be approved"));

        mockMvc.perform(post("/ui/applications/7/approve"))
                .andExpect(view().name("error-message"))
                .andExpect(content().string(containsString("ConflictException")))
                .andExpect(content().string(containsString("Application with id 7 is APPROVED and cannot be approved")));
    }

    @Test
    void applicationDetail_showsTheErrorPage_whenApplicationDoesNotExist() throws Exception {
        when(loanApplicationService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Loan application with id 99 not found"));

        mockMvc.perform(get("/ui/applications/99"))
                .andExpect(view().name("error-message"))
                .andExpect(content().string(containsString("Loan application with id 99 not found")));
    }
}
