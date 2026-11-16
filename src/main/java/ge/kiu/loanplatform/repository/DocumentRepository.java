package ge.kiu.loanplatform.repository;

import ge.kiu.loanplatform.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Method-name query: used before deleting a document type, so we can return a clear 409
    boolean existsByDocumentTypeId(Long documentTypeId);
}
