package com.dataiku.dss;

import javax.swing.*;

public class Icons {
    public static Icon RECIPE_PYTHON = buildIcon("/images/recipe-python22.png");
    public static Icon RECIPE_R = buildIcon("/images/recipe-r22.png");
    public static Icon RECIPE_SHELL = buildIcon("/images/recipe-shell22.png");
    public static Icon RECIPE_SQL = buildIcon("/images/recipe-sql22.png");
    public static Icon PLUGIN = buildIcon("/images/plugin22.png");
    public static Icon PLUGIN_WHITE = buildIcon("/images/plugin-white22.png");
    public static Icon WARNING_16 = buildIcon("/images/warning16.png");
    public static Icon CHECK_16 = buildIcon("/images/check16.png");
    public static Icon INFO_16 = buildIcon("/images/info16.png");
    public static Icon FLAG_GREEN_16 = buildIcon("/images/flag-green16.png");
    public static Icon FILE_PYTHON = buildIcon("/images/filePython.png");
    public static Icon FILE_SQL = buildIcon("/images/fileSql.png");

    private static Icon buildIcon(String path) {
        return new ImageIcon(Icons.class.getResource(path));
    }
}
