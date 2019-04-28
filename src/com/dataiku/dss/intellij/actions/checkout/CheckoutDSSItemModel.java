package com.dataiku.dss.intellij.actions.checkout;

import java.util.ArrayList;
import java.util.List;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.Recipe;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;

public class CheckoutDSSItemModel {

    public enum ItemType {
        RECIPE,
        PLUGIN
    }

    public DssServer server;
    public String serverVersion;
    public DSSClient serverClient;
    public String projectKey;
    public List<Recipe> recipes;
    public Module module;
    public String checkoutLocation;
    public ItemType itemType;
    public boolean generateRunConfigurations;
    public Sdk runConfigurationsPythonSdk;

    public CheckoutDSSItemModel() {

    }

    public CheckoutDSSItemModel(DssServer server, Recipe recipe, Module module) {
        this.server = server;
        this.recipes = new ArrayList<>();
        this.recipes.add(recipe);
        this.module = module;
    }
}
