package com.dataiku.dss.intellij.utils;

import static com.google.common.base.Charsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;

@SuppressWarnings("WeakerAccess")
public class VirtualFileManager {
    private final Object requestor;
    private final boolean runInBackgroundThread;

    public VirtualFileManager(final Object requestor, boolean runInBackgroundThread) {
        this.requestor = requestor;
        this.runInBackgroundThread = runInBackgroundThread;
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------------------
    //
    // READ
    //
    //----------------------------------------------------------------------------------------------------------------------------------------------------------

    public static String readVirtualFile(VirtualFile file) throws IOException {
        return new String(readVirtualFileAsByteArray(file), UTF_8);
    }

    public static byte[] readVirtualFileAsByteArray(VirtualFile file) throws IOException {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return readVirtualFileAsByteArrayUnsafe(file);
        } else {
            return ReadAction.compute(() -> readVirtualFileAsByteArrayUnsafe(file));
        }
    }

    public static String getRelativePath(VirtualFile base, VirtualFile file) {
        String baseUrl = base.getUrl();
        String fileUrl = file.getUrl();
        if (fileUrl.startsWith(baseUrl) && fileUrl.length() > baseUrl.length()) {
            return fileUrl.substring(baseUrl.length() + 1);
        }
        return null;
    }

    public static VirtualFile getVirtualFile(VirtualFile parent, String... names) {
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

    //----------------------------------------------------------------------------------------------------------------------------------------------------------
    //
    // HASH
    //
    //----------------------------------------------------------------------------------------------------------------------------------------------------------

    public static int getContentHash(VirtualFile file) throws IOException {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return getContentHashUnsafe(file);
        } else {
            return ReadAction.compute(() -> getContentHashUnsafe(file));
        }
    }

    public static int getContentHash(String content) throws IOException {
        return getContentHash(content.getBytes(UTF_8));
    }

    public static int getContentHash(byte[] data) throws IOException {
        return ByteSource.wrap(data).hash(Hashing.adler32()).asInt();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------------------
    //
    // WRITE
    //
    //----------------------------------------------------------------------------------------------------------------------------------------------------------

    @NotNull
    public VirtualFile createVirtualFile(VirtualFile parent, String name) throws IOException {
        return safeWrite(() -> createVirtualFileUnsafe(requestor, parent, name));
    }

    @NotNull
    public VirtualFile getOrCreateVirtualDirectory(VirtualFile parent, String name) throws IOException {
        VirtualFile projectFolder = parent.findChild(name);
        if (projectFolder == null) {
            projectFolder = createVirtualDirectory(requestor, parent, name);
        } else if (!projectFolder.isDirectory()) {
            throw new IllegalStateException(String.format("Folder %s cannot be created because a file with the same name is already present in the project.", name));
        }
        return projectFolder;
    }

    @NotNull
    public VirtualFile createVirtualDirectory(Object requestor, VirtualFile parent, String name) throws IOException {
        return safeWrite(() -> createVirtualDirectoryUnsafe(requestor, parent, name));
    }

    public void writeToVirtualFile(VirtualFile file, String content) throws IOException {
        writeToVirtualFile(file, content.getBytes(UTF_8), UTF_8);
    }

    public void writeToVirtualFile(VirtualFile file, byte[] data, @Nullable Charset charset) throws IOException {
        safeWrite(() -> writeToVirtualFileUnsafe(file, data, charset));
    }

    public void renameVirtualFile(VirtualFile file, String newName) throws IOException {
        safeWrite(() -> renameVirtualFileUnsafe(requestor, file, newName));
    }

    public void deleteVirtualFile(VirtualFile file) throws IOException {
        safeWrite(() -> deleteVirtualFileUnsafe(requestor, file));
    }

    @NotNull
    public VirtualFile getOrCreateVirtualFile(VirtualFile parent, String... names) throws IOException {
        if (names.length == 0) {
            return parent;
        } else if (names.length == 1) {
            VirtualFile file = parent.findChild(names[0]);
            if (file != null) {
                return file;
            }
            return createVirtualFile(parent, names[0]);
        } else {
            VirtualFile directory = getOrCreateVirtualDirectory(parent, names[0]);
            String[] subnames = new String[names.length - 1];
            System.arraycopy(names, 1, subnames, 0, names.length - 1);
            return getOrCreateVirtualFile(directory, subnames);
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------------------
    //
    // WRITE unsafe methods
    //
    //----------------------------------------------------------------------------------------------------------------------------------------------------------

    private static VirtualFile createVirtualFileUnsafe(Object requestor, VirtualFile parent, String name) throws IOException {
        return parent.createChildData(requestor, name);
    }

    private static VirtualFile createVirtualDirectoryUnsafe(Object requestor, VirtualFile parent, String name) throws IOException {
        return parent.createChildDirectory(requestor, name);
    }

    private static void writeToVirtualFileUnsafe(VirtualFile file, byte[] data, @Nullable Charset charset) throws IOException {
        file.setBinaryContent(data);
        if (charset != null) {
            file.setCharset(charset);
        }
    }

    private void deleteVirtualFileUnsafe(Object requestor, VirtualFile file) throws IOException {
        file.delete(requestor);
    }

    private static void renameVirtualFileUnsafe(Object requestor, VirtualFile file, String newName) throws IOException {
        file.rename(requestor, newName);
    }

    private static byte[] readVirtualFileAsByteArrayUnsafe(VirtualFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return ByteStreams.toByteArray(inputStream);
        }
    }

    private static int getContentHashUnsafe(VirtualFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return getContentHash(ByteStreams.toByteArray(inputStream));
        }
    }

    private interface WriteOperation<T> {
        T run() throws IOException;
    }

    private interface WriteCommand {
        void execute() throws IOException;
    }

    private static class ResultHolder<T> {
        public T result;
        public IOException exception;
    }

    private <T> T safeWrite(WriteOperation<T> operation) throws IOException {
        ResultHolder<T> resultHolder = new ResultHolder<>();
        if (runInBackgroundThread) {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                try {
                    resultHolder.result = run(operation);
                } catch (IOException e) {
                    resultHolder.exception = e;
                }
            });
            if (resultHolder.exception != null) {
                throw resultHolder.exception;
            }
            return resultHolder.result;
        } else {
            return run(operation);
        }
    }

    private <T> T run(WriteOperation<T> operation) throws IOException {
        if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
            return operation.run();
        } else {
            return WriteAction.compute(operation::run);
        }
    }

    private <T> void safeWrite(WriteCommand command) throws IOException {
        ResultHolder<T> resultHolder = new ResultHolder<>();
        if (runInBackgroundThread) {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                try {
                    execute(command);
                } catch (IOException e) {
                    resultHolder.exception = e;
                }
            });
            if (resultHolder.exception != null) {
                throw resultHolder.exception;
            }
        } else {
            execute(command);
        }
    }

    private void execute(WriteCommand command) throws IOException {
        if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
            command.execute();
        } else {
            WriteAction.compute(() -> {
                command.execute();
                return null;
            });
        }
    }
}
