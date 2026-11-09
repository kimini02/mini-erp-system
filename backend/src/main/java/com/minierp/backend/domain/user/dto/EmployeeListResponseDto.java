package com.minierp.backend.domain.user.dto;

import java.util.List;
import java.util.Map;
import com.minierp.backend.global.response.PageResponseDto;

public record EmployeeListResponseDto(
        List<EmployeeDetailResponseDto> content,
        PageResponseDto.Page page,
        Map<String, Long> statusCounts) {
}
