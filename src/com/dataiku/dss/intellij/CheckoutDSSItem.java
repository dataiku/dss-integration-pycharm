package com.dataiku.dss.intellij;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.model.Recipe;
import com.intellij.openapi.module.Module;

public class CheckoutDSSItem {
    public final DssServer server;
    public final Recipe recipe;
    public final Module module;

    public CheckoutDSSItem(DssServer server, Recipe recipe, Module module) {
        this.server = server;
        this.recipe = recipe;
        this.module = module;
    }
}
