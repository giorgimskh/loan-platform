package ge.kiu.loanplatform.repository;

import ge.kiu.loanplatform.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    boolean existsByName(String name);
}
