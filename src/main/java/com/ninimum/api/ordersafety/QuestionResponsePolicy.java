package com.ninimum.api.ordersafety;
import java.util.Map;
public final class QuestionResponsePolicy {
 private QuestionResponsePolicy() {}
 public static int minutes(Map<String,Object> settings,String kind) {
  Object value=settings.get("COMPLAINT".equals(kind)?"complaint_minutes":"question_minutes");
  if(value==null)value=settings.get("question_minutes");
  try {int minutes=new java.math.BigDecimal(String.valueOf(value)).intValueExact();if(minutes<1||minutes>43200)throw new ArithmeticException();return minutes;}
  catch(Exception ex){throw new IllegalArgumentException("Invalid response deadline");}
 }
}
