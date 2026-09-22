package br.com.deladopara.catalog.domain;

public record ProductImageDetails(
        String contentType,
        String extension,
        int width,
        int height,
        String altText,
        String source,
        String license,
        String creator,
        String attribution,
        boolean rightsReviewed) {

    private static final long MAX_PIXELS = 12_000_000L;

    public ProductImageDetails {
        if (!(("image/jpeg".equals(contentType) && "jpg".equals(extension))
                || ("image/png".equals(contentType) && "png".equals(extension)))) {
            throw new IllegalArgumentException("Image content type and extension must match JPEG or PNG");
        }
        if (width <= 0 || height <= 0 || width > 6000 || height > 6000 || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("Image dimensions exceed the processing limits");
        }
        altText = required(altText, "alt text", 250);
        source = required(source, "source", 1500);
        license = required(license, "license", 200);
        creator = optional(creator, "creator", 200);
        attribution = optional(attribution, "attribution", 500);
        if (!rightsReviewed) {
            throw new IllegalArgumentException("Image rights must be reviewed");
        }
    }

    private static String required(String value, String field, int max) {
        String result = optional(value, field, max);
        if (result.isBlank()) {
            throw new IllegalArgumentException("Image " + field + " is required");
        }
        return result;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.length() > max) {
            throw new IllegalArgumentException("Image " + field + " exceeds its limit");
        }
        return value.trim();
    }
}
