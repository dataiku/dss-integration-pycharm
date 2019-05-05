package com.dataiku.dss;

import javax.swing.*;

public class Icons {
    public static Icon RECIPE_PYTHON = buildIcon("/images/filePython.png");
    public static Icon RECIPE_R = buildIcon("/images/fileR.png");
    public static Icon RECIPE_SHELL = buildIcon("/images/fileShell.png");
    public static Icon RECIPE_SQL = buildIcon("/images/fileSql.png");
    public static Icon PLUGIN = buildIcon("/images/filePlugin.png");
    public static Icon PLUGIN_WHITE = buildIcon("/images/filePluginWhite.png");
    public static Icon WARNING_16 = buildIcon("/images/warning16.png");
    public static Icon INFO_16 = buildIcon("/images/info16.png");

    private static Icon buildIcon(String path) {
        return new ImageIcon(Icons.class.getResource(path));
    }
}
