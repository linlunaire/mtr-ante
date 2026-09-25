package cn.zbx1425.mtrsteamloco.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class WidgetLabel extends AbstractWidget implements IGraphics {
    public boolean alignR;
    public boolean centre = true;
    public int color = -1;
    private final Runnable onClick;

    public WidgetLabel(int x, int y, int width, Component text) { this(x, y, width, text, null); }
    public WidgetLabel(int x, int y, int width, Component text, Runnable onClick) {
        super(x, y, width, text.getString().split("\n", -1).length * 10, text);
        this.onClick = onClick;
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible || alpha <= 0) return;
        String[] lines = getMessage().getString().split("\n", -1);
        height = lines.length * 10;
        int textColor = fadedColor(color);
        if (isMouseOver(mouseX, mouseY)) graphics.fill(getX(), getY(), getRight(), getBottom(), fadedColor(0x88A8A9AD));
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < lines.length; i++) {
            int textWidth = font.width(lines[i]);
            int x = centre ? getX() + (width - textWidth) / 2 : alignR ? getRight() - textWidth : getX();
            int y = getY() + 10 * i;
            if (textWidth > width) {
                int offset = (int) (System.currentTimeMillis() / 25 % (textWidth + 40));
                graphics.enableScissor(getX(), getY(), getRight(), getBottom());
                try {
                    graphics.text(font, lines[i], x - offset, y, textColor);
                    graphics.text(font, lines[i], x + textWidth + 40 - offset, y, textColor);
                } finally { graphics.disableScissor(); }
            } else graphics.text(font, lines[i], x, y, textColor);
            if (!isActive()) graphics.text(font, "▶", x - 8, y, fadedColor(0xFFFF0000));
        }
    }

    private int fadedColor(int value) {
        int opacity = value >>> 24;
        if (opacity == 0) opacity = 255; // Existing ANTE labels also accept 24-bit RGB.
        return Math.round(opacity * Math.clamp(alpha, 0, 1)) << 24 | value & 0xFFFFFF;
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event, doubleClick);
        if (onClick != null) onClick.run();
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
