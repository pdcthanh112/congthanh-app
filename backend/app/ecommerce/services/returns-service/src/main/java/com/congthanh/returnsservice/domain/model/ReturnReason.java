package com.congthanh.returnsservice.domain.model;

public enum ReturnReason {
    DAMAGED(true), WRONG_ITEM(true), NOT_AS_DESCRIBED(true), NO_LONGER_NEEDED(false), OTHER(false);

    private final boolean sellerFault;

    ReturnReason(boolean sellerFault) {
        this.sellerFault = sellerFault;
    }

    public boolean sellerFault() {
        return sellerFault;
    }
}
