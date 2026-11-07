package com.minierp.backend.domain.user.service;

import com.minierp.backend.domain.user.repository.EmployeeQueries;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLookupService {
    private final EmployeeQueries queries;
    public List<Map<String, Object>> lookup(String q, Long departmentId, int size) {
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String prefix = null;
        if (q != null && !q.isBlank()) {
            prefix = q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
        return queries.findActiveUsers(size, prefix, departmentId);
    }
}
