package br.com.deladopara.catalog.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageContentProcessorTest {

    private final ImageContentProcessor processor = new ImageContentProcessor();

    @Test
    void normalizesPngAndReportsDetectedContentType() throws Exception {
        var result = processor.process(image("png", 3, 2));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.width()).isEqualTo(3);
        assertThat(result.height()).isEqualTo(2);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes())))
                .isNotNull();
    }

    @Test
    void normalizesJpegAndReportsDetectedContentType() throws Exception {
        var result = processor.process(image("jpeg", 4, 3));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(result.width()).isEqualTo(4);
        assertThat(result.height()).isEqualTo(3);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes())))
                .isNotNull();
    }

    @Test
    void rejectsSvgAndOtherNonRasterContent() {
        var svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processor.process(svg))
                .isInstanceOf(ImageContentProcessor.InvalidImageException.class);
    }

    @Test
    void rejectsCorruptRasterContent() {
        assertThatThrownBy(() -> processor.process(new byte[] {(byte) 0x89, 'P', 'N', 'G'}))
                .isInstanceOf(ImageContentProcessor.InvalidImageException.class);
    }

    @Test
    void rejectsPayloadLargerThanFiveMebibytes() {
        assertThatThrownBy(() -> processor.process(new byte[5 * 1024 * 1024 + 1]))
                .isInstanceOf(ImageContentProcessor.ImageTooLargeException.class);
    }

    @Test
    void rejectsImageWithDimensionOverSixThousandPixelsBeforeDecoding() throws Exception {
        assertThatThrownBy(() -> processor.process(image("png", 6001, 1)))
                .isInstanceOf(ImageContentProcessor.ImageTooLargeException.class);
    }

    @Test
    void rejectsImageWithPixelCountOverTwelveMegapixelsBeforeDecoding() throws Exception {
        assertThatThrownBy(() -> processor.process(image("png", 3600, 3600)))
                .isInstanceOf(ImageContentProcessor.ImageTooLargeException.class);
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        int imageType = format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        var image = new BufferedImage(width, height, imageType);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
