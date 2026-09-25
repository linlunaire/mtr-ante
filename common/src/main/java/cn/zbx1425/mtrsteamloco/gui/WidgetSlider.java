package cn.zbx1425.mtrsteamloco.gui;

import net.minecraft.client.input.KeyEvent;
import mtr.data.IGui;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;
import java.util.function.Function;

public class WidgetSlider extends AbstractSliderButton implements IGui {

    private final int maxValue;
    private final Function<Integer, Component> setMessage;
    private boolean editable = true;

    private static final int SLIDER_WIDTH = 10;

    public WidgetSlider(int maxValue, int value, Function<Integer, String> setMessage) {
        this(20, maxValue, value, i -> Text.literal(setMessage.apply(i)));
    }

    public WidgetSlider(int height, int maxValue, int value, Function<Integer, Component> setMessage) {
        super(0, 0, 0, height, Text.literal(""), 0);
        this.maxValue = maxValue;
        this.setMessage = setMessage;
        this.setValue(value);
    }


    @Override
    protected void updateMessage() {
        setMessage(setMessage.apply(getIntValue()));
    }

    @Override
    protected void applyValue() {
    }

    public void setValue(int valueInt) {
        if (!editable) return;
        value = (double) valueInt / maxValue;
        updateMessage();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!editable) return false;
        return super.keyPressed(event);
    }

    public int getIntValue() {
        return (int) Math.round(value * maxValue);
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }
}
