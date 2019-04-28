package com.dataiku.dss.intellij;

public class Os {
    private static boolean isWindows = System.getProperty("os.name").startsWith("Windows");

    public static boolean isWindows() {
        return isWindows;
    }
}
