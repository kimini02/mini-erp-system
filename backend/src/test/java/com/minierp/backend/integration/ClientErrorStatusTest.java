package com.minierp.backend.integration;

import com.minierp.backend.global.exception.ErrorCode;
import com.minierp.backend.support.ApprovalApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class ClientErrorStatusTest extends ApprovalApiTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/users?page=x",
            "/api/v1/users?departmentId=x",
            "/api/v1/users/x",
            "/api/v1/users?includeSubDepartments=x",
            "/api/v1/users/login-id-preview?hireDate=not-a-date",
            "/api/v1/attendance/records/1/not-a-date",
            "/api/v1/projects?status=UNKNOWN"
    })
    void invalidTypedValuesReturnGenericBadRequest(String path) throws Exception {
        expectInputError(getAs(path, chiefToken), MethodArgumentTypeMismatchException.class);
    }

    @Test
    void missingRequiredParameterReturnsGenericBadRequest() throws Exception {
        expectInputError(getAs("/api/v1/calendar/events?year=2026", chiefToken),
                MissingServletRequestParameterException.class);
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        var action = mvc.perform(post("/api/v1/home").header("Authorization", "Bearer " + chiefToken));
        expectError(action, 405, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.",
                HttpRequestMethodNotSupportedException.class);
        HttpHeaders headers = new HttpHeaders();
        headers.put(HttpHeaders.ALLOW, action.andReturn().getResponse().getHeaders(HttpHeaders.ALLOW));
        assertThat(headers.getAllow()).contains(HttpMethod.GET);
    }

    @Test
    void unsupportedAcceptReturnsEmpty406WithoutExceptionHandlerWarnings(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + chiefToken)
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(""));
        assertThat(output.getAll()).doesNotContain("Failure in @ExceptionHandler", "ERROR");
    }

    @Test
    void largeRoleMemberPageReturnsEmptyContentWithoutOffsetOverflow() throws Exception {
        var first = data(getAs("/api/v1/roles/1/members?page=0&size=100", chiefToken));
        assertThat(first.path("content").size()).isPositive();
        var action = getAs("/api/v1/roles/1/members?page=30000000&size=100", chiefToken);
        System.out.printf("ROLE_MEMBER_LARGE_PAGE status=%d%n", action.andReturn().getResponse().getStatus());
        var page = data(action);
        assertThat(page.path("content").isArray()).isTrue();
        assertThat(page.path("content").size()).isZero();
        assertThat(page.path("page").path("number").asInt()).isEqualTo(30000000);
        assertThat(page.path("page").path("totalElements")).isEqualTo(first.path("page").path("totalElements"));
        assertThat(page.path("page").path("last").asBoolean()).isTrue();
    }

    @Test
    void unsupportedMediaTypeReturnsUnsupportedMediaType() throws Exception {
        expectError(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + chiefToken)
                        .contentType(MediaType.TEXT_PLAIN).content("{}")),
                415, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 본문 형식입니다.",
                HttpMediaTypeNotSupportedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{", "{\"leaderId\":\"not-a-number\"}" })
    void unreadableJsonReturnsGenericBadRequestWithoutInternalDetails(String body) throws Exception {
        expectInputError(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + chiefToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)),
                HttpMessageNotReadableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/v1/users?page=x", "/api/v1/calendar/events" })
    void unauthenticatedRequestsStillReturnUnauthorized(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({
            "PUT, /api/v1/projects/1",
            "PUT, /api/v1/tasks/1",
            "GET, /api/v1/projects/1/members",
            "POST, /api/v1/projects/1/members"
    })
    void removedV1MethodsStillReturnNotFound(String method, String path) throws Exception {
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + chiefToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private void expectInputError(ResultActions action, Class<? extends Exception> exceptionType) throws Exception {
        expectError(action, 400, "INVALID_INPUT_VALUE", ErrorCode.INVALID_INPUT_VALUE.getMessage(), exceptionType);
    }

    private void expectError(ResultActions action, int expectedStatus, String code, String message,
            Class<? extends Exception> exceptionType) throws Exception {
        var result = action.andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        System.out.printf("CLIENT_ERROR method=%s path=%s status=%d body=%s%n",
                result.getRequest().getMethod(), result.getRequest().getRequestURI(),
                result.getResponse().getStatus(), body);
        assertThat(result.getResolvedException()).isInstanceOf(exceptionType);
        action.andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message));
        assertThat(body).doesNotContain("Exception", "Failed to convert", "JSON parse error", "java.lang",
                "com.minierp", "org.springframework", "Cannot deserialize", "Unexpected end-of-input");
    }
}
