package com.playbook.vt.api;

import com.playbook.vt.concurrency.BulkheadFullException;
import com.playbook.vt.concurrency.DeadlineExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

/** RFC 9457 problem details for the concurrency failure modes. */
@RestControllerAdvice
public class ErrorHandling {

    @ExceptionHandler(BulkheadFullException.class)
    ProblemDetail bulkheadFull(BulkheadFullException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        pd.setProperty("bulkhead", e.bulkhead());
        return pd;
    }

    @ExceptionHandler(DeadlineExceededException.class)
    ProblemDetail deadline(DeadlineExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    ProblemDetail notFound(HttpClientErrorException.NotFound e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Product not found");
    }

    @ExceptionHandler(InterruptedException.class)
    ProblemDetail interrupted(InterruptedException e) {
        Thread.currentThread().interrupt();
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted");
    }
}
