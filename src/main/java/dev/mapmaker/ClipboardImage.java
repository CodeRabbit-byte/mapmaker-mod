package dev.mapmaker;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Reads an image from the system clipboard. Never throws; returns null if there is none. */
final class ClipboardImage {
    private ClipboardImage() {}

    static BufferedImage read() {
        // AWT and Minecraft's GLFW window do not get along on macOS; use Upload / drag & drop there.
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) return null;
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null) return null;
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return toBuffered((Image) t.getTransferData(DataFlavor.imageFlavor));
            }
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<?> files = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) return ImageIO.read((File) files.get(0));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static BufferedImage toBuffered(Image img) {
        if (img instanceof BufferedImage bi) return bi;
        int w = img.getWidth(null), h = img.getHeight(null);
        if (w <= 0 || h <= 0) return null;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }
}
