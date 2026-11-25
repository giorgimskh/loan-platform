package ge.kiu.loanplatform.controller.api;

import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.product.LoanProductRequest;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import ge.kiu.loanplatform.service.product.LoanProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web layer test: starts only this controller + GlobalExceptionHandler (no database).
// The service is a mock, MockMvc sends fake HTTP requests.
@WebMvcTest(LoanProductController.class)
class LoanProductControllerTest {

    // a valid request body, reused by several tests
    private static final String VALID_BODY = """
            {"name":"Consumer Loan","interestRate":12.5,"minAmount":500,"maxAmount":20000,
             "minTermMonths":6,"maxTermMonths":60,"requiredDocumentTypeIds":[1]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanProductService loanProductService;
    @MockitoBean
    private LoanApplicationService loanApplicationService; // used by the payment calculator endpoint

    // ---------- payment calculator ----------

    @Test
    void calculate_returns200WithThePayment() throws Exception {
        when(loanApplicationService.calculate(1L, new BigDecimal("5000"), 12))
                .thenReturn(new PaymentCalculationResponse(new BigDecimal("5000"), new BigDecimal("14.50"), 12, RepaymentType.ANNUITY,
                        new BigDecimal("450.11"), new BigDecimal("450.15"), new BigDecimal("5401.36"), new BigDecimal("401.36")));

        mockMvc.perform(get("/api/products/1/calculate").param("amount", "5000").param("termMonths", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPayment").value(450.11))
                .andExpect(jsonPath("$.lastPayment").value(450.15))
                .andExpect(jsonPath("$.totalInterest").value(401.36));
    }

    @Test
    void calculate_returns400WithFieldErrors_whenParametersAreInvalid() throws Exception {
        mockMvc.perform(get("/api/products/1/calculate").param("amount", "0").param("termMonths", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").value("Amount must be greater than 0"))
                .andExpect(jsonPath("$.fieldErrors.termMonths").value("Term must be at most 360 months"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void calculate_returns400_whenAmountIsMissing() throws Exception {
        mockMvc.perform(get("/api/products/1/calculate").param("termMonths", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'amount'"));
    }

    @Test
    void calculate_returns400_whenAmountIsOutsideTheProductRange() throws Exception { // rule 1
        when(loanApplicationService.calculate(1L, new BigDecimal("99"), 12))
                .thenThrow(new BusinessRuleException("Amount must be between 500.00 and 20000.00 for product Consumer Loan"));

        mockMvc.perform(get("/api/products/1/calculate").param("amount", "99").param("termMonths", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must be between 500.00 and 20000.00 for product Consumer Loan"));
    }

    @Test
    void getById_returns200_whenProductExists() throws Exception {
        when(loanProductService.getById(1L)).thenReturn(response());

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Consumer Loan"));
    }

    // ---------- products ----------

    @Test
    void getAll_returns200WithProducts() throws Exception {
        when(loanProductService.getAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Consumer Loan"))
                .andExpect(jsonPath("$[0].requiredDocuments[0].name").value("ID Card"));
    }

    @Test
    void create_returns201_whenBodyIsValid() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Consumer Loan"))
                .andExpect(jsonPath("$.repaymentType").value("ANNUITY"));
    }

    @Test
    void create_passesTheRepaymentTypeToTheService() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Consumer Loan","interestRate":12.5,"minAmount":500,"maxAmount":20000,
                                 "minTermMonths":6,"maxTermMonths":60,"repaymentType":"DIFFERENTIATED",
                                 "requiredDocumentTypeIds":[1]}
                                """))
                .andExpect(status().isCreated());

        // Jackson turned the text "DIFFERENTIATED" into the enum value
        verify(loanProductService).create(argThat(request -> request.repaymentType() == RepaymentType.DIFFERENTIATED));
    }

    @Test
    void create_returns400_whenRepaymentTypeIsUnknown() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Consumer Loan","interestRate":12.5,"minAmount":500,"maxAmount":20000,
                                 "minTermMonths":6,"maxTermMonths":60,"repaymentType":"WEEKLY",
                                 "requiredDocumentTypeIds":[1]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));

        verifyNoInteractions(loanProductService);
    }

    @Test
    void create_returns400WithFieldErrors_whenBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","interestRate":150,"minAmount":0,"maxAmount":20000,
                                 "minTermMonths":0,"maxTermMonths":60}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.interestRate").value("Interest rate cannot be more than 100"))
                .andExpect(jsonPath("$.fieldErrors.minAmount").value("Minimum amount must be greater than 0"))
                .andExpect(jsonPath("$.fieldErrors.minTermMonths").value("Minimum term must be at least 1 month"))
                .andExpect(jsonPath("$.fieldErrors.requiredDocumentTypeIds").exists());

        // validation failed before the controller method ran, so the service was never called
        verifyNoInteractions(loanProductService);
    }

    @Test
    void create_returns400_whenNameContainsHtml() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"<b>Loan</b>","interestRate":12.5,"minAmount":500,"maxAmount":20000,
                                 "minTermMonths":6,"maxTermMonths":60,"requiredDocumentTypeIds":[1]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name")
                        .value("Name may contain only letters, digits, spaces and . , & ( ) ' -"));

        verifyNoInteractions(loanProductService);
    }

    @Test
    void create_returns400_whenAmountHasTooManyDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Consumer Loan","interestRate":12.5,"minAmount":500.999,"maxAmount":20000,
                                 "minTermMonths":6,"maxTermMonths":60,"requiredDocumentTypeIds":[1]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minAmount")
                        .value("Minimum amount can have at most 13 digits before and 2 after the decimal point"));

        verifyNoInteractions(loanProductService);
    }

    @Test
    void create_returns400_whenDocumentTypeIdIsNegative() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Consumer Loan","interestRate":12.5,"minAmount":500,"maxAmount":20000,
                                 "minTermMonths":6,"maxTermMonths":60,"requiredDocumentTypeIds":[-1]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['requiredDocumentTypeIds[]']")
                        .value("Document type id must be a positive number"));

        verifyNoInteractions(loanProductService);
    }

    @Test
    void create_returns400_whenMinAmountIsGreaterThanMaxAmount() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class)))
                .thenThrow(new BusinessRuleException("Minimum amount cannot be greater than maximum amount"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Minimum amount cannot be greater than maximum amount"));
    }

    @Test
    void create_returns409_whenNameAlreadyExists() throws Exception {
        when(loanProductService.create(any(LoanProductRequest.class)))
                .thenThrow(new ConflictException("Loan product with name Consumer Loan already exists"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Loan product with name Consumer Loan already exists"));
    }

    @Test
    void update_returns200_whenBodyIsValid() throws Exception {
        when(loanProductService.update(eq(1L), any(LoanProductRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Consumer Loan"));
    }

    @Test
    void update_returns400WithFieldErrors_whenBodyIsInvalid() throws Exception {
        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.interestRate").value("Interest rate is required"));

        verifyNoInteractions(loanProductService);
    }

    @Test
    void update_returns400_whenIdIsNegative() throws Exception {
        mockMvc.perform(put("/api/products/-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));

        verifyNoInteractions(loanProductService);
    }

    @Test
    void update_returns404_whenProductDoesNotExist() throws Exception {
        when(loanProductService.update(eq(99L), any(LoanProductRequest.class)))
                .thenThrow(new ResourceNotFoundException("Loan product with id 99 not found"));

        mockMvc.perform(put("/api/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Loan product with id 99 not found"));
    }

    @Test
    void delete_returns204_whenProductIsDeleted() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(loanProductService).delete(1L);
    }

    @Test
    void delete_returns400_whenIdIsNegative() throws Exception {
        mockMvc.perform(delete("/api/products/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));

        verifyNoInteractions(loanProductService);
    }

    @Test
    void delete_returns409_whenProductHasApplications() throws Exception {
        doThrow(new ConflictException("Loan product with id 1 has applications and cannot be deleted"))
                .when(loanProductService).delete(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Loan product with id 1 has applications and cannot be deleted"));
    }

    private LoanProductResponse response() {
        return new LoanProductResponse(1L, "Consumer Loan", new BigDecimal("12.5"),
                new BigDecimal("500"), new BigDecimal("20000"), 6, 60, RepaymentType.ANNUITY,
                List.of(new DocumentTypeResponse(1L, "ID Card")));
    }
}
