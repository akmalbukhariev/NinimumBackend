package com.ninimum.api.order.service;

import com.ninimum.api.common.VersionResponseResult;

public class StockUnavailableResponse extends VersionResponseResult {
    private final int availableQuantity;
    private final long unavailableProductId;

    public StockUnavailableResponse(VersionResponseResult base, StockUnavailableException ex) {
        setResultCode("STOCK_UNAVAILABLE");
        setResultMsg(ex.getMessage());
        setApiVersion(base.getApiVersion());
        setWebVersion(base.getWebVersion());
        availableQuantity = ex.getAvailable();
        unavailableProductId = ex.getProductId();
    }

    public int getAvailableQuantity() { return availableQuantity; }
    public long getUnavailableProductId() { return unavailableProductId; }
}
