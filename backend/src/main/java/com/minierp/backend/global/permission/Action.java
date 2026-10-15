package com.minierp.backend.global.permission;
public enum Action {
    VIEW("V"), CREATE("C"), UPDATE("U"), DELETE("D");
    public final String code;
    Action(String code) { this.code=code; }
}
