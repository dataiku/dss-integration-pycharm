package com.dataiku.dss.intellij.config;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.google.common.base.Preconditions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(name = "DssConfig", storages = {@Storage("dataiku-dss.xml")})
public final class DssSettings implements ApplicationComponent, PersistentStateComponent<DssSettings.DssConfig> {

    public static DssSettings getInstance() {
        return ApplicationManager.getApplication().getComponent(DssSettings.class);
    }

    public static class DssConfig {
        public List<DssServer> servers = new LinkedList<>();

        public DssConfig() {
        }
    }

    private DssConfig config = new DssConfig();
    private final List<Listener> listeners = new ArrayList<>();

    public DssSettings() {
    }

    public DssConfig getState() {
        return config;
    }

    public void loadState(DssConfig state) {
        config = state;
    }

    @NotNull
    @NonNls
    public String getComponentName() {
        return "DSSPluginSettings";
    }

    public void setDssServers(List<DssServer> servers) {
        config.servers.clear();
        config.servers.addAll(servers);
        for (Listener listener : listeners) {
            listener.onConfigurationUpdated();
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public interface Listener {
        void onConfigurationUpdated();
    }

    public List<DssServer> getDssServers() {
        return config.servers;
    }

    public DssServer getDssServer(String name) {
        Preconditions.checkNotNull(name);
        for (DssServer server : config.servers) {
            if (name.equals(server.name)) {
                return server;
            }
        }
        return null;
    }
}
