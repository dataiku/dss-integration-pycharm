package com.dataiku.dss.intellij.actions.checkout;

import static com.dataiku.dss.intellij.actions.checkout.DataikuInternalClientInstaller.extractHostAndPort;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DataikuInternalClientInstallerTest {

    @Test
    public void testExtractHostAndPort() throws Exception {
        assertEquals("localhost:11200", extractHostAndPort("https://localhost:11200"));
        assertEquals("localhost:11200", extractHostAndPort("https://localhost:11200/"));
        assertEquals("localhost:11200", extractHostAndPort("https://localhost:11200/baseUrl"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractHostAndPort_NoProtocol() throws Exception {
        extractHostAndPort("localhost:11200");
    }
}