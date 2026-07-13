package com.kommhub.controller.web;

import com.kommhub.model.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class WebFallbackHandler {

    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api")) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "No endpoint at " + path);
        }
        return "redirect:/";
    }
}
