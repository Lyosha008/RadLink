package org.Lyosha008.radlink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.network.GatherLoginConfigurationTasksEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.config.ConfigurationTaskContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadlinkContentSync {
    private static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type("radlink:content_sync");
    private static final Set<String> ROOT_CONTENT_FILES = Set.of(
        "armor_item.json",
        "blocks.json",
        "effects.json",
        "global.json",
        "items.json",
        "menu_background.json",
        "pack.mcmeta"
    );
    private static final ConcurrentMap<Connection, ServerTransfer> SERVER_TRANSFERS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Connection, ClientTransfer> CLIENT_TRANSFERS = new ConcurrentHashMap<>();
    private static int cleanupTicks;
    private static int clientCleanupTicks;

    private RadlinkContentSync() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void gatherContentSyncTask(GatherLoginConfigurationTasksEvent event) {
        Connection connection = event.getConnection();
        if (connection.isMemoryConnection()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT).resolve("radlink");
        try {
            ContentSnapshot snapshot = scanContent(worldRoot);
            if (!snapshot.files.isEmpty()) {
                event.addTask(new ContentSyncTask(worldRoot, snapshot));
            }
        } catch (IOException e) {
            System.err.println("[RadLink] Не удалось подготовить контент для синхронизации: " + e.getMessage());
            event.addTask(new FailedContentTask(connection));
        }
    }

    @SubscribeEvent
    public static void cleanupDisconnectedTransfers(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++cleanupTicks < 200) {
            return;
        }
        cleanupTicks = 0;
        SERVER_TRANSFERS.keySet().removeIf(connection -> !connection.isConnected());
        CLIENT_TRANSFERS.keySet().removeIf(connection -> !connection.isConnected());
    }

    @SubscribeEvent
    public static void cleanupDisconnectedClientTransfers(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clientCleanupTicks < 20) {
            return;
        }
        clientCleanupTicks = 0;
        CLIENT_TRANSFERS.entrySet().removeIf(entry -> {
            if (entry.getKey().isConnected()) {
                return false;
            }
            entry.getValue().discard();
            return true;
        });
    }

    static void handleOffer(RadlinkNetwork.ContentOffer offer, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        Connection connection = context.getConnection();
        if (!isValidManifest(offer)) {
            disconnect(connection, "RadLink: получен повреждённый манифест контента.");
            return;
        }

        String serverAddress = StartupContentPack.getConfiguredServerAddress();
        if (serverAddress.isBlank()) {
            disconnect(connection,
                "RadLink: укажи адрес сервера в config/radlink-client.toml (serverOrWorldName), перезапусти игру и подключись снова.");
            return;
        }

        ServerData currentServer = Minecraft.getInstance().getCurrentServer();
        if (currentServer != null
            && !StartupContentPack.addressesMatch(serverAddress, currentServer.ip)) {
            disconnect(connection,
                "RadLink: serverOrWorldName должен совпадать с адресом выбранного сервера (порт можно не указывать). Исправь конфиг и подключись снова.");
            return;
        }

        if (offer.contentHash().equals(StartupContentPack.getManifestHash())) {
            RadlinkNetwork.send(new RadlinkNetwork.ContentResult(offer.transferId(), false, ""), connection);
            return;
        }

        try {
            ClientTransfer transfer = new ClientTransfer(serverAddress, offer);
            ClientTransfer previous = CLIENT_TRANSFERS.put(connection, transfer);
            if (previous != null) {
                previous.discard();
            }
            RadlinkNetwork.send(new RadlinkNetwork.ContentRequest(offer.transferId()), connection);
        } catch (IOException e) {
            System.err.println("[RadLink] Не удалось создать временное хранилище контента: " + e.getMessage());
            RadlinkNetwork.send(
                new RadlinkNetwork.ContentResult(offer.transferId(), false, "Не удалось подготовить место для загрузки."),
                connection
            );
        }
    }

    static void handleChunk(RadlinkNetwork.ContentChunk chunk, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        Connection connection = context.getConnection();
        ClientTransfer transfer = CLIENT_TRANSFERS.get(connection);
        if (transfer == null || !transfer.offer.transferId().equals(chunk.transferId())) {
            disconnect(connection, "RadLink: неожиданный фрагмент контента.");
            return;
        }

        try {
            transfer.accept(chunk);
        } catch (IOException | RuntimeException e) {
            System.err.println("[RadLink] Ошибка получения контента: " + e.getMessage());
            failClientTransfer(connection, transfer, "Не удалось сохранить контент сервера.");
        }
    }

    static void handleComplete(RadlinkNetwork.ContentComplete complete, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        Connection connection = context.getConnection();
        ClientTransfer transfer = CLIENT_TRANSFERS.get(connection);
        if (transfer == null || !transfer.offer.transferId().equals(complete.transferId())) {
            disconnect(connection, "RadLink: получено неожиданное завершение передачи.");
            return;
        }

        try {
            transfer.finish();
            CLIENT_TRANSFERS.remove(connection, transfer);
            RadlinkNetwork.send(new RadlinkNetwork.ContentResult(complete.transferId(), true, ""), connection);
        } catch (IOException | RuntimeException e) {
            System.err.println("[RadLink] Проверка загруженного контента не прошла: " + e.getMessage());
            failClientTransfer(connection, transfer, "Проверка контента сервера не прошла.");
        }
    }

    static void handleFailure(RadlinkNetwork.ContentFailure failure, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        Connection connection = context.getConnection();
        ClientTransfer transfer = CLIENT_TRANSFERS.remove(connection);
        if (transfer != null) {
            transfer.discard();
        }
        disconnect(connection, "RadLink: сервер не смог передать контент (" + failure.reason() + ").");
    }

    static void handleRequest(RadlinkNetwork.ContentRequest request, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        Connection connection = context.getConnection();
        ServerTransfer transfer = SERVER_TRANSFERS.get(connection);
        if (transfer == null || !transfer.snapshot.transferId.equals(request.transferId())) {
            disconnect(connection, "RadLink: неизвестный запрос синхронизации.");
            return;
        }

        if (transfer.started) {
            disconnect(connection, "RadLink: повторный запрос синхронизации.");
            return;
        }
        transfer.started = true;

        try {
            ContentSnapshot current = scanContent(transfer.worldRoot);
            if (!current.contentHash.equals(transfer.snapshot.contentHash)) {
                throw new IOException("Содержимое мира изменилось во время синхронизации");
            }
            sendContent(connection, current.withTransferId(transfer.snapshot.transferId));
        } catch (IOException e) {
            System.err.println("[RadLink] Ошибка передачи контента: " + e.getMessage());
            SERVER_TRANSFERS.remove(connection, transfer);
            RadlinkNetwork.send(
                new RadlinkNetwork.ContentFailure(request.transferId(), "ошибка чтения файлов мира"),
                connection
            );
        }
    }

    static void handleResult(RadlinkNetwork.ContentResult result, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        Connection connection = context.getConnection();
        ServerTransfer transfer = SERVER_TRANSFERS.get(connection);
        if (transfer == null || !transfer.snapshot.transferId.equals(result.transferId())) {
            disconnect(connection, "RadLink: неизвестный ответ синхронизации.");
            return;
        }

        SERVER_TRANSFERS.remove(connection, transfer);
        if (!result.error().isBlank()) {
            disconnect(connection, "RadLink: синхронизация не завершена (" + result.error() + ").");
        } else if (result.restartRequired()) {
            disconnect(connection,
                "RadLink: контент сервера сохранён. Полностью перезапусти Minecraft и подключись к серверу снова.");
        } else {
            transfer.context.finish(TASK_TYPE);
        }
    }

    private static void sendContent(Connection connection, ContentSnapshot snapshot) throws IOException {
        byte[] buffer = new byte[RadlinkNetwork.MAX_CHUNK_SIZE];
        for (int fileIndex = 0; fileIndex < snapshot.files.size(); fileIndex++) {
            ContentFile file = snapshot.files.get(fileIndex);
            long offset = 0;
            MessageDigest digest = newSha256();

            try (InputStream input = Files.newInputStream(file.path)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    byte[] data = java.util.Arrays.copyOf(buffer, read);
                    RadlinkNetwork.send(
                        new RadlinkNetwork.ContentChunk(snapshot.transferId, fileIndex, offset, data),
                        connection
                    );
                    offset += read;
                }
            }

            if (offset != file.entry.size()
                || !HexFormat.of().formatHex(digest.digest()).equals(file.entry.sha256())) {
                throw new IOException("Файл изменился после подготовки манифеста: " + file.entry.path());
            }
        }
        RadlinkNetwork.send(new RadlinkNetwork.ContentComplete(snapshot.transferId), connection);
    }

    private static ContentSnapshot scanContent(Path root) throws IOException {
        if (Files.notExists(root)) {
            return ContentSnapshot.empty();
        }
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Папка radlink на сервере не должна быть символической ссылкой");
        }

        List<Path> paths;
        try (var stream = Files.walk(root)) {
            paths = stream
                .filter(path -> !path.equals(root))
                .filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path))
                .filter(path -> !path.getFileName().toString().equalsIgnoreCase("variables.json"))
                .filter(path -> isSyncableContentPath(root.relativize(path).toString().replace('\\', '/')))
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .toList();
        }

        if (paths.size() > RadlinkNetwork.MAX_FILES) {
            throw new IOException("Слишком много файлов RadLink: " + paths.size());
        }

        List<ContentFile> files = new ArrayList<>(paths.size());
        long totalSize = 0;
        for (Path path : paths) {
            String relativePath = root.relativize(path).toString().replace('\\', '/');
            if (!isSafeRelativePath(relativePath)) {
                throw new IOException("Недопустимый путь файла RadLink: " + relativePath);
            }

            long size = Files.size(path);
            if (size > RadlinkNetwork.MAX_FILE_SIZE) {
                throw new IOException("Файл RadLink превышает лимит размера: " + relativePath);
            }
            totalSize += size;
            if (totalSize > RadlinkNetwork.MAX_TOTAL_SIZE) {
                throw new IOException("Общий размер файлов RadLink превышает 64 МиБ");
            }

            String fileHash = hashFile(path);
            files.add(new ContentFile(path, new RadlinkNetwork.FileEntry(relativePath, size, fileHash)));
        }

        List<RadlinkNetwork.FileEntry> entries = files.stream().map(file -> file.entry).toList();
        return new ContentSnapshot(UUID.randomUUID(), calculateManifestHash(entries), totalSize, files);
    }

    private static boolean isValidManifest(RadlinkNetwork.ContentOffer offer) {
        if (offer.totalSize() > RadlinkNetwork.MAX_TOTAL_SIZE
            || offer.files().size() > RadlinkNetwork.MAX_FILES) {
            return false;
        }

        Set<String> seenPaths = new HashSet<>();
        long measuredSize = 0;
        for (RadlinkNetwork.FileEntry file : offer.files()) {
            if (!isSafeRelativePath(file.path()) || !seenPaths.add(file.path())
                || file.size() < 0 || file.size() > RadlinkNetwork.MAX_FILE_SIZE
                || !file.sha256().matches("[a-f0-9]{64}")) {
                return false;
            }
            measuredSize += file.size();
            if (measuredSize > RadlinkNetwork.MAX_TOTAL_SIZE) {
                return false;
            }
        }
        return measuredSize == offer.totalSize()
            && calculateManifestHash(offer.files()).equals(offer.contentHash());
    }

    private static boolean isSafeRelativePath(String value) {
        if (value == null || value.isBlank() || value.length() > 512
            || value.startsWith("/") || value.contains("\\") || value.contains(":")) {
            return false;
        }

        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                || segment.chars().anyMatch(Character::isISOControl)) {
                return false;
            }
        }
        try {
            Path path = Path.of(value).normalize();
            return !path.isAbsolute() && !path.startsWith("..");
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static boolean isSyncableContentPath(String relativePath) {
        return ROOT_CONTENT_FILES.contains(relativePath)
            || relativePath.startsWith("assets/")
            || relativePath.startsWith("data/");
    }

    private static String calculateManifestHash(List<RadlinkNetwork.FileEntry> entries) {
        MessageDigest digest = newSha256();
        for (RadlinkNetwork.FileEntry entry : entries) {
            digest.update(entry.path().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(entry.size()).array());
            digest.update(HexFormat.of().parseHex(entry.sha256()));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hashFile(Path path) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[RadlinkNetwork.MAX_CHUNK_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void failClientTransfer(Connection connection, ClientTransfer transfer, String message) {
        CLIENT_TRANSFERS.remove(connection, transfer);
        transfer.discard();
        RadlinkNetwork.send(
            new RadlinkNetwork.ContentResult(transfer.offer.transferId(), false, message),
            connection
        );
    }

    private static void disconnect(Connection connection, String message) {
        connection.disconnect(Component.literal(message));
    }

    private record ContentFile(Path path, RadlinkNetwork.FileEntry entry) {}

    private static final class ContentSnapshot {
        private final UUID transferId;
        private final String contentHash;
        private final long totalSize;
        private final List<ContentFile> files;

        private ContentSnapshot(UUID transferId, String contentHash, long totalSize, List<ContentFile> files) {
            this.transferId = transferId;
            this.contentHash = contentHash;
            this.totalSize = totalSize;
            this.files = List.copyOf(files);
        }

        private static ContentSnapshot empty() {
            return new ContentSnapshot(new UUID(0, 0), calculateManifestHash(List.of()), 0, List.of());
        }

        private List<RadlinkNetwork.FileEntry> entries() {
            return files.stream().map(file -> file.entry).toList();
        }

        private ContentSnapshot withTransferId(UUID transferId) {
            return new ContentSnapshot(transferId, contentHash, totalSize, files);
        }
    }

    private static final class ContentSyncTask implements ConfigurationTask {
        private final Path worldRoot;
        private final ContentSnapshot snapshot;

        private ContentSyncTask(Path worldRoot, ContentSnapshot snapshot) {
            this.worldRoot = worldRoot;
            this.snapshot = snapshot;
        }

        @Override
        public void start(ConfigurationTaskContext context) {
            Connection connection = context.getConnection();
            ServerTransfer transfer = new ServerTransfer(context, worldRoot, snapshot);
            SERVER_TRANSFERS.put(connection, transfer);
            RadlinkNetwork.send(
                new RadlinkNetwork.ContentOffer(
                    snapshot.transferId,
                    snapshot.contentHash,
                    snapshot.totalSize,
                    snapshot.entries()
                ),
                connection
            );
        }

        @Override
        public void start(java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> sender) {
            throw new IllegalStateException("RadLink content synchronization requires the Forge task context");
        }

        @Override
        public ConfigurationTask.Type type() {
            return TASK_TYPE;
        }
    }

    private static final class FailedContentTask implements ConfigurationTask {
        private final Connection connection;

        private FailedContentTask(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void start(ConfigurationTaskContext context) {
            disconnect(connection, "RadLink: сервер не смог прочитать файлы мира.");
        }

        @Override
        public void start(java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> sender) {
            throw new IllegalStateException("RadLink content synchronization requires the Forge task context");
        }

        @Override
        public ConfigurationTask.Type type() {
            return TASK_TYPE;
        }
    }

    private static final class ServerTransfer {
        private final ConfigurationTaskContext context;
        private final Path worldRoot;
        private final ContentSnapshot snapshot;
        private boolean started;

        private ServerTransfer(ConfigurationTaskContext context, Path worldRoot, ContentSnapshot snapshot) {
            this.context = context;
            this.worldRoot = worldRoot;
            this.snapshot = snapshot;
        }
    }

    private static final class ClientTransfer {
        private final String serverAddress;
        private final RadlinkNetwork.ContentOffer offer;
        private final Path stagingDirectory;
        private int currentFileIndex;
        private long currentFileOffset;
        private long receivedBytes;

        private ClientTransfer(String serverAddress, RadlinkNetwork.ContentOffer offer) throws IOException {
            this.serverAddress = serverAddress;
            this.offer = offer;
            this.stagingDirectory = StartupContentPack.createStagingDirectory(serverAddress, offer.transferId());
            try {
                prepareFiles();
                skipEmptyFiles();
            } catch (IOException | RuntimeException e) {
                discard();
                throw e;
            }
        }

        private void prepareFiles() throws IOException {
            Path normalizedRoot = stagingDirectory.toAbsolutePath().normalize();
            for (RadlinkNetwork.FileEntry file : offer.files()) {
                Path destination = normalizedRoot.resolve(file.path()).normalize();
                if (!destination.startsWith(normalizedRoot)) {
                    throw new IOException("Путь файла выходит за пределы пакета: " + file.path());
                }
                Files.createDirectories(destination.getParent());
                Files.createFile(destination);
            }
        }

        private void accept(RadlinkNetwork.ContentChunk chunk) throws IOException {
            skipEmptyFiles();
            if (currentFileIndex >= offer.files().size() || chunk.fileIndex() != currentFileIndex
                || chunk.offset() != currentFileOffset) {
                throw new IOException("Нарушен порядок файловых фрагментов");
            }

            RadlinkNetwork.FileEntry file = offer.files().get(currentFileIndex);
            if (chunk.data().length > RadlinkNetwork.MAX_CHUNK_SIZE
                || currentFileOffset + chunk.data().length > file.size()) {
                throw new IOException("Фрагмент превышает размер файла");
            }

            Path destination = stagingDirectory.resolve(file.path()).normalize();
            try (FileChannel channel = FileChannel.open(destination, StandardOpenOption.WRITE)) {
                channel.position(currentFileOffset);
                ByteBuffer data = ByteBuffer.wrap(chunk.data());
                while (data.hasRemaining()) {
                    channel.write(data);
                }
            }

            currentFileOffset += chunk.data().length;
            receivedBytes += chunk.data().length;
            skipEmptyFiles();
        }

        private void finish() throws IOException {
            skipEmptyFiles();
            if (currentFileIndex != offer.files().size() || receivedBytes != offer.totalSize()) {
                throw new IOException("Получен не весь пакет RadLink");
            }

            for (RadlinkNetwork.FileEntry file : offer.files()) {
                Path path = stagingDirectory.resolve(file.path()).normalize();
                if (Files.size(path) != file.size() || !hashFile(path).equals(file.sha256())) {
                    throw new IOException("Контрольная сумма файла не совпадает: " + file.path());
                }
            }

            StartupContentPack.publishDownloadedPack(
                serverAddress,
                offer.transferId(),
                offer.contentHash(),
                stagingDirectory
            );
        }

        private void skipEmptyFiles() {
            while (currentFileIndex < offer.files().size()
                && offer.files().get(currentFileIndex).size() == currentFileOffset) {
                currentFileIndex++;
                currentFileOffset = 0;
            }
        }

        private void discard() {deleteTree(stagingDirectory);}
    }

    private static void deleteTree(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    System.err.println("[RadLink] Не удалось удалить временный файл " + path + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("[RadLink] Не удалось очистить временный кэш: " + e.getMessage());
        }
    }
}
