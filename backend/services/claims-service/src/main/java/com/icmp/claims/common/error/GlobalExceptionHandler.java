package com.icmp.claims.common.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.icmp.claims.common.web.CorrelationIdFilter;

/** Maps every error to an RFC 7807 problem detail that carries the correlation id. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getTitle(), ex.getMessage(), null);
    }

    @ExceptionHandler(com.icmp.claims.claim.domain.ClaimRuleException.class)
    ResponseEntity<ProblemDetail> handleClaimRule(com.icmp.claims.claim.domain.ClaimRuleException ex) {
        return switch (ex.getKind()) {
            case INVALID_TRANSITION -> build(HttpStatus.CONFLICT, "Action not allowed in current status",
                    ex.getMessage(), null);
            case STALE_VERSION -> build(HttpStatus.CONFLICT, "Concurrent modification", ex.getMessage(), null);
            case DUPLICATE_CLAIM -> build(HttpStatus.CONFLICT, "Duplicate claim", ex.getMessage(), null);
            case UNSUPPORTED_FILE -> build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file", ex.getMessage(),
                    null);
            case FILE_TOO_LARGE -> build(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", ex.getMessage(), null);
            case NOT_PERMITTED -> build(HttpStatus.FORBIDDEN, "Not permitted", ex.getMessage(), null);
            case RULE_VIOLATION -> build(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated",
                    ex.getMessage(), null);
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "is invalid" : fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.", errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request", "The request could not be read: "
                + rootMessage(ex), null);
    }

    @ExceptionHandler(com.icmp.claims.document.application.DocumentStoragePort.StorageUnavailableException.class)
    ResponseEntity<ProblemDetail> handleStorageDown(RuntimeException ex) {
        log.warn("Document storage unavailable: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable",
                "Documents cannot be stored or opened right now. Please try again in a few minutes.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "File too large",
                "The file is larger than the 10 MB limit. Reduce the photo size or upload a smaller PDF.", null);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ResponseEntity<ProblemDetail> handleBadUpload(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed upload",
                "Send the file as multipart/form-data in a part named \"file\".", null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "Concurrent modification",
                "This record was changed by someone else. Reload and try again.", null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ProblemDetail> handleAccessDenied(Exception ex) {
        return build(HttpStatus.FORBIDDEN, "Access denied", "Your role is not allowed to perform this action.",
                null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Not found", "No endpoint " + ex.getResourcePath(), null);
    }

    /**
     * The browser went away while a response was being written (typically a photo still streaming when the user
     * left the page). Nothing can be sent back, so this is not an error.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void handleClientGone(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected before the response was complete: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "Something went wrong on our side. Quote the correlation id when reporting it.", null);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String title, String detail,
                                                List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        if (errors != null) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).body(problem);
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
