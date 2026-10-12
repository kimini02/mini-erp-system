package com.minierp.backend.domain.user.dto;
import lombok.Getter;
@Getter
public class RegistrationResult {
    private final long id; private final String loginId; private boolean invitationSent;
    public RegistrationResult(long id,String loginId) { this.id=id;this.loginId=loginId; }
    public void invitationDelivered() { invitationSent=true; }
}
