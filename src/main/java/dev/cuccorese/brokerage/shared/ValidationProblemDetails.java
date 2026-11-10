package dev.cuccorese.brokerage.shared;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Spring's ProblemDetail responses, plus an {@code errors} property that names each invalid
 * field or parameter and why, so a client can tell which value to fix. Declaring this handler
 * replaces the one Spring Boot registers for {@code spring.mvc.problemdetails.enabled}.
 */
@RestControllerAdvice
class ValidationProblemDetails extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ex.getBody().setProperty("errors", messagesByName(ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(FieldError::getField))));
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ex.getBody().setProperty("errors", messagesByName(ex.getParameterValidationResults().stream()
                .collect(Collectors.toMap(result -> result.getMethodParameter().getParameterName(),
                        ParameterValidationResult::getResolvableErrors))));
        return super.handleHandlerMethodValidationException(ex, headers, status, request);
    }

    private static Map<String, String> messagesByName(Map<String, ? extends List<? extends MessageSourceResolvable>> errors) {
        return errors.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream()
                        .map(error -> Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"))
                        .sorted()
                        .collect(Collectors.joining("; ")),
                (first, second) -> first, TreeMap::new));
    }
}
