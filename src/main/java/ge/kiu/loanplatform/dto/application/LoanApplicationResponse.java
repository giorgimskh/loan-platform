package ge.kiu.loanplatform.dto.application;

import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LoanApplicationResponse(
        Long id,
        BigDecimal amount,
        Integer termMonths,
        ApplicationStatus status,
        LocalDateTime createdAt,
        String decisionComment,
        Long customerId,
        String customerFullName,
        Long loanProductId,
        String loanProductName,
        List<DocumentResponse> documents
) {

    public static LoanApplicationResponse from(LoanApplication application) {
        return new LoanApplicationResponse(
                application.getId(),
                application.getAmount(),
                application.getTermMonths(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getDecisionComment(),
                application.getCustomer().getId(),
                application.getCustomer().getFullName(),
                application.getLoanProduct().getId(),
                application.getLoanProduct().getName(),
                application.getDocuments().stream()
                        .map(DocumentResponse::from)
                        .toList()
        );
    }
}
