package ge.kiu.loanplatform.dto.document;

import ge.kiu.loanplatform.dto.file.ApplicationPart;
import ge.kiu.loanplatform.dto.file.ApplicationPartVisitor;
import ge.kiu.loanplatform.entity.document.Document;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String originalFileName,
        Long documentTypeId,
        String documentTypeName,
        LocalDateTime uploadedAt
) implements ApplicationPart {

    // Visitor pattern: send the visitor to the method for this type of part (double dispatch)
    @Override
    public void accept(ApplicationPartVisitor visitor) {
        visitor.visitDocument(this);
    }

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
