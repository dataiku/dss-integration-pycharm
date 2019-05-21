package com.dataiku.dss.wt1;

import static org.apache.commons.codec.Charsets.UTF_8;

import java.awt.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.MetadataFilesIndex;
import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.ComponentUtils;
import com.dataiku.dss.model.dss.DssException;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.gson.Gson;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.ApplicationComponent;

public class WT1 implements ApplicationComponent {
    private static final Logger log = Logger.getInstance(MetadataFilesIndex.class);

    private static final String DATAIKU_DSS_PLUGIN_ID = "com.dataiku.dss.intellij";

    public static final String CLIENTTS_PARAM = "__wt1ts";
    public static final String TRACKTYPE_PARAM = "__wt1ty";
    public static final String REFERRER_PARAM = "__wt1ref";

    public static final String TZOFFSET_PARAM = "__wt1tzo";
    public static final String BROWSER_LANG_PARAM = "__wt1lang";
    public static final String BROWSER_WIDTH_PARAM = "__wt1bw";
    public static final String BROWSER_HEIGHT_PARAM = "__wt1bh";
    public static final String SCREEN_WIDTH_PARAM = "__wt1sw";
    public static final String SCREEN_HEIGHT_PARAM = "__wt1sh";

    public static final String VISITOR_ID_COOKIE = "__wt1vic";
    public static final String VISITOR_PARAMS_COOKIE = "__wt1vpc";
    public static final String SESSION_ID_COOKIE = "__wt1sic";
    public static final String SESSION_PARAMS_COOKIE = "__wt1spc";

    public static final String EVENT_TYPE = "type";
    public static final String PYCHARM_VERSION = "product";
    public static final String PLUGIN_VERSION = "plugin";
    private static final String PYCHARM_PLUGIN_REFERER = "http://pycharm.dataiku.com";

    private static final String TRACKER_URL = "http://tracker.dataiku.com/public/p.gif";
    private static final int MAX_ATTEMPTS = 5;

    private String sessionId;
    private CookieStore cookieStore = new BasicCookieStore();
    private ExecutorService executorService;
    private int tzOffset;
    private String language;
    private Dimension screenSize;
    private String pyCharmVersion;
    private String dssPluginVersion;

    @NotNull
    @Override
    public String getComponentName() {
        return "DSSWT1";
    }

    public static WT1 getInstance() {
        return ComponentUtils.getComponent(WT1.class);
    }

    @Override
    public void initComponent() {
        cookieStore = new BasicCookieStore();
        sessionId = UUIDGenerator.generate();
        executorService = Executors.newCachedThreadPool();

        // Initialize values sent with every event
        tzOffset = Calendar.getInstance().getTimeZone().getRawOffset() / 60000; // We want the offsets in minutes.
        language = Locale.getDefault().getLanguage();
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        pyCharmVersion = ApplicationInfo.getInstance().getBuild().asString();
        dssPluginVersion = getDssPlugin().getVersion();
    }

