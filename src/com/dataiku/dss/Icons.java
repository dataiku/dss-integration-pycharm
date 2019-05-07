package com.dataiku.dss;

import javax.swing.*;

import com.intellij.openapi.util.IconLoader;

public class Icons {
    public static final Icon RECIPE_PYTHON = buildIcon("/images/filePython.png");
    public static final Icon RECIPE_R = buildIcon("/images/fileR.png");
    public static final Icon RECIPE_SHELL = buildIcon("/images/fileShell.png");
    public static final Icon RECIPE_SQL = buildIcon("/images/fileSql.png");
    public static final Icon PLUGIN = buildIcon("/images/filePlugin.png");
    public static final Icon PLUGIN_WHITE = buildIcon("/images/filePluginWhite.png");
    public static final Icon WARNING_16 = buildIcon("/images/warning16.png");
    public static final Icon INFO_16 = buildIcon("/images/info16.png");
    public static final Icon BIRD_TEAL = IconLoader.getIcon("/images/birdTeal16.png");
    public static final Icon BIRD_GRAY = IconLoader.getIcon("/images/birdGray16.png");

    private static Icon buildIcon(String path) {
        return new ImageIcon(Icons.class.getResource(path));
    }
}
