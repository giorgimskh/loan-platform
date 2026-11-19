package ge.kiu.loanplatform.dto.report;

import ge.kiu.loanplatform.repository.application.ProductTotalAmount;

import java.math.BigDecimal;

// One row of the "total approved amount per product" report
public record ProductTotalResponse(
        String productName,
        BigDecimal totalApprovedAmount
) {

    public static ProductTotalResponse from(ProductTotalAmount row) {
        return new ProductTotalResponse(row.getProductName(), row.getTotalAmount());
    }
}
