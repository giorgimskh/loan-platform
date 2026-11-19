package ge.kiu.loanplatform.repository.document;

import ge.kiu.loanplatform.entity.document.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    boolean existsByNameIgnoreCase(String name);
}
