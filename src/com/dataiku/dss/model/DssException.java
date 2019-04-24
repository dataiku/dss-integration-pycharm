package com.dataiku.dss.model;

public class DssException extends RuntimeException {
    private final int code;

    public DssException(int code, String message) {
        super(message);
        this.code = code;
    }

    public DssException(int code, String message, Exception e) {
        super(message, e);
        this.code = code;
    }

    public DssException(int code, Exception e) {
        super(e);
        this.code = code;
    }

    public DssException(String message) {
        this(-1, message);
    }


    public DssException(String message, Exception e) {
        this(-1, message, e);
    }

    public DssException(Exception e) {
        this(-1, e);
    }

    public int getCode() {
        return code;
    }
}
