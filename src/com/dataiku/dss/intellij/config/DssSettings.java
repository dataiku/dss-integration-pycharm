package com.dataiku.dss.intellij.config;

import static com.intellij.openapi.util.PasswordUtil.encodePassword;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.json.DataikuConfig;
import com.dataiku.dss.intellij.utils.ComponentUtils;
import com.dataiku.dss.model.DSSClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(name = "DssConfig", storages = {@Storage("dataiku-dss.xml")})
@SuppressWarnings("WeakerAccess")
public final class DssSettings implements ApplicationComponent, PersistentStateComponent<DssSettings.DssConfig> {
    private static final Logger log = Logger.getInstance(DssSettings.class);
    private static final boolean READ_ONLY = true;
    private static final boolean CHECK_CERTIFICATE = false;
    private static final boolean IS_DEFAULT = true;
    private static final String DEFAULT_DSS_INSTANCE = "default";
    private static final String ENV_VAR__DKU_DSS_URL = "DKU_DSS_URL";
    private static final String ENV_VAR__DKU_API_KEY = "DKU_API_KEY";

    public interface Listener {
        void onConfigurationUpdated();
    }

    public static DssSettings getInstance() {
        return ComponentUtils.getComponent(DssSettings.class);
    }

    @SuppressWarnings("WeakerAccess")
    public static class DssConfig {
        public List<DssServer> servers = new LinkedList<>();
        public boolean enableBackgroundSynchronization = true;
        public int backgroundSynchronizationPollIntervalInSeconds = 120; // 2 minute

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
        if (state != null) {
            List<DssServer> servers = state.servers;
            for (DssServer server : servers) {
                addServer(server.name, server.baseUrl, server.encryptedApiKey, server.noCheckCertificate, false, false);
            }
            config.enableBackgroundSynchronization = state.enableBackgroundSynchronization;
            config.backgroundSynchronizationPollIntervalInSeconds = state.backgroundSynchronizationPollIntervalInSeconds;
        }
    }

    @NotNull
    @NonNls
    public String getComponentName() {
        return "DSSPluginSettings";
    }

    @Override
    public void initComponent() {
        // Read special file & environment variables for possibles pre-canned values
        try {
            loadDataikuConfig(getDataikuConfigFile());
        } catch (RuntimeException e) {
            log.warn("Unable to read DSS configuration from {user.home}/.dataiku/config.json file", e);
        }
        try {
            loadConfigFromEnvironmentVariables();
        } catch (RuntimeException e) {
            log.warn("Unable to read DSS configuration from environment variables", e);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void updateConfig(List<DssServer> servers, boolean enableBackgroundSync, int backgroundSyncPollingInterval) {
        config.servers.clear();
        config.servers.addAll(servers);
        config.enableBackgroundSynchronization = enableBackgroundSync;
        config.backgroundSynchronizationPollIntervalInSeconds = backgroundSyncPollingInterval;
        for (Listener listener : listeners) {
            listener.onConfigurationUpdated();
        }
    }

    public boolean isBackgroundSynchronizationEnabled() {
        return config.enableBackgroundSynchronization;
    }

    public int getBackgroundSynchronizationPollIntervalInSeconds() {
        return config.backgroundSynchronizationPollIntervalInSeconds;
    }

    public DssServer getDefaultServer() {
        return config.servers.stream().findFirst().orElse(null);
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

    @NotNull
    public DSSClient getDssClient(String instanceName) {
        DssServer dssServer = getDssServer(instanceName);
        if (dssServer == null) {
            throw new IllegalStateException(String.format("Unknown DSS instance name: '%s'", instanceName));
        }
        return dssServer.createClient();
    }

    @VisibleForTesting
    void loadDataikuConfig(File dataikuConfigFile) {
        if (dataikuConfigFile != null) {
            try {
                try (FileReader fileReader = new FileReader(dataikuConfigFile)) {
                    loadDataikuConfig(new Gson().fromJson(fileReader, DataikuConfig.class));
                }
            } catch (IOException e) {
                log.warn(String.format("Unable to read '%s' file.", dataikuConfigFile), e);
            }
        }
    }

    private void loadConfigFromEnvironmentVariables() {
        String url = System.getenv(ENV_VAR__DKU_DSS_URL);
        String apiKey = System.getenv(ENV_VAR__DKU_API_KEY);
        if (url != null && url.length() > 0 && apiKey != null && apiKey.length() > 0) {
            addServer(DEFAULT_DSS_INSTANCE, url, encodePassword(apiKey), CHECK_CERTIFICATE, false, READ_ONLY);
        }
    }

    private void loadDataikuConfig(DataikuConfig dataikuConfig) {
        if (dataikuConfig != null && dataikuConfig.dss_instances != null) {
            String defaultInstanceName = dataikuConfig.default_instance;
            dataikuConfig.dss_instances.forEach((instanceName, serverConfig) -> {
                if (instanceName != null && serverConfig != null) {
                    boolean isDefault = instanceName.equals(defaultInstanceName);
                    addServer(instanceName, serverConfig.url, encodePassword(serverConfig.api_key), serverConfig.no_check_certificate, isDefault, READ_ONLY);
                }
            });
        }
    }

    @VisibleForTesting
    File getDataikuConfigFile() {
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            File configFile = new File(new File(userHome, ".dataiku"), "config.json");
            if (configFile.exists()) {
                return configFile;
            }
        }
        return null;
    }

    private void addServer(String name, String baseUrl, String encryptedApiKey, Boolean noCheckCertificate, boolean isDefault, boolean isReadOnly) {
        DssServer existingServer = config.servers.stream().filter(s -> name.equals(s.name)).findFirst().orElse(null);
        if (existingServer != null) {
            if (existingServer.readonly) {
                return; // if a non-editable server is already present in the list, we don't want to override it.
            }
            // Remove existing server with same name if exist.
            config.servers.remove(existingServer);
        }

        DssServer newServer = new DssServer(name, baseUrl, encryptedApiKey, noCheckCertificate != null && noCheckCertificate);
        newServer.readonly = isReadOnly;
        newServer.isDefault = isDefault;
        if (isDefault) {
            config.servers.add(0, newServer);
        } else {
            config.servers.add(newServer);
        }
    }
}
