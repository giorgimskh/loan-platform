package ge.kiu.loanplatform.config;

import ge.kiu.loanplatform.entity.DocumentType;
import ge.kiu.loanplatform.entity.LoanProduct;
import ge.kiu.loanplatform.repository.DocumentTypeRepository;
import ge.kiu.loanplatform.repository.LoanProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Set;

// Inserts sample data on startup, only when the tables are still empty.
// The only document a loan needs here is the customer's ID. More types can be added
// later with POST /api/document-types.
@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner loadSampleData(DocumentTypeRepository documentTypeRepository,
                                            LoanProductRepository loanProductRepository) {
        return args -> {
            if (documentTypeRepository.count() > 0 || loanProductRepository.count() > 0) {
                return; // data already loaded on an earlier start
            }

            DocumentType idCard = documentTypeRepository.save(documentType("ID Card"));

            loanProductRepository.save(product("Consumer Loan", "14.50", "500.00", "20000.00", 3, 48,
                    Set.of(idCard)));
            loanProductRepository.save(product("Mortgage", "9.75", "20000.00", "500000.00", 60, 240,
                    Set.of(idCard)));
        };
    }

    private DocumentType documentType(String name) {
        DocumentType documentType = new DocumentType();
        documentType.setName(name);
        return documentType;
    }

    private LoanProduct product(String name, String interestRate, String minAmount, String maxAmount,
                                int minTermMonths, int maxTermMonths, Set<DocumentType> requiredDocuments) {
        LoanProduct product = new LoanProduct();
        product.setName(name);
        product.setInterestRate(new BigDecimal(interestRate));
        product.setMinAmount(new BigDecimal(minAmount));
        product.setMaxAmount(new BigDecimal(maxAmount));
        product.setMinTermMonths(minTermMonths);
        product.setMaxTermMonths(maxTermMonths);
        product.setRequiredDocuments(requiredDocuments);
        return product;
    }
}
