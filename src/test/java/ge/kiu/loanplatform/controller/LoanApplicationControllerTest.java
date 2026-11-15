package ge.kiu.loanplatform.controller;

import ge.kiu.loanplatform.dto.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.RejectRequest;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.exception.BusinessRuleException;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.service.LoanApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(LoanApplicationController.class)
class LoanApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanApplicationService loanApplicationService;

    @Test
    void create_returns201_whenBodyIsValid() throws Exception {
        when(loanApplicationService.create(any(LoanApplicationRequest.class)))
                .thenReturn(response(ApplicationStatus.SUBMITTED, null));

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1,"loanProductId":1,"amount":5000,"termMonths":12}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.loanProductName").value("Consumer Loan"));
    }

    @Test
    void create_returns400WithFieldErrors_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerId").exists())
                .andExpect(jsonPath("$.fieldErrors.loanProductId").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.termMonths").exists());

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void create_returns400_whenAmountIsOutsideProductRange() throws Exception {
        when(loanApplicationService.create(any(LoanApplicationRequest.class)))
                .thenThrow(new BusinessRuleException("Amount must be between 500.00 and 20000.00 for product Consumer Loan"));

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1,"loanProductId":1,"amount":50,"termMonths":12}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must be between 500.00 and 20000.00 for product Consumer Loan"));
    }

    @Test
    void approve_returns200WithApprovedStatus() throws Exception {
        when(loanApplicationService.approve(1L)).thenReturn(response(ApplicationStatus.APPROVED, null));

        mockMvc.perform(put("/api/applications/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_returns409_whenApplicationIsNotSubmitted() throws Exception {
        when(loanApplicationService.approve(1L))
                .thenThrow(new ConflictException("Application with id 1 is APPROVED and cannot be approved"));

        mockMvc.perform(put("/api/applications/1/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void reject_returns200_whenReasonIsGiven() throws Exception {
        when(loanApplicationService.reject(eq(1L), any(RejectRequest.class)))
                .thenReturn(response(ApplicationStatus.REJECTED, "Income too low"));

        mockMvc.perform(put("/api/applications/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionComment":"Income too low"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionComment").value("Income too low"));
    }

    @Test
    void reject_returns400_whenReasonIsBlank() throws Exception { // rule 5
        mockMvc.perform(put("/api/applications/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionComment":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.decisionComment").value("Rejection reason is required"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void delete_returns204_whenApplicationIsCancelled() throws Exception {
        mockMvc.perform(delete("/api/applications/1"))
                .andExpect(status().isNoContent());

        verify(loanApplicationService).delete(1L);
    }

    @Test
    void delete_returns409_whenApplicationIsNotSubmitted() throws Exception {
        doThrow(new ConflictException("Application with id 1 is REJECTED and cannot be cancelled"))
                .when(loanApplicationService).delete(1L);

        mockMvc.perform(delete("/api/applications/1"))
                .andExpect(status().isConflict());
    }

    @Test
    void getAll_returns400_whenStatusIsNotAValidEnumValue() throws Exception {
        mockMvc.perform(get("/api/applications").param("status", "WRONG"))
                .andExpect(status().isBadRequest());
    }

    private LoanApplicationResponse response(ApplicationStatus status, String decisionComment) {
        return new LoanApplicationResponse(1L, new BigDecimal("5000"), 12, status, LocalDateTime.now(),
                decisionComment, 1L, "Ana Beridze", 1L, "Consumer Loan", List.of());
    }
}
