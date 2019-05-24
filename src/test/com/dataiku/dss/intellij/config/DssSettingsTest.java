package com.dataiku.dss.intellij.config;

import static com.google.common.base.Charsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;

import com.google.common.io.Files;

@SuppressWarnings("UnstableApiUsage")
public class DssSettingsTest {
    private static final String CONFIG_JSON_CONTENT = "{\"dss_instances\":{\"default\":{\"url\":\"https://dataiku.acme.com:11200\",\"api_key\":\"my-secret-api-key\"}},\"default_instance\":\"default\"}";

    @Test
    public void shouldLoadInstancesFromDataikuConfigFile() throws Exception {
        File file = new File(Files.createTempDir(), "config.json");
        try {
            Files.asCharSink(file, UTF_8).write(CONFIG_JSON_CONTENT);

            DssSettings dssSettings = new DssSettings();
            dssSettings.loadDataikuConfig(file);
            DssInstance dssInstance = dssSettings.getDssInstance("default");
            assertNotNull(dssInstance);
            assertEquals("https://dataiku.acme.com:11200", dssInstance.baseUrl);
            assertEquals("my-secret-api-key", dssInstance.apiKey);
            assertFalse(dssInstance.noCheckCertificate);
            assertEquals(dssInstance, dssSettings.getDefaultInstance());
        } finally {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

}