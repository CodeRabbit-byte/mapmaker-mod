package dev.mapmaker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/** Image -> palette indices (scaling, cropping, Floyd-Steinberg dithering). */
public final class MapConverter {

    public record Result(int w, int h, boolean stairs, int[] pick, List<MapPalette.Choice> palette) {
        public int rgbAt(int i) { return palette.get(pick[i]).rgb(); }
    }

    private MapConverter() {}

    public static Result convert(BufferedImage src, int mapsW, int mapsH, String fit, boolean dither,
                                 boolean stairs, List<MapPalette.Choice> palette) {
        int w = mapsW * 128, h = mapsH * 128, n = w * h;
        BufferedImage img = resize(src, w, h, fit);
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);

        float[] r = new float[n], g = new float[n], b = new float[n];
        for (int i = 0; i < n; i++) {
            r[i] = px[i] >> 16 & 255;
            g[i] = px[i] >> 8 & 255;
            b[i] = px[i] & 255;
        }

        int pn = palette.size();
        int[] pr = new int[pn], pg = new int[pn], pb = new int[pn];
        for (int i = 0; i < pn; i++) {
            int c = palette.get(i).rgb();
            pr[i] = c >> 16 & 255;
            pg[i] = c >> 8 & 255;
            pb[i] = c & 255;
        }

        int[] pick = new int[n];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int cr = clamp(r[i]), cg = clamp(g[i]), cb = clamp(b[i]);
                int best = 0;
                long bd = Long.MAX_VALUE;
                for (int k = 0; k < pn; k++) {
                    int rm = (cr + pr[k]) >> 1;
                    int dr = cr - pr[k], dg = cg - pg[k], db = cb - pb[k];
                    long d = (((512 + rm) * dr * dr) >> 8) + 4L * dg * dg + (((767 - rm) * db * db) >> 8);
                    if (d < bd) { bd = d; best = k; }
                }
                pick[i] = best;
                if (dither) {
                    float er = cr - pr[best], eg = cg - pg[best], eb = cb - pb[best];
                    spread(r, g, b, w, h, x + 1, y, er, eg, eb, 7 / 16f);
                    spread(r, g, b, w, h, x - 1, y + 1, er, eg, eb, 3 / 16f);
                    spread(r, g, b, w, h, x, y + 1, er, eg, eb, 5 / 16f);
                    spread(r, g, b, w, h, x + 1, y + 1, er, eg, eb, 1 / 16f);
                }
            }
        }
        return new Result(w, h, stairs, pick, palette);
    }

    private static void spread(float[] r, float[] g, float[] b, int w, int h, int x, int y,
                               float er, float eg, float eb, float f) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        r[i] += er * f;
        g[i] += eg * f;
        b[i] += eb * f;
    }

    private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : (int) v; }

    private static BufferedImage resize(BufferedImage src, int tw, int th, String fit) {
        double sw = src.getWidth(), sh = src.getHeight();
        int sx = 0, sy = 0, sW = (int) sw, sH = (int) sh;
        int dx = 0, dy = 0, dw = tw, dh = th;
        if ("CROP".equals(fit)) {
            double scale = Math.max(tw / sw, th / sh);
            sW = Math.max(1, (int) Math.round(tw / scale));
            sH = Math.max(1, (int) Math.round(th / scale));
            sx = Math.max(0, (int) ((sw - sW) / 2));
            sy = Math.max(0, (int) ((sh - sH) / 2));
            sW = Math.min(sW, src.getWidth() - sx);
            sH = Math.min(sH, src.getHeight() - sy);
        } else if ("FIT".equals(fit)) {
            double scale = Math.min(tw / sw, th / sh);
            dw = Math.max(1, (int) Math.round(sw * scale));
            dh = Math.max(1, (int) Math.round(sh * scale));
            dx = (tw - dw) / 2;
            dy = (th - dh) / 2;
        }
        BufferedImage part = src.getSubimage(sx, sy, sW, sH);
        part = halve(part, dw, dh);

        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, tw, th);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(part, dx, dy, dx + dw, dy + dh, 0, 0, part.getWidth(), part.getHeight(), null);
        g.dispose();
        return out;
    }

    /** Repeated 2x shrinking keeps big photos from turning into aliased noise. */
    private static BufferedImage halve(BufferedImage img, int tw, int th) {
        while (img.getWidth() >= tw * 2 && img.getHeight() >= th * 2) {
            int nw = img.getWidth() / 2, nh = img.getHeight() / 2;
            BufferedImage o = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = o.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = o;
        }
        return img;
    }
}
