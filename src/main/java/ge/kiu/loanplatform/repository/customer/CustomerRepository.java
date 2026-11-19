package ge.kiu.loanplatform.repository.customer;

import ge.kiu.loanplatform.entity.customer.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    boolean existsByPersonalNumber(String personalNumber);

    // @Query (JPQL): customers whose name, email or personal number contains the search text.
    // LOWER(...) on both sides makes the search ignore upper and lower case.
    // The Pageable adds LIMIT/OFFSET/ORDER BY, and Spring runs a second "count" query for the total.
    @Query("""
            SELECT c
            FROM Customer c
            WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR c.personalNumber LIKE CONCAT('%', :search, '%')
            """)
    Page<Customer> search(@Param("search") String search, Pageable pageable);
}
