package com.minierp.backend.global.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvitationTokenStore {
    private final StringRedisTemplate redis;
    private static final String TOKEN_PREFIX = "invitation:";
    private static final String EMAIL_PREFIX = "invitation-email:";
    private static final String USER_PREFIX = "invitation-user:";
    // Replacement and consumption must be atomic, including the per-user active pointer.
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
        local old = redis.call('GET', KEYS[1])
        if old then redis.call('DEL', ARGV[3] .. old, ARGV[4] .. old) end
        redis.call('SET', KEYS[1], ARGV[1], 'EX', 86400)
        redis.call('SET', KEYS[2], ARGV[2], 'EX', 86400)
        redis.call('SET', KEYS[3], ARGV[5], 'EX', 86400)
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
        if ARGV[3] ~= '' and redis.call('GET', KEYS[2]) ~= ARGV[3] then return nil end
        redis.call('DEL', KEYS[1], KEYS[2], active)
        return id
        """, String.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
        local token = redis.call('GET', KEYS[1])
        if token then
            if ARGV[3] ~= '' and redis.call('GET', ARGV[2] .. token) ~= ARGV[3] then return 0 end
            redis.call('DEL', ARGV[1] .. token, ARGV[2] .. token)
        end
        return redis.call('DEL', KEYS[1])
        """, Long.class);
    public void save(String token, long userId) {
        save(token, userId, "");
    }

    public void save(String token, long userId, String email) {
        redis.execute(SAVE, List.of(USER_PREFIX + userId, TOKEN_PREFIX + token, EMAIL_PREFIX + token), token,
                Long.toString(userId), TOKEN_PREFIX, EMAIL_PREFIX, email);
    }

    /** Only locates the user to lock; consume must still validate the active token afterward. */
    public Long owner(String token) {
        String id = redis.opsForValue().get(TOKEN_PREFIX + token);
        return id == null ? null : Long.valueOf(id);
    }

    public Long consume(String token) {
        return consume(token, "");
    }

    public Long consume(String token, String email) {
        String id = redis.execute(CONSUME, List.of(TOKEN_PREFIX + token, EMAIL_PREFIX + token), token, USER_PREFIX,
                email);
        return id == null ? null : Long.valueOf(id);
    }

    // A late callback must not delete a new invitation issued for the new address.
    public void revokeForEmail(long userId, String email) {
        redis.execute(REVOKE, List.of(USER_PREFIX + userId), TOKEN_PREFIX, EMAIL_PREFIX, email);
    }

    public void revoke(long userId) {
        redis.execute(REVOKE, List.of(USER_PREFIX + userId), TOKEN_PREFIX, EMAIL_PREFIX, "");
    }
}
