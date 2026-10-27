package com.creditsai.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception thrown by a controller into an RFC 9457 {@link ProblemDetail} response
 * whose {@code detail} the frontend can show to the user. Stack traces, SQL and class names are
 * never sent; unexpected errors are logged and answered with a generic message.
 *
 * <p>Standard Spring MVC errors (malformed JSON, wrong method, ...) are handled by the
 * {@link ResponseEntityExceptionHandler} base class.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String VALIDATION_FAILED = "Some fields are invalid. Please correct them and try again.";
    static final String ACCESS_DENIED = "You do not have permission to perform this action.";
    static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please try again later.";
    static final String UNSUPPORTED_PROPERTY = "The request refers to a field that is not supported.";

    /**
     * Answers a missing or archived record with 404.
     *
     * @param ex the exception carrying the user-readable message
     * @return the problem detail
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Answers a broken business rule with 409.
     *
     * @param ex the exception carrying the user-readable message
     * @return the problem detail
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Answers an unacceptable request parameter with 400.
     *
     * @param ex the exception carrying the user-readable message
     * @return the problem detail
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Answers a sort (or other property path) naming an unknown entity property with 400 instead
     * of 500. A safety net behind the controllers' sort allow-lists; the exception's own message
     * names internal types, so a generic text is sent.
     *
     * @param ex the exception thrown by Spring Data
     * @return the problem detail
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handlePropertyReference(PropertyReferenceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, UNSUPPORTED_PROPERTY);
    }

    /**
     * Answers a failed {@code @PreAuthorize} check with 403. Without this handler the catch-all
     * below would turn it into a 500.
     *
     * @param ex the access-denied exception from method security
     * @return the problem detail
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ACCESS_DENIED);
    }

    /**
     * Answers any other exception with 500 and a generic message, logging the stack trace so it
     * stays on the server.
     *
     * @param ex the unexpected exception
     * @return the problem detail
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR);
    }

    /**
     * Answers a failed {@code @Valid} request body with 400 and an {@code errors} map from field
     * name to message, so the frontend can show each message next to its field.
     *
     * @param ex the validation exception
     * @param headers the response headers
     * @param status the status chosen by Spring (400)
     * @param request the current request
     * @return the response with the problem detail as body
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).headers(headers).body(problem);
    }
}
