package com.dataiku.dss.intellij;

import static org.apache.commons.codec.Charsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;

public class VirtualFileUtils {
    @NotNull
    public static VirtualFile createVirtualFile(final Object requestor, final VirtualFile parent, final String name) throws IOException {
        Application application = ApplicationManager.getApplication();
        if (application.isWriteAccessAllowed()) {
            return parent.createChildData(requestor, name);
        } else {
            return application.runWriteAction(
                    (ThrowableComputable<VirtualFile, IOException>) () -> parent.createChildData(requestor, name));
        }
    }

    @NotNull
    public static VirtualFile getOrCreateVirtualDirectory(Object requestor, VirtualFile parent, String name) throws IOException {
        VirtualFile projectFolder = parent.findChild(name);
        if (projectFolder == null) {
            projectFolder = createVirtualDirectory(requestor, parent, name);
        } else if (!projectFolder.isDirectory()) {
            throw new IllegalStateException(String.format("Folder %s cannot be created because a file with the same name is already present in the project.", name));
        }
        return projectFolder;
    }

    @NotNull
    public static VirtualFile createVirtualDirectory(final Object requestor, final VirtualFile parent, final String name) throws IOException {
        Application application = ApplicationManager.getApplication();
        if (application.isWriteAccessAllowed()) {
            return parent.createChildDirectory(requestor, name);
        } else {
            return application.runWriteAction(
                    (ThrowableComputable<VirtualFile, IOException>) () -> parent.createChildDirectory(requestor, name));
        }
    }

    public static String readVirtualFile(VirtualFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] bytes = ByteStreams.toByteArray(inputStream);
            return new String(bytes, Charsets.UTF_8);
        }
    }

    public static byte[] readVirtualFileAsByteArray(VirtualFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return ByteStreams.toByteArray(inputStream);
        }
    }

    public static void writeToVirtualFile(final VirtualFile file, final String content) throws IOException {
        writeToVirtualFile(file, content.getBytes(UTF_8), UTF_8);
    }

    public static void writeToVirtualFile(final VirtualFile file, final byte[] data, @Nullable Charset charset) throws IOException {
        Application application = ApplicationManager.getApplication();
        if (application.isWriteAccessAllowed()) {
            file.setBinaryContent(data);
            if (charset != null) {
                file.setCharset(charset);
            }
        } else {
            application.runWriteAction((ThrowableComputable<Object, IOException>) () -> {
                file.setBinaryContent(data);
                if (charset != null) {
                    file.setCharset(charset);
                }
                return null;
            });
        }
    }

    public static void renameVirtualFile(final Object requestor, final VirtualFile file, String newName) throws IOException {
        Application application = ApplicationManager.getApplication();
        if (application.isWriteAccessAllowed()) {
            file.rename(requestor, newName);
        } else {
            application.runWriteAction((ThrowableComputable<Object, IOException>) () -> {
                file.rename(requestor, newName);
                return null;
            });
        }
    }

    @NotNull
    public static VirtualFile getOrCreateVirtualFile(Object requestor, VirtualFile parent, String... names) throws IOException {
        if (names.length == 0) {
            return parent;
        } else if (names.length == 1) {
            VirtualFile file = parent.findChild(names[0]);
            if (file != null) {
                return file;
            }
            return createVirtualFile(requestor, parent, names[0]);
        } else {
            VirtualFile directory = getOrCreateVirtualDirectory(requestor, parent, names[0]);
            String[] subnames = new String[names.length - 1];
            System.arraycopy(names, 1, subnames, 0, names.length - 1);
            return getOrCreateVirtualFile(requestor, directory, subnames);
        }
    }

    public static VirtualFile getVirtualFile(VirtualFile parent, String... names) throws IOException {
        if (names.length == 1) {
            return parent.findChild(names[0]);
        } else {
            VirtualFile directory = parent.findChild(names[0]);
            if (directory == null || !directory.isDirectory()) {
                return null;
            }
            String[] subnames = new String[names.length];
            System.arraycopy(names, 1, subnames, 0, names.length - 1);
            return getVirtualFile(directory, subnames);
        }
    }

    public static int getContentHash(VirtualFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return getContentHash(ByteStreams.toByteArray(inputStream));
        }
    }

    public static int getContentHash(String content) throws IOException {
        return getContentHash(content.getBytes(UTF_8));
    }

    public static int getContentHash(byte[] data) throws IOException {
        return ByteSource.wrap(data).hash(Hashing.adler32()).asInt();
    }
}
