package com.ninimum.api.warehouse;

/** Shared input checks for other packages (finance): positive ids and photos with the receipt image limits. */
public final class WarehouseServiceAccess {
    private WarehouseServiceAccess() {}
    public static long positive(Object value) { return WarehouseService.positive(value); }
    public static byte[] image(Object value) { return ReceiptImage.decode(value); }
}
