package ge.kiu.loanplatform.exception.handler;

import ge.kiu.loanplatform.dto.error.ErrorResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.customer.Customer;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ErrorCode;
import ge.kiu.loanplatform.exception.custom.InvalidStatusException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test for the status errors and for the error codes that the controller tests cannot easily reach
// (they need a broken body, a wrong HTTP method, a real database, ...).
// The handler is a plain object here: no Spring, we call its methods directly.
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/customers");

    @Test
    void invalidStatus_returnsItsOwnCodeAndTheStatusDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidStatus(
                new InvalidStatusException(ErrorCode.APPLICATION_NOT_MODIFIABLE, ApplicationStatus.APPROVED,
                        "Application with id 5 is APPROVED and cannot be modified."), request);

        assertCodeAndStatus(response, ErrorCode.APPLICATION_NOT_MODIFIABLE, HttpStatus.CONFLICT);
        assertThat(response.getBody().message())
                .isEqualTo("Application with id 5 is APPROVED and cannot be modified. An APPROVED application can only be rejected or have its decision undone.");
        assertThat(response.getBody().currentStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(response.getBody().allowedStatuses()).containsExactly(ApplicationStatus.REJECTED);
    }

    @Test
    void plainConflict_hasNoStatusDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleConflict(
                new ConflictException("Customer with email ana@mail.com already exists"), request);

        assertCodeAndStatus(response, ErrorCode.CONFLICT, HttpStatus.CONFLICT);
        assertThat(response.getBody().currentStatus()).isNull();
        assertThat(response.getBody().allowedStatuses()).isNull();
    }

    @Test
    void malformedBody_returnsMalformedRequestBodyCode() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad JSON", new MockHttpInputMessage(new byte[0])), request);

        assertCodeAndStatus(response, ErrorCode.MALFORMED_REQUEST_BODY, HttpStatus.BAD_REQUEST);
    }

    @Test
    void wrongHttpMethod_returnsMethodNotAllowedCode() {
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"), request);

        assertCodeAndStatus(response, ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void missingMultipartPart_returnsMissingParameterCode() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingPart(
                new MissingServletRequestPartException("file"), request);

        assertCodeAndStatus(response, ErrorCode.MISSING_PARAMETER, HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownSortField_returnsInvalidSortFieldCode() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidSort(
                new PropertyReferenceException("wrongField", TypeInformation.of(Customer.class), List.of()), request);

        assertCodeAndStatus(response, ErrorCode.INVALID_SORT_FIELD, HttpStatus.BAD_REQUEST);
    }

    @Test
    void fileAboveTheLimit_returnsFileTooLargeCode() {
        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(5L * 1024 * 1024), request);

        assertCodeAndStatus(response, ErrorCode.FILE_TOO_LARGE, HttpStatus.BAD_REQUEST);
    }

    @Test
    void databaseRefusal_returnsDataIntegrityViolationCode() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key"), request);

        assertCodeAndStatus(response, ErrorCode.DATA_INTEGRITY_VIOLATION, HttpStatus.CONFLICT);
    }

    private void assertCodeAndStatus(ResponseEntity<ErrorResponse> response, ErrorCode code, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().path()).isEqualTo("/api/customers");
    }
}
