package ge.kiu.loanplatform.controller.api;

import ge.kiu.loanplatform.dto.application.LoanApplicationRequest;
import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.LoanApplicationUpdateRequest;
import ge.kiu.loanplatform.dto.application.RejectRequest;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.calculation.RepaymentScheduleResponse;
import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.report.ProductTotalResponse;
import ge.kiu.loanplatform.dto.report.StatisticsResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
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
    void create_returns400_whenIdsAreNegative() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":-1,"loanProductId":0,"amount":5000,"termMonths":12}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerId").value("Customer id must be a positive number"))
                .andExpect(jsonPath("$.fieldErrors.loanProductId").value("Loan product id must be a positive number"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void create_returns400_whenAmountHasTooManyDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1,"loanProductId":1,"amount":5000.999,"termMonths":12}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount")
                        .value("Amount can have at most 13 digits before and 2 after the decimal point"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void reject_returns400_whenReasonContainsHtml() throws Exception {
        mockMvc.perform(put("/api/applications/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionComment":"<script>alert(1)</script>"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.decisionComment")
                        .value("Rejection reason must not contain < or > or control characters"));

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
    void update_returns200_whenBodyIsValid() throws Exception {
        when(loanApplicationService.update(eq(1L), any(LoanApplicationUpdateRequest.class)))
                .thenReturn(response(ApplicationStatus.SUBMITTED, null));

        mockMvc.perform(put("/api/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loanProductId":1,"amount":6000,"termMonths":24}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        verify(loanApplicationService).update(1L, new LoanApplicationUpdateRequest(1L, new BigDecimal("6000"), 24));
    }

    @Test
    void update_returns400WithFieldErrors_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(put("/api/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.loanProductId").value("Loan product id is required"))
                .andExpect(jsonPath("$.fieldErrors.amount").value("Amount is required"))
                .andExpect(jsonPath("$.fieldErrors.termMonths").value("Term is required"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void update_returns409_whenApplicationIsNotSubmitted() throws Exception { // rule 4
        when(loanApplicationService.update(eq(1L), any(LoanApplicationUpdateRequest.class)))
                .thenThrow(new ConflictException("Application with id 1 is APPROVED and cannot be modified"));

        mockMvc.perform(put("/api/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loanProductId":1,"amount":6000,"termMonths":24}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Application with id 1 is APPROVED and cannot be modified"));
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
    void undoDecision_returns200WithTheEarlierStatus() throws Exception { // memento pattern
        when(loanApplicationService.undoLastDecision(1L)).thenReturn(response(ApplicationStatus.SUBMITTED, null));

        mockMvc.perform(put("/api/applications/1/undo-decision"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void undoDecision_returns409_whenThereIsNoDecision() throws Exception {
        when(loanApplicationService.undoLastDecision(1L)).thenThrow(
                new ConflictException("Application with id 1 is SUBMITTED and has no decision to undo"));

        mockMvc.perform(put("/api/applications/1/undo-decision"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void undoDecision_returns400_whenIdIsNotPositive() throws Exception {
        mockMvc.perform(put("/api/applications/0/undo-decision"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(loanApplicationService);
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
    void reject_returns400_whenIdIsNegative() throws Exception {
        mockMvc.perform(put("/api/applications/-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionComment":"Income too low"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void delete_returns400_whenIdIsNegative() throws Exception {
        mockMvc.perform(delete("/api/applications/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));

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

    @Test
    void search_returns200WithMatchingApplications() throws Exception {
        when(loanApplicationService.searchByProductAndAmount("Consumer Loan", new BigDecimal("1000")))
                .thenReturn(List.of(response(ApplicationStatus.SUBMITTED, null)));

        mockMvc.perform(get("/api/applications/search")
                        .param("productName", "Consumer Loan")
                        .param("amountAbove", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loanProductName").value("Consumer Loan"))
                .andExpect(jsonPath("$[0].amount").value(5000));
    }

    @Test
    void search_returns400_whenAmountAboveIsMissing() throws Exception {
        mockMvc.perform(get("/api/applications/search").param("productName", "Consumer Loan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'amountAbove'"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void search_returns400_whenParametersAreInvalid() throws Exception {
        mockMvc.perform(get("/api/applications/search")
                        .param("productName", "   ")
                        .param("amountAbove", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productName").value("Product name is required"))
                .andExpect(jsonPath("$.fieldErrors.amountAbove").value("Amount cannot be negative"));

        verifyNoInteractions(loanApplicationService);
    }

    @Test
    void approvedTotals_returns200WithTotalPerProduct() throws Exception {
        when(loanApplicationService.getApprovedTotalsPerProduct())
                .thenReturn(List.of(new ProductTotalResponse("Consumer Loan", new BigDecimal("12000.00"))));

        mockMvc.perform(get("/api/applications/reports/approved-totals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Consumer Loan"))
                .andExpect(jsonPath("$[0].totalApprovedAmount").value(12000.00));
    }

    // ---------- statistics, history, missing documents, schedule ----------

    @Test
    void statistics_returns200WithTheNumbers() throws Exception {
        when(loanApplicationService.getStatistics()).thenReturn(new StatisticsResponse(
                4, 1, 2, 1, new BigDecimal("66.7"), new BigDecimal("4500.00"), new BigDecimal("12000.00")));

        mockMvc.perform(get("/api/applications/reports/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(4))
                .andExpect(jsonPath("$.approvalRate").value(66.7))
                .andExpect(jsonPath("$.totalApprovedAmount").value(12000.00));
    }

    @Test
    void history_returns200WithEveryStatusChange() throws Exception {
        when(loanApplicationService.getHistory(1L)).thenReturn(List.of(
                new StatusChangeResponse(ApplicationStatus.SUBMITTED, null, LocalDateTime.now()),
                new StatusChangeResponse(ApplicationStatus.REJECTED, "Income too low", LocalDateTime.now())));

        mockMvc.perform(get("/api/applications/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].decisionComment").value("Income too low"));
    }

    @Test
    void missingDocuments_returns200WithTheMissingTypes() throws Exception {
        when(loanApplicationService.getMissingDocuments(1L)).thenReturn(List.of(new DocumentTypeResponse(1L, "ID Card")));

        mockMvc.perform(get("/api/applications/1/missing-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ID Card"));
    }

    @Test
    void approve_returns409_whenARequiredDocumentIsMissing() throws Exception { // rule 7
        when(loanApplicationService.approve(1L)).thenThrow(new ConflictException(
                "Application with id 1 cannot be approved, required documents are missing: ID Card"));

        mockMvc.perform(put("/api/applications/1/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Application with id 1 cannot be approved, required documents are missing: ID Card"));
    }

    @Test
    void schedule_returns200WithSummaryAndPayments() throws Exception {
        PaymentCalculationResponse summary = new PaymentCalculationResponse(new BigDecimal("5000"),
                new BigDecimal("14.50"), 12, new BigDecimal("450.11"), new BigDecimal("5401.36"), new BigDecimal("401.36"));
        when(loanApplicationService.getSchedule(1L)).thenReturn(new RepaymentScheduleResponse(1L, summary,
                List.of(new ScheduleEntryResponse(1, new BigDecimal("450.11"), new BigDecimal("60.42"),
                        new BigDecimal("389.69"), new BigDecimal("4610.31")))));

        mockMvc.perform(get("/api/applications/1/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.monthlyPayment").value(450.11))
                .andExpect(jsonPath("$.payments[0].month").value(1))
                .andExpect(jsonPath("$.payments[0].remainingBalance").value(4610.31));
    }

    @Test
    void schedule_returns409_whenApplicationIsNotApproved() throws Exception {
        when(loanApplicationService.getSchedule(1L)).thenThrow(new ConflictException(
                "Application with id 1 is SUBMITTED; only an APPROVED application has a repayment schedule"));

        mockMvc.perform(get("/api/applications/1/schedule"))
                .andExpect(status().isConflict());
    }

    @Test
    void history_returns400_whenIdIsNegative() throws Exception {
        mockMvc.perform(get("/api/applications/-1/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Id must be a positive number"));
    }

    private LoanApplicationResponse response(ApplicationStatus status, String decisionComment) {
        return new LoanApplicationResponse(1L, new BigDecimal("5000"), 12, status, LocalDateTime.now(),
                decisionComment, 1L, "Ana Beridze", 1L, "Consumer Loan", List.of());
    }
}
