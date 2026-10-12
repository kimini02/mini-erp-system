package com.minierp.backend.domain.user.service;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.service.*;
import com.minierp.backend.global.security.PasswordPolicy;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.UUID;
@Service @RequiredArgsConstructor @Slf4j
public class InvitationService {
    private final InvitationTokenStore tokens; private final MailService mail;
    private final UserRepository users; private final PasswordEncoder encoder;
    @Value("${app.mail.enabled:false}") private boolean enabled;
    @Value("${app.invitation.setup-url:http://localhost:5173/password/setup}") private String setupUrl;
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void send(EmployeeService.InvitationRequested event) {
        if(!enabled) return;
        try {
            String token=UUID.randomUUID().toString(); tokens.save(token,event.userId());
            String link=UriComponentsBuilder.fromUriString(setupUrl).queryParam("setupToken",token).build().encode().toUriString();
            mail.sendInvitationMail(event.email(),link); event.result().invitationDelivered();
        } catch(RuntimeException ex) {
            // Do not log the exception: SMTP/Redis exceptions can include message bodies or tokens.
            log.warn("초대 메일 발송 실패: 사원 등록은 유지됩니다. 재발송을 이용하세요.");
        }
    }
    @Transactional
    public void setup(String token,String password) {
        PasswordPolicy.validate(password);
        Long userId=tokens.consume(token);
        if(userId==null) throw new BusinessException(ErrorCode.SETUP_TOKEN_INVALID);
        var user=users.findByIdForUpdate(userId).orElseThrow(()->new BusinessException(ErrorCode.SETUP_TOKEN_INVALID));
        if(user.getUserPw()!=null) throw new BusinessException(ErrorCode.SETUP_TOKEN_INVALID);
        user.updatePassword(encoder.encode(password));
    }
}
