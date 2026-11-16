import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.jetbrains.skia.Data;
import org.jetbrains.skia.EncodedImageFormat;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.svg.SVGDOM;

/** Generates exports from the checked-in vector and original generated raster masters. */
public final class GenerateBrandAssets {
    private static final Pattern SVG_ROOT = Pattern.compile("<svg\\b[^>]*>");

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected master SVG, raster artwork and output directory");
        String logo = Files.readString(Path.of(args[0]));
        BufferedImage rasterLogo = decodeRaster(Path.of(args[1]));
        Path output = Path.of(args[2]);
        Renderer appIcon = size -> appPng(rasterLogo, size);
        write(output.resolve("app/maryk-app.png"), appIcon.render(512));
        write(output.resolve("resources/maryk/app-icon.png"), appIcon.render(512));
        write(output.resolve("app/maryk-app.ico"), ico(appIcon));
        write(output.resolve("app/maryk-app.icns"), icns(appIcon));
        write(output.resolve("logo/maryk-logo.svg"), logo.getBytes(StandardCharsets.UTF_8));
        write(output.resolve("logo/maryk-logo.png"), png(logo, 1024));
        write(output.resolve("logo/maryk-logo-small.png"), png(logo, 256));
        write(output.resolve("logo/favicon.png"), png(logo, 64));
        write(output.resolve("logo/favicon-32.png"), png(logo, 32));
        write(output.resolve("logo/favicon.ico"), ico(size -> png(logo, size)));
    }

    private static String sizeRoot(String svg, String attributes) {
        var matcher = SVG_ROOT.matcher(svg);
        if (!matcher.find()) throw new IllegalArgumentException("Master must contain an SVG root");
        String root = matcher.group().replaceAll("\\s+(?:width|height|x|y)=\"[^\"]*\"", "");
        String replacement = root.substring(0, root.length() - 1) + " " + attributes + ">";
        return svg.substring(0, matcher.start()) + replacement + svg.substring(matcher.end());
    }

    private static String desktopBackground() {
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
              <defs>
                <linearGradient id="app-backplate" x2="0.7" y2="1">
                  <stop stop-color="#173f59"/><stop offset="1" stop-color="#111d36"/>
                </linearGradient>
                <radialGradient id="app-halo" cx=".5" cy=".57" r=".48">
                  <stop stop-color="#a1d8ee" stop-opacity=".55"/>
                  <stop offset="1" stop-color="#a1d8ee" stop-opacity="0"/>
                </radialGradient>
              </defs>
              <rect x="64" y="64" width="896" height="896" rx="196" fill="url(#app-backplate)"/>
              <rect x="64" y="64" width="896" height="896" rx="196" fill="url(#app-halo)"/>
            </svg>
            """;
    }

    @FunctionalInterface
    private interface Renderer {
        byte[] render(int size) throws Exception;
    }

    private static BufferedImage decodeRaster(Path path) throws Exception {
        // Standard ImageIO has no WebP decoder. Reuse Skia, then hand a PNG to AWT.
        try (Image image = Image.Companion.makeFromEncoded(Files.readAllBytes(path));
             Data encoded = Objects.requireNonNull(image.encodeToData(EncodedImageFormat.PNG, 100, 0))) {
            return Objects.requireNonNull(ImageIO.read(new ByteArrayInputStream(encoded.getBytes())));
        }
    }

    private static byte[] appPng(BufferedImage logo, int size) throws Exception {
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(png(desktopBackground(), size)));
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            float scale = size / 1024f;
            graphics.clip(new RoundRectangle2D.Float(64 * scale, 64 * scale, 896 * scale, 896 * scale, 392 * scale, 392 * scale));
            int x = Math.round(size * 56f / 1024);
            int y = Math.round(size * 70f / 1024);
            int markSize = Math.round(size * 912f / 1024);
            graphics.drawImage(logo, x, y, markSize, markSize, null);
        } finally {
            graphics.dispose();
        }
        var encoded = new ByteArrayOutputStream();
        ImageIO.write(result, "png", encoded);
        return encoded.toByteArray();
    }

    private static byte[] png(String svg, int size) {
        // Skia's SVG DOM uses SVG 1.1 references; the editable master uses SVG 2.
        String compatible = svg.replace(" href=", " xlink:href=");
        if (!compatible.contains("xmlns:xlink=")) {
            compatible = compatible.replaceFirst("<svg\\s", "<svg xmlns:xlink=\"http://www.w3.org/1999/xlink\" ");
        }
        byte[] bytes = sizeRoot(compatible, "width=\"" + size + "\" height=\"" + size + "\"")
            .getBytes(StandardCharsets.UTF_8);
        try (Data source = Data.Companion.makeFromBytes(bytes, 0, bytes.length);
             SVGDOM document = new SVGDOM(source);
             Surface surface = Objects.requireNonNull(Surface.Companion.makeRasterN32Premul(size, size))) {
            document.setContainerSize(size, size);
            surface.getCanvas().clear(0);
            document.render(surface.getCanvas());
            try (Image image = surface.makeImageSnapshot();
                 Data encoded = Objects.requireNonNull(image.encodeToData(EncodedImageFormat.PNG, 100, 0))) {
                return encoded.getBytes();
            }
        }
    }

    private static byte[] ico(Renderer renderer) throws Exception {
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        Map<Integer, byte[]> images = new LinkedHashMap<>();
        for (int size : sizes) images.put(size, renderer.render(size));
        ByteBuffer directory = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        directory.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = directory.capacity();
        for (var entry : images.entrySet()) {
            int size = entry.getKey();
            directory.put((byte) (size == 256 ? 0 : size)).put((byte) (size == 256 ? 0 : size));
            directory.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            directory.putInt(entry.getValue().length).putInt(offset);
            offset += entry.getValue().length;
        }
        var result = new ByteArrayOutputStream();
        result.write(directory.array());
        for (byte[] image : images.values()) result.write(image);
        return result.toByteArray();
    }

    private static byte[] icns(Renderer renderer) throws Exception {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("icp4", 16); types.put("icp5", 32); types.put("icp6", 64);
        types.put("ic07", 128); types.put("ic08", 256); types.put("ic09", 512); types.put("ic10", 1024);
        types.put("ic11", 32); types.put("ic12", 64); types.put("ic13", 256); types.put("ic14", 512);
        var chunks = new ByteArrayOutputStream();
        var stream = new DataOutputStream(chunks);
        for (var entry : types.entrySet()) {
            byte[] image = renderer.render(entry.getValue());
            stream.writeBytes(entry.getKey());
            stream.writeInt(8 + image.length);
            stream.write(image);
        }
        var result = new ByteArrayOutputStream();
        var container = new DataOutputStream(result);
        container.writeBytes("icns");
        container.writeInt(8 + chunks.size());
        chunks.writeTo(result);
        return result.toByteArray();
    }

    private static void write(Path path, byte[] bytes) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }
}
