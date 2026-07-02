package lk.customs.rms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final Pattern ATTACHMENT_DOWNLOAD_PATH_WITH_ID =
            Pattern.compile("^/api/attachments/(\\d+)/download$");

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            log.debug("JWT authentication failed for {} {}: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String bearer = authHeader.substring(7);
            // A scoped download token (short-lived, carried in URLs) must never authenticate as a
            // general Bearer access token; it is valid only via the download_token path below.
            if (jwtService.isDownloadToken(bearer)) {
                return null;
            }
            return bearer;
        }

        String downloadToken = request.getParameter("download_token");
        if (downloadToken != null && !downloadToken.isBlank() && isAllowedDownloadTokenRequest(request, downloadToken.trim())) {
            return downloadToken.trim();
        }

        return null;
    }

    private boolean isAllowedDownloadTokenRequest(HttpServletRequest request, String token) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        // Query-token auth is intentionally limited to generated download URLs for browser previews.
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        String path = requestUri;
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            path = requestUri.substring(contextPath.length());
        }

        try {
            if ("/api/auth/me/profile-picture".equals(path)) {
                return jwtService.isDownloadTokenFor(token, "PROFILE_PICTURE", null);
            }

            Matcher attachmentMatcher = ATTACHMENT_DOWNLOAD_PATH_WITH_ID.matcher(path);
            if (attachmentMatcher.matches()) {
                return jwtService.isDownloadTokenFor(token, "ATTACHMENT", Long.parseLong(attachmentMatcher.group(1)));
            }
        } catch (Exception ex) {
            log.debug("Download token validation failed for {} {}: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ex.getMessage());
        }

        return false;
    }
}
