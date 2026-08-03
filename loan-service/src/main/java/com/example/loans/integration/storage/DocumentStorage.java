package com.example.loans.integration.storage;
public interface DocumentStorage {
    record Metadata(String checksum,long size,String mediaType) {}
    void put(String key,byte[] content,Metadata metadata);
    Metadata head(String key);
    byte[] get(String key);
    void delete(String key);
}

