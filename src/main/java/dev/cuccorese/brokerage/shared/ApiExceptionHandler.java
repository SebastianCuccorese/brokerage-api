package dev.cuccorese.brokerage.shared;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The only place that turns an exception into an HTTP response, always an RFC 9457 problem
 * detail. Errors raised by the domain carry a stable {@code code}; every response, including
 * the ones Spring raises itself (wrong method, unsupported media type, unknown route), carries
 * the {@code requestId} that tags the request's log lines. Framework errors outside the ones
 * mapped here keep their status and have no {@code code}.
 *
 * An unexpected error never reaches the client with its message or stack: it is logged in full
 * and answered with a generic detail.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomain(DomainException ex, WebRequest request) {
        HttpStatus status = switch (ex) {
            case NotFoundException e -> HttpStatus.NOT_FOUND;
            case InvalidRequestException e -> HttpStatus.BAD_REQUEST;
            case ConflictException e -> HttpStatus.CONFLICT;
        };
        return respond(ex, status, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return respond(ex, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Unexpected error; quote the request id", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldProblem(fieldOf(error), error.getDefaultMessage()))
                .toList();
        return validationFailed(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldProblem(fieldOf(error, result.getMethodParameter()),
                                error.getDefaultMessage())))
                .toList();
        return validationFailed(ex, errors, headers, request);
    }

    /** The detail is generic on purpose: Jackson's message names classes and echoes the payload. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(ex, HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "The request body is missing or is not valid JSON for this endpoint", request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                problem.setProperty("requestId", requestId);
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationFailed(Exception ex, List<FieldProblem> errors, HttpHeaders headers,
            WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "The request has invalid fields");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private ResponseEntity<Object> respond(Exception ex, HttpStatus status, ErrorCode code, String detail,
            WebRequest request) {
        return handleExceptionInternal(ex, problem(status, code, detail), new HttpHeaders(), status, request);
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    private static String fieldOf(ObjectError error) {
        return error instanceof FieldError field ? field.getField() : error.getObjectName();
    }

    private static String fieldOf(MessageSourceResolvable error, MethodParameter parameter) {
        return error instanceof FieldError field ? field.getField() : parameter.getParameterName();
    }

    /** One invalid input: the field (or parameter) that failed and why. */
    record FieldProblem(String field, String message) {
    }
}
