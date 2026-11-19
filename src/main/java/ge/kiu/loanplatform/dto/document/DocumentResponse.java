package ge.kiu.loanplatform.dto.document;

import ge.kiu.loanplatform.entity.document.Document;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String originalFileName,
        Long documentTypeId,
        String documentTypeName,
        LocalDateTime uploadedAt
) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFileName(),
                document.getDocumentType().getId(),
                document.getDocumentType().getName(),
                document.getUploadedAt()
        );
    }
}
