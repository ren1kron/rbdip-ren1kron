package com.rbdip.bookstore.purchase;

/** Read-only purchase history available to other bookstore modules. */
public interface PurchaseHistory {

    boolean hasPurchased(String customerName, Long productId);
}
