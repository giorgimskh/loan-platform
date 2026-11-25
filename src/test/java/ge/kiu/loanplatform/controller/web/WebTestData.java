package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Sample responses the website tests give to the mocked services.
// Shared by every *WebControllerTest, so each test class does not need its own copy.
final class WebTestData {

    private WebTestData() {
    }

    static PaymentCalculationResponse calculation() {
        return new PaymentCalculationResponse(new BigDecimal("5000"), new BigDecimal("14.50"), 12, RepaymentType.ANNUITY,
                new BigDecimal("450.11"), new BigDecimal("450.15"), new BigDecimal("5401.36"), new BigDecimal("401.36"));
    }

    // the same loan, calculated with decreasing payments (DifferentiatedRepaymentStrategy)
    static PaymentCalculationResponse decreasingCalculation() {
        return new PaymentCalculationResponse(new BigDecimal("5000"), new BigDecimal("14.50"), 12,
                RepaymentType.DIFFERENTIATED, new BigDecimal("477.09"), new BigDecimal("421.66"),
                new BigDecimal("5392.70"), new BigDecimal("392.70"));
    }

    static CustomerResponse customer() {
        return new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", "555123456");
    }

    static LoanProductResponse product() {
        return new LoanProductResponse(1L, "Consumer Loan", new BigDecimal("14.50"),
                new BigDecimal("500.00"), new BigDecimal("20000.00"), 3, 48, RepaymentType.ANNUITY,
                List.of(new DocumentTypeResponse(1L, "ID Card"), new DocumentTypeResponse(2L, "Salary Certificate")));
    }

    static List<DocumentTypeResponse> documentTypes() {
        return List.of(new DocumentTypeResponse(1L, "ID Card"),
                new DocumentTypeResponse(2L, "Salary Certificate"),
                new DocumentTypeResponse(3L, "Bank Statement"));
    }

    static LoanApplicationResponse application(ApplicationStatus status) {
        return new LoanApplicationResponse(7L, new BigDecimal("5000.00"), 12, status,
                LocalDateTime.of(2026, 3, 15, 10, 30),
                null, 1L, "Ana Beridze", 1L, "Consumer Loan",
                List.of(new DocumentResponse(10L, "id-card.pdf", 1L, "ID Card", LocalDateTime.now())));
    }

    static Page<LoanApplicationResponse> pageOf(LoanApplicationResponse... applications) {
        return new PageImpl<>(List.of(applications), PageRequest.of(0, 5), applications.length);
    }
}
