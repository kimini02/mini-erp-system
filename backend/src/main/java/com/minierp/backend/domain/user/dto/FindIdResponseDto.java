package com.minierp.backend.domain.user.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FindIdResponseDto {
    private final String message;
    public static FindIdResponseDto success() {
        return new FindIdResponseDto("입력하신 정보와 일치하는 계정이 있으면 이메일로 아이디를 보냈습니다.");
    }
}
