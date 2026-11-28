package dev.transittracker;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Something a client asked for by ID (an agency's route or stop) doesn't exist: a ProblemDetail 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

	@RestControllerAdvice
	static class Handler {

		@ExceptionHandler(NotFoundException.class)
		ProblemDetail notFound(NotFoundException e) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		}

	}

}
