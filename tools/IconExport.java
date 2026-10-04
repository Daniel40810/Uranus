import com.dan.uranus.app.UranusApp;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Schreibt das Programmsymbol als icon/uranus.ico (alle Größen aus UranusApp.ICON_SIZES, je als PNG im ICO)
 * und icon/uranus-256.png. Dieselbe Zeichnung wie Fenster und Taskleiste (UranusApp.icons()).
 * Aufruf im Projektordner: java -cp build/classes;build/tools;lib/FStyle.jar IconExport .
 */
public class IconExport {

    public static void main(String[] a) throws IOException {
        File dir = new File(a.length > 0 ? a[0] : ".", "icon");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner nicht anlegbar: " + dir);
        List<Image> icons = UranusApp.icons();
        List<byte[]> png = new ArrayList<>();
        for (Image im : icons) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write((BufferedImage) im, "png", b);
            png.add(b.toByteArray());
        }
        File ico = new File(dir, "uranus.ico");
        try (OutputStream out = new FileOutputStream(ico)) {
            ByteBuffer h = ByteBuffer.allocate(6 + 16 * icons.size()).order(ByteOrder.LITTLE_ENDIAN);
            h.putShort((short) 0).putShort((short) 1).putShort((short) icons.size());
            int off = 6 + 16 * icons.size();
            for (int i = 0; i < icons.size(); i++) {
                int n = ((BufferedImage) icons.get(i)).getWidth();
                h.put((byte) (n >= 256 ? 0 : n)).put((byte) (n >= 256 ? 0 : n)).put((byte) 0).put((byte) 0);
                h.putShort((short) 1).putShort((short) 32).putInt(png.get(i).length).putInt(off);
                off += png.get(i).length;
            }
            out.write(h.array());
            for (byte[] p : png) out.write(p);
        }
        BufferedImage big = (BufferedImage) icons.get(icons.size() - 1);
        File p256 = new File(dir, "uranus-256.png");
        ImageIO.write(big, "png", p256);
        System.out.println(ico.getPath() + " (" + icons.size() + " Größen, " + ico.length() + " Bytes) · " + p256.getPath());
    }
}
