package com.hatkid.mkxpz;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PayloadInstaller
{
    public interface ProgressListener
    {
        void onPhase(String phase, int percent);
    }

    private static final String PAYLOAD_CATALOG = "payload.catalog.json";
    private static final String LEGACY_PAYLOAD_ZIP = "payload.zip";
    private static final String LEGACY_PAYLOAD_MANIFEST = "payload.manifest.json";
    private static final String LEGACY_GAME_DIR = "game";
    private static final int BUFFER_SIZE = 64 * 1024;

    private PayloadInstaller()
    {
    }

    public static void installOrUpdate(Context context, ProgressListener listener) throws Exception
    {
        InstallPlan plan = loadInstallPlan(context.getAssets());

        try {
            installInternal(context, plan, listener);
        } catch (Exception firstError) {
            listener.onPhase("retry", 0);
            cleanupInstallState(context, plan);

            try {
                installInternal(context, plan, listener);
            } catch (Exception secondError) {
                secondError.addSuppressed(firstError);
                throw secondError;
            }
        }
    }

    private static void installInternal(Context context, InstallPlan plan, ProgressListener listener) throws Exception
    {
        File filesDir = context.getFilesDir();
        List<PayloadTarget> pendingTargets = new ArrayList<>();
        long totalPayloadBytes = 0;

        for (PayloadTarget target : plan.targets) {
            File installDir = new File(filesDir, target.installDir);
            if (isInstalled(filesDir, installDir, target)) {
                continue;
            }

            pendingTargets.add(target);
            totalPayloadBytes += target.manifest.payloadSize;
        }

        if (pendingTargets.isEmpty()) {
            return;
        }

        final long totalPendingBytes = totalPayloadBytes;
        long verifiedBytes = 0;
        for (PayloadTarget target : pendingTargets) {
            final long verifyBase = verifiedBytes;
            verifyPayload(context.getAssets(), target, bytesRead ->
                listener.onPhase("verify", percent(verifyBase + bytesRead, totalPendingBytes))
            );
            verifiedBytes += target.manifest.payloadSize;
        }

        long extractedBytes = 0;
        int finalizedCount = 0;

        for (PayloadTarget target : pendingTargets) {
            File tempDir = new File(filesDir, target.tempDirName());
            File installDir = new File(filesDir, target.installDir);
            File versionFile = new File(filesDir, target.versionFileName());

            deleteRecursively(tempDir);
            if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
                throw new IOException("Unable to prepare temporary install directory for " + target.id + ".");
            }

            final long extractBase = extractedBytes;
            int extractedFiles = extractPayload(context.getAssets(), target, tempDir, bytesRead ->
                listener.onPhase("extract", percent(extractBase + bytesRead, totalPendingBytes))
            );
            extractedBytes += target.manifest.payloadSize;

            writeVersionFile(versionFile, target.manifest, extractedFiles);

            if (installDir.exists()) {
                deleteRecursively(installDir);
            }

            if (!tempDir.renameTo(installDir)) {
                throw new IOException("Unable to finalize installed game files for " + target.id + ".");
            }

            finalizedCount += 1;
            listener.onPhase("finalize", percent(finalizedCount, pendingTargets.size()));
        }
    }

    private static InstallPlan loadInstallPlan(AssetManager assets) throws IOException, JSONException
    {
        if (assetExists(assets, PAYLOAD_CATALOG)) {
            JSONObject catalog = new JSONObject(readAll(assets.open(PAYLOAD_CATALOG)));
            JSONArray chapters = catalog.getJSONArray("chapters");
            List<PayloadTarget> targets = new ArrayList<>();

            for (int index = 0; index < chapters.length(); index++) {
                JSONObject chapter = chapters.getJSONObject(index);
                String manifestAsset = chapter.getString("manifestAsset");
                PayloadManifest manifest = loadManifest(assets, manifestAsset);

                targets.add(new PayloadTarget(
                    chapter.getString("id"),
                    chapter.optString("title", chapter.getString("id")),
                    chapter.getString("installDir"),
                    chapter.getString("zipAsset"),
                    manifestAsset,
                    manifest
                ));
            }

            return new InstallPlan(targets);
        }

        PayloadManifest manifest = loadManifest(assets, LEGACY_PAYLOAD_MANIFEST);
        List<PayloadTarget> targets = new ArrayList<>();
        targets.add(new PayloadTarget(
            "1",
            manifest.title,
            LEGACY_GAME_DIR,
            LEGACY_PAYLOAD_ZIP,
            LEGACY_PAYLOAD_MANIFEST,
            manifest
        ));
        return new InstallPlan(targets);
    }

    private static boolean assetExists(AssetManager assets, String assetName)
    {
        try (InputStream ignored = assets.open(assetName)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static PayloadManifest loadManifest(AssetManager assets, String manifestAsset) throws IOException, JSONException
    {
        try (InputStream stream = assets.open(manifestAsset)) {
            String json = readAll(stream);
            JSONObject root = new JSONObject(json);
            JSONObject payloadZip = root.getJSONObject("payloadZip");
            JSONObject source = root.getJSONObject("source");

            return new PayloadManifest(
                root.getString("version"),
                root.optString("title", ""),
                payloadZip.getString("sha256"),
                payloadZip.getLong("size"),
                source.optInt("fileCount", 0)
            );
        }
    }

    private static boolean isInstalled(File filesDir, File gameDir, PayloadTarget target) throws IOException, JSONException
    {
        if (!gameDir.isDirectory()) {
            return false;
        }

        File versionFile = new File(filesDir, target.versionFileName());
        if (!versionFile.isFile()) {
            return false;
        }

        try (InputStream stream = new FileInputStream(versionFile)) {
            JSONObject current = new JSONObject(readAll(stream));
            return target.manifest.version.equals(current.optString("version")) &&
                target.manifest.sha256.equalsIgnoreCase(current.optString("sha256")) &&
                hasRequiredGameFiles(gameDir);
        }
    }

    private static void verifyPayload(AssetManager assets, PayloadTarget target, ProgressCallback callback) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream stream = new BufferedInputStream(assets.open(target.zipAsset))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long totalRead = 0;
            int read;

            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                totalRead += read;
                callback.onBytesRead(totalRead);
            }
        }

        String actualHash = hex(digest.digest());
        if (!target.manifest.sha256.equalsIgnoreCase(actualHash)) {
            throw new IOException("Bundled payload verification failed for " + target.id + ".");
        }
    }

    private static int extractPayload(AssetManager assets, PayloadTarget target, File tempDir, ProgressCallback callback) throws IOException
    {
        int extractedFiles = 0;

        try (ProgressInputStream rawStream = new ProgressInputStream(
            new BufferedInputStream(assets.open(target.zipAsset)),
            callback
        );
             ZipInputStream zipStream = new ZipInputStream(rawStream)) {

            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zipStream.getNextEntry()) != null) {
                File targetFile = resolveZipEntry(tempDir, entry);

                if (entry.isDirectory()) {
                    if (!targetFile.mkdirs() && !targetFile.isDirectory()) {
                        throw new IOException("Unable to create directory " + targetFile.getAbsolutePath());
                    }
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Unable to create directory " + parent.getAbsolutePath());
                    }

                    try (FileOutputStream output = new FileOutputStream(targetFile)) {
                        int read;
                        while ((read = zipStream.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    }

                    extractedFiles += 1;
                }

                zipStream.closeEntry();
            }
        }

        if (extractedFiles == 0) {
            throw new IOException("Bundled payload archive was empty for " + target.id + ".");
        }

        if (!hasRequiredGameFiles(tempDir)) {
            throw new IOException("Installed payload is missing Game.ini or Data/Scripts.rvdata for " + target.id + ".");
        }

        return target.manifest.fileCount > 0 ? target.manifest.fileCount : extractedFiles;
    }

    private static File resolveZipEntry(File root, ZipEntry entry) throws IOException
    {
        String normalizedEntryName = entry.getName().replace('\\', '/');
        File target = new File(root, normalizedEntryName);
        String rootPath = root.getCanonicalPath() + File.separator;
        String targetPath = target.getCanonicalPath();

        if (!targetPath.startsWith(rootPath) && !targetPath.equals(root.getCanonicalPath())) {
            throw new IOException("Invalid payload entry path.");
        }

        return target;
    }

    private static void writeVersionFile(File versionFile, PayloadManifest manifest, int fileCount) throws IOException
    {
        JSONObject version = new JSONObject();

        try {
            version.put("version", manifest.version);
            version.put("sha256", manifest.sha256);
            version.put("installedAt", System.currentTimeMillis());
            version.put("fileCount", fileCount);
        } catch (JSONException e) {
            throw new IOException("Unable to encode payload version metadata.", e);
        }

        try (FileOutputStream stream = new FileOutputStream(versionFile, false)) {
            stream.write(version.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void cleanupInstallState(Context context, InstallPlan plan)
    {
        File filesDir = context.getFilesDir();

        for (PayloadTarget target : plan.targets) {
            deleteRecursively(new File(filesDir, target.installDir));
            deleteRecursively(new File(filesDir, target.tempDirName()));

            File versionFile = new File(filesDir, target.versionFileName());
            if (versionFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                versionFile.delete();
            }
        }
    }

    private static void deleteRecursively(File file)
    {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static boolean hasRequiredGameFiles(File gameDir)
    {
        return new File(gameDir, "Game.ini").isFile() &&
            new File(gameDir, "Data/Scripts.rvdata").isFile();
    }

    private static String readAll(InputStream stream) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;

        while ((read = stream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }

        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static int percent(long numerator, long denominator)
    {
        if (denominator <= 0) {
            return 0;
        }

        return (int) Math.max(0, Math.min(100, (numerator * 100L) / denominator));
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static final class InstallPlan
    {
        private final List<PayloadTarget> targets;

        private InstallPlan(List<PayloadTarget> targets)
        {
            this.targets = targets;
        }
    }

    private static final class PayloadTarget
    {
        private final String id;
        private final String title;
        private final String installDir;
        private final String zipAsset;
        private final String manifestAsset;
        private final PayloadManifest manifest;

        private PayloadTarget(String id, String title, String installDir, String zipAsset, String manifestAsset, PayloadManifest manifest)
        {
            this.id = id;
            this.title = title;
            this.installDir = installDir;
            this.zipAsset = zipAsset;
            this.manifestAsset = manifestAsset;
            this.manifest = manifest;
        }

        private String tempDirName()
        {
            return installDir + ".tmp";
        }

        private String versionFileName()
        {
            return "payload." + installDir + ".version.json";
        }
    }

    private static final class PayloadManifest
    {
        private final String version;
        private final String title;
        private final String sha256;
        private final long payloadSize;
        private final int fileCount;

        private PayloadManifest(String version, String title, String sha256, long payloadSize, int fileCount)
        {
            this.version = version;
            this.title = title;
            this.sha256 = sha256;
            this.payloadSize = payloadSize;
            this.fileCount = fileCount;
        }
    }

    private interface ProgressCallback
    {
        void onBytesRead(long bytesRead);
    }

    private static final class ProgressInputStream extends InputStream
    {
        private final InputStream delegate;
        private final ProgressCallback callback;
        private long bytesRead;

        private ProgressInputStream(InputStream delegate, ProgressCallback callback)
        {
            this.delegate = delegate;
            this.callback = callback;
        }

        @Override
        public int read() throws IOException
        {
            int value = delegate.read();
            if (value >= 0) {
                bytesRead += 1;
                callback.onBytesRead(bytesRead);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            int read = delegate.read(b, off, len);
            if (read > 0) {
                bytesRead += read;
                callback.onBytesRead(bytesRead);
            }
            return read;
        }

        @Override
        public void close() throws IOException
        {
            delegate.close();
        }
    }
}
