package com.example.loans.document;
public interface OcrAdapter {
    // A deployment may replace this bean with a bounded isolated OCR implementation.
    record Result(String text,boolean reliable) {}
    Result extract(byte[] content,String mediaType,int page);
}

