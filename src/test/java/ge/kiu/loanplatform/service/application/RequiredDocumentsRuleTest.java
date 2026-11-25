package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.entity.document.Document;
import ge.kiu.loanplatform.entity.document.DocumentType;
import ge.kiu.loanplatform.entity.product.LoanProduct;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test for rule 7 as an approval rule: no Spring, no mocks, just plain objects.
class RequiredDocumentsRuleTest {

    private final RequiredDocumentsRule rule = new RequiredDocumentsRule();

    private final DocumentType idCard = documentType(1L, "ID Card");
    private final DocumentType passport = documentType(2L, "Passport");

    @Test
    void findProblem_returnsNull_whenEveryRequiredDocumentIsUploaded() {
        LoanApplication application = application(Set.of(idCard));
        upload(application, idCard);

        assertThat(rule.findProblem(application)).isNull();
    }

    @Test
    void findProblem_namesTheMissingDocumentsInAlphabeticalOrder() {
        LoanApplication application = application(Set.of(passport, idCard)); // nothing uploaded

        assertThat(rule.findProblem(application)).isEqualTo("required documents are missing: ID Card, Passport");
    }

    private LoanApplication application(Set<DocumentType> requiredDocuments) {
        LoanProduct product = new LoanProduct();
        product.setRequiredDocuments(requiredDocuments);
        LoanApplication application = new LoanApplication();
        application.setLoanProduct(product);
        return application;
    }

    private void upload(LoanApplication application, DocumentType type) {
        Document document = new Document();
        document.setDocumentType(type);
        application.getDocuments().add(document);
    }

    private DocumentType documentType(Long id, String name) {
        DocumentType type = new DocumentType();
        type.setId(id);
        type.setName(name);
        return type;
    }
}
