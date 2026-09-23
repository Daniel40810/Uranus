package com.dan.uranus.app;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standbild als PNG mit Textfeldern (iTXt, UTF-8): Titel, Szene, Sonnenstand, Einstellungen, Zeitpunkt.
 * Ordner: {@code Bilder\Uranus} (unter Windows der Ordner „Bilder“ = {@code %USERPROFILE%\Pictures}),
 * oder {@code -Duranus.bilder}. Der Dateiname trägt das Datum der Szene und ist immer neu.
 */
public final class Snapshot {

    private Snapshot() { }

    /** Zielordner der Standbilder. */
    public static File folder() {
        String p = System.getProperty("uranus.bilder");
        if (p != null && !p.isBlank()) return new File(p);
        File home = new File(System.getProperty("user.home", "."));
        for (String n : new String[]{"Pictures", "Bilder"}) {
            File f = new File(home, n);
            if (f.isDirectory()) return new File(f, "Uranus");
        }
        return new File(home, "Uranus-Bilder");
    }

    /** Freier Dateiname „Uranus_JJJJ-MM-TT_hhmmss.png“ nach dem Datum der Szene, bei Bedarf mit _2, _3 … */
    public static File target(File dir, ZonedDateTime scene) {
        String base = "Uranus_" + scene.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        File f = new File(dir, base + ".png");
        for (int i = 2; f.exists(); i++) f = new File(dir, base + "_" + i + ".png");
        return f;
    }

    /** Schreibt das Bild mit Textfeldern; gibt die Datei zurück. */
    public static synchronized File write(BufferedImage img, Map<String, String> info, File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner nicht anlegbar: " + dir);
        ZonedDateTime scene;
        try { scene = java.time.Instant.parse(info.getOrDefault("Szene", "")).atZone(ZoneId.systemDefault()); }
        catch (RuntimeException e) { scene = ZonedDateTime.now(); }
        File out = target(dir, scene);
        ImageWriter w = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        IIOMetadata meta = w.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), p);
        String fmt = "javax_imageio_png_1.0";
        IIOMetadataNode root = new IIOMetadataNode(fmt), itxt = new IIOMetadataNode("iTXt");
        for (Map.Entry<String, String> e : info.entrySet()) {
            IIOMetadataNode n = new IIOMetadataNode("iTXtEntry");
            n.setAttribute("keyword", e.getKey());
            n.setAttribute("compressionFlag", "FALSE");
            n.setAttribute("compressionMethod", "0");
            n.setAttribute("languageTag", "de");
            n.setAttribute("translatedKeyword", "");
            n.setAttribute("text", e.getValue());
            itxt.appendChild(n);
        }
        root.appendChild(itxt);
        meta.mergeTree(fmt, root);
        try (ImageOutputStream os = ImageIO.createImageOutputStream(out)) {
            w.setOutput(os);
            w.write(null, new IIOImage(img, null, meta), p);
        } finally {
            w.dispose();
        }
        return out;
    }

    /** Liest die Textfelder eines PNG (für Prüfungen). */
    public static Map<String, String> readInfo(File f) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(f)) {
            javax.imageio.ImageReader r = ImageIO.getImageReaders(in).next();
            r.setInput(in);
            org.w3c.dom.Node root = r.getImageMetadata(0).getAsTree("javax_imageio_png_1.0");
            for (org.w3c.dom.Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!"iTXt".equals(n.getNodeName())) continue;
                for (org.w3c.dom.Node e = n.getFirstChild(); e != null; e = e.getNextSibling()) {
                    org.w3c.dom.NamedNodeMap a = e.getAttributes();
                    m.put(a.getNamedItem("keyword").getNodeValue(), a.getNamedItem("text").getNodeValue());
                }
            }
            r.dispose();
        }
        return m;
    }
}
