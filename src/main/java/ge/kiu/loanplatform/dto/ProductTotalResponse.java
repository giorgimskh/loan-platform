package ge.kiu.loanplatform.dto;

import ge.kiu.loanplatform.repository.ProductTotalAmount;

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
