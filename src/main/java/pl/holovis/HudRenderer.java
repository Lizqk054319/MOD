package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class HudRenderer {
    private HudRenderer() {}

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        if (mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;

        int c1, c2;
        if (cfg.guiGradient) { c1 = cfg.guiColor.rgb(); c2 = cfg.guiColor2.rgb(); }
        else { c1 = cfg.hudColor.rgb(); c2 = c1; }

        if (cfg.watermark) {
            String name = "HoloVis";
            String rest = cfg.showFps ? " | " + mc.getCurrentFps() + " FPS" : "";
            int w = tr.getWidth(name) + tr.getWidth(rest);
            int x = 8, y = 8;
            ctx.fill(x - 4, y - 3, x + w + 4, y + 11, 0x88000000);
            ctx.fill(x - 4, y - 3, x - 2, y + 11, 0xFF000000 | c1);
            Gfx.gradientText(ctx, tr, name, x, y, c1, c2, 255, false);
            if (!rest.isEmpty()) ctx.drawText(tr, rest, x + tr.getWidth(name), y, 0xFFE8EEF8, false);
        }

        if (cfg.coords) {
            String label = "XYZ ";
            String val = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
            int w = tr.getWidth(label) + tr.getWidth(val);
            int x = 8, y = ctx.getScaledWindowHeight() - 20;
            ctx.fill(x - 4, y - 3, x + w + 4, y + 11, 0x88000000);
            ctx.fill(x - 4, y - 3, x - 2, y + 11, 0xFF000000 | c1);
            Gfx.gradientText(ctx, tr, label, x, y, c1, c2, 255, false);
            ctx.drawText(tr, val, x + tr.getWidth(label), y, 0xFFE8EEF8, false);
        }
    }
}
