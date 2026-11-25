package ge.kiu.loanplatform.entity.product;

import ge.kiu.loanplatform.entity.document.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "loan_products")
@Getter
@Setter
@NoArgsConstructor
public class LoanProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(nullable = false)
    private Integer minTermMonths;

    @Column(nullable = false)
    private Integer maxTermMonths;

    // Strategy pattern: which RepaymentStrategy calculates the payments of this product's loans.
    // Stored as text ("ANNUITY"), like ApplicationStatus. @ColumnDefault: ddl-auto=update adds the new
    // column with this default, so products saved before the column existed become ANNUITY.
    // "= ANNUITY": a new product in Java also starts as ANNUITY.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'ANNUITY'")
    private RepaymentType repaymentType = RepaymentType.ANNUITY;

    @ManyToMany
    @JoinTable(
            name = "product_document_type",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "document_type_id")
    )
    private Set<DocumentType> requiredDocuments = new HashSet<>();
}
