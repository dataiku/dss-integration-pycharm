package com.dataiku.dss.intellij;

public class RecipeUtils {

    public static String extension(String type) {
        switch (type) {
        case "python":
            return ".py";
        case "sql_script":
            return ".sql";
        case "r":
            return ".r";
        case "shell":
            return ".sh";
        default:
            return "";
        }
    }

    public static boolean isEditableRecipe(String type) {
        switch (type) {
        case "python":
        case "sql_script":
        case "r":
        case "shell":
            return true;
        default:
            return false;
        }
    }
}
