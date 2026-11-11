package com.minierp.backend.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.global.exception.GlobalExceptionHandler;
import com.minierp.backend.support.ApprovalApiTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ServerErrorLoggingTest extends ApprovalApiTestSupport {
    @MockitoSpyBean
    private NotificationService notifications;

    @Test
    void logsServerFailureWithStackButNoRequestSecretsAndDoesNotLogBadRequestAsError() throws Exception {
        long documentId = submittedLeave();
        String path = "/api/v1/approval-documents/" + documentId + "/approve";
        String query = "privateQuery=PRIVATE_QUERY_MARKER";
        String body = mapper.writeValueAsString(Map.of("comment", "PRIVATE_BODY_MARKER"));
        String failureMessage = "injected logging notification storage failure";
        NotificationService target = AopTestUtils.getUltimateTargetObject(notifications);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException(failureMessage);
        }).when(target).store(eq(employee.getId()), eq(documentId), eq("APPROVED"), anyString());

        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mvc.perform(post(path + "?" + query).header("Authorization", "Bearer " + leaderToken)
                            .header("X-Private-Header", "PRIVATE_HEADER_MARKER")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
            List<ILoggingEvent> serverEvents = List.copyOf(appender.list);
            appender.list.clear();
            getAs("/api/v1/users?page=x", chiefToken).andExpect(status().isBadRequest());
            long clientErrorCount = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).count();
            System.out.printf("SERVER_LOG_CAPTURE server_events=%d client_error_events=%d%n",
                    serverEvents.size(), clientErrorCount);
            assertThat(clientErrorCount).isZero();
            assertThat(serverEvents).hasSize(1);
            var event = serverEvents.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("POST", path)
                    .doesNotContain("Authorization", "Bearer", query, body);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo(failureMessage);
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
            String logs = serverEvents.stream().map(entry -> entry.getFormattedMessage() + "\n"
                            + ThrowableProxyUtil.asString(entry.getThrowableProxy()))
                    .collect(Collectors.joining("\n"));
            assertThat(logs).contains(failureMessage, "NotificationService", path)
                    .doesNotContain(query, "PRIVATE_QUERY_MARKER", "PRIVATE_BODY_MARKER", body,
                            "PRIVATE_HEADER_MARKER", "X-Private-Header", "Authorization:", "Authorization=",
                            "Bearer ", leaderToken);
            assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?",
                    String.class, documentId))
                    .isEqualTo("IN_PROGRESS");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            reset(target);
        }
    }
}
