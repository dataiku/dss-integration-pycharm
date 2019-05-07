package com.dataiku.dss.intellij.config;

import java.util.Objects;

import com.dataiku.dss.model.DSSClient;
import com.intellij.openapi.util.PasswordUtil;

public class DssServer {
    public String name;
    public String baseUrl;
    public String encryptedApiKey;
    public boolean noCheckCertificate;
    public boolean readonly; // true when server has been created from environment variable or ~/.dataiku/config.json
    public boolean isDefault; // true if the server has been defined as default server in ~/.dataiku/config.json

    public DssServer() {
    }

    public DssServer(String name, String baseUrl, String encryptedApiKey, boolean noCheckCertificate) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.encryptedApiKey = encryptedApiKey;
        this.noCheckCertificate = noCheckCertificate;
    }

    public DssServer(String name, String baseUrl, String encryptedApiKey) {
        this(name, baseUrl, encryptedApiKey, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DssServer dssServer = (DssServer) o;
        return Objects.equals(name, dssServer.name) &&
                Objects.equals(baseUrl, dssServer.baseUrl) &&
                Objects.equals(encryptedApiKey, dssServer.encryptedApiKey) &&
                noCheckCertificate == dssServer.noCheckCertificate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, baseUrl, encryptedApiKey, noCheckCertificate);
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return name + " [" + baseUrl + "]";
    }

    public DSSClient createClient() {
        return new DSSClient(baseUrl, PasswordUtil.decodePassword(encryptedApiKey), noCheckCertificate);
    }
}
