package com.agrishrimp.agrishrimpbe.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agrishrimp.agrishrimpbe.utils.MessagesUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Logger.*;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger LOG
            = getLogger(String.valueOf(CustomAccessDeniedHandler.class));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Authentication auth
                = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            LOG.warning("User: " + auth.getName()
                    + " attempted to access the protected URL: "
                    + request.getRequestURI());
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        ErrorDetail errorVm = new ErrorDetail(HttpStatus.FORBIDDEN.toString(), "Access Denied",
                MessagesUtils.getMessage("FORBIDDEN"));
        response.getWriter().write(objectMapper.writeValueAsString(errorVm));
    }
}

