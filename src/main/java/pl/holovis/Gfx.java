package pl.holovis;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class Gfx {
    private Gfx() {}

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int lerpArgb(int a, int b, float t) {
        int al = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        return (al << 24) | mix(a & 0xFFFFFF, b & 0xFFFFFF, t);
    }

    /** Tekst z gradientem c1 -> c2 (litera po literze). alpha 0..255. */
    public static void gradientText(DrawContext ctx, TextRenderer tr, String s, int x, int y,
                                    int c1, int c2, int alpha, boolean shadow) {
        int cx = x;
        int n = Math.max(1, s.length() - 1);
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            ctx.drawText(tr, ch, cx, y, (alpha << 24) | mix(c1, c2, i / (float) n), shadow);
            cx += tr.getWidth(ch);
        }
    }
}
