package com.dataiku.dss.intellij;

public class PathUtils {
    public static String makeRelative(String path, String referencePath) {
        int index = 0;
        while (index < path.length() && index < referencePath.length() && path.charAt(index) == referencePath.charAt(index)) {
            index++;
        }
        int pathIndex = (index == referencePath.length() && (path.charAt(index) == '/' || path.charAt(index) == '\\')) ? (index + 1) : index;
        String remainingPath = path.substring(pathIndex);
        String remainingRefPath = referencePath.substring(index);
        if (remainingRefPath.length() > 0) {
            StringBuilder level = new StringBuilder("../");
            for (int j = 0; j < remainingRefPath.length(); j++) {
                if (remainingRefPath.charAt(j) == '\\' || remainingRefPath.charAt(j) == '/') {
                    level.append("../");
                }
            }
            return level.toString() + remainingPath;
        } else {
            return remainingPath;
        }
    }

}
