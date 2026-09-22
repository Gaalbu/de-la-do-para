package br.com.deladopara.catalog.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalImageFileStoreTest {

    @TempDir
    Path directory;

    @Test
    void storesUsingOpaqueIdAndCanReadAfterConstructingAnotherAdapter() throws Exception {
        var id = UUID.randomUUID();
        var image = processedImage(new byte[] {1, 2, 3}, "png");
        var first = new LocalImageFileStore(directory);

        first.store(id, image);

        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactly(id + ".png");
        }
        assertThat(new LocalImageFileStore(directory).read(id, "png")).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void refusesToOverwriteAnExistingOpaqueId() throws Exception {
        var id = UUID.randomUUID();
        var store = new LocalImageFileStore(directory);
        store.store(id, processedImage(new byte[] {1}, "png"));

        assertThatThrownBy(() -> store.store(id, processedImage(new byte[] {2}, "png")))
                .isInstanceOf(java.io.IOException.class);
        assertThat(store.read(id, "png")).containsExactly((byte) 1);
    }

    @Test
    void rejectsUnrecognizedExtensionBeforeResolvingAPath() {
        var image = processedImage(new byte[] {1}, "../../outside");

        assertThatThrownBy(() -> new LocalImageFileStore(directory).store(UUID.randomUUID(), image))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removesOnlyTheGeneratedFile() throws Exception {
        var id = UUID.randomUUID();
        var store = new LocalImageFileStore(directory);
        store.store(id, processedImage(new byte[] {1}, "jpg"));

        store.delete(id, "jpg");

        assertThat(Files.exists(directory.resolve(id + ".jpg"))).isFalse();
        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    private static ImageContentProcessor.ProcessedImage processedImage(byte[] bytes, String extension) {
        return new ImageContentProcessor.ProcessedImage(bytes, "image/png", extension, 1, 1);
    }
}
