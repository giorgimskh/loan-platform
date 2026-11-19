package ge.kiu.loanplatform.dto.document;

import ge.kiu.loanplatform.entity.document.DocumentType;

public record DocumentTypeResponse(
        Long id,
        String name
) {

    public static DocumentTypeResponse from(DocumentType documentType) {
        return new DocumentTypeResponse(documentType.getId(), documentType.getName());
    }
}
