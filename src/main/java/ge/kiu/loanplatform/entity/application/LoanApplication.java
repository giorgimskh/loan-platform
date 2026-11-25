package ge.kiu.loanplatform.entity.application;

import ge.kiu.loanplatform.entity.customer.Customer;
import ge.kiu.loanplatform.entity.document.Document;
import ge.kiu.loanplatform.entity.document.DocumentType;
import ge.kiu.loanplatform.entity.product.LoanProduct;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "loan_applications")
@Getter
@Setter
@NoArgsConstructor
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer termMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private String decisionComment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_product_id", nullable = false)
    private LoanProduct loanProduct;

    @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Document> documents = new ArrayList<>();

    // Every status this application has had, oldest first.
    // cascade = ALL: a StatusChange added to this list is saved together with the application.
    @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StatusChange> statusHistory = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Static factory method: the only way the application code creates a new application.
    // The name says what happens (a customer submits an application), and the rules of a new
    // application live here, in one place: it starts as SUBMITTED (rule 3) and its history
    // starts with that first line. The caller cannot forget either step.
    // (The no-argument constructor stays public because JPA needs it and the tests use it.)
    public static LoanApplication submit(Customer customer, LoanProduct loanProduct,
                                         BigDecimal amount, int termMonths) {
        LoanApplication application = new LoanApplication();
        application.customer = customer;
        application.loanProduct = loanProduct;
        application.amount = amount;
        application.termMonths = termMonths;
        application.status = ApplicationStatus.SUBMITTED; // Rule 3: new applications start as SUBMITTED
        application.statusHistory.add(StatusChange.from(application)); // the first line of its history
        return application;
    }

    // Memento pattern (this class is the originator): only the application itself decides
    // what goes into a snapshot of its state and how to go back to one.
    public ApplicationStateMemento createMemento() {
        return new ApplicationStateMemento(status, decisionComment);
    }

    public void restore(ApplicationStateMemento memento) {
        status = memento.status();
        decisionComment = memento.decisionComment();
    }

    // Rule 7: the document types the product requires that are not uploaded yet (empty list = nothing is missing).
    // It lives here because it only reads this application's own data (its product and its documents),
    // and three places need it: RequiredDocumentsRule, the missing-documents endpoint and undo.
    public List<DocumentType> findMissingDocumentTypes() {
        Set<Long> uploadedTypeIds = new HashSet<>();
        for (Document document : documents) {
            uploadedTypeIds.add(document.getDocumentType().getId());
        }

        List<DocumentType> missing = new ArrayList<>();
        for (DocumentType required : loanProduct.getRequiredDocuments()) {
            if (!uploadedTypeIds.contains(required.getId())) {
                missing.add(required);
            }
        }
        // requiredDocuments is a Set, which has no order; sorting keeps the answer the same every time
        missing.sort(Comparator.comparing(DocumentType::getName));
        return missing;
    }
}