    @Override
    public void disposeComponent() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                log.warn("WT1 executor did not shutdown within 1 minute");
            }
        } catch (InterruptedException e) {
            log.warn("WT1 executor did not shutdown within 1 minute due to InterruptedException", e);
            Thread.currentThread().interrupt();
        }
    }

    public void track(String eventType, Map<String, Object> additionalParams) {
        log.debug("Submitting track event: type=" + eventType + ", params=" + new Gson().toJson(additionalParams));
        executorService.submit(new WT1Event(eventType, additionalParams));
    }

    public void track(String eventType) {
        track(eventType, Collections.emptyMap());
    }

    private class WT1Event implements Runnable {
        public final String eventType;
        public final Map<String, Object> additionalParams;
        private final long timestamp;
        private final AtomicInteger attempt = new AtomicInteger(0);

        public WT1Event(String eventType, Map<String, Object> additionalParams) {
            this.eventType = eventType;
            this.additionalParams = additionalParams;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public void run() {
            Map<String, Object> params = new HashMap<>();
            params.put(CLIENTTS_PARAM, timestamp);
            params.put(TRACKTYPE_PARAM, "event");
            params.put(REFERRER_PARAM, "");
            params.put(TZOFFSET_PARAM, tzOffset);
            params.put(BROWSER_LANG_PARAM, language);
            params.put(BROWSER_WIDTH_PARAM, screenSize.width);
            params.put(BROWSER_HEIGHT_PARAM, screenSize.height);
            params.put(SCREEN_WIDTH_PARAM, screenSize.width);
            params.put(SCREEN_HEIGHT_PARAM, screenSize.height);

            params.put(VISITOR_ID_COOKIE, getVisitorId());
            params.put(VISITOR_PARAMS_COOKIE, "");
            params.put(SESSION_ID_COOKIE, sessionId);
            params.put(SESSION_PARAMS_COOKIE, "");

            params.put(EVENT_TYPE, eventType);
            params.put(PYCHARM_VERSION, pyCharmVersion);
            params.put(PLUGIN_VERSION, dssPluginVersion);
            params.putAll(additionalParams);

            URI uri = buildUrl(params);
            try {
                log.debug("Sending track event to dataiku server: type=" + eventType + ", params=" + new Gson().toJson(params));
                executeGet(uri);
            } catch (IOException e) {
                int currentAttempt = attempt.getAndIncrement();
                if (currentAttempt < MAX_ATTEMPTS) {
                    log.info(String.format("An error occurred while trying to notify tracker service. Retrying (%d/%d)...", currentAttempt + 1, MAX_ATTEMPTS), e);
                    executorService.submit(this);
                } else {
                    log.info(String.format("An error occurred while trying to notify tracker service. Giving up after having tried %d times", MAX_ATTEMPTS), e);
                }
            }
        }
    }

    private String getVisitorId() {
        try {
            DssInstance defaultInstance = DssSettings.getInstance().getDefaultInstance();
            return defaultInstance != null ? hash128(defaultInstance.apiKey) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private String hash128(String apiKey) throws IOException {
        byte[] hash = ByteSource.wrap(apiKey.getBytes(UTF_8)).hash(Hashing.sha256()).asBytes();
        return BaseEncoding.base32().withPadChar('!').lowerCase().encode(hash).replaceAll("!", "");
    }

    private void executeGet(URI url) throws DssException {
        try {
            try (CloseableHttpClient client = createHttpClient()) {
                HttpRequestBase request = new HttpGet(url);
                request.addHeader("Referer", PYCHARM_PLUGIN_REFERER);
                HttpResponse response;
                try {
                    response = ((HttpClient) client).execute(request, createHttpClientContext());
                } catch (IOException e) {
                    throw new DssException(e);
                }
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new DssException(statusCode, "Dataiku tracker server returned error code " + statusCode);
                }
            }
        } catch (IOException e) {
            throw new DssException(e);
        }
    }

    private CloseableHttpClient createHttpClient() {
        RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();
        return HttpClients.custom().setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore).build();
    }

    private HttpClientContext createHttpClientContext() {
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);
        return context;
    }

    @NotNull
    private URI buildUrl(Map<String, Object> parameters) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (query.length() > 0) {
                query.append("&");
            }
            query.append(encodeParameter(entry.getKey()));
            query.append("=");
            query.append(encodeParameter(entry.getValue()));
        }
        try {
            return new URI(TRACKER_URL + "?" + query);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL", e);
        }
    }

    private String encodeParameter(Object str) {
        if (str == null) {
            return "";
        }
        try {
            return URLEncoder.encode(String.valueOf(str), UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Cannot encode string using UTF8!", e);
        }
    }

    private IdeaPluginDescriptor getDssPlugin() {
        IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
        for (IdeaPluginDescriptor plugin : plugins) {
            if (DATAIKU_DSS_PLUGIN_ID.equals(plugin.getPluginId().getIdString())) {
                return plugin;
            }
        }
        throw new IllegalStateException("Unable to find the Dataiku DSS plugin back: " + DATAIKU_DSS_PLUGIN_ID);
    }
}
