package com.example.filenormalizer.util;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public class FilenameSanitizer {
    private static final Pattern INVALID = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]");
    private static final int MAX_NAME = 255;
    private static final Set<String> RESERVED = new HashSet<>();

    static {
        RESERVED.addAll(Arrays.asList("CON","PRN","AUX","NUL"));
        for (int i=1;i<=9;i++) { RESERVED.add("COM"+i); RESERVED.add("LPT"+i); }
    }

    public static String sanitize(String name) {
        // 1) macOS NFD → NFC
        name = Normalizer.normalize(name, Normalizer.Form.NFC);

        // 2) 제어문자/윈도우 금지문자 제거
        name = INVALID.matcher(name).replaceAll("_");

        // 3) 공백 정리 + 양끝 공백/마침표 제거
        name = name.replaceAll("\\s+", " ").replaceAll("^[ .]+|[ .]+$", "");
        if (name.isEmpty()) name = "unnamed";

        // 4) 예약어 회피
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String ext  = dot >= 0 ? name.substring(dot) : "";
        if (RESERVED.contains(base.toUpperCase(Locale.ROOT))) {
            base = "_" + base;
        }
        name = base + ext;

        // 5) 길이 제한
        if (name.length() > MAX_NAME) {
            int keep = MAX_NAME - ext.length();
            if (keep <= 0) {
                name = name.substring(0, MAX_NAME);
            } else {
                name = base.substring(0, Math.min(base.length(), keep)) + ext;
            }
        }
        return name;
    }

    public static List<String> dedupe(List<String> names) {
        Map<String,Integer> seen = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (String n : names) {
            String key = n.toLowerCase(Locale.ROOT);
            if (!seen.containsKey(key)) {
                seen.put(key, 1);
                out.add(n);
                continue;
            }
            int i = seen.get(key);
            String candidate;
            String base, ext;
            int dot = n.lastIndexOf('.');
            base = dot >= 0 ? n.substring(0, dot) : n;
            ext  = dot >= 0 ? n.substring(dot) : "";
            while (true) {
                candidate = base + " (" + i + ")" + ext;
                String key2 = candidate.toLowerCase(Locale.ROOT);
                if (!seen.containsKey(key2)) {
                    seen.put(key, i+1);
                    seen.put(key2, 1);
                    out.add(candidate);
                    break;
                }
                i++;
            }
        }
        return out;
    }
}