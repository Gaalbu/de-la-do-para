package br.com.deladopara.catalog.adapter.storage;

import br.com.deladopara.catalog.adapter.storage.ImageContentProcessor.ProcessedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalImageFileStore {

    private final Path directory;

    @Autowired
    public LocalImageFileStore(@Value("${app.media.directory:./data/media}") String directory) {
        this(Path.of(directory));
    }

    LocalImageFileStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public void store(UUID id, ProcessedImage image) throws IOException {
        var destination = resolve(id, image.extension());
        Files.createDirectories(directory);
        if (Files.exists(destination)) {
            throw new FileAlreadyExistsException(destination.getFileName().toString());
        }

        var temporary = Files.createTempFile(directory, ".image-", ".pending");
        try {
            Files.write(temporary, image.bytes());
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public byte[] read(UUID id, String extension) throws IOException {
        return Files.readAllBytes(resolve(id, extension));
    }

    public void delete(UUID id, String extension) throws IOException {
        Files.deleteIfExists(resolve(id, extension));
    }

    private Path resolve(UUID id, String extension) {
        if (id == null) {
            throw new IllegalArgumentException("Image identity is required");
        }
        if (!"jpg".equals(extension) && !"png".equals(extension)) {
            throw new IllegalArgumentException("Image format is not supported");
        }
        var path = directory.resolve(id + "." + extension).normalize();
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException("Image path is invalid");
        }
        return path;
    }
}
