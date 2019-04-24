package com.dataiku.dss.intellij.config;

import java.util.Objects;

public class DssServer {
    public String name;
    public String baseUrl;
    public String encryptedApiKey;

    public DssServer() {
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
                Objects.equals(encryptedApiKey, dssServer.encryptedApiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, baseUrl, encryptedApiKey);
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return name;
    }
}
