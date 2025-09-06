package com.atakant.emailtracker.utils;

import java.util.Locale;

public final class AppNorm {
    private AppNorm() {}

    private static final java.util.regex.Pattern YEAR = java.util.regex.Pattern.compile("\\b20\\d{2}\\b");
    private static final java.util.Set<String> ROLE_STOPWORDS = java.util.Set.of(
            // keep "intern", drop only these:
            "internship", "program",
            // (optional) season noise — remove if you want to keep seasons
            "summer", "fall", "spring", "winter"
    );


    public static String normCompany(String s) {
        if (s == null) return "(unknown)";
        String x = s.toLowerCase(Locale.ROOT)
                // drop common suffixes
                .replaceAll("\\b(inc\\.?|llc|corp\\.?|co\\.?|ltd\\.?|plc|corporation|company)\\b", "")
                // collapse punctuation to space
                .replaceAll("[^a-z0-9&\\s]", " ")
                // collapse spaces
                .replaceAll("\\s+", " ")
                .trim();
        return x.isEmpty() ? "(unknown)" : x;
    }

    public static String normRole(String s) {
        if (s == null) return "(unknown)";
        String x = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")                 // strip accents
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")           // punctuation → space
                .replaceAll("\\s+", " ")
                .trim();

        // drop years like 2025, 2026, ...
        x = YEAR.matcher(x).replaceAll(" ");

        // drop stopwords but KEEP "intern"
        for (String w : ROLE_STOPWORDS) {
            x = x.replaceAll("\\b" + java.util.regex.Pattern.quote(w) + "\\b", " ");
        }

        x = x.replaceAll("\\s+", " ").trim();
        if (x.isEmpty()) return "(unknown)";

        // word-sort to collapse "software consulting intern" == "consulting intern software"
        String[] words = x.split(" ");
        java.util.Arrays.sort(words);

        // de-dup while preserving order after sort
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>(java.util.Arrays.asList(words));
        return String.join(" ", uniq);
    }

    public static String normLocation(String s) {
        if (s == null) return "(unknown)";
        String x = s.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        if (x.matches("^(remote|virtual|us remote|remote - us|united states \\(remote\\))$")) return "remote";
        return x.isEmpty() ? "(unknown)" : x;
    }

    public static String promoteStatus(String oldS, String newS) {
        if (oldS == null || oldS.isBlank()) return newS;
        if ("offer".equals(newS) || "rejected".equals(newS)) return newS; // terminal wins
        if ("offer".equals(oldS) || "rejected".equals(oldS)) return oldS; // keep terminal
        return rank(newS) > rank(oldS) ? newS : oldS;
    }

    private static int rank(String s) {
        return switch (s == null ? "" : s) {
            case "applied" -> 1;
            case "assessment" -> 2;
            case "interview" -> 3;
            case "offer" -> 4;
            case "rejected" -> 5;
            default -> 0; // "other"/unknown
        };
    }
}

