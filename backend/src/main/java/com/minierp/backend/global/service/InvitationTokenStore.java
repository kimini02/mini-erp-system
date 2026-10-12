package com.minierp.backend.global.service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
@Service @RequiredArgsConstructor
public class InvitationTokenStore {
    private final StringRedisTemplate redis;
    public void save(String token,long userId) { redis.opsForValue().set("invitation:"+token,Long.toString(userId),Duration.ofHours(24)); }
    public Long consume(String token) {
        String id=redis.opsForValue().getAndDelete("invitation:"+token);
        return id==null?null:Long.valueOf(id);
    }
}
