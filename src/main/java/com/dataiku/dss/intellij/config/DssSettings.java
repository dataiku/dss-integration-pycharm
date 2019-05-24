package com.dataiku.dss.intellij.config;

import static com.dataiku.dss.intellij.config.DssInstance.ENVIRONMENT_VARIABLE_INSTANCE_ID;
import static com.dataiku.dss.intellij.config.DssInstance.ENVIRONMENT_VARIABLE_INSTANCE_LABEL;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.json.DataikuConfig;
import com.dataiku.dss.intellij.utils.ComponentUtils;
import com.dataiku.dss.model.DSSClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(name = "DssConfig", storages = {@Storage("dataiku-dss.xml")})
@SuppressWarnings("WeakerAccess")
public final class DssSettings implements ApplicationComponent, PersistentStateComponent<DssSettings.DssConfig> {
    private static final Logger log = Logger.getInstance(DssSettings.class);
    private static final String ENV_VAR__DKU_DSS_URL = "DKU_DSS_URL";
    private static final String ENV_VAR__DKU_API_KEY = "DKU_API_KEY";
    private static final String ENV_VAR__DKU_NO_CHECK_CERTIFICATE = "DKU_NO_CHECK_CERTIFICATE";

    private final List<DssInstance> servers = new LinkedList<>();
    private final DssConfig config = new DssConfig();
    private final List<Listener> listeners = new ArrayList<>();
    private File dataikuConfigFile;
    private DssInstance defaultServer;

    public interface Listener {
        void onConfigurationUpdated();
    }

    public static DssSettings getInstance() {
        return ComponentUtils.getComponent(DssSettings.class);
    }

    @SuppressWarnings("WeakerAccess")
    public static class DssConfig {
        public boolean enableBackgroundSynchronization = true;
        public int backgroundSynchronizationPollIntervalInSeconds = 120; // 2 minutes
        public boolean trackingEnabled = true;

        public DssConfig() {
        }
    }

    public DssSettings() {
    }

    public DssConfig getState() {
        return config;
    }

    public void loadState(DssConfig state) {
        if (state != null) {
            config.enableBackgroundSynchronization = state.enableBackgroundSynchronization;
            config.backgroundSynchronizationPollIntervalInSeconds = state.backgroundSynchronizationPollIntervalInSeconds;
            config.trackingEnabled = state.trackingEnabled;
        }
    }

    @NotNull
    @NonNls
    public String getComponentName() {
        return "DSSPluginSettings";
    }

