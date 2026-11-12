package com.luminamods;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class LuminaConfig {
    public static boolean clickSounds = true;
    public static boolean keystrokes = true;
    public static boolean fps = true;
    public static boolean coords = false;
    public static boolean watermark = true;
    public static boolean rainbow = true;
    public static float volume = 0.5f;
    public static int soundIndex = 0;
    public static int colorIndex = 0;

    public static final String[] SOUND_IDS = {
        "ui.button.click", "block.note_block.hat", "block.note_block.bass",
        "block.stone_button.click_on", "block.wooden_button.click_on", "entity.experience_orb.pickup"
    };
    public static final String[] SOUND_NAMES = {
        "UI Click", "Hi-hat", "Bass", "Stone", "Wood", "Orb"
    };
    public static final String[] COLOR_NAMES = {"Cyan", "Pink", "Green", "Orange", "Purple", "Red"};
    public static final int[] COLORS = {0xFF55FFFF, 0xFFFF55FF, 0xFF55FF55, 0xFFFFAA00, 0xFFAA55FF, 0xFFFF5555};

    public static int accent() {
        if (rainbow) {
            float h = (System.currentTimeMillis() % 4000L) / 4000f;
            return 0xFF000000 | MathHelper.hsvToRgb(h, 0.6f, 1f);
        }
        return COLORS[colorIndex];
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("luminamods.properties");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) { p.load(in); }
            clickSounds = Boolean.parseBoolean(p.getProperty("clickSounds", "true"));
            keystrokes = Boolean.parseBoolean(p.getProperty("keystrokes", "true"));
            fps = Boolean.parseBoolean(p.getProperty("fps", "true"));
            coords = Boolean.parseBoolean(p.getProperty("coords", "false"));
            watermark = Boolean.parseBoolean(p.getProperty("watermark", "true"));
            rainbow = Boolean.parseBoolean(p.getProperty("rainbow", "true"));
            volume = Float.parseFloat(p.getProperty("volume", "0.5"));
            soundIndex = Math.floorMod(Integer.parseInt(p.getProperty("soundIndex", "0")), SOUND_IDS.length);
            colorIndex = Math.floorMod(Integer.parseInt(p.getProperty("colorIndex", "0")), COLORS.length);
        } catch (Exception ignored) { }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("clickSounds", String.valueOf(clickSounds));
            p.setProperty("keystrokes", String.valueOf(keystrokes));
            p.setProperty("fps", String.valueOf(fps));
            p.setProperty("coords", String.valueOf(coords));
            p.setProperty("watermark", String.valueOf(watermark));
            p.setProperty("rainbow", String.valueOf(rainbow));
            p.setProperty("volume", String.valueOf(volume));
            p.setProperty("soundIndex", String.valueOf(soundIndex));
            p.setProperty("colorIndex", String.valueOf(colorIndex));
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "LuminaMods"); }
        } catch (Exception ignored) { }
    }
}
