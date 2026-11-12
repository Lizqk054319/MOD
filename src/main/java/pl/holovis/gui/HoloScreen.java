package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.HoloSounds;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleToIntFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Menu w stylu kolumn z modułami: panele, przełączniki, rozwijane ustawienia, gradient, własne kolory. */
public class HoloScreen extends Screen {
    private static final int PW = 144, GAP = 8, HEAD = 24, ROW = 20;
    private static final int GRAY = 0x8791A6;
    private static final int[] PRESETS = {0x00F0FF, 0x3D7BFF, 0xB04CFF, 0xFF4FD8, 0xFF3B3B, 0xFF9A1F, 0xFFE81F, 0x3BFF7A, 0xFFFFFF};
    private static final int[] NEUTRALS = {0x000000, 0x0A1020, 0x151515, 0x1E2A3A, 0x3A3A48, 0x808080, 0xC8C8C8, 0xE8EEF8, 0xFFFFFF};

    private final List<Panel> panels = new ArrayList<>();
    private final long openedAt = Util.getMeasuringTimeMs();
    private float fade = 1f;
    private float sc = 1f;
    private int vw, vh;
    private int accent = 0x00F0FF, accent2 = 0xB04CFF, bgc = 0x0A1020, txtC = 0xE8EEF8;
    private float bgA = 0.88f;
    private boolean grad;
    private String filter = "";
    private boolean searchFocus, placed;
    private Panel dragPanel;
    private int dragDx, dragDy;
    private Setting dragging;
    private String status = "";
    private long statusUntil;

