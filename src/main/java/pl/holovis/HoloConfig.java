package pl.holovis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;

public class HoloConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("holovis.json");
    private static HoloConfig instance = new HoloConfig();

    public enum HitboxStyle {
        OUTLINE("Kontur"), HOLOGRAM("Hologram"), FILLED("Wypełnienie");
        public final String label;
        HitboxStyle(String l) { label = l; }
        public HitboxStyle next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum Target {
        ALL("Wszystkie"), PLAYERS("Gracze"), MOBS("Moby");
        public final String label;
        Target(String l) { label = l; }
        public Target next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static class ColorSetting {
        public float h, s = 1f, b = 1f;
        public boolean rainbow = false;

        public ColorSetting() {}
        public ColorSetting(int rgb) { set(rgb); }

        public void set(int rgb) {
            float[] hsv = toHsv(rgb);
            h = hsv[0]; s = hsv[1]; b = hsv[2];
        }

        /** Aktualny kolor RGB (0xRRGGBB), z uwzględnieniem trybu rainbow. */
        public int rgb() {
            float hue = rainbow ? (Util.getMeasuringTimeMs() % 6000L) / 6000f : h;
            return hsv(hue, s, b);
        }
    }

    public static class SoundSetting {
        public boolean enabled = false;
        public String sound = "mc:entity.experience_orb.pickup";
        public float volume = 0.8f;
        public float pitch = 1f;

        public SoundSetting() {}
        public SoundSetting(boolean enabled, String sound) { this.enabled = enabled; this.sound = sound; }
    }

    public boolean hitboxEnabled = true;
    public HitboxStyle hitboxStyle = HitboxStyle.HOLOGRAM;
    public Target target = Target.ALL;
    public float fillAlpha = 0.25f;
    public ColorSetting hitboxColor = new ColorSetting(0x00F0FF);

    public ColorSetting guiColor = new ColorSetting(0x00F0FF);
    public boolean hologramFx = true;
    public ColorSetting guiColor2 = new ColorSetting(0xB04CFF);
    public boolean guiGradient = false;
    public boolean guiGradientText = false;
    public ColorSetting guiBg = new ColorSetting(0x0A1020);
    public float guiBgAlpha = 0.88f;
    public ColorSetting textColor = new ColorSetting(0xE8EEF8);

    public boolean skyEnabled = false;
    public ColorSetting skyColor = new ColorSetting(0x8A2BE2);
    public boolean skyDaylight = true;
    public boolean skyClouds = true;

    public boolean watermark = true;
    public boolean showFps = true;
    public boolean coords = false;
    public ColorSetting hudColor = new ColorSetting(0x00F0FF);

    public SoundSetting hitSound = new SoundSetting(true, "mc:entity.experience_orb.pickup");
    public SoundSetting killSound = new SoundSetting(false, "mc:entity.player.levelup");

    public static HoloConfig get() { return instance; }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                HoloConfig loaded = GSON.fromJson(Files.readString(PATH), HoloConfig.class);
                if (loaded != null) instance = loaded;
            }
        } catch (Exception e) {
            System.err.println("[HoloVis] Nie udało się wczytać configu: " + e);
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(instance));
        } catch (Exception e) {
            System.err.println("[HoloVis] Nie udało się zapisać configu: " + e);
        }
    }

    // ---- HSV helpers ----
    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) / 6f;
        else if (max == g) h = (2 + (b - r) / d) / 6f;
        else h = (4 + (r - g) / d) / 6f;
        if (h < 0) h += 1;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}
