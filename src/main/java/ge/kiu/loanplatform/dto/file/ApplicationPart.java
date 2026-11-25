package ge.kiu.loanplatform.dto.file;

// Visitor pattern: the "element". Every kind of part an application file is made of
// (the application itself, the customer, the product, a document, a history line) implements this.
// A part only knows how to "let a visitor in"; what the visitor does with it is the visitor's business.
public interface ApplicationPart {

    // Double dispatch: the part calls the visit method meant for its own type,
    // e.g. CustomerResponse calls visitor.visitCustomer(this).
    void accept(ApplicationPartVisitor visitor);
}
