package ge.kiu.loanplatform.dto.product;

import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.entity.product.LoanProduct;

import java.math.BigDecimal;
import java.util.List;

public record LoanProductResponse(
        Long id,
        String name,
        BigDecimal interestRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer minTermMonths,
        Integer maxTermMonths,
        List<DocumentTypeResponse> requiredDocuments
) {

    public static LoanProductResponse from(LoanProduct product) {
        return new LoanProductResponse(
                product.getId(),
                product.getName(),
                product.getInterestRate(),
                product.getMinAmount(),
                product.getMaxAmount(),
                product.getMinTermMonths(),
                product.getMaxTermMonths(),
                product.getRequiredDocuments().stream()
                        .map(DocumentTypeResponse::from)
                        .toList()
        );
    }
}
