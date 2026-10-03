package com.ninimum.api.order.service;

public class StockUnavailableException extends Exception {
    private final long productId;
    private final int available;

    public StockUnavailableException(long productId, int available) {
        super(available <= 0 ? "Uzr, bu mahsulot qolmagan."
                : "Omborda faqat " + available + " dona qolgan. Miqdorni kamaytiring.");
        this.productId = productId;
        this.available = Math.max(0, available);
    }

    public long getProductId() { return productId; }
    public int getAvailable() { return available; }
}
