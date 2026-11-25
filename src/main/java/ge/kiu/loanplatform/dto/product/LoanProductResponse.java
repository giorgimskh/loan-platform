package ge.kiu.loanplatform.dto.product;

import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.file.ApplicationPart;
import ge.kiu.loanplatform.dto.file.ApplicationPartVisitor;
import ge.kiu.loanplatform.entity.product.LoanProduct;
import ge.kiu.loanplatform.entity.product.RepaymentType;

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
        RepaymentType repaymentType,
        List<DocumentTypeResponse> requiredDocuments
) implements ApplicationPart {

    // Visitor pattern: send the visitor to the method for this type of part (double dispatch)
    @Override
    public void accept(ApplicationPartVisitor visitor) {
        visitor.visitProduct(this);
    }

    public static LoanProductResponse from(LoanProduct product) {
        return new LoanProductResponse(
                product.getId(),
                product.getName(),
                product.getInterestRate(),
                product.getMinAmount(),
                product.getMaxAmount(),
                product.getMinTermMonths(),
                product.getMaxTermMonths(),
                product.getRepaymentType(),
                product.getRequiredDocuments().stream()
                        .map(DocumentTypeResponse::from)
                        .toList()
        );
    }
}
