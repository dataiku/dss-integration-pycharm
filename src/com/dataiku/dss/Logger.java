package com.dataiku.dss;

import org.jetbrains.annotations.NotNull;

public class Logger {
    public static boolean LOG_INTO_CONSOLE = true;
    private com.intellij.openapi.diagnostic.Logger logger;

    public Logger(Class clazz) {
        logger = com.intellij.openapi.diagnostic.Logger.getInstance(clazz);
    }

    public static Logger getInstance(@NotNull Class clazz) {
        return new Logger(clazz);
    }

    public void info(String msg) {
        logger.info(msg);
        if (LOG_INTO_CONSOLE) {
            System.out.println("INFO  - " + msg);
        }
    }

    public void warn(String msg) {
        logger.warn(msg);
        if (LOG_INTO_CONSOLE) {
            System.out.println("WARN  - " + msg);
        }
    }

    public void warn(String msg, Throwable t) {
        logger.warn(msg, t);
        if (LOG_INTO_CONSOLE) {
            System.out.println("WARN  - " + msg);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }

    public void error(String msg) {
        logger.error(msg);
        if (LOG_INTO_CONSOLE) {
            System.err.println("ERROR - " + msg);
        }
    }

    public void error(String msg, Throwable t) {
        logger.error(msg, t);
        if (LOG_INTO_CONSOLE) {
            System.err.println("ERROR - " + msg);
            if (t != null) {
                t.printStackTrace(System.err);
            }
        }
    }

}
