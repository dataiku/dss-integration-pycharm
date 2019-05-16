package com.dataiku.dss.intellij.config;

import java.util.Objects;

import com.dataiku.dss.model.DSSClient;

public class DssInstance {
    public static final String ENVIRONMENT_VARIABLE_INSTANCE_ID = "_ENV_VARS_";
    public static final String ENVIRONMENT_VARIABLE_INSTANCE_LABEL = "Environment variables";

    public String id;
    public String label;
    public String baseUrl;
    public String apiKey;
    public boolean noCheckCertificate;
    public boolean isDefault; // true if the server has been defined as default server in ~/.dataiku/config.json

    public DssInstance() {
    }

    public DssInstance(String id, String label, String baseUrl, String apiKey, boolean noCheckCertificate) {
        this.id = id;
        this.label = label;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.noCheckCertificate = noCheckCertificate;
    }

    public DssInstance(String id, String label, String baseUrl, String apiKey) {
        this(id, label, baseUrl, apiKey, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DssInstance dssServer = (DssInstance) o;
        return Objects.equals(id, dssServer.id) &&
                Objects.equals(label, dssServer.label) &&
                Objects.equals(baseUrl, dssServer.baseUrl) &&
                Objects.equals(apiKey, dssServer.apiKey) &&
                noCheckCertificate == dssServer.noCheckCertificate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, baseUrl, apiKey, noCheckCertificate);
    }

    public String getId() {
        return id;
    }

    public String toString() {
        return label + " [" + baseUrl + "]";
    }

    public DSSClient createClient() {
        return new DSSClient(baseUrl, apiKey, noCheckCertificate);
    }
}
