package ge.kiu.loanplatform.dto.report;

import java.math.BigDecimal;

// Numbers about all applications, shown on the overview page of the website
public record StatisticsResponse(
        long totalApplications,
        long submitted,
        long approved,
        long rejected,
        BigDecimal approvalRate,          // % of the decided applications that were approved
        BigDecimal averageAmount,         // average amount of all applications
        BigDecimal totalApprovedAmount    // sum of the amounts of all approved applications
) {
}
