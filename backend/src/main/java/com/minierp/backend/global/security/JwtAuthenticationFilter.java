package com.minierp.backend.global.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.response.ErrorResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
@Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final Clock clock;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String header=request.getHeader("Authorization");
        if(header!=null && header.startsWith("Bearer ")) {
            String token=header.substring(7);
            try {
                if(!jwtTokenProvider.validateToken(token)) throw new BusinessException(ErrorCode.INVALID_TOKEN);
                long id=Long.parseLong(jwtTokenProvider.getSubject(token));
                var user=users.findById(id).orElseThrow(()->new BusinessException(ErrorCode.TOKEN_REVOKED));
                if(!Objects.equals(jwtTokenProvider.getTokenVersion(token),user.getTokenVersion()) || !user.isActive())
                    throw new BusinessException(ErrorCode.TOKEN_REVOKED);
                SecurityContextHolder.getContext().setAuthentication(jwtTokenProvider.getAuthentication(token,user.getUserRole().name()));
            } catch(BusinessException ex) { error(response,ex.getErrorCode()); return; }
              catch(NumberFormatException ex) { error(response,ErrorCode.INVALID_TOKEN); return; }
        }
        chain.doFilter(request,response);
    }
    private void error(HttpServletResponse response,ErrorCode code) throws IOException {
        SecurityContextHolder.clearContext(); response.setStatus(code.getStatus().value());
        response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(),ErrorResponse.of(clock,code));
    }
}
