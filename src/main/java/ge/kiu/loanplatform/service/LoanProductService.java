package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.LoanProductRequest;
import ge.kiu.loanplatform.dto.LoanProductResponse;
import ge.kiu.loanplatform.entity.DocumentType;
import ge.kiu.loanplatform.entity.LoanProduct;
import ge.kiu.loanplatform.exception.BusinessRuleException;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.DocumentTypeRepository;
import ge.kiu.loanplatform.repository.LoanApplicationRepository;
import ge.kiu.loanplatform.repository.LoanProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoanProductService {

    private final LoanProductRepository loanProductRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    @Transactional(readOnly = true)
    public List<LoanProductResponse> getAll() {
        return loanProductRepository.findAll().stream()
                .map(LoanProductResponse::from)
                .toList();
    }

    @Transactional
    public LoanProductResponse create(LoanProductRequest request) {
        if (loanProductRepository.existsByName(request.name())) {
            throw new ConflictException("Loan product with name " + request.name() + " already exists");
        }
        validateRanges(request);

        LoanProduct product = new LoanProduct();
        copyFields(request, product);

        LoanProduct saved = loanProductRepository.save(product);
        return LoanProductResponse.from(saved);
    }

    @Transactional
    public LoanProductResponse update(Long id, LoanProductRequest request) {
        LoanProduct product = findProduct(id);

        // name must stay unique, but keeping the product's own name is fine
        boolean nameChanged = !product.getName().equals(request.name());
        if (nameChanged && loanProductRepository.existsByName(request.name())) {
            throw new ConflictException("Loan product with name " + request.name() + " already exists");
        }
        validateRanges(request);

        copyFields(request, product);
        // no save() needed: product is managed by JPA, changes are written automatically on commit
        return LoanProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        LoanProduct product = findProduct(id);
        if (loanApplicationRepository.existsByLoanProductId(id)) {
            throw new ConflictException("Loan product with id " + id + " has applications and cannot be deleted");
        }
        loanProductRepository.delete(product);
    }

    // ---------- helpers ----------

    private LoanProduct findProduct(Long id) {
        return loanProductRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product with id " + id + " not found"));
    }

    private void validateRanges(LoanProductRequest request) {
        if (request.minAmount().compareTo(request.maxAmount()) > 0) {
            throw new BusinessRuleException("Minimum amount cannot be greater than maximum amount");
        }
        if (request.minTermMonths() > request.maxTermMonths()) {
            throw new BusinessRuleException("Minimum term cannot be greater than maximum term");
        }
    }

    private void copyFields(LoanProductRequest request, LoanProduct product) {
        product.setName(request.name());
        product.setInterestRate(request.interestRate());
        product.setMinAmount(request.minAmount());
        product.setMaxAmount(request.maxAmount());
        product.setMinTermMonths(request.minTermMonths());
        product.setMaxTermMonths(request.maxTermMonths());
        product.setRequiredDocuments(findDocumentTypes(request.requiredDocumentTypeIds()));
    }

    private Set<DocumentType> findDocumentTypes(Set<Long> ids) {
        List<DocumentType> found = documentTypeRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new ResourceNotFoundException("One or more document types not found: " + ids);
        }
        return new HashSet<>(found);
    }
}
