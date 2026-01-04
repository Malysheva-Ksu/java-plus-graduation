package practicum.exception.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import practicum.exception.*;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class ErrorHandler {

    private void logError(Throwable e) {
        log.error("Error occurred: {}", e.getMessage(), e);
    }

    private ResponseEntity<ApiError> buildResponseEntity(ApiError error, HttpStatus status) {
        return new ResponseEntity<>(error, status);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParams(final MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError error = ApiError.builder(status, "Incorrectly made request.")
                .message(ex.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(final ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError error = ApiError.builder(status, "Incorrectly made request.")
                .message(e.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(final MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ApiError error = ApiError.builder(status, "Incorrectly made request.")
                .message(message)
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFoundException(final NotFoundException e) {
        log.warn("Not found: {}", e.getMessage());
        HttpStatus status = HttpStatus.NOT_FOUND;
        ApiError error = ApiError.builder(status, "The required object was not found.")
                .message(e.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflictHandler(final ConflictException e) {
        log.warn("Conflict: {}", e.getMessage());
        HttpStatus status = HttpStatus.CONFLICT;
        ApiError error = ApiError.builder(status, "Integrity constraint has been violated.")
                .message(e.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidationException(final ValidationException e) {
        log.warn("Validation: {}", e.getMessage());HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError error = ApiError.builder(status, "Incorrectly made request.")
                .message(e.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(final Exception e) {
        logError(e);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ApiError error = ApiError.builder(status, "Internal Server Error.")
                .message(e.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }
}