package com.securevoting.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Phase 13 — central exception handler.
 *
 * Maps every uncaught error to the styled error page (templates/error/generic.html)
 * with a generic message. Stack traces are LOGGED, never echoed to the user.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String VIEW = "error/generic";

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex,
                                 HttpServletRequest req, Model model) {
        log.warn("404 on {}: {}", req.getRequestURI(), ex.getMessage());
        addModel(model, 404, "Not found",
                ex.getMessage() != null ? ex.getMessage()
                        : "We couldn't find what you were looking for.");
        return VIEW;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoHandler(NoHandlerFoundException ex,
                                  HttpServletRequest req, Model model) {
        log.warn("404 unmapped: {}", req.getRequestURI());
        addModel(model, 404, "Page not found",
                "The page you're looking for doesn't exist.");
        return VIEW;
    }

    /**
     * Static-resource misses (favicon.ico, missing images, etc.). Return a clean
     * 404 with no body — never escalate to the generic 500 handler, never render
     * the error page (the browser doesn't want HTML for a missing /favicon.ico).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex,
                                                 HttpServletRequest req) {
        log.debug("404 static resource: {}", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidation(ValidationException ex,
                                   HttpServletRequest req, Model model) {
        log.info("400 on {}: {}", req.getRequestURI(), ex.getMessage());
        addModel(model, 400, "Invalid request",
                ex.getMessage() != null ? ex.getMessage() : "Your request couldn't be processed.");
        return VIEW;
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex,
                                     HttpServletRequest req, Model model) {
        AUDIT.info("ACCESS_DENIED path={} reason={}", req.getRequestURI(), ex.getMessage());
        addModel(model, 403, "Access denied",
                "You don't have permission to view this page.");
        return VIEW;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex,
                                HttpServletRequest req, Model model) {
        log.error("500 on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        AUDIT.info("UNEXPECTED_ERROR path={} type={}",
                req.getRequestURI(), ex.getClass().getSimpleName());
        addModel(model, 500, "Something went wrong",
                "An unexpected error occurred. Please try again or contact support.");
        return VIEW;
    }

    private static void addModel(Model model, int status, String title, String message) {
        model.addAttribute("status", status);
        model.addAttribute("title", title);
        model.addAttribute("message", message);
    }
}