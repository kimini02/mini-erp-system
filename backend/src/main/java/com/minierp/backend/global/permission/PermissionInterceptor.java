package com.minierp.backend.global.permission;

import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.exception.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {
    private final PermissionAccess permissions;
    private final UserRepository users;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            var user = users.findById(permissions.actor())
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            boolean allowed = ("GET".equals(request.getMethod()) && "/api/v1/me".equals(request.getRequestURI()))
                    || ("PATCH".equals(request.getMethod()) && "/api/v1/me/password".equals(request.getRequestURI()));
            if (user.isMustChangePassword() && !allowed) {
                throw new BusinessException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
            }
        }
        var required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequirePermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequirePermission.class);
        }
        if (required != null) {
            permissions.require(required.menu(), required.action());
        }
        return true;
    }
}
