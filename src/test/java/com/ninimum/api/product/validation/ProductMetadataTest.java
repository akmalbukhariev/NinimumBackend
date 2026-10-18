package com.ninimum.api.product.validation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ProductMetadataTest {
 @Test void normalizesOptionalValuesAndPreservesZeroCost() {
  var fields=new HashMap<String,Object>();fields.put("cost_price","0.00");fields.put("min_stock_quantity","3");fields.put("shelf_life_days",365);fields.put("ingredients","  Milk  ");fields.put("age_range"," ");
  ProductMetadata.normalize(fields);assertEquals(new BigDecimal("0.00"),fields.get("cost_price"));assertEquals(3,fields.get("min_stock_quantity"));assertEquals("Milk",fields.get("ingredients"));assertNull(fields.get("age_range"));
 }
 @Test void rejectsBadCostsCountsAndOverlongMetadata() {
  for(String value:List.of("-1","1.001","NaN","9999999999999"))assertThrows(IllegalArgumentException.class,()->ProductMetadata.normalize(new HashMap<>(Map.of("cost_price",value))));
  for(String value:List.of("-1","1.5","2147483648"))assertThrows(IllegalArgumentException.class,()->ProductMetadata.normalize(new HashMap<>(Map.of("min_stock_quantity",value))));
  assertThrows(IllegalArgumentException.class,()->ProductMetadata.normalize(new HashMap<>(Map.of("shelf_life_days",0))));
  assertThrows(IllegalArgumentException.class,()->ProductMetadata.normalize(new HashMap<>(Map.of("age_range","x".repeat(161)))));
 }
}
