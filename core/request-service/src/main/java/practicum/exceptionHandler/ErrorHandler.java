package practicum.exceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import practicum.exception.ApiError;
import practicum.exception.ConflictException;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class ErrorHandler {
    @ExceptionHandler
    public ResponseEntity<ApiError> conflictHandler(final ConflictException e) {
        log.warn("Conflict: {}", e.getMessage());
        HttpStatus status = HttpStatus.CONFLICT;
        ApiError error = ApiError.builder(status, "Integrity constraint has been violated.")
                .message(e.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    private ResponseEntity<ApiError> buildResponseEntity(ApiError error, HttpStatus status) {
        return ResponseEntity.status(status).body(error);
    }

    private void logError(Exception e) {
        String template = """
                Message: {}
                Exception type: {}
                Stacktrace: {}
                """;

        log.error(template, e.getMessage(), e.getClass().getSimpleName(), getStackTrace(e));
    }

    private String getStackTrace(final Exception e) {
        String stackTrace = Arrays.stream(e.getStackTrace())
                .map(element -> "\tat " + element)
                .collect(Collectors.joining("\n"));

        return e + "\n" + stackTrace;
    }
}