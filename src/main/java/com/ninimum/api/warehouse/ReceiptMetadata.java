package com.ninimum.api.warehouse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.util.Map;

/** Incoming price is per piece; quantity is converted once into the stock's piece unit. */
public record ReceiptMetadata(BigDecimal unitPrice, LocalDate manufactured, LocalDate expiry,
                              String unit, long inputQuantity, long unitsPerBlock, long pieces, String batch) {
    public static ReceiptMetadata parse(Map<?,?> item, Clock clock) {
        try {
            BigDecimal price=new BigDecimal(String.valueOf(item.get("unit_price"))).setScale(2,RoundingMode.UNNECESSARY);
            if(price.signum()<=0||price.compareTo(new BigDecimal("999999999999.99"))>0)throw new IllegalArgumentException();
            LocalDate expiry=LocalDate.parse(String.valueOf(item.get("expiry_date")));
            LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Tashkent")));
            if(!expiry.isAfter(today))throw new WarehouseException("WAREHOUSE_EXPIRED_BATCH");
            String raw=WarehouseService.text(item.get("manufacture_date"),10,false);
            LocalDate manufactured=raw.isEmpty()?null:LocalDate.parse(raw);
            if(manufactured!=null&&(manufactured.isAfter(today)||!manufactured.isBefore(expiry)))throw new IllegalArgumentException();
            String unit=item.get("input_unit")==null?"PIECE":item.get("input_unit").toString();
            if(!unit.equals("PIECE")&&!unit.equals("BLOCK"))throw new IllegalArgumentException();
            long quantity=WarehouseService.positive(item.get("quantity"));
            long factor=unit.equals("BLOCK")?WarehouseService.positive(item.get("units_per_block")):1;
            long pieces=Math.multiplyExact(quantity,factor);
            if(pieces>Integer.MAX_VALUE)throw new IllegalArgumentException();
            return new ReceiptMetadata(price,manufactured,expiry,unit,quantity,factor,pieces,WarehouseService.text(item.get("batch_number"),100,false));
        } catch(WarehouseException ex) {throw ex;}
        catch(Exception ex) {throw new WarehouseException("WAREHOUSE_RECEIPT_METADATA_REQUIRED");}
    }
}
