package com.minierp.backend.domain.leave.service;

import com.minierp.backend.domain.leave.repository.LeaveQueries;
import com.minierp.backend.domain.leave.repository.LeaveStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
public class LeaveGrantWork {
    private final LeaveQueries queries;
    private final LeaveStore store;
    private final Clock clock;
    public record Counts(int processed, int skipped, String message) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Counts run(LocalDate through) {
        int processed = 0;
        List<Long> missing = new ArrayList<>();
        List<Long> legacy = new ArrayList<>();
        var now = LocalDateTime.now(clock);
        for (var candidate : queries.findGrantCandidates()) {
            long id = number(candidate, "id");
            var user = store.lockGrantUser(id);
            if (!Set.of("ACTIVE", "ON_LEAVE").contains(user.get("status"))) {
                continue;
            }
            if (user.get("hire_date") == null) {
                missing.add(id);
                continue;
            }
            if (queries.countLegacyGrants(id, through.getYear()) > 0) {
                legacy.add(id);
                continue;
            }
            var existing = new HashSet<>(
                    queries.findGrantKeys(id).stream().map(r -> (String) r.get("grant_key")).toList());
            for (var grant : LeaveGrantRules.due(id, (LocalDate) user.get("hire_date"), through)) {
                if (existing.contains(grant.key())) {
                    continue;
                }
                processed += store.insertGrant(id, grant.date().getYear(), grant.days(), grant.key(),
                        grant.memo(), now);
            }
        }
        List<String> messages = new ArrayList<>();
        if (!missing.isEmpty()) {
            messages.add("입사일 없음: " + missing);
        }
        if (!legacy.isEmpty()) {
            messages.add("옛 부여 있음: " + legacy);
        }
        return new Counts(processed, missing.size() + legacy.size(),
                messages.isEmpty() ? null : String.join(" · ", messages));
    }
}
