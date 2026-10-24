package com.minierp.backend.domain.approval.service;
import com.minierp.backend.domain.approval.repository.InboxRepository;
import com.minierp.backend.global.permission.PermissionAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class InboxService {
    private final InboxRepository repository;private final PermissionAccess access;
    public Map<String,Object> list(String box,String form,String q,int page,int size) {return repository.search(access.actor(),box,form,q,page,size);}
    public Map<String,Object> counts() {return repository.counts(access.actor());}
}
