package com.playbook.vt.concurrency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Caps requests in flight. With virtual threads Tomcat no longer runs out of threads, so without this a traffic spike
 * becomes an unbounded number of concurrent requests hammering every downstream. Excess load is shed with 503.
 */
public class AdmissionControlFilter extends OncePerRequestFilter {

    private final Semaphore inFlight;

    public AdmissionControlFilter(int maxInFlight) {
        this.inFlight = new Semaphore(maxInFlight);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!inFlight.tryAcquire()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setHeader("Retry-After", "1");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Service Unavailable","status":503,\
                    "detail":"Too many requests in flight"}""");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            inFlight.release();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator"); // never shed health checks
    }

    public int available() {
        return inFlight.availablePermits();
    }
}
