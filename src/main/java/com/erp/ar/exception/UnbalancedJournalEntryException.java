package com.erp.ar.exception;

public class UnbalancedJournalEntryException extends RuntimeException {
    public UnbalancedJournalEntryException(String message) {
        super(message);
    }
}
