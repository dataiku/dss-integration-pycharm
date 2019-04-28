package com.dataiku.dss.intellij;

import javax.swing.*;

import com.dataiku.dss.Icons;

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

    public static Icon icon(String type) {
        switch (type) {
        case "python":
            return Icons.RECIPE_PYTHON;
        case "sql_script":
            return Icons.RECIPE_SQL;
        case "r":
            return Icons.RECIPE_R;
        case "shell":
            return Icons.RECIPE_SHELL;
        default:
            return null;
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
