package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
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

/** Menu w stylu "kolumn z modułami": ciemne panele, przełączniki, rozwijane ustawienia. */
public class HoloScreen extends Screen {
    private static final int PW = 164, GAP = 14, HEAD = 24, ROW = 20;
    private static final int WHITE = 0xE8EEF8, GRAY = 0x8791A6, BASE = 0x0A1020;
    private static final int[] PRESETS = {0x00F0FF, 0x3D7BFF, 0xB04CFF, 0xFF4FD8, 0xFF3B3B, 0xFF9A1F, 0xFFE81F, 0x3BFF7A, 0xFFFFFF};

    private final List<Panel> panels = new ArrayList<>();
    private final long openedAt = Util.getMeasuringTimeMs();
    private float fade = 1f;
    private int accent = 0x00F0FF;
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
        colorRows(hb, c.hitboxColor);
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

        Panel cl = new Panel("Klient");
        Module gui = new Module("Wygląd GUI", null, null);
        colorRows(gui, c.guiColor);
        gui.s.add(new ToggleSet("Efekt hologramu", () -> c.hologramFx, v -> c.hologramFx = v));
        cl.mods.add(gui);
        panels.add(cl);
    }

    private void colorRows(Module m, HoloConfig.ColorSetting cs) {
        m.s.add(new SwatchSet(cs));
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
        m.s.add(new InfoSet("Przeciągnij plik .wav", "na okno, aby dodać dźwięk"));
    }

    // =====================================================================
    //  Rysowanie
    // =====================================================================
    private int argb(int rgb, float a) {
        return (((int) (MathHelper.clamp(a * fade, 0f, 1f) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    private void rrect(DrawContext c, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
        int tr = top ? r : 0, br = bottom ? r : 0;
        c.fill(x, y + tr, x + w, y + h - br, color);
        for (int i = 0; i < tr; i++) { int in = inset(r, i); c.fill(x + in, y + i, x + w - in, y + i + 1, color); }
        for (int i = 0; i < br; i++) { int in = inset(r, i); c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color); }
    }

    private void txt(DrawContext c, String s, int x, int y, int rgb, float a) {
        c.drawText(textRenderer, s, x, y, argb(rgb, a), false);
    }

    private void toggle(DrawContext c, int x, int y, int w, int h, boolean on) {
        rrect(c, x, y, w, h, h / 2, on ? argb(accent, 1f) : argb(0x3A4256, 1f), true, true);
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
        if (placed) return;
        placed = true;
        int total = panels.size() * PW + (panels.size() - 1) * GAP;
        int x = Math.max(8, (width - total) / 2);
        for (Panel p : panels) { p.x = x; p.y = 52; x += PW + GAP; }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = Util.getMeasuringTimeMs();
        HoloConfig cfg = HoloConfig.get();
        accent = cfg.guiColor.rgb();
        float t = Math.min(1f, (now - openedAt) / 220f);
        fade = t;
        if (cfg.hologramFx) {
            fade *= 0.96f + 0.04f * (float) Math.sin(now / 130.0);
            if ((now / 90) % 53 == 0) fade *= 0.85f;
        }

        c.fill(0, 0, width, height, ((int) (0x66 * t)) << 24);

        // tytuł
        c.getMatrices().push();
        c.getMatrices().scale(2f, 2f, 1f);
        txt(c, "holovis", 12, 8, WHITE, 1f);
        c.getMatrices().pop();
        txt(c, "VISUALS", 100, 14, accent, 0.9f);

        // wyszukiwarka
        int sx = width - 170;
        rrect(c, sx, 18, 150, 18, 5, argb(BASE, 0.85f), true, true);
        if (searchFocus) c.drawBorder(sx, 18, 150, 18, argb(accent, 0.8f));
        String shown = filter.isEmpty() && !searchFocus ? "Szukaj..." : filter + (searchFocus && (now / 500) % 2 == 0 ? "_" : "");
        txt(c, shown, sx + 8, 24, filter.isEmpty() ? GRAY : WHITE, 1f);

        if (cfg.hologramFx) {
            int sy = (int) ((now / 10) % (height + 40)) - 20;
            c.fillGradient(0, sy - 14, width, sy, argb(accent, 0f), argb(accent, 0.07f));
            c.fill(0, sy, width, sy + 1, argb(accent, 0.12f));
        }

        for (Panel p : panels) renderPanel(c, p, mx, my, cfg.hologramFx);

        if (now < statusUntil) {
            int w = textRenderer.getWidth(status) + 20;
            int x = (width - w) / 2, y = height - 34;
            rrect(c, x, y, w, 20, 6, argb(BASE, 0.92f), true, true);
            txt(c, status, x + 10, y + 6, WHITE, 1f);
        }
        txt(c, "Prawy Shift / ESC - zamknij   |   PPM na module - ustawienia", 10, height - 14, GRAY, 0.8f);
    }

    private void renderPanel(DrawContext c, Panel p, int mx, int my, boolean fx) {
        layout(p);
        rrect(c, p.x, p.y + HEAD, PW, p.h - HEAD, 6, argb(mix(BASE, accent, 0.10f), 0.88f), false, true);
        rrect(c, p.x, p.y, PW, HEAD, 6, argb(mix(BASE, accent, 0.24f), 0.94f), true, false);
        if (fx) {
            for (int yy = p.y + HEAD; yy < p.y + p.h - 3; yy += 3)
                c.fill(p.x + 3, yy, p.x + PW - 3, yy + 1, argb(accent, 0.04f));
        }

        txt(c, p.title, p.x + 10, p.y + 8, WHITE, 1f);
        int vis = 0;
        for (Module m : p.mods) if (m.visible) vis++;
        String cnt = String.valueOf(vis);
        txt(c, cnt, p.x + PW - 10 - textRenderer.getWidth(cnt), p.y + 8, GRAY, 1f);

        for (Module m : p.mods) {
            if (!m.visible) continue;
            boolean hov = mx >= p.x && mx < p.x + PW && my >= m.rowY && my < m.rowY + ROW;
            boolean hasToggle = m.on != null;
            boolean on = !hasToggle || m.on.getAsBoolean();
            if (hov) c.fill(p.x + 4, m.rowY, p.x + PW - 4, m.rowY + ROW, argb(accent, 0.12f));
            if (hasToggle && on) c.fill(p.x + 4, m.rowY + 4, p.x + 6, m.rowY + ROW - 4, argb(accent, 1f));
            txt(c, m.name, p.x + 12, m.rowY + 6, on ? WHITE : GRAY, 1f);
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
        int sx = width - 170;
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
        return super.mouseClicked(mx, my, button);
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
        if (dragPanel != null) {
            dragPanel.x = MathHelper.clamp((int) mx - dragDx, 0, Math.max(0, width - PW));
            dragPanel.y = MathHelper.clamp((int) my - dragDy, 0, Math.max(0, height - HEAD));
            return true;
        }
        if (dragging != null) { dragging.drag(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null || dragPanel != null) HoloConfig.save();
        dragging = null;
        dragPanel = null;
        return super.mouseReleased(mx, my, button);
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
            txt(c, label, x + 2, y + 4, WHITE, 0.95f);
            toggle(c, x + w - 20, y + 4, 18, 8, get.getAsBoolean());
        }
        boolean click(double mx, double my, int b) { set.accept(!get.getAsBoolean()); HoloConfig.save(); return true; }
    }

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
            int maxW = w - textRenderer.getWidth(label) - 16;
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

    private int accentLight() { return mix(accent, 0xFFFFFF, 0.55f); }

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
            txt(c, s, x + w - textRenderer.getWidth(s) - 2, y + 1, WHITE, 1f);
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
        final HoloConfig.ColorSetting cs;
        SwatchSet(HoloConfig.ColorSetting cs) { this.cs = cs; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            for (int i = 0; i < PRESETS.length; i++) {
                int bx = x + 2 + i * 16;
                boolean hov = mx >= bx && mx < bx + 14 && my >= y + 1 && my < y + 13;
                rrect(c, bx, y + 1, 14, 12, 3, argb(PRESETS[i], 1f), true, true);
                if (hov) c.drawBorder(bx - 1, y, 16, 14, argb(0xFFFFFF, 0.9f));
            }
        }
        boolean click(double mx, double my, int b) {
            int i = (int) ((mx - (x + 2)) / 16);
            if (i >= 0 && i < PRESETS.length) { cs.set(PRESETS[i]); cs.rainbow = false; HoloConfig.save(); }
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
                txt(c, labels[i], bx + (bw - tw) / 2, y + 4, WHITE, 1f);
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
