package com.minierp.backend.global.permission;

import java.lang.annotation.*;

@Target({
    ElementType.METHOD, ElementType.TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public
@interface RequirePermission {
    String menu();
    Action action();
}
