package ge.kiu.loanplatform.entity.application;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// One line of an application's history: "at this time the application got this status".
// A new row is added when an application is submitted, approved or rejected. Rows are never changed.
@Entity
@Table(name = "status_changes")
@Getter
@Setter
@NoArgsConstructor
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    // the rejection reason; null for SUBMITTED and APPROVED
    private String decisionComment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;

    @PrePersist
    void onCreate() {
        changedAt = LocalDateTime.now();
    }

    // Memento pattern: a history row is a saved snapshot; this turns it back into one
    public ApplicationStateMemento toMemento() {
        return new ApplicationStateMemento(status, decisionComment);
    }
}
