package dev.coms4156.project.groupproject.config;

import dev.coms4156.project.groupproject.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Global exception handler for runtime and validation exceptions. */
@RestControllerAdvice
public class GlobalExceptionAdvice {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionAdvice.class);

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntime(RuntimeException ex) {
        log.error("Unhandled runtime exception", ex);
        return Result.fail(ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, IllegalArgumentException.class})
    public Result<Void> handleValidation(Exception ex) {
        return Result.fail("Invalid request: " + ex.getMessage());
    }
}
