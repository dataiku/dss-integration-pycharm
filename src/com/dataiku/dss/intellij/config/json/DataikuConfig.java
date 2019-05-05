package com.dataiku.dss.intellij.config.json;

import java.util.Map;

public class DataikuConfig {
    public Map<String, DssInstanceConfig> dss_instances;
    public String default_instance;

    public static class DssInstanceConfig {
        public String url;
        public String api_key;
        public Boolean no_check_certificate;
    }
}
