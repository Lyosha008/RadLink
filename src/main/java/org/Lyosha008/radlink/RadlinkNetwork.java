package org.Lyosha008.radlink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RadlinkNetwork {
    public static final int MAX_FILES = 1024;
    public static final int MAX_FILE_SIZE = 32 * 1024 * 1024;
    public static final long MAX_TOTAL_SIZE = 64L * 1024L * 1024L;
    public static final int MAX_CHUNK_SIZE = 24 * 1024;
    private static final SimpleChannel CHANNEL = ChannelBuilder
        .named(new ResourceLocation(Radlink.MOD_ID, "content_sync"))
        .networkProtocolVersion(1)
        .clientAcceptedVersions(Channel.VersionTest.exact(1))
        .serverAcceptedVersions(Channel.VersionTest.exact(1))
        .simpleChannel();

    private RadlinkNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(ContentOffer.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ContentOffer::encode)
            .decoder(ContentOffer::decode)
            .consumerMainThread(RadlinkContentSync::handleOffer)
            .add();
        CHANNEL.messageBuilder(ContentChunk.class, 1, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ContentChunk::encode)
            .decoder(ContentChunk::decode)
            .consumerMainThread(RadlinkContentSync::handleChunk)
            .add();
        CHANNEL.messageBuilder(ContentComplete.class, 2, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ContentComplete::encode)
            .decoder(ContentComplete::decode)
            .consumerMainThread(RadlinkContentSync::handleComplete)
            .add();
        CHANNEL.messageBuilder(ContentFailure.class, 3, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ContentFailure::encode)
            .decoder(ContentFailure::decode)
            .consumerMainThread(RadlinkContentSync::handleFailure)
            .add();
        CHANNEL.messageBuilder(ContentRequest.class, 4, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ContentRequest::encode)
            .decoder(ContentRequest::decode)
            .consumerMainThread(RadlinkContentSync::handleRequest)
            .add();
        CHANNEL.messageBuilder(ContentResult.class, 5, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ContentResult::encode)
            .decoder(ContentResult::decode)
            .consumerMainThread(RadlinkContentSync::handleResult)
            .add();
    }

    static void send(Object message, net.minecraft.network.Connection connection) {
        CHANNEL.send(message, connection);
    }

    public record FileEntry(String path, long size, String sha256) {}

    public record ContentOffer(UUID transferId, String contentHash, long totalSize, List<FileEntry> files) {
        public ContentOffer {
            files = List.copyOf(files);
        }

        private static void encode(ContentOffer message, FriendlyByteBuf buffer) {
            writeUuid(buffer, message.transferId);
            buffer.writeUtf(message.contentHash, 64);
            buffer.writeVarLong(message.totalSize);
            buffer.writeVarInt(message.files.size());
            for (FileEntry file : message.files) {
                buffer.writeUtf(file.path, 512);
                buffer.writeVarLong(file.size);
                buffer.writeUtf(file.sha256, 64);
            }
        }

        private static ContentOffer decode(FriendlyByteBuf buffer) {
            UUID transferId = readUuid(buffer);
            String contentHash = buffer.readUtf(64);
            long totalSize = buffer.readVarLong();
            int fileCount = buffer.readVarInt();
            if (!contentHash.matches("[a-f0-9]{64}") || totalSize < 0
                || totalSize > MAX_TOTAL_SIZE || fileCount < 1 || fileCount > MAX_FILES) {
                throw new IllegalArgumentException("Invalid RadLink content manifest");
            }

            List<FileEntry> files = new ArrayList<>(fileCount);
            long measuredTotal = 0;
            for (int i = 0; i < fileCount; i++) {
                String path = buffer.readUtf(512);
                long size = buffer.readVarLong();
                String hash = buffer.readUtf(64);
                if (size < 0 || size > MAX_FILE_SIZE || !hash.matches("[a-f0-9]{64}")) {
                    throw new IllegalArgumentException("Invalid RadLink content file entry");
                }
                measuredTotal += size;
                if (measuredTotal > MAX_TOTAL_SIZE) {
                    throw new IllegalArgumentException("RadLink content manifest exceeds size limit");
                }
                files.add(new FileEntry(path, size, hash));
            }
            if (measuredTotal != totalSize) {
                throw new IllegalArgumentException("RadLink content manifest size mismatch");
            }
            return new ContentOffer(transferId, contentHash, totalSize, files);
        }
    }

    public record ContentChunk(UUID transferId, int fileIndex, long offset, byte[] data) {
        private static void encode(ContentChunk message, FriendlyByteBuf buffer) {
            writeUuid(buffer, message.transferId);
            buffer.writeVarInt(message.fileIndex);
            buffer.writeVarLong(message.offset);
            buffer.writeByteArray(message.data);
        }

        private static ContentChunk decode(FriendlyByteBuf buffer) {
            UUID transferId = readUuid(buffer);
            int fileIndex = buffer.readVarInt();
            long offset = buffer.readVarLong();
            byte[] data = buffer.readByteArray(MAX_CHUNK_SIZE);
            if (fileIndex < 0 || offset < 0 || data.length == 0) {
                throw new IllegalArgumentException("Invalid RadLink content chunk");
            }
            return new ContentChunk(transferId, fileIndex, offset, data);
        }
    }

    public record ContentComplete(UUID transferId) {
        private static void encode(ContentComplete message, FriendlyByteBuf buffer) {
            writeUuid(buffer, message.transferId);
        }

        private static ContentComplete decode(FriendlyByteBuf buffer) {
            return new ContentComplete(readUuid(buffer));
        }
    }

    public record ContentFailure(UUID transferId, String reason) {
        private static void encode(ContentFailure message, FriendlyByteBuf buffer) {
            writeUuid(buffer, message.transferId);
            buffer.writeUtf(message.reason, 256);
        }

        private static ContentFailure decode(FriendlyByteBuf buffer) {
            return new ContentFailure(readUuid(buffer), buffer.readUtf(256));
        }
    }

    public record ContentRequest(UUID transferId) {
        private static void encode(ContentRequest message, FriendlyByteBuf buffer) {
            writeUuid(buffer, message.transferId);
        }

        private static ContentRequest decode(FriendlyByteBuf buffer) {
            return new ContentRequest(readUuid(buffer));
        }
    }

    public record ContentResult(UUID transferId, boolean restartRequired, String error) {
        private static void encode(ContentResult message, FriendlyByteBuf buffer) {
            writeUuid(buffer, message.transferId);
            buffer.writeBoolean(message.restartRequired);
            buffer.writeUtf(message.error, 256);
        }

        private static ContentResult decode(FriendlyByteBuf buffer) {
            return new ContentResult(readUuid(buffer), buffer.readBoolean(), buffer.readUtf(256));
        }
    }

    private static void writeUuid(FriendlyByteBuf buffer, UUID value) {
        buffer.writeLong(value.getMostSignificantBits());
        buffer.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(FriendlyByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }
}
