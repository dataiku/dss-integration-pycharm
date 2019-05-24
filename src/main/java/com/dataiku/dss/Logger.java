package com.dataiku.dss;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.jetbrains.annotations.NotNull;

import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

public class Logger {
    @SuppressWarnings("FieldCanBeLocal") private static final boolean DEBUG_MODE = false;
    private static final PrintStream ADDITIONAL_LOGGER = createAdditionalLogger();

    private final com.intellij.openapi.diagnostic.Logger logger;

    public static Logger getInstance(@NotNull Class clazz) {
        return new Logger(clazz);
    }

    private Logger(Class clazz) {
        logger = com.intellij.openapi.diagnostic.Logger.getInstance(clazz);
    }

    public void debug(String msg) {
        logger.debug(msg);
        log("DEBUG - " + msg);
    }

    public void debug(String msg, Throwable t) {
        logger.debug(msg, t);
        log("DEBUG - " + msg, t);
    }

    public void info(String msg) {
        logger.info(msg);
        log("INFO - " + msg);
    }

    public void info(String msg, Throwable t) {
        logger.info(msg, t);
        log("INFO - " + msg, t);
    }

    public void warn(String msg) {
        logger.warn(msg);
        log("WARN - " + msg);
    }

    public void warn(String msg, Throwable t) {
        logger.warn(msg, t);
        log("WARN - " + msg, t);
    }

    public void error(String msg) {
        logger.error(msg);
        log("ERROR - " + msg);
    }

    public void error(String msg, Throwable t) {
        logger.error(msg, t);
        log("ERROR - " + msg, t);
    }

    private void log(String msg) {
        log(msg, null);
    }

    private void log(String msg, Throwable t) {
        if (ADDITIONAL_LOGGER != null) {
            ADDITIONAL_LOGGER.println(msg);
            if (t != null) {
                t.printStackTrace(ADDITIONAL_LOGGER);
            }
        }
    }

    private static PrintStream createAdditionalLogger() {
        if (DEBUG_MODE) {
            return System.out;
        }
        String logSettings = System.getenv("DKU_PYCHARM_LOG");
        if (logSettings == null || logSettings.isEmpty()) {
            return null;
        }

        if (logSettings.equalsIgnoreCase("console")) {
            return System.out;
        } else {
            try {
                return new PrintStream(Files.asByteSink(new File(logSettings), FileWriteMode.APPEND).openBufferedStream());
            } catch (IOException e) {
                System.err.println("Unable to create File logger. File logging will be disabled.");
                e.printStackTrace(System.err);
                return null;
            }
        }
    }
}
