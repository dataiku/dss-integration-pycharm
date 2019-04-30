package com.dataiku.dss.intellij.config;

import java.util.Objects;

import com.dataiku.dss.model.DSSClient;
import com.intellij.openapi.util.PasswordUtil;

public class DssServer {
    public String name;
    public String baseUrl;
    public String encryptedApiKey;
    public boolean noCheckCertificate;

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
        return name;
    }

    public DSSClient createClient() {
        return new DSSClient(baseUrl, PasswordUtil.decodePassword(encryptedApiKey), noCheckCertificate);
    }
}
