package dev.mapmaker;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes vanilla structure .nbt files (Litematica loads these from the schematics folder). */
public final class MapSchematic {
    /** 1.21.11. Litematica does not care much, but vanilla uses it for datafixing. */
    private static final int DATA_VERSION = 4671;

    private MapSchematic() {}

    /**
     * Writes the whole image as one schematic, or (split) one schematic per 128x128 map.
     * Split files are named name_r1_c1.nbt (row, column). Only the top row of maps carries the
     * reference row; the maps below use the last row of the map above as their north neighbour,
     * so build them touching, top to bottom.
     *
     * @return {totalBlocks, maxDistinctStatesInAFile, fileCount}
     */
    public static int[] writeAll(MapConverter.Result r, BlockState support, Path dir, String name, boolean split)
            throws IOException {
        Files.createDirectories(dir);
        int[][] hs = heights(r);
        int tilesX = split ? r.w() / 128 : 1, tilesY = split ? r.h() / 128 : 1;
        int tw = r.w() / tilesX, th = r.h() / tilesY;
        int blocks = 0, kinds = 0, files = 0;
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                String fn = tilesX * tilesY == 1 ? name : name + "_r" + (ty + 1) + "_c" + (tx + 1);
                int[] c = writeTile(r, hs, support, dir.resolve(fn + ".nbt"), tx * tw, ty * th, tw, th, ty == 0);
                blocks += c[0];
                kinds = Math.max(kinds, c[1]);
                files++;
            }
        }
        return new int[]{blocks, kinds, files};
    }

    /** Column heights for the whole image. Index 0 of each column is the reference row. */
    private static int[][] heights(MapConverter.Result r) {
        int W = r.w(), H = r.h();
        int[][] hs = new int[W][H + 1];
        for (int x = 0; x < W; x++) {
            int min = 0;
            for (int z = 0; z < H; z++) {
                int shade = r.palette().get(r.pick()[z * W + x]).shade();
                hs[x][z + 1] = hs[x][z] + (shade - 1);
                min = Math.min(min, hs[x][z + 1]);
            }
            int shift = 1 - min; // lowest top is y=1 so every top block has room for support below
            for (int z = 0; z <= H; z++) hs[x][z] += shift;
        }
        return hs;
    }

    private static int[] writeTile(MapConverter.Result r, int[][] hs, BlockState support, Path file,
                                   int x0, int z0, int tw, int th, boolean ref) throws IOException {
        int W = r.w();
        int zOff = ref ? 1 : 0;
        Map<BlockState, Integer> pal = new LinkedHashMap<>();
        NbtList blocks = new NbtList();
        int maxY = 0;

        for (int x = x0; x < x0 + tw; x++) {
            if (ref) {
                add(blocks, pal, x - x0, hs[x][z0], 0, support);
                maxY = Math.max(maxY, hs[x][z0]);
            }
            for (int z = z0; z < z0 + th; z++) {
                MapPalette.Cand c = r.palette().get(r.pick()[z * W + x]).cand();
                int y = hs[x][z + 1];
                maxY = Math.max(maxY, y);
                int pz = z - z0 + zOff;
                if (!c.full()) add(blocks, pal, x - x0, y - 1, pz, support);
                add(blocks, pal, x - x0, y, pz, c.placed());
            }
        }

        NbtList paletteList = new NbtList();
        for (BlockState s : pal.keySet()) paletteList.add(stateTag(s));

        NbtList size = new NbtList();
        size.add(NbtInt.of(tw));
        size.add(NbtInt.of(maxY + 1));
        size.add(NbtInt.of(th + zOff));

        NbtCompound root = new NbtCompound();
        root.put("size", size);
        root.put("palette", paletteList);
        root.put("blocks", blocks);
        root.put("entities", new NbtList());
        root.putInt("DataVersion", DATA_VERSION);
        NbtIo.writeCompressed(root, file);
        return new int[]{blocks.size(), pal.size()};
    }

    private static void add(NbtList blocks, Map<BlockState, Integer> pal, int x, int y, int z, BlockState s) {
        Integer idx = pal.get(s);
        if (idx == null) {
            idx = pal.size();
            pal.put(s, idx);
        }
        NbtList pos = new NbtList();
        pos.add(NbtInt.of(x));
        pos.add(NbtInt.of(y));
        pos.add(NbtInt.of(z));
        NbtCompound e = new NbtCompound();
        e.put("pos", pos);
        e.putInt("state", idx);
        blocks.add(e);
    }

    private static NbtCompound stateTag(BlockState s) {
        NbtCompound t = new NbtCompound();
        t.putString("Name", Registries.BLOCK.getId(s.getBlock()).toString());
        if (!s.getEntries().isEmpty()) {
            NbtCompound props = new NbtCompound();
            for (Map.Entry<Property<?>, Comparable<?>> en : s.getEntries().entrySet()) {
                props.putString(en.getKey().getName(), valueName(en.getKey(), en.getValue()));
            }
            t.put("Properties", props);
        }
        return t;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property p, Comparable v) {
        return p.name(v);
    }
}
