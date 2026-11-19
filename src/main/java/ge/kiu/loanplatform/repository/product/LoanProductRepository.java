package ge.kiu.loanplatform.repository.product;

import ge.kiu.loanplatform.entity.product.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    boolean existsByName(String name);

    // Method-name query through the ManyToMany: "is there a product whose requiredDocuments contain this id?"
    boolean existsByRequiredDocumentsId(Long documentTypeId);
}
