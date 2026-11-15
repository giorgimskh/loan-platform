package ge.kiu.loanplatform.repository;

import java.math.BigDecimal;

/**
 * Result row of LoanApplicationRepository.findTotalApprovedAmountPerProduct().
 * Spring Data fills it from the query aliases "productName" and "totalAmount".
 */
public interface ProductTotalAmount {

    String getProductName();

    BigDecimal getTotalAmount();
}
