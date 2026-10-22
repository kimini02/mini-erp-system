package com.minierp.backend.domain.approval.handler;
import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.global.exception.*;
import java.time.*;
public final class BodyValues {
    private BodyValues() {}
    public static String text(JsonNode body,String key,int max) {
        if(body==null || !body.isObject() || !body.path(key).isTextual()) throw invalid();
        String value=body.path(key).textValue();
        if(value.isBlank() || value.length()>max) throw invalid();
        return value;
    }
    public static LocalDate date(JsonNode body,String key) {
        String value=text(body,key,10);
        try { if(!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw invalid(); return LocalDate.parse(value); }
        catch(DateTimeException ex) { throw invalid(); }
    }
    public static LocalTime time(JsonNode body,String key) {
        try { return LocalTime.parse(text(body,key,18)); }
        catch(DateTimeException ex) { throw invalid(); }
    }
    public static Long optionalLong(JsonNode body,String key) {
        var value=body.get(key);
        if(value==null || value.isNull()) return null;
        if(!value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
        return value.longValue();
    }
    public static BusinessException invalid() { return new BusinessException(ErrorCode.INVALID_INPUT_VALUE); }
}
