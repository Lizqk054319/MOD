package com.luminamods;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Random;

public class LuminaClient implements ClientModInitializer {
    private static KeyBinding openKey;
    private static final Random RNG = new Random();
    private static final boolean[] keyDown = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final boolean[] mouseDown = new boolean[3];
    private static final ArrayDeque<Long> lmb = new ArrayDeque<>();
    private static final ArrayDeque<Long> rmb = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {
        LuminaConfig.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.luminamods.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.luminamods"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) {
                mc.setScreen(new LuminaScreen());
            }
            poll(mc);
        });
        HudRenderCallback.EVENT.register(LuminaClient::renderHud);
    }

    // ---------- DZWIEKI ----------

    public static void click(MinecraftClient mc) {
        if (!LuminaConfig.clickSounds || LuminaConfig.volume <= 0f) return;
        SoundEvent ev = SoundEvent.of(Identifier.ofVanilla(LuminaConfig.SOUND_IDS[LuminaConfig.soundIndex]));
        float pitch = 0.9f + RNG.nextFloat() * 0.2f;
        mc.getSoundManager().play(PositionedSoundInstance.master(ev, pitch, LuminaConfig.volume));
    }

    private static void poll(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        long h = mc.getWindow().getHandle();

        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            boolean down = GLFW.glfwGetKey(h, k) == GLFW.GLFW_PRESS;
            if (down && !keyDown[k]) click(mc);
            keyDown[k] = down;
        }
        for (int b = 0; b < 3; b++) {
            boolean down = GLFW.glfwGetMouseButton(h, b) == GLFW.GLFW_PRESS;
            if (down && !mouseDown[b]) {
                click(mc);
                if (b == 0) lmb.addLast(System.currentTimeMillis());
                if (b == 1) rmb.addLast(System.currentTimeMillis());
            }
            mouseDown[b] = down;
        }
    }

    private static int cps(ArrayDeque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
        return q.size();
    }

    // ---------- HUD ----------

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        poll(mc);

        TextRenderer tr = mc.textRenderer;
        int accent = LuminaConfig.accent();
        int y = 4;

        if (LuminaConfig.watermark) {
            line(ctx, tr, "LuminaMods", 4, y, accent, accent);
            y += 14;
        }
        if (LuminaConfig.fps) {
            line(ctx, tr, "FPS: " + mc.getCurrentFps(), 4, y, accent, 0xFFFFFFFF);
            y += 14;
        }
        if (LuminaConfig.coords) {
            line(ctx, tr, "XYZ: " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ(),
                4, y, accent, 0xFFFFFFFF);
        }
        if (LuminaConfig.keystrokes) {
            long h = mc.getWindow().getHandle();
            int x = 10;
            int by = mc.getWindow().getScaledHeight() - 92;
            keyBox(ctx, tr, x + 25, by, 22, 22, "W", mc.options.forwardKey.isPressed(), accent);
            keyBox(ctx, tr, x, by + 25, 22, 22, "A", mc.options.leftKey.isPressed(), accent);
            keyBox(ctx, tr, x + 25, by + 25, 22, 22, "S", mc.options.backKey.isPressed(), accent);
            keyBox(ctx, tr, x + 50, by + 25, 22, 22, "D", mc.options.rightKey.isPressed(), accent);
            keyBox(ctx, tr, x, by + 50, 35, 22, "L " + cps(lmb),
                GLFW.glfwGetMouseButton(h, 0) == GLFW.GLFW_PRESS, accent);
            keyBox(ctx, tr, x + 37, by + 50, 35, 22, "R " + cps(rmb),
                GLFW.glfwGetMouseButton(h, 1) == GLFW.GLFW_PRESS, accent);
            keyBox(ctx, tr, x, by + 75, 72, 12, "SPACE", mc.options.jumpKey.isPressed(), accent);
        }
    }

    private static void line(DrawContext ctx, TextRenderer tr, String text, int x, int y, int accent, int textColor) {
        int w = tr.getWidth(text);
        ctx.fill(x, y, x + w + 10, y + 12, 0x90000000);
        ctx.fill(x, y, x + 2, y + 12, accent);
        ctx.drawTextWithShadow(tr, text, x + 6, y + 2, textColor);
    }

    private static void keyBox(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                               String label, boolean down, int accent) {
        int bg = down ? ((accent & 0x00FFFFFF) | 0xC0000000) : 0x90000000;
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + (h - 8) / 2, down ? 0xFF000000 : 0xFFFFFFFF);
    }
}
