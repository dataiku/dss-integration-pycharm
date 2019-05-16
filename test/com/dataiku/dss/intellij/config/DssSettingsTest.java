package com.dataiku.dss.intellij.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class DssSettingsTest {
    private static final String CONFIG_JSON_CONTENT = "{\"dss_instances\":{\"default\":{\"url\":\"https://dataiku.acme.com:11200\",\"api_key\":\"my-secret-api-key\"}},\"default_instance\":\"default\"}";

    @Test
    public void shouldLoadInstancesFromDataikuConfigFile() throws Exception {
        File file = new File(Files.createTempDir(), "config.json");
        try {
            Files.write(CONFIG_JSON_CONTENT, file, Charsets.UTF_8);

            DssSettings dssSettings = new DssSettings();
            dssSettings.loadDataikuConfig(file);
            DssInstance dssInstance = dssSettings.getDssInstance("default");
            assertNotNull(dssInstance);
            assertEquals("https://dataiku.acme.com:11200", dssInstance.baseUrl);
            assertEquals("my-secret-api-key", dssInstance.apiKey);
            assertEquals(false, dssInstance.noCheckCertificate);
            assertEquals(dssInstance, dssSettings.getDefaultInstances());
        } finally {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

}