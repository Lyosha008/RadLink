package org.Lyosha008.radlink;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
        Path localWorld = getLocalWorldContentDirectory(gameDir, target);
        Path sanitizedLocalWorld = getLocalWorldContentDirectory(gameDir, safeName);
        if ((localWorld != null && Files.isDirectory(localWorld))
            || (sanitizedLocalWorld != null && Files.isDirectory(sanitizedLocalWorld))) {
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
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("radlink-client.toml");
        if (Files.isRegularFile(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                CommentedConfig config = new TomlParser().parse(reader);
                String address = config.get("Menu Settings.serverOrWorldName");
                return address == null ? "" : address.trim();
            } catch (IOException | RuntimeException e) {
                System.err.println("[RadLink] Не удалось прочитать раннюю настройку serverOrWorldName: " + e.getMessage());
            }
        }

        try {
            return RadlinkConfig.SERVER_OR_WORLD_NAME.get().trim();
        } catch (IllegalStateException e) {
            return "";
        }
    }

    static Path getServerCacheRoot(String serverAddress) {
        String safeName = getAddressHost(serverAddress).replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return FMLPaths.GAMEDIR.get()
            .resolve("radlink")
            .resolve("servers")
            .resolve(safeName)
            .normalize();
    }

    static Path getLocalWorldContentDirectory(Path gameDir, String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        try {
            Path name = Path.of(worldName);
            if (name.isAbsolute() || name.getNameCount() != 1) {
                return null;
            }

            Path savesDirectory = gameDir.resolve("saves").normalize();
            Path worldDirectory = savesDirectory.resolve(name).normalize();
            if (!worldDirectory.startsWith(savesDirectory) || worldDirectory.equals(savesDirectory)) {
                return null;
            }
            return worldDirectory.resolve("radlink");
        } catch (InvalidPathException e) {
            return null;
        }
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

        cleanupOldPacks(cacheRoot, packDirectory);

        if (FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT
            && getConfiguredServerAddress().equalsIgnoreCase(serverAddress)) {
            downloadedDirectory = packDirectory;
            manifestHash = contentHash;
            remoteTargetSelected = true;
        }
    }

    static boolean addressesMatch(String configuredAddress, String actualAddress) {
        if (hasExplicitPort(configuredAddress)) {
            return normalizeAddress(configuredAddress).equals(normalizeAddress(actualAddress));
        }
        return getAddressHost(configuredAddress).equalsIgnoreCase(getAddressHost(actualAddress));
    }

    private static boolean hasExplicitPort(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }

        String normalized = address.trim();
        int closingBracket = normalized.startsWith("[") ? normalized.indexOf(']') : -1;
        if (closingBracket >= 0) {
            return normalized.length() > closingBracket + 2
                && normalized.charAt(closingBracket + 1) == ':'
                && isNumericPort(normalized.substring(closingBracket + 2));
        }

        int firstColon = normalized.indexOf(':');
        return firstColon >= 0
            && firstColon == normalized.lastIndexOf(':')
            && isNumericPort(normalized.substring(firstColon + 1));
    }

    private static String getAddressHost(String address) {
        if (address == null) {
            return "";
        }

        String normalized = address.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("[")) {
            int closingBracket = normalized.indexOf(']');
            if (closingBracket > 0) {
                return normalized.substring(0, closingBracket + 1);
            }
        }

        int firstColon = normalized.indexOf(':');
        if (firstColon >= 0 && firstColon == normalized.lastIndexOf(':')
            && isNumericPort(normalized.substring(firstColon + 1))) {
            return normalized.substring(0, firstColon);
        }
        return normalized;
    }

    private static boolean isNumericPort(String value) {
        return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }

    private static void cleanupOldPacks(Path cacheRoot, Path currentPack) {
        try (var entries = Files.list(cacheRoot)) {
            for (Path entry : entries.filter(Files::isDirectory).toList()) {
                if (!entry.equals(currentPack) && entry.getParent().equals(cacheRoot)
                    && PACK_DIRECTORY_PATTERN.matches(entry.getFileName().toString())) {
                    try {
                        deleteCacheDirectory(entry);
                    } catch (IOException e) {
                        System.err.println("[RadLink] Не удалось удалить старый кэш " + entry + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[RadLink] Не удалось очистить старые версии кэша: " + e.getMessage());
        }
    }

    private static void deleteCacheDirectory(Path directory) throws IOException {
        try (var entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
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
