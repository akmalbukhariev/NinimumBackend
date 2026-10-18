package com.ninimum.api.warehouse;

import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/** Invoice photos stay in protected receipt data rather than the public product-image directory. */
final class ReceiptImage {
    static byte[] decode(Object value) {
        if(value==null||value.toString().isBlank())return null;
        try {
            String encoded=value.toString();
            if(encoded.length()>2800000)throw new IllegalArgumentException();
            byte[] bytes=Base64.getDecoder().decode(encoded);
            if(bytes.length>2*1024*1024)throw new IllegalArgumentException();
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext())throw new IllegalArgumentException();
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    if((long)reader.getWidth(0)*reader.getHeight(0)>16000000)throw new IllegalArgumentException();
                    var image=reader.read(0);var out=new ByteArrayOutputStream();
                    try {if(!ImageIO.write(image,"png",out)||out.size()>4*1024*1024)throw new IllegalArgumentException();}
                    finally{image.flush();}
                    return out.toByteArray();
                }finally{reader.dispose();}
            }
        }catch(Exception ex){throw new WarehouseException("WAREHOUSE_INVALID_INVOICE_IMAGE");}
    }
}
