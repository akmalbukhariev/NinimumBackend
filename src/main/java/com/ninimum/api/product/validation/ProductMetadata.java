package com.ninimum.api.product.validation;
import com.ninimum.api.param.AddProductParam;
import java.util.*;
import java.math.*;

public final class ProductMetadata {
    private ProductMetadata() {}
    public static void normalize(Map<String,Object> data) {
        if(data.containsKey("cost_price")) {
            Object raw=data.get("cost_price");
            if(raw==null||raw.toString().isBlank())data.put("cost_price",null);
            else try {
                var value=new BigDecimal(raw.toString()).setScale(2,RoundingMode.UNNECESSARY);
                if(value.signum()<0||value.compareTo(new BigDecimal("999999999999.99"))>0)throw new ArithmeticException();
                data.put("cost_price",value);
            } catch(Exception ex){throw new IllegalArgumentException("Invalid cost price");}
        }
        for(String key:List.of("min_stock_quantity","shelf_life_days"))if(data.containsKey(key)) {
            Object raw=data.get(key);
            if(raw==null||raw.toString().isBlank())data.put(key,null);
            else try {
                int value=new BigDecimal(raw.toString()).intValueExact();
                if(value<0||(key.equals("shelf_life_days")&&(value==0||value>36500)))throw new ArithmeticException();
                data.put(key,value);
            }catch(Exception ex){throw new IllegalArgumentException("Invalid "+key);}
        }
        for(String key:List.of("storage_temperature","ingredients","age_range"))if(data.containsKey(key)) {
            String value=Objects.toString(data.get(key),"").trim();
            if(value.length()>(key.equals("ingredients")?4000:160))throw new IllegalArgumentException("Metadata too long");
            data.put(key,value.isEmpty()?null:value);
        }
    }
    public static void normalize(AddProductParam p) {
        var data=new HashMap<String,Object>();
        data.put("cost_price",p.getCost_price());data.put("min_stock_quantity",p.getMin_stock_quantity());data.put("shelf_life_days",p.getShelf_life_days());
        data.put("storage_temperature",p.getStorage_temperature());data.put("ingredients",p.getIngredients());data.put("age_range",p.getAge_range());
        normalize(data);
        p.setCost_price((BigDecimal)data.get("cost_price"));p.setMin_stock_quantity((Integer)data.get("min_stock_quantity"));p.setShelf_life_days((Integer)data.get("shelf_life_days"));
        p.setStorage_temperature((String)data.get("storage_temperature"));p.setIngredients((String)data.get("ingredients"));p.setAge_range((String)data.get("age_range"));
    }
}
