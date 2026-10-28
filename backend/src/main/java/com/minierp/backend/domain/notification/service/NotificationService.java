package com.minierp.backend.domain.notification.service;
import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.map;
@Service @RequiredArgsConstructor
public class NotificationService {
    private final SqlStore db;
    private final Clock clock;
    /** App notifications are DB state: never AFTER_COMMIT or REQUIRES_NEW. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void store(long user,long document,String type,String title) {
        db.update("INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,:doc,:type,:title,:link,:now)",
            map("user",user,"doc",document,"type",type,"title",title,"link","/approvals/"+document,"now",LocalDateTime.now(clock)));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void storeTask(long user,long task,String title,String link) {
        db.update("INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,NULL,'TASK_ASSIGNED',:title,:link,:now)",
            map("user",user,"title",title,"link",link,"now",LocalDateTime.now(clock)));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void storeTaskOverdue(long user,String title,String link) {
        db.update("INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,NULL,'TASK_OVERDUE',:title,:link,:now)",
            map("user",user,"title",title,"link",link,"now",LocalDateTime.now(clock)));
    }
}
