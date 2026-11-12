package com.luminamods;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class LuminaScreen extends Screen {
    private int panelLeft, panelTop, panelRight, panelBottom, titleY;

    public LuminaScreen() {
        super(Text.literal("LuminaMods"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y0 = this.height / 2 - 70;
        int xl = cx - 155;
        int xr = cx + 5;
        int w = 150;

        panelLeft = cx - 165;
        panelRight = cx + 165;
        panelTop = y0 - 34;
        panelBottom = y0 + 6 * 24 + 22;
        titleY = y0 - 24;

        addDrawableChild(toggle(xl, y0, w, "Click sounds", () -> LuminaConfig.clickSounds, v -> LuminaConfig.clickSounds = v));

        addDrawableChild(ButtonWidget.builder(soundLabel(), b -> {
            LuminaConfig.soundIndex = (LuminaConfig.soundIndex + 1) % LuminaConfig.SOUND_IDS.length;
            b.setMessage(soundLabel());
            LuminaClient.click(MinecraftClient.getInstance());
        }).dimensions(xr, y0, w, 20).build());

        addDrawableChild(toggle(xl, y0 + 24, w, "Keystrokes", () -> LuminaConfig.keystrokes, v -> LuminaConfig.keystrokes = v));
        addDrawableChild(toggle(xr, y0 + 24, w, "FPS counter", () -> LuminaConfig.fps, v -> LuminaConfig.fps = v));
        addDrawableChild(toggle(xl, y0 + 48, w, "Coordinates", () -> LuminaConfig.coords, v -> LuminaConfig.coords = v));
        addDrawableChild(toggle(xr, y0 + 48, w, "Watermark", () -> LuminaConfig.watermark, v -> LuminaConfig.watermark = v));
        addDrawableChild(toggle(xl, y0 + 72, w, "Rainbow", () -> LuminaConfig.rainbow, v -> LuminaConfig.rainbow = v));

        addDrawableChild(ButtonWidget.builder(colorLabel(), b -> {
            LuminaConfig.colorIndex = (LuminaConfig.colorIndex + 1) % LuminaConfig.COLORS.length;
            b.setMessage(colorLabel());
        }).dimensions(xr, y0 + 72, w, 20).build());

        addDrawableChild(new VolumeSlider(xl, y0 + 96, 310, 20));

        addDrawableChild(ButtonWidget.builder(Text.literal("Copy Discord"), b -> {
            MinecraftClient.getInstance().keyboard.setClipboard("https://dc.gg/luminamods");
            b.setMessage(Text.literal("Copied!"));
        }).dimensions(xl, y0 + 120, 150, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
            .dimensions(xr, y0 + 120, 150, 20).build());
    }

    private static Text soundLabel() {
        return Text.literal("Sound: " + LuminaConfig.SOUND_NAMES[LuminaConfig.soundIndex]);
    }

    private static Text colorLabel() {
        return Text.literal("Color: " + LuminaConfig.COLOR_NAMES[LuminaConfig.colorIndex]);
    }

    private static ButtonWidget toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return ButtonWidget.builder(toggleLabel(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleLabel(name, get.getAsBoolean()));
        }).dimensions(x, y, w, 20).build();
    }

    private static Text toggleLabel(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF")).formatted(on ? Formatting.GREEN : Formatting.RED);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        int accent = LuminaConfig.accent();
        ctx.fill(panelLeft, panelTop, panelRight, panelBottom, 0xD0101018);
        ctx.fill(panelLeft, panelTop, panelRight, panelTop + 2, accent);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "LUMINAMODS", this.width / 2, titleY, accent);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "by k4cperts  |  dc.gg/luminamods",
            this.width / 2, panelBottom - 14, 0xFFAAAAAA);
    }

    @Override
    public void removed() {
        LuminaConfig.save();
    }

    private static class VolumeSlider extends SliderWidget {
        VolumeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), LuminaConfig.volume);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Volume: " + (int) Math.round(this.value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            LuminaConfig.volume = (float) this.value;
        }
    }
}
