package com.docsearch.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String docId) {
        super("Document not found: " + docId);
    }
}
