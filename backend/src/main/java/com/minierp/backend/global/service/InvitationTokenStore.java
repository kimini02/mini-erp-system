package com.minierp.backend.global.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class InvitationTokenStore {
    private final StringRedisTemplate redis;
    private static final String TOKEN_PREFIX="invitation:";
    private static final String USER_PREFIX="invitation-user:";
    // Replacement and consumption must be atomic, including the per-user active pointer.
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
        local old = redis.call('GET', KEYS[1])
        if old then redis.call('DEL', ARGV[3] .. old) end
        redis.call('SET', KEYS[1], ARGV[1], 'EX', 86400)
        redis.call('SET', KEYS[2], ARGV[2], 'EX', 86400)
        return 1
        """, Long.class);
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
        local id = redis.call('GET', KEYS[1])
        if not id then return nil end
        local active = ARGV[2] .. id
        if redis.call('GET', active) ~= ARGV[1] then
            redis.call('DEL', KEYS[1])
            return nil
        end
        redis.call('DEL', KEYS[1], active)
        return id
        """, String.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
        local token = redis.call('GET', KEYS[1])
        if token then redis.call('DEL', ARGV[1] .. token) end
        return redis.call('DEL', KEYS[1])
        """, Long.class);

    public void save(String token,long userId) {
        redis.execute(SAVE,List.of(USER_PREFIX+userId,TOKEN_PREFIX+token),token,Long.toString(userId),TOKEN_PREFIX);
    }
    /** Only locates the user to lock; consume must still validate the active token afterward. */
    public Long owner(String token) {
        String id=redis.opsForValue().get(TOKEN_PREFIX+token);
        return id==null?null:Long.valueOf(id);
    }
    public Long consume(String token) {
        String id=redis.execute(CONSUME,List.of(TOKEN_PREFIX+token),token,USER_PREFIX);
        return id==null?null:Long.valueOf(id);
    }
    public void revoke(long userId) {
        redis.execute(REVOKE,List.of(USER_PREFIX+userId),TOKEN_PREFIX);
    }
}
