package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.report.ProductTotalResponse;
import ge.kiu.loanplatform.dto.report.StatisticsResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static ge.kiu.loanplatform.controller.web.WebTestData.application;
import static ge.kiu.loanplatform.controller.web.WebTestData.pageOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Web layer test for HomeWebController. The services are mocks.
// The templates are really rendered, so these tests also catch mistakes in the HTML files.
@WebMvcTest(HomeWebController.class)
class HomeWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanApplicationService loanApplicationService;

    // ---------- home page ----------
    @Test
    void home_showsWaitingApplicationsAndTheReport() throws Exception {
        when(loanApplicationService.getAll(eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(pageOf(application(ApplicationStatus.SUBMITTED)));
        when(loanApplicationService.getApprovedTotalsPerProduct())
                .thenReturn(List.of(new ProductTotalResponse("Consumer Loan", new BigDecimal("12000.00"))));
        when(loanApplicationService.getStatistics()).thenReturn(new StatisticsResponse(
                4, 1, 2, 1, new BigDecimal("66.7"), new BigDecimal("4500.00"), new BigDecimal("12000.00")));

        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("See all waiting applications")))
                .andExpect(content().string(containsString("12,000.00")))
                // the statistics: approval rate, average amount and the status chart
                .andExpect(content().string(containsString("Approval rate 66.7%")))
                .andExpect(content().string(containsString("Average amount 4,500.00")))
                .andExpect(content().string(containsString("1 submitted, 2 approved, 1 rejected")));
    }

    @Test
    void home_showsEmptyStates_whenThereIsNothingToShow() throws Exception {
        when(loanApplicationService.getAll(eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(pageOf());
        when(loanApplicationService.getApprovedTotalsPerProduct()).thenReturn(List.of());
        when(loanApplicationService.getStatistics()).thenReturn(new StatisticsResponse(
                0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No applications are waiting for a decision")))
                .andExpect(content().string(containsString("No applications yet. The chart appears with the first one.")))
                .andExpect(content().string(containsString("Nothing has been approved yet")));
    }
}
