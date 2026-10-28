package com.minierp.backend.support;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.*;
public final class MariaDbPlan {
 public record Read(String table,String key,double loops,double rows){public double total(){return loops*rows;}}
 public static List<Read> analyze(NamedParameterJdbcTemplate jdbc,String sql,Map<String,?> args)throws Exception{
  String json=jdbc.queryForObject("ANALYZE FORMAT=JSON "+sql,args,String.class);List<Read> result=new ArrayList<>();walk(new ObjectMapper().readTree(json),result);return result;
 }
 private static void walk(JsonNode node,List<Read> rows){
  if(node.isObject() && node.has("table_name"))rows.add(new Read(node.path("table_name").asText(),node.path("key").asText("none"),node.path("r_loops").asDouble(),node.path("r_rows").asDouble()));
  if(node.isContainerNode())node.elements().forEachRemaining(n->walk(n,rows));
 }
 public static double median(NamedParameterJdbcTemplate jdbc,String sql,Map<String,?> args){double[] times=new double[9];for(int n=-3;n<9;n++){long start=System.nanoTime();jdbc.queryForList(sql,args);if(n>=0)times[n]=(System.nanoTime()-start)/1e6;}Arrays.sort(times);return times[4];}
}
