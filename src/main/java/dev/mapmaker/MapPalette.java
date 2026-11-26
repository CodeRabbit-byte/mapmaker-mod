package dev.mapmaker;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Builds the list of usable blocks, grouped by map colour. */
public final class MapPalette {

    /** Declaration order = priority. Special blocks win over plain full blocks when colours overlap. */
    public enum Kind { LEAVES, RESIN, CANDLE, CARPET, FULL }

    public record Cand(MapColor color, BlockState state, Kind kind, Identifier id, int score) {
        public boolean full() { return kind == Kind.FULL; }

        /** The state actually written to the schematic. */
        public BlockState placed() {
            if (kind == Kind.RESIN) return state.with(Properties.DOWN, true); // clump attached to the block below
            return state;
        }
    }

    /** One selectable (block, shade) combination. shade: 0 = dark, 1 = normal, 2 = light. */
    public record Choice(Cand cand, int shade, int rgb) {}

    private static final int[] MULT = {180, 220, 255};

    private static final String[] BAD = {
            "glazed", "budding", "respawn_anchor", "note_block", "reinforced", "bedrock", "barrier",
            "command", "structure", "jigsaw", "spawner", "light", "tnt", "observer", "piston",
            "slime_block", "honey_block", "lodestone", "dispenser", "dropper"
    };
    private static final String[] CHEAP_SUFFIX = {"_wool", "_planks", "_concrete", "_terracotta", "_carpet", "_candle"};
    private static final Set<String> CHEAP = Set.of(
            "stone", "dirt", "coarse_dirt", "cobblestone", "netherrack", "sandstone", "red_sandstone", "clay",
            "snow_block", "podzol", "mycelium", "grass_block", "moss_block", "bone_block", "deepslate",
            "cobbled_deepslate", "blackstone", "basalt", "smooth_stone", "stone_bricks", "bricks", "terracotta");
    private static final String[] EXPENSIVE = {
            "diamond", "emerald", "netherite", "gold", "lapis", "ancient", "beacon", "amethyst", "obsidian", "purpur"};

    private MapPalette() {}

    private static Kind classify(Block b, BlockState s, Identifier id) {
        if (b == Blocks.LEAF_LITTER) return Kind.LEAVES;
        if (b == Blocks.RESIN_CLUMP) return Kind.RESIN;
        if (b instanceof CandleBlock) return Kind.CANDLE;
        if (b instanceof CarpetBlock) return Kind.CARPET;
        return isFullCandidate(b, s, id) ? Kind.FULL : null;
    }

    private static boolean isFullCandidate(Block b, BlockState s, Identifier id) {
        if (b instanceof BlockEntityProvider || b instanceof FallingBlock || b instanceof LeavesBlock
                || b instanceof FluidBlock || b instanceof InfestedBlock) return false;
        String p = id.getPath();
        for (String bad : BAD) if (p.contains(bad)) return false;
        if (!s.getFluidState().isEmpty()) return false;
        if (!s.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) return false;
        return !(s.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) < 0);
    }

    private static int score(Identifier id) {
        String p = id.getPath();
        int s = 10;
        if (CHEAP.contains(p)) s = 0;
        if (!p.contains("glazed")) for (String suf : CHEAP_SUFFIX) if (p.endsWith(suf)) s = 0;
        for (String e : EXPENSIVE) if (p.contains(e)) s += 50;
        return s;
    }

    /** map colour id -> candidate blocks (best first), only for block types enabled in the config. */
    public static Map<Integer, List<Cand>> candidates(MapMakerConfig cfg) {
        Map<Integer, List<Cand>> map = new TreeMap<>();
        for (Block b : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(b);
            BlockState s = b.getDefaultState();
            Kind k = classify(b, s, id);
            if (k == null || !cfg.kindEnabled(k)) continue;
            MapColor mc = s.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            if (mc == null || mc.id == 0) continue; // CLEAR
            map.computeIfAbsent(mc.id, i -> new ArrayList<>()).add(new Cand(mc, s, k, id, score(id)));
        }
        Comparator<Cand> cmp = Comparator.comparingInt((Cand c) -> c.kind().ordinal())
                .thenComparingInt(Cand::score)
                .thenComparing(c -> c.id().toString());
        for (List<Cand> l : map.values()) l.sort(cmp);
        return map;
    }

    public static Cand pick(MapMakerConfig cfg, int colorId, List<Cand> list) {
        String ov = cfg.overrides.get(String.valueOf(colorId));
        if (ov != null) for (Cand c : list) if (c.id().toString().equals(ov)) return c;
        return list.get(0);
    }

    public static List<Choice> choices(MapMakerConfig cfg, boolean stairs) {
        List<Choice> out = new ArrayList<>();
        for (Map.Entry<Integer, List<Cand>> e : candidates(cfg).entrySet()) {
            if (cfg.disabledColors.contains(e.getKey())) continue;
            Cand c = pick(cfg, e.getKey(), e.getValue());
            for (int sh = stairs ? 0 : 1; sh <= (stairs ? 2 : 1); sh++) out.add(new Choice(c, sh, rgb(c.color(), sh)));
        }
        return out;
    }

    public static int rgb(MapColor mc, int shade) {
        int m = MULT[shade];
        int r = (mc.color >> 16 & 255) * m / 255;
        int g = (mc.color >> 8 & 255) * m / 255;
        int b = (mc.color & 255) * m / 255;
        return r << 16 | g << 8 | b;
    }
}
