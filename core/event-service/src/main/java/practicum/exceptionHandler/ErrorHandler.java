package practicum.exceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import practicum.exception.ApiError;
import practicum.exception.ConflictException;
import practicum.exception.EventException;
import practicum.exception.NotFoundException;
import practicum.exception.ValidationException;
import practicum.exception.WrongTimeException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    public ResponseEntity<ApiError> handleException(final Exception exception) {
        logError(exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ApiError error = ApiError.builder(status, "Internal Server Error.")
                .message(exception.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(EventException.class)
    public ResponseEntity<ApiError> handleEventException(final EventException exception) {
        HttpStatus status = HttpStatus.CONFLICT;
        ApiError error = ApiError.builder(status, "Event exception.")
                .message(exception.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationExceptions(final MethodArgumentNotValidException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());

        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        response.put("errors", errors);
        return response;
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> conflictHandler(final ConflictException exception) {
        log.warn("Conflict: {}", exception.getMessage());
        HttpStatus status = HttpStatus.CONFLICT;
        ApiError error = ApiError.builder(status, "Integrity constraint has been violated.")
                .message(exception.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> handleValidationException(final ValidationException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError error = ApiError.builder(status, "validation error")
                .message(exception.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> handleNotFoundException(final NotFoundException exception) {
        log.warn("Not found: {}", exception.getMessage());
        HttpStatus status = HttpStatus.NOT_FOUND;
        ApiError error = ApiError.builder(status, "The required object was not found.")
                .message(exception.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiError> handleMissingPathVariable(final MissingPathVariableException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError error = ApiError.builder(status, "Missing path variable.")
                .message(exception.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParams(final MissingServletRequestParameterException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError error = ApiError.builder(status, "Missing params of method.")
                .message(exception.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    @ExceptionHandler(WrongTimeException.class)
    public ResponseEntity<ApiError> handleWrongTime(final WrongTimeException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError error = ApiError.builder(status, "Wrong time of event.")
                .message(exception.getMessage())
                .build();
        return buildResponseEntity(error, status);
    }

    private ResponseEntity<ApiError> buildResponseEntity(final ApiError error, final HttpStatus status) {
        return ResponseEntity.status(status).body(error);
    }

    private void logError(final Exception exception) {
        String template = """
                Message: {}
                Exception type: {}
                Stacktrace: {}
                """;

        log.error(template, exception.getMessage(), exception.getClass().getSimpleName(), getStackTrace(exception));
    }

    private String getStackTrace(final Exception exception) {
        String stackTrace = Arrays.stream(exception.getStackTrace())
                .map(element -> "\tat " + element)
                .collect(Collectors.joining("\n"));

        return exception + "\n" + stackTrace;
    }
}