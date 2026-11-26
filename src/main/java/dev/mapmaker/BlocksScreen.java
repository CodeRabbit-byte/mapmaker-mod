package dev.mapmaker;

import net.minecraft.block.MapColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Per map colour: click the block name to cycle through candidates, or switch the colour off. */
public class BlocksScreen extends Screen {
    private static final int PER = 9, ROW_Y0 = 30;

    private final Screen parent;
    private final MapMakerConfig cfg = MapMakerConfig.get();
    private Map<Integer, List<MapPalette.Cand>> cands;
    private List<Integer> ids;
    private int page, x0;

    public BlocksScreen(Screen parent) {
        super(Text.literal("Blocks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cands = MapPalette.candidates(cfg);
        ids = new ArrayList<>(cands.keySet());
        int pages = Math.max(1, (ids.size() + PER - 1) / PER);
        page = Math.min(page, pages - 1);
        x0 = width / 2 - 125;

        int y = ROW_Y0;
        for (int i = page * PER; i < Math.min(ids.size(), (page + 1) * PER); i++) {
            final int colorId = ids.get(i);
            final List<MapPalette.Cand> list = cands.get(colorId);
            MapPalette.Cand cur = MapPalette.pick(cfg, colorId, list);
            boolean on = !cfg.disabledColors.contains(colorId);

            Text label = Text.empty().append(cur.state().getBlock().getName())
                    .append(Text.literal(list.size() > 1 ? "  (" + list.size() + ")" : ""));
            addDrawableChild(ButtonWidget.builder(label, b -> {
                int idx = 0;
                for (int k = 0; k < list.size(); k++) if (list.get(k) == cur) idx = k;
                cfg.overrides.put(String.valueOf(colorId), list.get((idx + 1) % list.size()).id().toString());
                cfg.save();
                clearAndInit();
            }).dimensions(x0 + 24, y, 150, 20).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal(on ? "On" : "Off").formatted(on ? Formatting.GREEN : Formatting.RED), b -> {
                        Integer boxed = colorId;
                        if (cfg.disabledColors.contains(boxed)) cfg.disabledColors.remove(boxed);
                        else cfg.disabledColors.add(boxed);
                        cfg.save();
                        clearAndInit();
                    }).dimensions(x0 + 178, y, 48, 20).build());
            y += 22;
        }

        int by = Math.max(height - 28, ROW_Y0 + PER * 22 + 6);
        addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> { page = Math.max(0, page - 1); clearAndInit(); })
                .dimensions(width / 2 - 125, by, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 30, by, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> { page = Math.min(pages - 1, page + 1); clearAndInit(); })
                .dimensions(width / 2 + 65, by, 60, 20).build());
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawTextWithShadow(textRenderer, title, (width - textRenderer.getWidth(title)) / 2, 8, 0xFFFFFFFF);
        String sub = "Click a block name to swap it. Page " + (page + 1) + "/" + Math.max(1, (ids.size() + PER - 1) / PER);
        ctx.drawTextWithShadow(textRenderer, sub, (width - textRenderer.getWidth(sub)) / 2, 19, 0xFFAAAAAA);

        int y = ROW_Y0;
        for (int i = page * PER; i < Math.min(ids.size(), (page + 1) * PER); i++) {
            int rgb = MapColor.get(ids.get(i)).color;
            ctx.fill(x0, y + 1, x0 + 20, y + 19, 0xFF000000);
            ctx.fill(x0 + 1, y + 2, x0 + 19, y + 18, 0xFF000000 | rgb);
            y += 22;
        }
    }
}