    public HoloScreen() {
        super(Text.literal("HoloVis"));
        HoloSounds.refresh();
        build();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { HoloConfig.save(); }

    // =====================================================================
    //  Budowa modułów
    // =====================================================================
    private void build() {
        HoloConfig c = HoloConfig.get();

        Panel vis = new Panel("Wizualne");
        Module hb = new Module("Hitbox", () -> c.hitboxEnabled, v -> c.hitboxEnabled = v);
        List<String> styles = Arrays.stream(HoloConfig.HitboxStyle.values()).map(e -> e.label).toList();
        List<String> targets = Arrays.stream(HoloConfig.Target.values()).map(e -> e.label).toList();
        hb.s.add(new CycleSet("Styl", () -> styles, () -> c.hitboxStyle.ordinal(),
                i -> c.hitboxStyle = HoloConfig.HitboxStyle.values()[i]));
        hb.s.add(new CycleSet("Cele", () -> targets, () -> c.target.ordinal(),
                i -> c.target = HoloConfig.Target.values()[i]));
        hb.s.add(new SliderSet("Wypełnienie", () -> c.fillAlpha, v -> c.fillAlpha = v, null, v -> (int) (v * 100) + "%"));
        colorRows(hb, c.hitboxColor, PRESETS);
        vis.mods.add(hb);
        panels.add(vis);

        Panel snd = new Panel("Dźwięki");
        Module hit = new Module("Hit Sound", () -> c.hitSound.enabled, v -> c.hitSound.enabled = v);
        hit.ss = c.hitSound;
        soundRows(hit, c.hitSound);
        Module kill = new Module("Kill Sound", () -> c.killSound.enabled, v -> c.killSound.enabled = v);
        kill.ss = c.killSound;
        soundRows(kill, c.killSound);
        snd.mods.add(hit);
        snd.mods.add(kill);
        panels.add(snd);

        Panel world = new Panel("Świat");
        Module sky = new Module("Custom Sky", () -> c.skyEnabled, v -> c.skyEnabled = v);
        colorRows(sky, c.skyColor, PRESETS);
        sky.s.add(new ToggleSet("Pora dnia", () -> c.skyDaylight, v -> c.skyDaylight = v));
        sky.s.add(new ToggleSet("Chmury", () -> c.skyClouds, v -> c.skyClouds = v));
        world.mods.add(sky);
        panels.add(world);

        Panel hud = new Panel("HUD");
        Module wm = new Module("Watermark", () -> c.watermark, v -> c.watermark = v);
        wm.s.add(new ToggleSet("Pokaż FPS", () -> c.showFps, v -> c.showFps = v));
        colorRows(wm, c.hudColor, PRESETS);
        Module co = new Module("Koordynaty", () -> c.coords, v -> c.coords = v);
        hud.mods.add(wm);
        hud.mods.add(co);
        panels.add(hud);

        Panel cl = new Panel("Klient");
        Module look = new Module("Akcent GUI", null, null);
        colorRows(look, c.guiColor, PRESETS);
        look.s.add(new ToggleSet("Efekt hologramu", () -> c.hologramFx, v -> c.hologramFx = v));
        Module gr = new Module("Gradient", () -> c.guiGradient, v -> c.guiGradient = v);
        colorRows(gr, c.guiColor2, PRESETS);
        gr.s.add(new ToggleSet("Gradient napisu", () -> c.guiGradientText, v -> c.guiGradientText = v));
        Module bg = new Module("Tło GUI", null, null);
        colorRows(bg, c.guiBg, NEUTRALS);
        bg.s.add(new SliderSet("Przezroczystość", () -> (c.guiBgAlpha - 0.2f) / 0.8f, v -> c.guiBgAlpha = 0.2f + v * 0.8f,
                null, v -> (int) ((0.2f + v * 0.8f) * 100) + "%"));
        Module tx = new Module("Kolor napisu", null, null);
        colorRows(tx, c.textColor, NEUTRALS);
        cl.mods.add(look);
        cl.mods.add(gr);
        cl.mods.add(bg);
        cl.mods.add(tx);
        panels.add(cl);
    }

    private void colorRows(Module m, HoloConfig.ColorSetting cs, int[] pal) {
        m.s.add(new SwatchSet(cs, pal));
        m.s.add(new SliderSet("Odcień", () -> cs.h, v -> { cs.h = v; cs.rainbow = false; },
                f -> HoloConfig.hsv((float) f, 1f, 1f), v -> (int) (v * 360) + "°"));
        m.s.add(new SliderSet("Nasycenie", () -> cs.s, v -> cs.s = v,
                f -> HoloConfig.hsv(cs.h, (float) f, cs.b), v -> (int) (v * 100) + "%"));
        m.s.add(new SliderSet("Jasność", () -> cs.b, v -> cs.b = v,
                f -> HoloConfig.hsv(cs.h, cs.s, (float) f), v -> (int) (v * 100) + "%"));
        m.s.add(new ToggleSet("Tęcza", () -> cs.rainbow, v -> cs.rainbow = v));
    }

    private void soundRows(Module m, HoloConfig.SoundSetting ss) {
        m.s.add(new CycleSet("Dźwięk",
                () -> HoloSounds.options().stream().map(HoloSounds::label).toList(),
                () -> HoloSounds.options().indexOf(ss.sound),
                i -> ss.sound = HoloSounds.options().get(i)));
        m.s.add(new SliderSet("Głośność", () -> ss.volume, v -> ss.volume = v, null, v -> (int) (v * 100) + "%"));
        m.s.add(new SliderSet("Pitch", () -> (ss.pitch - 0.5f) / 1.5f, v -> ss.pitch = 0.5f + v * 1.5f, null,
                v -> String.format("%.2f", 0.5f + v * 1.5f)));
        m.s.add(new ButtonRow(new String[]{"Test", "Folder", "Odśw."},
                new Runnable[]{() -> HoloSounds.play(ss), HoloSounds::openFolder, HoloSounds::refresh}));
        m.s.add(new InfoSet("Przeciągnij plik", ".wav na okno gry,", "aby dodać dźwięk"));
    }

    // =====================================================================
    //  Rysowanie
    // =====================================================================
    private int argb(int rgb, float a) {
        return (((int) (MathHelper.clamp(a * fade, 0f, 1f) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) { return Gfx.mix(a, b, t); }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /** Zaokrąglony prostokąt z gradientem (pionowym albo poziomym) c1 -> c2 (ARGB). */
    private void box(DrawContext c, int x, int y, int w, int h, int r, int c1, int c2,
                     boolean horiz, boolean top, boolean bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (horiz) {
            for (int i = 0; i < w; i++) {
                int col = Gfx.lerpArgb(c1, c2, w <= 1 ? 0f : i / (float) (w - 1));
                int yt = 0, yb = 0;
                if (r > 0) {
                    int e = i < r ? i : (i >= w - r ? w - 1 - i : -1);
                    if (e >= 0) { int in = inset(r, e); if (top) yt = in; if (bottom) yb = in; }
                }
                c.fill(x + i, y + yt, x + i + 1, y + h - yb, col);
            }
        } else {
            for (int j = 0; j < h; j++) {
                int col = Gfx.lerpArgb(c1, c2, h <= 1 ? 0f : j / (float) (h - 1));
                int in = 0;
                if (r > 0) {
                    if (top && j < r) in = inset(r, j);
                    else if (bottom && j >= h - r) in = inset(r, h - 1 - j);
                }
                c.fill(x + in, y + j, x + w - in, y + j + 1, col);
            }
        }
    }

    private void rrect(DrawContext c, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
        box(c, x, y, w, h, r, color, color, false, top, bottom);
    }

    private void txt(DrawContext c, String s, int x, int y, int rgb, float a) {
        c.drawText(textRenderer, s, x, y, argb(rgb, a), false);
    }

    private void toggle(DrawContext c, int x, int y, int w, int h, boolean on) {
        if (on) box(c, x, y, w, h, h / 2, argb(accent, 1f), argb(grad ? accent2 : accent, 1f), true, true, true);
        else rrect(c, x, y, w, h, h / 2, argb(0x3A4256, 1f), true, true);
        int k = h - 4;
        int kx = on ? x + w - k - 2 : x + 2;
        rrect(c, kx, y + 2, k, k, k / 2, argb(0xFFFFFF, 1f), true, true);
    }

    private boolean matches(Module m) {
        return filter.isEmpty() || m.name.toLowerCase().contains(filter.toLowerCase());
    }

    private void layout(Panel p) {
        int cy = p.y + HEAD + 3;
        for (Module m : p.mods) {
            m.visible = matches(m);
            if (!m.visible) continue;
            m.rowY = cy;
            cy += ROW;
            if (m.open) {
                cy += 2;
                for (Setting s : m.s) { s.x = p.x + 8; s.y = cy; s.w = PW - 16; cy += s.h() + 2; }
                cy += 2;
            }
            m.endY = cy;
        }
        p.h = cy + 3 - p.y;
    }

    @Override
    protected void init() {
        int total = panels.size() * PW + (panels.size() - 1) * GAP;
        sc = MathHelper.clamp((width - 16f) / total, 0.5f, 1f);
        vw = (int) (width / sc);
        vh = (int) (height / sc);
        if (placed) return;
        placed = true;
        int x = Math.max(8, (vw - total) / 2);
        for (Panel p : panels) { p.x = x; p.y = 52; x += PW + GAP; }
    }

    @Override
    public void render(DrawContext c, int rawMx, int rawMy, float delta) {
        long now = Util.getMeasuringTimeMs();
        HoloConfig cfg = HoloConfig.get();
        accent = cfg.guiColor.rgb();
        accent2 = cfg.guiColor2.rgb();
        bgc = cfg.guiBg.rgb();
        txtC = cfg.textColor.rgb();
        bgA = cfg.guiBgAlpha;
        grad = cfg.guiGradient;

        float t = Math.min(1f, (now - openedAt) / 220f);
        fade = t;
        if (cfg.hologramFx) {
            fade *= 0.96f + 0.04f * (float) Math.sin(now / 130.0);
            if ((now / 90) % 53 == 0) fade *= 0.85f;
        }

        c.fill(0, 0, width, height, ((int) (0x66 * t)) << 24);

        c.getMatrices().push();
        c.getMatrices().scale(sc, sc, 1f);
        int mx = (int) (rawMx / sc), my = (int) (rawMy / sc);

        // tytuł
        c.getMatrices().push();
        c.getMatrices().scale(2f, 2f, 1f);
        int ta = (int) (MathHelper.clamp(fade, 0f, 1f) * 255);
        if (cfg.guiGradientText && grad) Gfx.gradientText(c, textRenderer, "holovis", 12, 8, accent, accent2, ta, false);
        else c.drawText(textRenderer, "holovis", 12, 8, (ta << 24) | txtC, false);
        c.getMatrices().pop();
        txt(c, "VISUALS", 116, 14, accent, 0.9f);

        // wyszukiwarka
        int sx = vw - 170;
        rrect(c, sx, 18, 150, 18, 5, argb(bgc, 0.85f), true, true);
        c.drawBorder(sx, 18, 150, 18, argb(accent, searchFocus ? 0.8f : 0.25f));
        String shown = filter.isEmpty() && !searchFocus ? "Szukaj..." : filter + (searchFocus && (now / 500) % 2 == 0 ? "_" : "");
        txt(c, shown, sx + 8, 24, filter.isEmpty() ? GRAY : txtC, 1f);

        if (cfg.hologramFx) {
            int sy = (int) ((now / 10) % (vh + 40)) - 20;
            c.fillGradient(0, sy - 14, vw, sy, argb(accent, 0f), argb(accent, 0.07f));
            c.fill(0, sy, vw, sy + 1, argb(accent, 0.12f));
        }

        for (Panel p : panels) renderPanel(c, p, mx, my, cfg.hologramFx);

        if (now < statusUntil) {
            int w = textRenderer.getWidth(status) + 20;
            int x = (vw - w) / 2, y = vh - 34;
            rrect(c, x, y, w, 20, 6, argb(bgc, 0.92f), true, true);
            txt(c, status, x + 10, y + 6, txtC, 1f);
        }
        txt(c, "Prawy Shift / ESC - zamknij   |   PPM na module - ustawienia", 10, vh - 14, GRAY, 0.8f);

        c.getMatrices().pop();
    }

    private void renderPanel(DrawContext c, Panel p, int mx, int my, boolean fx) {
        layout(p);
        int bodyTop = argb(grad ? mix(bgc, accent, 0.30f) : bgc, bgA);
        int bodyBot = argb(grad ? mix(bgc, accent2, 0.30f) : bgc, bgA);
        box(c, p.x, p.y + HEAD, PW, p.h - HEAD, 6, bodyTop, bodyBot, false, false, true);
        if (grad) {
            box(c, p.x, p.y, PW, HEAD, 6, argb(mix(bgc, accent, 0.8f), 0.95f), argb(mix(bgc, accent2, 0.8f), 0.95f), true, true, false);
        } else {
            rrect(c, p.x, p.y, PW, HEAD, 6, argb(mix(bgc, accent, 0.24f), 0.94f), true, false);
        }
        if (fx) {
            for (int yy = p.y + HEAD; yy < p.y + p.h - 3; yy += 3)
                c.fill(p.x + 3, yy, p.x + PW - 3, yy + 1, argb(accent, 0.04f));
        }

        txt(c, p.title, p.x + 10, p.y + 8, txtC, 1f);
        int vis = 0;
        for (Module m : p.mods) if (m.visible) vis++;
        String cnt = String.valueOf(vis);
        txt(c, cnt, p.x + PW - 10 - textRenderer.getWidth(cnt), p.y + 8, grad ? txtC : GRAY, 0.8f);

        for (Module m : p.mods) {
            if (!m.visible) continue;
            boolean hov = mx >= p.x && mx < p.x + PW && my >= m.rowY && my < m.rowY + ROW;
            boolean hasToggle = m.on != null;
            boolean on = !hasToggle || m.on.getAsBoolean();
            if (hov) c.fill(p.x + 4, m.rowY, p.x + PW - 4, m.rowY + ROW, argb(accent, 0.12f));
            if (hasToggle && on) {
                if (grad) c.fillGradient(p.x + 4, m.rowY + 4, p.x + 6, m.rowY + ROW - 4, argb(accent, 1f), argb(accent2, 1f));
                else c.fill(p.x + 4, m.rowY + 4, p.x + 6, m.rowY + ROW - 4, argb(accent, 1f));
            }
            txt(c, m.name, p.x + 12, m.rowY + 6, on ? txtC : GRAY, 1f);
            if (!m.s.isEmpty()) txt(c, m.open ? "v" : ">", p.x + PW - 46, m.rowY + 6, GRAY, 1f);
            if (hasToggle) toggle(c, p.x + PW - 32, m.rowY + 5, 22, 10, on);

            if (m.open) {
                c.fill(p.x + 4, m.rowY + ROW, p.x + PW - 4, m.endY, argb(0x000000, 0.28f));
                for (Setting s : m.s) s.draw(c, mx, my);
            }
        }
    }

    // =====================================================================
    //  Input
    // =====================================================================
    private void toggleOpen(Panel p, Module m) {
        boolean to = !m.open;
        for (Module o : p.mods) o.open = false;
        m.open = to;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx /= sc;
        my /= sc;
        int sx = vw - 170;
        searchFocus = mx >= sx && mx < sx + 150 && my >= 18 && my < 36;
        if (searchFocus) return true;

        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (mx < p.x || mx >= p.x + PW || my < p.y || my >= p.y + p.h) continue;
            if (my < p.y + HEAD) {
                if (button == 0) {
                    dragPanel = p;
                    dragDx = (int) mx - p.x;
                    dragDy = (int) my - p.y;
                    panels.remove(i);
                    panels.add(p);
                }
                return true;
            }
            for (Module m : p.mods) {
                if (!m.visible) continue;
                if (my >= m.rowY && my < m.rowY + ROW) { rowClick(p, m, mx, button); return true; }
                if (m.open) {
                    for (Setting s : m.s) {
                        if (s.hit(mx, my)) {
                            s.click(mx, my, button);
                            if (s.draggable()) dragging = s;
                            return true;
                        }
                    }
                }
            }
            return true;
        }
        return true;
    }

    private void rowClick(Panel p, Module m, double mx, int button) {
        boolean hasToggle = m.on != null, hasSet = !m.s.isEmpty();
        if (button == 1) { if (hasSet) toggleOpen(p, m); return; }
        if (button != 0) return;
        int rx = (int) mx - p.x;
        if (hasToggle && rx >= PW - 36) m.setOn.accept(!m.on.getAsBoolean());
        else if (hasSet && (rx >= PW - 54 || !hasToggle)) toggleOpen(p, m);
        else if (hasToggle) m.setOn.accept(!m.on.getAsBoolean());
        HoloConfig.save();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mx /= sc;
        my /= sc;
        if (dragPanel != null) {
            dragPanel.x = MathHelper.clamp((int) mx - dragDx, 0, Math.max(0, vw - PW));
            dragPanel.y = MathHelper.clamp((int) my - dragDy, 0, Math.max(0, vh - HEAD));
            return true;
        }
        if (dragging != null) { dragging.drag(mx); return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null || dragPanel != null) HoloConfig.save();
        dragging = null;
        dragPanel = null;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocus && chr >= 32 && filter.length() < 20) { filter += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !filter.isEmpty()) { filter = filter.substring(0, filter.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { searchFocus = false; return true; }
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** Przeciągnięcie plików .wav na okno gry = dodanie własnego dźwięku. */
    public void onFilesDropped(List<Path> paths) {
        String last = null;
        int ok = 0;
        for (Path p : paths) {
            String k = HoloSounds.importFile(p);
            if (k != null) { last = k; ok++; }
        }
        if (ok == 0) { setStatus("Obsługiwane są tylko pliki .wav"); return; }
        Module target = null, fallback = null;
        for (Panel p : panels) for (Module m : p.mods) {
            if (m.ss == null) continue;
            if (fallback == null) fallback = m;
            if (m.open) target = m;
        }
        if (target == null) target = fallback;
        if (target != null) { target.ss.sound = last; HoloConfig.save(); }
        setStatus("Dodano dźwięk: " + HoloSounds.label(last));
    }

    private void setStatus(String s) {
        status = s;
        statusUntil = Util.getMeasuringTimeMs() + 3500;
    }

    // =====================================================================
    //  Struktury
    // =====================================================================
    private class Panel {
        final String title;
        final List<Module> mods = new ArrayList<>();
        int x, y, h;
        Panel(String t) { title = t; }
    }

    private class Module {
        final String name;
        final BooleanSupplier on;
        final Consumer<Boolean> setOn;
        final List<Setting> s = new ArrayList<>();
        HoloConfig.SoundSetting ss;
        boolean open, visible = true;
        int rowY, endY;
        Module(String n, BooleanSupplier on, Consumer<Boolean> setOn) { name = n; this.on = on; this.setOn = setOn; }
    }

    private abstract class Setting {
        int x, y, w;
        abstract int h();
        abstract void draw(DrawContext c, int mx, int my);
        boolean click(double mx, double my, int button) { return false; }
        void drag(double mx) {}
        boolean draggable() { return false; }
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h(); }
        boolean hover(int mx, int my) { return hit(mx, my); }
    }

    private class ToggleSet extends Setting {
        final String label; final BooleanSupplier get; final Consumer<Boolean> set;
        ToggleSet(String l, BooleanSupplier g, Consumer<Boolean> s) { label = l; get = g; set = s; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            if (hover(mx, my)) c.fill(x - 2, y, x + w + 2, y + 16, argb(accent, 0.10f));
            txt(c, label, x + 2, y + 4, txtC, 0.95f);
            toggle(c, x + w - 20, y + 4, 18, 8, get.getAsBoolean());
        }
        boolean click(double mx, double my, int b) { set.accept(!get.getAsBoolean()); HoloConfig.save(); return true; }
    }

    private int accentLight() { return mix(accent, 0xFFFFFF, 0.55f); }

    private class CycleSet extends Setting {
        final String label; final Supplier<List<String>> opts; final IntSupplier get; final IntConsumer set;
        CycleSet(String l, Supplier<List<String>> o, IntSupplier g, IntConsumer s) { label = l; opts = o; get = g; set = s; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            if (hover(mx, my)) c.fill(x - 2, y, x + w + 2, y + 16, argb(accent, 0.10f));
            txt(c, label, x + 2, y + 4, GRAY, 1f);
            List<String> o = opts.get();
            int i = get.getAsInt();
            String v = i >= 0 && i < o.size() ? o.get(i) : "?";
            int maxW = w - textRenderer.getWidth(label) - 24;
            v = textRenderer.trimToWidth(v, Math.max(20, maxW));
            String s = "< " + v + " >";
            txt(c, s, x + w - textRenderer.getWidth(s) - 2, y + 4, accentLight(), 1f);
        }
        boolean click(double mx, double my, int b) {
            int n = opts.get().size();
            if (n == 0) return true;
            int i = get.getAsInt();
            int next = b == 1 ? (i <= 0 ? n - 1 : i - 1) : (i + 1) % n;
            set.accept(next);
            HoloConfig.save();
            return true;
        }
    }

    private class SliderSet extends Setting {
        final String label; final Supplier<Float> get; final Consumer<Float> set;
        final DoubleToIntFunction track; final Function<Float, String> fmt;
        SliderSet(String l, Supplier<Float> g, Consumer<Float> s, DoubleToIntFunction t, Function<Float, String> f) {
            label = l; get = g; set = s; track = t; fmt = f;
        }
        int h() { return 22; }
        boolean draggable() { return true; }
        void draw(DrawContext c, int mx, int my) {
            float v = MathHelper.clamp(get.get(), 0f, 1f);
            txt(c, label, x + 2, y + 1, GRAY, 1f);
            String s = fmt.apply(v);
            txt(c, s, x + w - textRenderer.getWidth(s) - 2, y + 1, txtC, 1f);
            int ty = y + 13, tw = w - 4, tx = x + 2;
            if (track != null) {
                for (int i = 0; i < tw; i += 2)
                    c.fill(tx + i, ty, tx + Math.min(i + 2, tw), ty + 4, argb(track.applyAsInt(i / (float) tw), 1f));
            } else {
                rrect(c, tx, ty, tw, 4, 2, argb(0x2A3246, 1f), true, true);
                int fw = (int) (tw * v);
                if (fw > 0) rrect(c, tx, ty, Math.max(4, fw), 4, 2, argb(accent, 1f), true, true);
            }
            int kx = tx + (int) (v * (tw - 4));
            rrect(c, kx, ty - 2, 4, 8, 2, argb(0xFFFFFF, 1f), true, true);
        }
        boolean click(double mx, double my, int b) { drag(mx); return true; }
        void drag(double mx) { set.accept(MathHelper.clamp((float) ((mx - (x + 2)) / (w - 4)), 0f, 1f)); }
    }

    private class SwatchSet extends Setting {
        final HoloConfig.ColorSetting cs; final int[] pal;
        SwatchSet(HoloConfig.ColorSetting cs, int[] pal) { this.cs = cs; this.pal = pal; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            for (int i = 0; i < pal.length; i++) {
                int bx = x + 2 + i * 14;
                boolean hov = mx >= bx && mx < bx + 12 && my >= y + 1 && my < y + 13;
                rrect(c, bx, y + 1, 12, 12, 3, argb(pal[i], 1f), true, true);
                c.drawBorder(bx, y + 1, 12, 12, argb(hov ? 0xFFFFFF : 0x808080, hov ? 0.95f : 0.35f));
            }
        }
        boolean click(double mx, double my, int b) {
            int i = (int) ((mx - (x + 2)) / 14);
            if (i >= 0 && i < pal.length && (mx - (x + 2)) % 14 < 12) {
                cs.set(pal[i]);
                cs.rainbow = false;
                HoloConfig.save();
            }
            return true;
        }
    }

    private class ButtonRow extends Setting {
        final String[] labels; final Runnable[] actions;
        ButtonRow(String[] l, Runnable[] a) { labels = l; actions = a; }
        int h() { return 18; }
        int bw() { return (w - 4 * (labels.length - 1)) / labels.length; }
        void draw(DrawContext c, int mx, int my) {
            int bw = bw();
            for (int i = 0; i < labels.length; i++) {
                int bx = x + i * (bw + 4);
                boolean hov = mx >= bx && mx < bx + bw && my >= y && my < y + 16;
                rrect(c, bx, y, bw, 16, 4, argb(accent, hov ? 0.40f : 0.22f), true, true);
                int tw = textRenderer.getWidth(labels[i]);
                txt(c, labels[i], bx + (bw - tw) / 2, y + 4, txtC, 1f);
            }
        }
        boolean click(double mx, double my, int b) {
            int bw = bw();
            int i = (int) ((mx - x) / (bw + 4));
            if (i >= 0 && i < actions.length && (mx - x) - i * (bw + 4) < bw) actions[i].run();
            return true;
        }
    }

    private class InfoSet extends Setting {
        final String[] lines;
        InfoSet(String... l) { lines = l; }
        int h() { return lines.length * 10 + 2; }
        void draw(DrawContext c, int mx, int my) {
            for (int i = 0; i < lines.length; i++) txt(c, lines[i], x + 2, y + 2 + i * 10, GRAY, 0.85f);
        }
    }
}
