package com.dataiku.dss;

import javax.swing.*;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

public class Icons {
    public static final Icon RECIPE_PYTHON = buildIcon("/images/recipePython.png");
    public static final Icon RECIPE_R = buildIcon("/images/recipeR.png");
    public static final Icon RECIPE_SHELL = buildIcon("/images/recipeShell.png");
    public static final Icon RECIPE_SQL = buildIcon("/images/recipeSql.png");
    public static final Icon PLUGIN = buildIcon("/images/plugin.png");
    public static final Icon BIRD_GRAY = IconLoader.getIcon("/images/birdGray.png");
    public static final Icon WARNING = AllIcons.General.Warning;
    public static final Icon INFO = AllIcons.General.Information;

    private static Icon buildIcon(String path) {
        return new ImageIcon(Icons.class.getResource(path));
    }
}
