package com.minierp.backend.domain.approval.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class GeneralRequestStore {
    private final SqlStore db;

    public int upsertBody(long id, String content, Long amount) {
        return db.update(
                "INSERT INTO general_requests(document_id,content,amount) VALUES(:id,:content,:amount) ON DUPLICATE KEY UPDATE content=VALUES(content),amount=VALUES(amount)",
                map("id", id, "content", content, "amount", amount));
    }

    public Map<String, Object> findBody(long id) {
        return db.one("SELECT content,amount FROM general_requests WHERE document_id=:id", map("id", id),
                ErrorCode.DOCUMENT_NOT_FOUND);
    }

    public int copyBody(long target, long source) {
        return db.update(
                "INSERT INTO general_requests(document_id,content,amount) SELECT :target,content,amount FROM general_requests WHERE document_id=:source",
                map("target", target, "source", source));
    }

    public int deleteBody(long id) {
        return db.update("DELETE FROM general_requests WHERE document_id=:id", map("id", id));
    }
}
