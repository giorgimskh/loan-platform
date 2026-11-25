package ge.kiu.loanplatform.entity.application;

import java.util.ArrayList;
import java.util.List;

// State pattern (enum version): every status is a state object that itself knows
// what an application in that status is allowed to do.
// The services ask the status ("can you be modified?") instead of checking
// "if (status == SUBMITTED)" in many places. A new status = one new constant here.
// Still stored as a plain string in the database (@Enumerated(EnumType.STRING)).
public enum ApplicationStatus {

    SUBMITTED {
        @Override
        public boolean canBeModified() {
            return true; // still waiting for a decision: can be modified, cancelled, documents deleted
        }

        @Override
        public boolean canMoveTo(ApplicationStatus target) {
            return target == APPROVED || target == REJECTED; // the first decision
        }

        @Override
        public boolean hasRepaymentSchedule() {
            return false;
        }

        @Override
        public boolean hasDecisionToUndo() {
            return false; // no decision was made yet
        }

        @Override
        public String describeAllowedActions() {
            return "A SUBMITTED application is waiting for a decision: it can be modified, cancelled, approved or rejected.";
        }
    },

    APPROVED {
        @Override
        public boolean canBeModified() {
            return false;
        }

        @Override
        public boolean canMoveTo(ApplicationStatus target) {
            return target == REJECTED; // rule 4: the decision can be changed, not repeated
        }

        @Override
        public boolean hasRepaymentSchedule() {
            return true; // only an approved application becomes a loan
        }

        @Override
        public boolean hasDecisionToUndo() {
            return true;
        }

        @Override
        public String describeAllowedActions() {
            return "An APPROVED application can only be rejected or have its decision undone.";
        }
    },

    REJECTED {
        @Override
        public boolean canBeModified() {
            return false;
        }

        @Override
        public boolean canMoveTo(ApplicationStatus target) {
            return target == APPROVED; // rule 4: the decision can be changed, not repeated
        }

        @Override
        public boolean hasRepaymentSchedule() {
            return false;
        }

        @Override
        public boolean hasDecisionToUndo() {
            return true;
        }

        @Override
        public String describeAllowedActions() {
            return "A REJECTED application can only be approved or have its decision undone.";
        }
    };

    // Rule 4, second half: only a SUBMITTED application can be modified, cancelled or lose a document
    public abstract boolean canBeModified();

    // Rule 4, first half: may an officer decision move the application from this status to target?
    public abstract boolean canMoveTo(ApplicationStatus target);

    // Only an APPROVED application has a repayment schedule
    public abstract boolean hasRepaymentSchedule();

    // Undo goes back to the state before the last decision, so there must have been one
    public abstract boolean hasDecisionToUndo();

    // One sentence for error messages: what an application in this status CAN still do,
    // so the user learns the way forward, not only what went wrong
    public abstract String describeAllowedActions();

    // Every status a decision can move to from this one (asks canMoveTo for each status).
    // Not abstract: it is the same for all statuses, so it is written once here.
    public List<ApplicationStatus> allowedNextStatuses() {
        List<ApplicationStatus> allowed = new ArrayList<>();
        for (ApplicationStatus target : values()) {
            if (canMoveTo(target)) {
                allowed.add(target);
            }
        }
        return allowed;
    }
}
