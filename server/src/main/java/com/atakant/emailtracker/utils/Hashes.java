package com.atakant.emailtracker.utils;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Hashes {
    private Hashes() {}
    public static String sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}