package ge.kiu.loanplatform.repository.document;

import ge.kiu.loanplatform.entity.document.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Method-name query: used before deleting a document type, so we can return a clear 409
    boolean existsByDocumentTypeId(Long documentTypeId);
}
