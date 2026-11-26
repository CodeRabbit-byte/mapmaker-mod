package dev.mapmaker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MapMakerScreen extends Screen {
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mapmaker-convert");
        t.setDaemon(true);
        return t;
    });
    private static final String[] FITS = {"CROP", "FIT", "STRETCH"};
    private static final int COL_W = 190;

    // survive closing/reopening the screen
    private static BufferedImage lastSource;

    private final Screen parent;
    private final MapMakerConfig cfg = MapMakerConfig.get();
    private final AtomicInteger gen = new AtomicInteger();

    private TextFieldWidget nameField;
    private volatile MapConverter.Result result;
    private volatile int[] preview;       // rgb cells
    private volatile int pvW, pvH;
    private volatile String status = "Paste (Ctrl+V), upload, or drag an image onto this window.";
    private volatile boolean statusError;
    private boolean listeningKey;
    private int top, pX, pY, pSize;

    public MapMakerScreen(Screen parent) {
        super(Text.literal("MapMaker"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        top = Math.max(18, (height - 232) / 2);
        int x = width - COL_W - 12;
        int half = (COL_W - 4) / 2;
        int y = top;
        int row = 22;

        addDrawableChild(btn(Text.literal("Paste (Ctrl+V)"), x, y, half, b -> paste()));
        addDrawableChild(btn(Text.literal("Upload..."), x + half + 4, y, half, b -> upload()));
        y += row;

        nameField = new TextFieldWidget(textRenderer, x, y, COL_W, 18, Text.literal("Name"));
        nameField.setMaxLength(48);
        nameField.setText(cfg.name);
        nameField.setChangedListener(s -> { cfg.name = s; cfg.save(); });
        addDrawableChild(nameField);
        y += row;

        // width / height in maps
        int lab = half - 32;
        addDrawableChild(btn(Text.literal("-"), x, y, 16, b -> { cfg.mapsW = Math.max(1, cfg.mapsW - 1); changed(); }));
        addDrawableChild(btn(Text.literal("W: " + cfg.mapsW + (cfg.mapsW == 1 ? " map" : " maps")), x + 16, y, lab, b -> {}));
        addDrawableChild(btn(Text.literal("+"), x + 16 + lab, y, 16, b -> { cfg.mapsW = Math.min(8, cfg.mapsW + 1); changed(); }));
        int hx = x + half + 4;
        addDrawableChild(btn(Text.literal("-"), hx, y, 16, b -> { cfg.mapsH = Math.max(1, cfg.mapsH - 1); changed(); }));
        addDrawableChild(btn(Text.literal("H: " + cfg.mapsH + (cfg.mapsH == 1 ? " map" : " maps")), hx + 16, y, lab, b -> {}));
        addDrawableChild(btn(Text.literal("+"), hx + 16 + lab, y, 16, b -> { cfg.mapsH = Math.min(8, cfg.mapsH + 1); changed(); }));
        y += row;

        addDrawableChild(btn(Text.literal("Mode: " + (cfg.staircase ? "Staircase" : "Flat")), x, y, half,
                b -> { cfg.staircase = !cfg.staircase; changed(); }));
        addDrawableChild(btn(Text.literal("Dither: " + (cfg.dither ? "On" : "Off")), x + half + 4, y, half,
                b -> { cfg.dither = !cfg.dither; changed(); }));
        y += row;

        addDrawableChild(btn(Text.literal("Fit: " + cap(cfg.fit)), x, y, half, b -> {
            int i = 0;
            for (int k = 0; k < FITS.length; k++) if (FITS[k].equals(cfg.fit)) i = k;
            cfg.fit = FITS[(i + 1) % FITS.length];
            changed();
        }));
        addDrawableChild(btn(Text.literal("Base: " + cfg.supportLabel()), x + half + 4, y, half,
                b -> { cfg.support = (cfg.support + 1) % MapMakerConfig.SUPPORT_IDS.length; changed(); }));
        y += row;

        addDrawableChild(btn(toggle("Full Blocks", cfg.full), x, y, half, b -> { cfg.full = !cfg.full; changed(); }));
        addDrawableChild(btn(toggle("Carpet", cfg.carpet), x + half + 4, y, half, b -> { cfg.carpet = !cfg.carpet; changed(); }));
        y += row;
        addDrawableChild(btn(toggle("Candles", cfg.candles), x, y, half, b -> { cfg.candles = !cfg.candles; changed(); }));
        addDrawableChild(btn(toggle("Resin", cfg.resin), x + half + 4, y, half, b -> { cfg.resin = !cfg.resin; changed(); }));
        y += row;
        addDrawableChild(btn(toggle("Leaves", cfg.leaves), x, y, half, b -> { cfg.leaves = !cfg.leaves; changed(); }));
        addDrawableChild(btn(Text.literal("Blocks..."), x + half + 4, y, half,
                b -> client.setScreen(new BlocksScreen(this))));
        y += row;

        addDrawableChild(btn(Text.literal(listeningKey ? "Press a key..." : "Key: " + keyName(cfg.key)), x, y, half, b -> {
            listeningKey = true;
            clearAndInit();
        }));
        addDrawableChild(btn(Text.literal("Open folder"), x + half + 4, y, half, b -> openFolder()));
        y += row;

        addDrawableChild(btn(Text.literal("Save .nbt"), x, y, half, b -> save()));
        addDrawableChild(btn(Text.literal("Done"), x + half + 4, y, half, b -> close()));

        pSize = Math.min(width - COL_W - 40, 214);
        pX = 12;
        pY = top;

        if (lastSource != null) reconvert();
    }

    private ButtonWidget btn(Text t, int x, int y, int w, ButtonWidget.PressAction a) {
        return ButtonWidget.builder(t, a).dimensions(x, y, w, 20).build();
    }

    private Text toggle(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "On" : "Off")).formatted(on ? Formatting.GREEN : Formatting.WHITE);
    }

    private static String cap(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private void changed() {
        cfg.save();
        clearAndInit();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (listeningKey) {
            listeningKey = false;
            cfg.key = key == GLFW.GLFW_KEY_ESCAPE ? -1 : key;
            cfg.save();
            clearAndInit();
            return true;
        }
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (key == GLFW.GLFW_KEY_V && ctrl && !nameField.isFocused()) {
            paste();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (!paths.isEmpty()) loadPath(paths.get(0));
    }

    static String keyName(int code) {
        if (code <= 0) return "Not Bound";
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        String n = GLFW.glfwGetKeyName(code, 0);
        return n != null ? n.toUpperCase() : "Key " + code;
    }

    // ------------------------------------------------------------------ loading images

    private void paste() {
        setStatus("Reading clipboard...", false);
        Thread t = new Thread(() -> {
            BufferedImage img = ClipboardImage.read();
            if (img != null) setImage(img, null);
            else client.execute(this::pasteText);
        }, "mapmaker-clipboard");
        t.setDaemon(true);
        t.start();
    }

    /** Fallback: clipboard text that is an image link or a file path. */
    private void pasteText() {
        String t = client.keyboard.getClipboard();
        t = t == null ? "" : t.trim().replace("\"", "");
        if (t.startsWith("http://") || t.startsWith("https://")) {
            final String url = t;
            setStatus("Downloading image...", false);
            Thread th = new Thread(() -> {
                try {
                    BufferedImage img = ImageIO.read(URI.create(url).toURL());
                    if (img == null) throw new IOException("That link is not an image");
                    setImage(img, null);
                } catch (Exception e) {
                    setStatus("Could not load link: " + e.getMessage(), true);
                }
            }, "mapmaker-download");
            th.setDaemon(true);
            th.start();
            return;
        }
        try {
            Path p = Path.of(t);
            if (Files.isRegularFile(p)) {
                loadPath(p);
                return;
            }
        } catch (Exception ignored) {
        }
        setStatus("No image on the clipboard. Copy an image, an image link or a file path - or use Upload / drag & drop.", true);
    }

    private void upload() {
        Thread t = new Thread(() -> {
            String path;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(5);
                filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg"))
                        .put(stack.UTF8("*.gif")).put(stack.UTF8("*.bmp")).flip();
                path = TinyFileDialogs.tinyfd_openFileDialog("Choose an image", "", filters, "Images", false);
            } catch (Throwable e) {
                setStatus("File dialog failed - drag & drop an image onto this window instead.", true);
                return;
            }
            if (path != null) loadPath(Path.of(path));
        }, "mapmaker-upload");
        t.setDaemon(true);
        t.start();
    }

    private void loadPath(Path p) {
        setStatus("Loading " + p.getFileName() + "...", false);
        Thread t = new Thread(() -> {
            try {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img == null) throw new IOException("unsupported format (use PNG, JPG, GIF or BMP)");
                String n = p.getFileName().toString();
                int dot = n.lastIndexOf('.');
                setImage(img, dot > 0 ? n.substring(0, dot) : n);
            } catch (Exception e) {
                setStatus("Could not load image: " + e.getMessage(), true);
            }
        }, "mapmaker-load");
        t.setDaemon(true);
        t.start();
    }

    private void setImage(BufferedImage img, String nameHint) {
        client.execute(() -> {
            lastSource = img;
            if (nameHint != null) {
                cfg.name = sanitize(nameHint);
                cfg.save();
                if (nameField != null) nameField.setText(cfg.name);
            }
            reconvert();
        });
    }

    // ------------------------------------------------------------------ conversion

    private void reconvert() {
        final BufferedImage src = lastSource;
        if (src == null) return;
        final int mw = cfg.mapsW, mh = cfg.mapsH;
        final boolean st = cfg.staircase, di = cfg.dither;
        final String fit = cfg.fit;
        final int g = gen.incrementAndGet();
        setStatus("Converting...", false);
        POOL.submit(() -> {
            try {
                List<MapPalette.Choice> pal = MapPalette.choices(cfg, st);
                if (pal.isEmpty()) {
                    if (g == gen.get()) { result = null; preview = null; }
                    setStatus("No blocks available. Turn on a block type (and check Blocks...).", true);
                    return;
                }
                MapConverter.Result res = MapConverter.convert(src, mw, mh, fit, di, st, pal);
                if (g != gen.get()) return;
                buildPreview(res);
                result = res;
                setStatus("Ready: " + res.w() + "x" + res.h() + (st ? " staircase" : " flat")
                        + ", " + countKinds(res) + " block kinds. Press Save .nbt.", false);
            } catch (Throwable e) {
                setStatus("Conversion failed: " + e, true);
            }
        });
    }

    private static int countKinds(MapConverter.Result r) {
        boolean[] used = new boolean[r.palette().size()];
        for (int p : r.pick()) used[p] = true;
        java.util.Set<String> kinds = new java.util.HashSet<>();
        for (int i = 0; i < used.length; i++) if (used[i]) kinds.add(r.palette().get(i).cand().id().toString());
        return kinds.size();
    }

    private void buildPreview(MapConverter.Result r) {
        int cell = Math.max(1, (int) Math.ceil(Math.max(r.w(), r.h()) / 128.0));
        int cw = (r.w() + cell - 1) / cell, ch = (r.h() + cell - 1) / cell;
        int[] out = new int[cw * ch];
        for (int cy = 0; cy < ch; cy++) {
            for (int cx = 0; cx < cw; cx++) {
                int sx = Math.min(r.w() - 1, cx * cell + cell / 2);
                int sy = Math.min(r.h() - 1, cy * cell + cell / 2);
                out[cy * cw + cx] = 0xFF000000 | r.rgbAt(sy * r.w() + sx);
            }
        }
        pvW = cw;
        pvH = ch;
        preview = out;
    }

    // ------------------------------------------------------------------ saving

    private static String sanitize(String s) {
        String t = s == null ? "" : s.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
        return t.isEmpty() ? "mapart" : t;
    }

    private Path schematicsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    private void save() {
        final MapConverter.Result r = result;
        if (r == null) {
            setStatus("Nothing to save yet. Load an image first.", true);
            return;
        }
        final String name = sanitize(cfg.name);
        setStatus("Saving...", false);
        POOL.submit(() -> {
            try {
                Path dir = schematicsDir();
                Files.createDirectories(dir);
                int[] counts = MapSchematic.write(r, cfg.supportState(), dir.resolve(name + ".nbt"));
                setStatus("Saved " + name + ".nbt (" + r.w() + "x" + r.h() + (r.stairs() ? " staircase" : " flat") + ") "
                        + String.format("%,d", counts[0]) + " blocks, " + counts[1] + " kinds - open it in Litematica", false);
            } catch (Throwable e) {
                setStatus("Save failed: " + e, true);
            }
        });
    }

    private void openFolder() {
        try {
            Path dir = schematicsDir();
            Files.createDirectories(dir);
            Util.getOperatingSystem().open(dir.toUri());
        } catch (Exception e) {
            setStatus("Could not open folder: " + e.getMessage(), true);
        }
    }

    private void setStatus(String s, boolean error) {
        status = s;
        statusError = error;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawTextWithShadow(textRenderer, title, (width - textRenderer.getWidth(title)) / 2, 6, 0xFFFFFFFF);

        ctx.fill(pX - 1, pY - 1, pX + pSize + 1, pY + pSize + 1, 0xFF555555);
        ctx.fill(pX, pY, pX + pSize, pY + pSize, 0xFF101010);

        int[] pv = preview;
        if (pv != null) {
            int cw = pvW, ch = pvH;
            float sc = Math.min(pSize / (float) cw, pSize / (float) ch);
            int ox = pX + (int) ((pSize - cw * sc) / 2f);
            int oy = pY + (int) ((pSize - ch * sc) / 2f);
            for (int cy = 0; cy < ch; cy++) {
                int y1 = oy + (int) (cy * sc), y2 = oy + (int) ((cy + 1) * sc);
                if (y2 <= y1) continue;
                int start = 0;
                for (int cx = 1; cx <= cw; cx++) {
                    if (cx == cw || pv[cy * cw + cx] != pv[cy * cw + start]) {
                        ctx.fill(ox + (int) (start * sc), y1, ox + (int) (cx * sc), y2, pv[cy * cw + start]);
                        start = cx;
                    }
                }
            }
        } else {
            String hint = "No image";
            ctx.drawTextWithShadow(textRenderer, hint, pX + (pSize - textRenderer.getWidth(hint)) / 2, pY + pSize / 2 - 4, 0xFF888888);
        }

        int ty = pY + pSize + 8;
        int color = statusError ? 0xFFFF5555 : 0xFF55FF55;
        for (OrderedText line : textRenderer.wrapLines(Text.literal(status), Math.max(60, width - COL_W - 36))) {
            ctx.drawTextWithShadow(textRenderer, line, pX, ty, color);
            ty += 10;
        }
    }
}
