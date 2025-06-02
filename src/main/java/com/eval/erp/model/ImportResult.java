package com.eval.erp.model;

import java.util.List;

public class ImportResult {
    private final boolean success;
    private final String message;
    private final List<String> errorLine;

    public ImportResult(boolean success, String message, List<String> results) {
        this.success = success;
        this.message = message;
        this.errorLine = results;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getErrorLine() {
        return errorLine;
    }
}
