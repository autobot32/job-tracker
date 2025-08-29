package com.atakant.emailtracker.utils;

public final class AppNorm {
    public static String normCompany(String s) {
        if (s == null) return "(unknown)";
        String x = s.toLowerCase()
                .replaceAll("\\b(inc\\.|llc|corp\\.|co\\.|ltd\\.)\\b", "")
                .replaceAll("[.,]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return x.isEmpty() ? "(unknown)" : x;
    }

    public static String normRole(String s) {
        if (s == null) return "(unknown)";
        String x = s.toLowerCase()
                .replaceAll("[.,]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return x.isEmpty() ? "(unknown)" : x;
    }

    public static String promoteStatus(String oldS, String newS) {
        if (oldS == null || oldS.isBlank()) return newS;
        if ("offer".equals(newS) || "rejected".equals(newS)) return newS;     // terminal wins
        if ("offer".equals(oldS) || "rejected".equals(oldS)) return oldS;     // keep terminal
        return rank(newS) > rank(oldS) ? newS : oldS;
    }

    private static int rank(String s) {
        return switch (s) {
            case "applied" -> 1;
            case "assessment" -> 2;
            case "interview" -> 3;
            case "offer" -> 4;
            case "rejected" -> 5;   // terminal
            default -> 0;            // "other"
        };
    }

    public static String mergeNotes(String existing, String incoming) {
        if (incoming == null || incoming.isBlank()) return existing;
        if (existing == null || existing.isBlank()) return incoming;
        if (existing.contains(incoming)) return existing;
        return existing + "\n" + incoming;
    }

    public static String mergeLocation(String existing, String incoming) {
        if (existing == null || "(unknown)".equals(existing)) return incoming;
        return existing;
    }
}
