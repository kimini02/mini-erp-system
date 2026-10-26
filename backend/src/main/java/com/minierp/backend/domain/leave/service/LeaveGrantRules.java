package com.minierp.backend.domain.leave.service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.*;
import java.util.*;
/** Company policy in M3 4.1, not a general statutory entitlement calculator. */
public final class LeaveGrantRules {
    private LeaveGrantRules() {}
    public record Grant(String key,LocalDate date,BigDecimal days,String memo) {}
    public static List<Grant> due(long user,LocalDate hire,LocalDate through) {
        List<Grant> result=new ArrayList<>();
        for(int k=1;k<=11;k++){var day=hire.plusMonths(k);if(!day.isAfter(through))result.add(new Grant("M-"+user+"-"+k,day,BigDecimal.ONE,"월차 자동 부여 ("+day+")"));}
        var first=hire.plusYears(1);
        if(!first.isAfter(through)) {
            long remaining=ChronoUnit.DAYS.between(first,LocalDate.of(first.getYear(),12,31))+1;
            var days=BigDecimal.valueOf(30*remaining).divide(BigDecimal.valueOf(first.lengthOfYear()),0,RoundingMode.CEILING).divide(BigDecimal.valueOf(2));
            result.add(new Grant("A-"+user,first,days,"입사 1주년 비례 부여 ("+first+")"));
        }
        for(int year=first.getYear()+1;year<=through.getYear();year++) {
            var day=LocalDate.of(year,1,1);if(day.isAfter(through))break;
            long years=ChronoUnit.YEARS.between(hire,day);long days=Math.min(25,15+(years-1)/2);
            result.add(new Grant("Y-"+user+"-"+year,day,BigDecimal.valueOf(days),"회계연도 연차 부여 ("+year+")"));
        }
        return result;
    }
}
