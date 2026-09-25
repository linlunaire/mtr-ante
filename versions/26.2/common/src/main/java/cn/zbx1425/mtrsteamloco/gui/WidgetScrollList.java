package cn.zbx1425.mtrsteamloco.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;

public class WidgetScrollList extends AbstractScrollWidget {
    public final ArrayList<AbstractWidget> children = new ArrayList<>();
    private AbstractWidget focusedChild;

    public WidgetScrollList(int x, int y, int width, int height) { super(x, y, width, height, Component.empty()); }

    @Override protected void renderContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().translate(getX(), getY());
        for (AbstractWidget child : children) {
            if (child.getBottom() < getOffset() || child.getY() > getOffset() + height) continue;
            child.extractRenderState(graphics, mouseX - getX(), (int) (mouseY + getOffset()) - getY(), delta);
        }
    }

    private MouseButtonEvent local(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() - getX(), event.y() + getOffset() - getY(), event.buttonInfo());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!visible || !active || !isMouseInside(event.x(), event.y())) return false;
        for (AbstractWidget child : children) {
            if (child.mouseClicked(local(event), doubleClick)) {
                if (focusedChild != null) focusedChild.setFocused(false);
                focusedChild = child; child.setFocused(true);
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = super.mouseReleased(event);
        return focusedChild != null && children.contains(focusedChild) && focusedChild.mouseReleased(local(event)) || handled;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (super.mouseDragged(event, dx, dy)) return true;
        return visible && active && focusedChild != null && children.contains(focusedChild) && focusedChild.mouseDragged(local(event), dx, dy);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        return isFocused() && focusedChild != null && children.contains(focusedChild) && focusedChild.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        return isFocused() && focusedChild != null && children.contains(focusedChild) && focusedChild.charTyped(event);
    }

    @Override public void mouseMoved(double x, double y) {
        for (AbstractWidget child : children) child.mouseMoved(x - getX(), y + getOffset() - getY());
    }

    @Override protected int getContentHeight() { return children.stream().mapToInt(AbstractWidget::getBottom).max().orElse(0); }
    @Override protected boolean getScrollBarVisible() { return getContentHeight() > height; }
    @Override protected double getScrollInterval() { return children.isEmpty() ? 0 : children.getLast().getHeight(); }
    @Override public void setHeight(int height) { super.setHeight(height); setOffset(getOffset()); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        if (focusedChild != null && children.contains(focusedChild)) focusedChild.updateNarration(output.nest());
    }
}
