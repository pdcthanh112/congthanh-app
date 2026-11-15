package com.congthanh.commonservice.id;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ErasedIds {

    private ErasedIds() {
    }

    public static String of(String subjectId) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(subjectId.getBytes(StandardCharsets.UTF_8));
            return "erased-" + HexFormat.of().formatHex(h).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
