package com.minierp.backend.domain.user.service;

import com.minierp.backend.domain.user.repository.EmployeeQueries;
import com.minierp.backend.domain.user.dto.EmployeeLookupResponseDto;
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
    public List<EmployeeLookupResponseDto> getUsers(String query, Long departmentId, int size) {
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String prefix = null;
        if (query != null && !query.isBlank()) {
            prefix = query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
        return queries.findActiveUsers(size, prefix, departmentId).stream()
                .map(EmployeeLookupResponseDto::from)
                .toList();
    }
}
