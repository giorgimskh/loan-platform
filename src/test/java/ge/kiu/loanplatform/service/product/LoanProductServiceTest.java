package ge.kiu.loanplatform.service.product;

import ge.kiu.loanplatform.dto.product.LoanProductRequest;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;
import ge.kiu.loanplatform.entity.product.LoanProduct;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.application.LoanApplicationRepository;
import ge.kiu.loanplatform.repository.document.DocumentTypeRepository;
import ge.kiu.loanplatform.repository.product.LoanProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanProductServiceTest {

    @Mock
    private LoanProductRepository loanProductRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @InjectMocks
    private LoanProductService loanProductService;

    @Test
    void create_savesProduct_whenRequestIsValid() {
        when(loanProductRepository.existsByName("Auto Loan")).thenReturn(false);
        when(documentTypeRepository.findAllById(Set.of())).thenReturn(List.of());
        when(loanProductRepository.save(any(LoanProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanProductResponse response = loanProductService.create(request("Auto Loan", "1000", "50000", 6, 60));

        assertThat(response.name()).isEqualTo("Auto Loan");
        assertThat(response.maxAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void create_throwsConflict_whenNameAlreadyExists() {
        when(loanProductRepository.existsByName("Auto Loan")).thenReturn(true);

        assertThatThrownBy(() -> loanProductService.create(request("Auto Loan", "1000", "50000", 6, 60)))
                .isInstanceOf(ConflictException.class);
        verify(loanProductRepository, never()).save(any());
    }

    @Test
    void create_throwsBusinessRule_whenMinAmountIsGreaterThanMaxAmount() {
        when(loanProductRepository.existsByName("Auto Loan")).thenReturn(false);

        assertThatThrownBy(() -> loanProductService.create(request("Auto Loan", "9000", "500", 6, 60)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void create_throwsBusinessRule_whenMinTermIsGreaterThanMaxTerm() {
        when(loanProductRepository.existsByName("Auto Loan")).thenReturn(false);

        assertThatThrownBy(() -> loanProductService.create(request("Auto Loan", "1000", "50000", 60, 6)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("term");
    }

    @Test
    void update_allowsKeepingTheSameName() {
        LoanProduct existing = new LoanProduct();
        existing.setId(3L);
        existing.setName("Auto Loan");
        when(loanProductRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(documentTypeRepository.findAllById(Set.of())).thenReturn(List.of());

        LoanProductResponse response = loanProductService.update(3L, request("Auto Loan", "1000", "60000", 6, 72));

        assertThat(response.maxTermMonths()).isEqualTo(72);
        // the name did not change, so the "already exists" check must not run
        verify(loanProductRepository, never()).existsByName(any());
    }

    @Test
    void getById_returnsTheProduct() {
        LoanProduct existing = new LoanProduct();
        existing.setId(3L);
        existing.setName("Auto Loan");
        when(loanProductRepository.findById(3L)).thenReturn(Optional.of(existing));

        LoanProductResponse response = loanProductService.getById(3L);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.name()).isEqualTo("Auto Loan");
    }

    @Test
    void getById_throwsNotFound_whenProductDoesNotExist() {
        when(loanProductRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanProductService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_throwsConflict_whenProductHasApplications() {
        LoanProduct existing = new LoanProduct();
        existing.setId(1L);
        when(loanProductRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(loanApplicationRepository.existsByLoanProductId(1L)).thenReturn(true);

        assertThatThrownBy(() -> loanProductService.delete(1L))
                .isInstanceOf(ConflictException.class);
        verify(loanProductRepository, never()).delete(any());
    }

    @Test
    void delete_throwsNotFound_whenProductDoesNotExist() {
        when(loanProductRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanProductService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private LoanProductRequest request(String name, String minAmount, String maxAmount, int minTerm, int maxTerm) {
        return new LoanProductRequest(name, new BigDecimal("11.5"), new BigDecimal(minAmount),
                new BigDecimal(maxAmount), minTerm, maxTerm, Set.of());
    }
}
