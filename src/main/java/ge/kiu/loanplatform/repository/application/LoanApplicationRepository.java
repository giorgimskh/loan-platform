package ge.kiu.loanplatform.repository.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    // Method-name query: Spring builds "WHERE status = ?" + LIMIT/OFFSET/ORDER BY from the Pageable
    Page<LoanApplication> findByStatus(ApplicationStatus status, Pageable pageable);

    // Method-name query: "WHERE customer.id = ? AND status = ?"
    List<LoanApplication> findByCustomerIdAndStatus(Long customerId, ApplicationStatus status);

    // Method-name query: used before deleting a product, so we can return a clear 409 instead of a DB error
    boolean existsByLoanProductId(Long loanProductId);

    // Method-name query: used before deleting a customer
    boolean existsByCustomerId(Long customerId);

    // Method-name query: "SELECT COUNT(*) ... WHERE status = ?", used by the statistics
    long countByStatus(ApplicationStatus status);

    // @Query (JPQL): the sum of all application amounts. COALESCE turns "no rows" (NULL) into 0.
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM LoanApplication a")
    BigDecimal sumAllAmounts();

    // @Query (JPQL): the sum of the amounts of all applications with one status
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM LoanApplication a WHERE a.status = :status")
    BigDecimal sumAmountsByStatus(@Param("status") ApplicationStatus status);

    // @Query (JPQL): total approved amount per product, using SUM + GROUP BY
    @Query("""
            SELECT p.name AS productName, SUM(a.amount) AS totalAmount
            FROM LoanApplication a
            JOIN a.loanProduct p
            WHERE a.status = ge.kiu.loanplatform.entity.application.ApplicationStatus.APPROVED
            GROUP BY p.name
            """)
    List<ProductTotalAmount> findTotalApprovedAmountPerProduct();

    // @Query (JPQL): applications of one product (by name) with amount above a limit, using JOIN
    @Query("""
            SELECT a
            FROM LoanApplication a
            JOIN a.loanProduct p
            WHERE p.name = :productName AND a.amount > :amount
            """)
    List<LoanApplication> findByProductNameAndAmountGreaterThan(@Param("productName") String productName,
                                                                @Param("amount") BigDecimal amount);
}
