package dev.mapmaker;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes a vanilla structure .nbt (Litematica loads these from the schematics folder). */
public final class MapSchematic {
    /** 1.21.11. Litematica does not care much, but vanilla uses it for datafixing. */
    private static final int DATA_VERSION = 4671;

    private MapSchematic() {}

    /** @return {blockCount, distinctBlockStates} */
    public static int[] write(MapConverter.Result r, BlockState support, Path file) throws IOException {
        int W = r.w(), H = r.h();

        // Column heights. Row 0 is the reference row (the "north neighbour" the map shades against).
        int[][] hs = new int[W][H + 1];
        int maxY = 0;
        for (int x = 0; x < W; x++) {
            int min = 0;
            for (int z = 0; z < H; z++) {
                int shade = r.palette().get(r.pick()[z * W + x]).shade();
                hs[x][z + 1] = hs[x][z] + (shade - 1);
                min = Math.min(min, hs[x][z + 1]);
            }
            int shift = 1 - min; // lowest top is y=1 so every top block has room for support below
            for (int z = 0; z <= H; z++) {
                hs[x][z] += shift;
                maxY = Math.max(maxY, hs[x][z]);
            }
        }

        Map<BlockState, Integer> pal = new LinkedHashMap<>();
        NbtList blocks = new NbtList();

        for (int x = 0; x < W; x++) {
            add(blocks, pal, x, hs[x][0], 0, support);
            for (int z = 0; z < H; z++) {
                MapPalette.Cand c = r.palette().get(r.pick()[z * W + x]).cand();
                int y = hs[x][z + 1];
                if (!c.full()) add(blocks, pal, x, y - 1, z + 1, support);
                add(blocks, pal, x, y, z + 1, c.placed());
            }
        }

        NbtList paletteList = new NbtList();
        for (BlockState s : pal.keySet()) paletteList.add(stateTag(s));

        NbtList size = new NbtList();
        size.add(NbtInt.of(W));
        size.add(NbtInt.of(maxY + 1));
        size.add(NbtInt.of(H + 1));

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
