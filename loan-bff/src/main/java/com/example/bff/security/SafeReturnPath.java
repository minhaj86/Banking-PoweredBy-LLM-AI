package com.example.bff.security;
public final class SafeReturnPath {
    private SafeReturnPath(){}
    public static String validate(String path) {
        if(path==null||path.isBlank())return "/app";
        if(path.length()>300||!path.matches("/(?:app|staff)(?:/[A-Za-z0-9_-]+)*")||path.contains(".."))throw new IllegalArgumentException("Invalid return path");
        return path;
    }
}

