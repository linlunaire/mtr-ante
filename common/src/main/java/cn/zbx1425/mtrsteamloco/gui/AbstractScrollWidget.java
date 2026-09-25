package cn.zbx1425.mtrsteamloco.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Scroll contents and bars are extracted into GUI state, including nested clipping. */
public abstract class AbstractScrollWidget extends AbstractWidget {
    private double offset;
    private boolean holdingScrollBar;

    public AbstractScrollWidget(int x, int y, int width, int height, Component component) {
        super(x, y, width, height, component);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!visible || !active) return false;
        boolean inside = isMouseInside(event.x(), event.y());
        boolean bar = getScrollBarVisible() && event.x() >= getRight() && event.x() < getRight() + 8
                && event.y() >= getY() && event.y() < getBottom();
        setFocused(inside || bar);
        holdingScrollBar = bar && event.button() == 0;
        return holdingScrollBar;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        boolean held = holdingScrollBar;
        if (event.button() == 0) holdingScrollBar = false;
        return held || super.mouseReleased(event);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!visible || !active || !isFocused() || !holdingScrollBar || event.button() != 0) return false;
        if (event.y() < getY()) setOffset(0);
        else if (event.y() > getBottom()) setOffset(getMaxOffset());
        else setOffset(offset + dragY * Math.max(1D, (double) getMaxOffset() / Math.max(1, height - getScrollBarHeight())));
        return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!visible || !active || !isMouseInside(mouseX, mouseY)) return false;
        setFocused(true);
        setOffset(offset - vertical * getScrollInterval());
        return true;
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        setOffset(offset); // Content can shrink after filtering.
        graphics.fill(getX(), getY() + 1, getRight(), getBottom() - 1, isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, 0xFF555555);
        graphics.enableScissor(getX() + 1, getY() + 1, Math.max(getX() + 1, getRight() - 1), Math.max(getY() + 1, getBottom() - 1));
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(0, (float) -offset);
            renderContents(graphics, mouseX, mouseY, delta);
        } finally {
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }
        if (getScrollBarVisible()) {
            int barHeight = getScrollBarHeight();
            int top = getY() + (int) (offset * (height - barHeight) / Math.max(1, getMaxOffset()));
            graphics.fill(getRight(), top, getRight() + 8, top + barHeight, 0xFF808080);
            graphics.fill(getRight(), top, getRight() + 7, top + barHeight - 1, 0xFFC0C0C0);
        }
    }

    private int getScrollBarHeight() {
        return Math.max(1, Math.min(height, Math.max(32, (int) ((double) height * height / Math.max(1, getContentHeight())))));
    }

    protected double getOffset() { return offset; }
    protected void setOffset(double value) { offset = Mth.clamp(value, 0, getMaxOffset()); }
    protected int getMaxOffset() { return Math.max(0, getContentHeight() - height); }
    protected boolean isMouseInside(double x, double y) { return x >= getX() && x < getRight() && y >= getY() && y < getBottom(); }
    protected abstract int getContentHeight();
    protected abstract boolean getScrollBarVisible();
    protected abstract double getScrollInterval();
    protected abstract void renderContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);
}
