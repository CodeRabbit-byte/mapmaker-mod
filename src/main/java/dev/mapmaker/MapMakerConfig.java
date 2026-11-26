package dev.mapmaker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MapMakerConfig {
    private static MapMakerConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String[] SUPPORT_IDS = {
            "minecraft:stone", "minecraft:dirt", "minecraft:cobblestone", "minecraft:glass"
    };

    public int mapsW = 1, mapsH = 1;
    public boolean staircase = false;
    public boolean dither = true;
    public String fit = "CROP"; // CROP, FIT, STRETCH

    // block types
    public boolean full = true, carpet = false, candles = false, resin = false, leaves = false;

    public int support = 0;
    public int key = -1;
    public String name = "mapart";

    public List<Integer> disabledColors = new ArrayList<>();
    public Map<String, String> overrides = new HashMap<>();

    public static MapMakerConfig get() {
        if (instance == null) load();
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("mapmaker.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), MapMakerConfig.class);
        } catch (Exception ignored) {
        }
        if (instance == null) instance = new MapMakerConfig();
        if (instance.disabledColors == null) instance.disabledColors = new ArrayList<>();
        if (instance.overrides == null) instance.overrides = new HashMap<>();
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }

    public boolean kindEnabled(MapPalette.Kind k) {
        return switch (k) {
            case FULL -> full;
            case CARPET -> carpet;
            case CANDLE -> candles;
            case RESIN -> resin;
            case LEAVES -> leaves;
        };
    }

    public BlockState supportState() {
        Block b = Registries.BLOCK.get(Identifier.of(SUPPORT_IDS[Math.floorMod(support, SUPPORT_IDS.length)]));
        return b.getDefaultState();
    }

    public String supportLabel() {
        String id = SUPPORT_IDS[Math.floorMod(support, SUPPORT_IDS.length)];
        String s = id.substring(id.indexOf(':') + 1);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
