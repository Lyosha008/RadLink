package org.Lyosha008.radlink;

import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class StartupContentPack {
    private static final String HASH_FILE = ".radlink-content-hash";
    private static final String CURRENT_FILE = "current.txt";
    private static final String PACK_DIRECTORY_PATTERN = "pack-[a-f0-9]{64}-[a-f0-9-]{36}";

    private static Path downloadedDirectory;
    private static String manifestHash;
    private static boolean remoteTargetSelected;

    private StartupContentPack() {}

    public static void loadBeforeRegistries() {
        downloadedDirectory = null;
        manifestHash = null;
        remoteTargetSelected = false;

        if (FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.CLIENT) {
            return;
        }

        String target = getConfiguredServerAddress();
        if (target.isBlank()) {
            return;
        }

        Path gameDir = FMLPaths.GAMEDIR.get();
        String safeName = target.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        Path localWorld = gameDir.resolve("saves").resolve(target).resolve("radlink");
        Path sanitizedLocalWorld = gameDir.resolve("saves").resolve(safeName).resolve("radlink");
        if (Files.isDirectory(localWorld) || Files.isDirectory(sanitizedLocalWorld)) {
            return;
        }

        remoteTargetSelected = true;
        Path cacheRoot = getServerCacheRoot(target);
        Path currentFile = cacheRoot.resolve(CURRENT_FILE);
        if (Files.notExists(currentFile)) {
            return;
        }

        try {
            String directoryName = Files.readString(currentFile, StandardCharsets.UTF_8).trim();
            if (!directoryName.matches(PACK_DIRECTORY_PATTERN)) {
                throw new IOException("Invalid RadLink content cache pointer");
            }

            Path packDirectory = cacheRoot.resolve(directoryName).normalize();
            if (!packDirectory.startsWith(cacheRoot.normalize()) || !Files.isDirectory(packDirectory)) {
                throw new IOException("RadLink content cache directory is missing");
            }

            String cachedHash = Files.readString(packDirectory.resolve(HASH_FILE), StandardCharsets.UTF_8).trim();
            String expectedHash = directoryName.substring("pack-".length(), "pack-".length() + 64);
            if (!cachedHash.equals(expectedHash)) {
                throw new IOException("RadLink content cache hash does not match");
            }

            downloadedDirectory = packDirectory;
            manifestHash = cachedHash;
        } catch (IOException e) {
            System.err.println("[RadLink] Не удалось загрузить локальный кэш контента: " + e.getMessage());
        }
    }

    public static Path getDownloadedDirectory() {
        return downloadedDirectory;
    }

    public static String getManifestHash() {
        return manifestHash;
    }

    public static boolean isRemoteTargetSelected() {
        return remoteTargetSelected;
    }

    public static String getConfiguredServerAddress() {
        try {
            return RadlinkConfig.SERVER_OR_WORLD_NAME.get().trim();
        } catch (IllegalStateException e) {
            return "";
        }
    }

    static Path getServerCacheRoot(String serverAddress) {
        String safeName = serverAddress.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return FMLPaths.GAMEDIR.get()
            .resolve("radlink")
            .resolve("servers")
            .resolve(safeName)
            .normalize();
    }

    static Path createStagingDirectory(String serverAddress, UUID transferId) throws IOException {
        Path cacheRoot = getServerCacheRoot(serverAddress);
        Files.createDirectories(cacheRoot);
        return Files.createTempDirectory(cacheRoot, ".incoming-" + transferId + "-");
    }

    static void publishDownloadedPack(
        String serverAddress,
        UUID transferId,
        String contentHash,
        Path stagingDirectory
    ) throws IOException {
        Path cacheRoot = getServerCacheRoot(serverAddress).normalize();
        Path staging = stagingDirectory.toAbsolutePath().normalize();
        if (!staging.startsWith(cacheRoot) || !contentHash.matches("[a-f0-9]{64}")) {
            throw new IOException("Invalid RadLink content cache destination");
        }

        Files.writeString(staging.resolve(HASH_FILE), contentHash, StandardCharsets.UTF_8);
        String directoryName = "pack-" + contentHash + "-" + transferId;
        Path packDirectory = cacheRoot.resolve(directoryName).normalize();
        if (!packDirectory.startsWith(cacheRoot) || Files.exists(packDirectory)) {
            throw new IOException("RadLink content cache destination already exists");
        }

        moveAtomically(staging, packDirectory);

        Path pointerTemp = Files.createTempFile(cacheRoot, ".current-", ".tmp");
        try {
            Files.writeString(pointerTemp, directoryName, StandardCharsets.UTF_8);
            moveAtomically(pointerTemp, cacheRoot.resolve(CURRENT_FILE), true);
        } finally {
            Files.deleteIfExists(pointerTemp);
        }

        if (FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT
            && getConfiguredServerAddress().equalsIgnoreCase(serverAddress)) {
            downloadedDirectory = packDirectory;
            manifestHash = contentHash;
            remoteTargetSelected = true;
        }
    }

    static String normalizeAddress(String address) {
        if (address == null) {
            return "";
        }

        String normalized = address.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }

        if (normalized.startsWith("[")) {
            int closingBracket = normalized.indexOf(']');
            if (closingBracket > 0) {
                return normalized.indexOf(':', closingBracket) >= 0
                    ? normalized
                    : normalized + ":25565";
            }
        }

        long colons = normalized.chars().filter(character -> character == ':').count();
        if (colons == 0) {
            return normalized + ":25565";
        }
        if (colons > 1) {
            return "[" + normalized + "]:25565";
        }
        return normalized;
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        moveAtomically(source, destination, false);
    }

    private static void moveAtomically(Path source, Path destination, boolean replaceExisting) throws IOException {
        try {
            if (replaceExisting) {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException e) {
            if (replaceExisting) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, destination);
            }
        }
    }
}
