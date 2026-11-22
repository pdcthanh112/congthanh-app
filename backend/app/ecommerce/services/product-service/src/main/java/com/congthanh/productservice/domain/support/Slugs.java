package com.congthanh.productservice.domain.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Slugs {

    public static final Pattern FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private Slugs() {
    }

    public static String from(String name) {
        String s = name == null ? "" : name.replace('đ', 'd').replace('Đ', 'D');
        s = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    public static boolean isValid(String slug) {
        return slug != null && slug.length() <= 255 && FORMAT.matcher(slug).matches();
    }
}
