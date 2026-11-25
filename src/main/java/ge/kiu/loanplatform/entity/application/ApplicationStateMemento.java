package ge.kiu.loanplatform.entity.application;

// Memento pattern: a frozen snapshot of the part of a LoanApplication that a decision changes.
// It is a record, so a snapshot can never be changed after it is made.
// Not an @Entity: in the database a snapshot is stored as a StatusChange row (the history).
public record ApplicationStateMemento(ApplicationStatus status, String decisionComment) {
}
