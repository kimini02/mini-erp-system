package com.minierp.backend.domain.org.dto;

import java.util.List;

public record OrgDepartmentTreeResponseDto(
        long id,
        String name,
        String code,
        OrgDepartmentResponseDto.Head head,
        Long directMemberCount,
        long totalMemberCount,
        List<OrgDepartmentTreeResponseDto> children) {
}