    @Override
    public void initComponent() {
        try {
            loadConfigFromEnvironmentVariables();
        } catch (RuntimeException e) {
            log.warn("Unable to read DSS configuration from environment variables", e);
        }

        // Read special file & environment variables for possibles pre-canned values
        dataikuConfigFile = dataikuConfigFile();
        try {
            loadDataikuConfig(dataikuConfigFile);
        } catch (IOException | RuntimeException e) {
            log.warn(String.format("Unable to read DSS configuration from '%s' file", dataikuConfigFile), e);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void updateConfig(List<DssInstance> servers, DssInstance defaultServer, boolean enableBackgroundSync, int backgroundSyncPollingInterval, boolean trackingEnabled) throws IOException {
        this.servers.clear();
        this.servers.addAll(servers);
        this.defaultServer = defaultServer;

        config.enableBackgroundSynchronization = enableBackgroundSync;
        config.backgroundSynchronizationPollIntervalInSeconds = backgroundSyncPollingInterval;
        config.trackingEnabled = trackingEnabled;

        for (Listener listener : listeners) {
            listener.onConfigurationUpdated();
        }

        saveDataikuConfig();
    }

    public boolean isTrackingEnabled() {
        return config.trackingEnabled;
    }

    public boolean isBackgroundSynchronizationEnabled() {
        return config.enableBackgroundSynchronization;
    }

    public int getBackgroundSynchronizationPollIntervalInSeconds() {
        return config.backgroundSynchronizationPollIntervalInSeconds;
    }

    public DssInstance getDefaultInstance() {
        return this.defaultServer;
    }

    public List<DssInstance> getDssInstances() {
        return this.servers;
    }

    public DssInstance getDssInstance(String id) {
        Preconditions.checkNotNull(id);
        for (DssInstance server : this.servers) {
            if (id.equals(server.id)) {
                return server;
            }
        }
        return null;
    }

    @NotNull
    public DssInstance getDssInstanceMandatory(String id) {
        DssInstance result = getDssInstance(id);
        if (result == null) {
            throw new IllegalArgumentException("Unknown DSS instance: " + id);
        }
        return result;
    }

    @NotNull
    public DSSClient getDssClient(String instanceId) {
        DssInstance dssInstance = getDssInstance(instanceId);
        if (dssInstance == null) {
            throw new IllegalStateException(String.format("Unknown DSS instance name: '%s'", instanceId));
        }
        return dssInstance.createClient();
    }

    @VisibleForTesting
    void loadDataikuConfig(File dataikuConfigFile) throws IOException {
        if (dataikuConfigFile.exists()) {
            try {
                try (FileReader fileReader = new FileReader(dataikuConfigFile)) {
                    loadDataikuConfig(new Gson().fromJson(fileReader, DataikuConfig.class));
                }
            } catch (IOException e) {
                log.warn(String.format("Unable to read '%s' file.", dataikuConfigFile), e);
                throw e;
            }
        }
    }

    private void saveDataikuConfig() throws IOException {
        DataikuConfig dataikuConfig = new DataikuConfig();
        dataikuConfig.dss_instances = new LinkedHashMap<>();
        for (DssInstance server : servers) {
            DataikuConfig.DssInstanceConfig instanceConfig = new DataikuConfig.DssInstanceConfig();
            instanceConfig.url = server.baseUrl;
            instanceConfig.api_key = server.apiKey;
            instanceConfig.no_check_certificate = server.noCheckCertificate;
            if (!Objects.equals(server.label, server.id)) {
                instanceConfig.label = server.label;
            }
            dataikuConfig.dss_instances.put(server.id, instanceConfig);
        }
        dataikuConfig.default_instance = defaultServer.id;
        saveDataikuConfig(dataikuConfig);
    }

    private void saveDataikuConfig(DataikuConfig config) throws IOException {
        try {
            // Create ~/.dataiku directory if it does not exist.
            if (!dataikuConfigFile.getParentFile().exists()) {
                if (!dataikuConfigFile.getParentFile().mkdirs()) {
                    throw new IOException("Unable to create directory: " + dataikuConfigFile.getParentFile());
                }
            }
            // Save the file.
            try (FileWriter fileWriter = new FileWriter(dataikuConfigFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(config, fileWriter);
            }
        } catch (IOException e) {
            log.warn(String.format("Unable to save settings into '%s' file.", dataikuConfigFile), e);
            throw e;
        }
    }

    private void loadConfigFromEnvironmentVariables() {
        String url = System.getenv(ENV_VAR__DKU_DSS_URL);
        String apiKey = System.getenv(ENV_VAR__DKU_API_KEY);
        if (url != null && url.length() > 0 && apiKey != null && apiKey.length() > 0) {
            String noCheckCertificateEnv = System.getenv(ENV_VAR__DKU_NO_CHECK_CERTIFICATE);
            boolean noCheckCertificate = noCheckCertificateEnv != null && noCheckCertificateEnv.trim().length() > 0 && !noCheckCertificateEnv.toLowerCase().equals("false");
            addServer(ENVIRONMENT_VARIABLE_INSTANCE_ID, ENVIRONMENT_VARIABLE_INSTANCE_LABEL, url, apiKey, noCheckCertificate);
        }
    }

    private void loadDataikuConfig(DataikuConfig dataikuConfig) {
        if (dataikuConfig != null && dataikuConfig.dss_instances != null) {
            String defaultInstanceName = dataikuConfig.default_instance;
            dataikuConfig.dss_instances.forEach((id, instanceConfig) -> {
                if (id != null && instanceConfig != null) {
                    String label = instanceConfig.label;
                    if (label == null || label.trim().isEmpty()) {
                        label = id;
                    }
                    addServer(id, label, instanceConfig.url, instanceConfig.api_key, instanceConfig.no_check_certificate);
                }
            });
            defaultServer = getDssInstance(defaultInstanceName);
            if (defaultServer != null) {
                defaultServer.isDefault = true;
            }
        }
    }

    @VisibleForTesting
    File dataikuConfigFile() {
        return new File(new File(System.getProperty("user.home"), ".dataiku"), "config.json");
    }

    private void addServer(String id, String label, String baseUrl, String encryptedApiKey, Boolean noCheckCertificate) {
        // Remove existing server with same name if exist.
        servers.stream().filter(s -> id.equals(s.id)).findFirst().ifPresent(servers::remove);

        servers.add(new DssInstance(id, label, baseUrl, encryptedApiKey, noCheckCertificate != null && noCheckCertificate));
    }
}
