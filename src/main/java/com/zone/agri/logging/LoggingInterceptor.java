package com.zone.agri.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoggingInterceptor implements HandlerInterceptor {
    private static final String START_TIME_ATTR = LoggingInterceptor.class.getName() + ".startTime";
    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        if (logger.isDebugEnabled()) {
            logger.debug("Incoming request: method={}, uri={}",
                    request.getMethod(),
                    request.getRequestURI());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long durationMs = startTime == null ? -1L : System.currentTimeMillis() - startTime;

        if (ex != null || response.getStatus() >= 500) {
            logger.warn("Request failed: method={}, uri={}, status={}, durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    ex);
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Outgoing response: method={}, uri={}, status={}, durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs);
        }
    }

}

