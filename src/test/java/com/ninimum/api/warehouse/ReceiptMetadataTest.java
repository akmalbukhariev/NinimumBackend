package com.ninimum.api.warehouse;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptMetadataTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-10-17T20:00:00Z"),ZoneOffset.UTC);
    Map<String,Object> item(){return new HashMap<>(Map.of("quantity",3,"unit_price","150.50","expiry_date","2026-10-19"));}
    @Test void blocksConvertToPiecesAndIncomingCostIsPerPiece() {
        var item=item();item.put("input_unit","BLOCK");item.put("units_per_block",12);
        var result=ReceiptMetadata.parse(item,clock);assertEquals(36,result.pieces());assertEquals("150.50",result.unitPrice().toString());
        item.put("units_per_block",Integer.MAX_VALUE);assertThrows(WarehouseException.class,()->ReceiptMetadata.parse(item,clock));
    }
    @Test void expiryUsesTashkentDayAndRejectsInvalidDatesPricesAndMissingFields() {
        var item=item();item.put("expiry_date","2026-10-18");assertEquals("WAREHOUSE_EXPIRED_BATCH",assertThrows(WarehouseException.class,()->ReceiptMetadata.parse(item,clock)).getMessage());
        for(String price:List.of("0","-1","1.001","NaN","")){var value=item();value.put("unit_price",price);assertThrows(WarehouseException.class,()->ReceiptMetadata.parse(value,clock));}
        for(String date:List.of("2026-02-30","","2026-10-17")){var value=item();value.put("expiry_date",date);assertThrows(WarehouseException.class,()->ReceiptMetadata.parse(value,clock));}
        var future=item();future.put("manufacture_date","2026-10-19");assertThrows(WarehouseException.class,()->ReceiptMetadata.parse(future,clock));
    }
    @Test void invoiceImagesAreDecodedReencodedAndInvalidBytesRejected() throws Exception {
        assertNull(ReceiptImage.decode(null));
        assertThrows(WarehouseException.class,()->ReceiptImage.decode(Base64.getEncoder().encodeToString("<script>".getBytes())));
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var stream=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"jpg",stream);
        byte[] stored=ReceiptImage.decode(Base64.getEncoder().encodeToString(stream.toByteArray()));assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(stored)));assertEquals((byte)137,stored[0]);
    }
}
