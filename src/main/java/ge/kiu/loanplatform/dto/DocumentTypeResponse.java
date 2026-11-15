package ge.kiu.loanplatform.dto;

import ge.kiu.loanplatform.entity.DocumentType;

public record DocumentTypeResponse(
        Long id,
        String name
) {

    public static DocumentTypeResponse from(DocumentType documentType) {
        return new DocumentTypeResponse(documentType.getId(), documentType.getName());
    }
}
