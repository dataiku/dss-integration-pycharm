package com.dataiku.dss.intellij.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.util.PasswordUtil;

public class DssSettingsTest {
    private static final String CONFIG_JSON_CONTENT = "{\"dss_instances\":{\"default\":{\"url\":\"https://dataiku.acme.com:11200\",\"api_key\":\"my-secret-api-key\"}},\"default_instance\":\"default\"}";

    @Test
    public void shouldLoadInstancesFromDataikuConfigFile() throws Exception {
        File file = new File(Files.createTempDir(), "config.json");
        try {
            Files.write(CONFIG_JSON_CONTENT, file, Charsets.UTF_8);

            DssSettings dssSettings = new DssSettings();
            dssSettings.loadDataikuConfig(file);
            DssServer dssServer = dssSettings.getDssServer("default");
            assertNotNull(dssServer);
            assertEquals("https://dataiku.acme.com:11200", dssServer.baseUrl);
            assertEquals("my-secret-api-key", PasswordUtil.decodePassword(dssServer.encryptedApiKey));
            assertEquals(true, dssServer.readonly);
            assertEquals(false, dssServer.noCheckCertificate);
        } finally {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

}