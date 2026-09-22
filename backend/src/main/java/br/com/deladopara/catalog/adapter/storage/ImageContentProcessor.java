package br.com.deladopara.catalog.adapter.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class ImageContentProcessor {

    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 6000;
    private static final long MAX_PIXELS = 12_000_000;

    public ProcessedImage process(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidImageException();
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new ImageTooLargeException();
        }

        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidImageException();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                var format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                var outputFormat =
                        switch (format) {
                            case "png" -> new Format("image/png", "png", "png");
                            case "jpeg", "jpg" -> new Format("image/jpeg", "jpg", "jpeg");
                            default -> throw new InvalidImageException();
                        };

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new ImageTooLargeException();
                }
                if ((long) width * height > MAX_PIXELS) {
                    throw new ImageTooLargeException();
                }

                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != width || image.getHeight() != height) {
                    throw new InvalidImageException();
                }

                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, outputFormat.writerFormat(), output)) {
                    throw new InvalidImageException();
                }
                byte[] normalized = output.toByteArray();
                if (normalized.length > MAX_FILE_BYTES) {
                    throw new ImageTooLargeException();
                }
                return new ProcessedImage(
                        normalized, outputFormat.contentType(), outputFormat.extension(), width, height);
            } finally {
                reader.dispose();
            }
        } catch (ImageTooLargeException | InvalidImageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidImageException();
        }
    }

    public record ProcessedImage(byte[] bytes, String contentType, String extension, int width, int height) {

        public ProcessedImage {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record Format(String contentType, String extension, String writerFormat) {}

    public static class InvalidImageException extends RuntimeException {}

    public static class ImageTooLargeException extends RuntimeException {}
}
