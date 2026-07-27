package com.example.loans.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class Json {
    private final ObjectMapper mapper;
    public String write(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("JSON serialisation failed"); } }
    public <T> T read(String value,Class<T> type) { try { return mapper.readValue(value,type); } catch (Exception e) { throw new IllegalStateException("Stored JSON is invalid"); } }
}

