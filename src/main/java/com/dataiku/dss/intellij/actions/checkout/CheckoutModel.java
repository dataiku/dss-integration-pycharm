package com.dataiku.dss.intellij.actions.checkout;

import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.Plugin;
import com.dataiku.dss.model.dss.Recipe;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;

import java.util.List;

public class CheckoutModel {

    public enum ItemType {
        RECIPE,
        PLUGIN,
        LIBRARY
    }

    public DssInstance server;
    public String serverVersion;
    public DSSClient serverClient;
    public ItemType itemType;

    // Recipe
    public String projectKey;
    public List<Recipe> recipes;
    public Module module;
    public String checkoutLocation;
    public boolean generateRunConfigurations;
    public Sdk runConfigurationsPythonSdk;

    // Plugins
    public List<Plugin> plugins;

    // Library
    public String libraryProjectKey;
}
