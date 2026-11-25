package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.product.LoanProductRequest;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.service.document.DocumentTypeService;
import ge.kiu.loanplatform.service.product.LoanProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static ge.kiu.loanplatform.controller.web.WebTestData.documentTypes;
import static ge.kiu.loanplatform.controller.web.WebTestData.product;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

// Web layer test for LoanProductWebController. The services are mocks.
// The templates are really rendered, so these tests also catch mistakes in the HTML files.
@WebMvcTest(LoanProductWebController.class)
class LoanProductWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanProductService loanProductService;
    @MockitoBean
    private DocumentTypeService documentTypeService;

    @Test
    void products_listsProductsWithEditLinks() throws Exception {
        when(loanProductService.getAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/ui/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(content().string(containsString("Consumer Loan")))
                .andExpect(content().string(containsString("ID Card")))
                .andExpect(content().string(containsString("/ui/products/1/edit")));
    }

    @Test
    void newProduct_showsAnEmptyFormWithOneCheckboxPerDocumentType() throws Exception {
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Create product")))
                .andExpect(content().string(containsString("ID Card")))
                .andExpect(content().string(containsString("Bank Statement")))
                .andExpect(content().string(not(containsString("checked=\"checked\""))))
                .andExpect(content().string(containsString("action=\"/ui/products\"")));
    }

    @Test
    void createProduct_sendsAnEmptySet_whenNoCheckboxIsTicked() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class))).thenReturn(product());

        mockMvc.perform(productForm(post("/ui/products"), "20000"))
                .andExpect(redirectedUrl("/ui/products"))
                .andExpect(flash().attribute("message", "Consumer Loan was created"));

        verify(loanProductService).create(productRequest("20000", Set.of()));
    }

    @Test
    void createProduct_showsTheFormAgain_whenAValueIsNotANumber() throws Exception {
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(productForm(post("/ui/products"), "a lot", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Enter a valid value")))
                .andExpect(content().string(containsString("value=\"a lot\"")))
                // the ticked checkbox is still ticked, the other one is not
                .andExpect(content().string(containsString("value=\"2\" checked=\"checked\"")))
                .andExpect(content().string(not(containsString("value=\"1\" checked=\"checked\""))));

        verify(loanProductService, never()).create(any());
    }

    @Test
    void editProduct_showsTheFormFilledWithTheCurrentValues() throws Exception {
        when(loanProductService.getById(1L)).thenReturn(product());
        when(documentTypeService.getAll()).thenReturn(documentTypes());

        mockMvc.perform(get("/ui/products/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Edit product 1")))
                .andExpect(content().string(containsString("action=\"/ui/products/1\"")))
                .andExpect(content().string(containsString("value=\"Consumer Loan\"")))
                .andExpect(content().string(containsString("value=\"14.50\"")))
                // the product requires document types 1 and 2, but not 3
                .andExpect(content().string(containsString("value=\"1\" checked=\"checked\"")))
                .andExpect(content().string(containsString("value=\"2\" checked=\"checked\"")))
                .andExpect(content().string(not(containsString("value=\"3\" checked=\"checked\""))))
                .andExpect(content().string(containsString("Save changes")));
    }

    @Test
    void updateProduct_savesAndRedirects() throws Exception {
        when(loanProductService.update(eq(1L), any(LoanProductRequest.class))).thenReturn(product());

        mockMvc.perform(productForm(post("/ui/products/1"), "30000", "1", "2"))
                .andExpect(redirectedUrl("/ui/products"))
                .andExpect(flash().attribute("message", "Consumer Loan was saved"));

        verify(loanProductService).update(1L, productRequest("30000", Set.of(1L, 2L)));
    }

    @Test
    void updateProduct_keepsTheEditForm_whenTheServiceRefuses() throws Exception {
        when(loanProductService.update(eq(1L), any(LoanProductRequest.class)))
                .thenThrow(new BusinessRuleException("Minimum amount cannot be greater than maximum amount"));

        mockMvc.perform(productForm(post("/ui/products/1"), "100", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-form"))
                .andExpect(content().string(containsString("Minimum amount cannot be greater than maximum amount")))
                // still the edit form of product 1, with the typed value
                .andExpect(content().string(containsString("action=\"/ui/products/1\"")))
                .andExpect(content().string(containsString("value=\"100\"")));
    }

    @Test
    void deleteProduct_showsTheErrorPage_whenProductHasApplications() throws Exception {
        doThrow(new ConflictException("Loan product with id 1 has applications and cannot be deleted"))
                .when(loanProductService).delete(1L);

        mockMvc.perform(post("/ui/products/1/delete"))
                .andExpect(view().name("error-message"))
                .andExpect(content().string(containsString("has applications and cannot be deleted")));
    }

    // ---------- helpers ----------

    // the product form with every field filled; only maxAmount and the ticked documents differ between tests
    private MockHttpServletRequestBuilder productForm(MockHttpServletRequestBuilder request,
                                                      String maxAmount, String... tickedDocumentTypeIds) {
        request.param("name", "Consumer Loan")
                .param("interestRate", "14.5")
                .param("minAmount", "500")
                .param("maxAmount", maxAmount)
                .param("minTermMonths", "3")
                .param("maxTermMonths", "48")
                .param("_requiredDocumentTypeIds", "on"); // the hidden marker field of the checkbox group
        for (String id : tickedDocumentTypeIds) {
            request.param("requiredDocumentTypeIds", id); // one parameter per ticked checkbox
        }
        return request;
    }

    private LoanProductRequest productRequest(String maxAmount, Set<Long> documentTypeIds) {
        return new LoanProductRequest("Consumer Loan", new BigDecimal("14.5"), new BigDecimal("500"),
                new BigDecimal(maxAmount), 3, 48, documentTypeIds);
    }
}
