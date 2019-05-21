package com.dataiku.dss.wt1;

import java.util.UUID;

public class UUIDGenerator {

    public static String generate() {
        UUID uuid = UUID.randomUUID();
        return String.format("%016x%016x", uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }
}
