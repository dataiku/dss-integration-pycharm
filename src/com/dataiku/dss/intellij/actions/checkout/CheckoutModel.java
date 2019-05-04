package com.dataiku.dss.intellij.actions.checkout;

import java.util.List;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.Plugin;
import com.dataiku.dss.model.dss.Recipe;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;

public class CheckoutModel {

    public enum ItemType {
        RECIPE,
        PLUGIN
    }

    public DssServer server;
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
}
