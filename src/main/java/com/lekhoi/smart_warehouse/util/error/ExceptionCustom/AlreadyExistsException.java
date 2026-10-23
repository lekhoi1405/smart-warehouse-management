package com.lekhoi.smart_warehouse.util.error.ExceptionCustom;

public class AlreadyExistsException extends RuntimeException {

    public AlreadyExistsException(String message) {
        super(message);
    }
}
