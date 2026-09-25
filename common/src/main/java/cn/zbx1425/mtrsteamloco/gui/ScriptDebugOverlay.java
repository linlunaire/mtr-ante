package cn.zbx1425.mtrsteamloco.gui;

import cn.zbx1425.mtrsteamloco.ClientConfig;
import cn.zbx1425.mtrsteamloco.scripting.AbstractScriptContext;
import cn.zbx1425.mtrsteamloco.scripting.ScriptContextManager;
import cn.zbx1425.mtrsteamloco.scripting.ScriptHolderBase;
import cn.zbx1425.mtrsteamloco.scripting.util.client.GraphicsTexture;
import com.google.common.base.Splitter;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import cn.zbx1425.mtrsteamloco.scripting.util.OrderedMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptDebugOverlay {

    public static final OrderedMap<String, Object> STATIC = new OrderedMap<>();
    // cn.zbx1425.mtrsteamloco.gui.ScriptDebugOverlay.STATIC.put(, );

    public synchronized static void render(GuiGraphicsExtractor vdStuff) {
        var matrices = vdStuff.pose();
        if (!ClientConfig.enableScriptDebugOverlay) return;
        if (Minecraft.getInstance().gui.screen() != null) return;

        matrices.pushMatrix();
        try {
        matrices.translate(10, 10);

        Map<ScriptHolderBase, List<AbstractScriptContext>> contexts = new HashMap<>();
        for (Map.Entry<AbstractScriptContext, ScriptHolderBase> entry : ScriptContextManager.livingContexts.entrySet()) {
            contexts.computeIfAbsent(entry.getValue(), k -> new java.util.ArrayList<>()).add(entry.getKey());
        }

        int y = 0, maxy = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        Font font = Minecraft.getInstance().font;
        int lineHeight = Mth.ceil(font.lineHeight * 1.2f);

        for (Map.Entry<String, Object> entry : STATIC.entryList()) {
            y = drawText(vdStuff, font, entry.getKey() + ": " + entry.getValue(), 20, y, 0xFFFFFFFF);
        }

        for (Map.Entry<ScriptHolderBase, List<AbstractScriptContext>> entry : contexts.entrySet()) {
            if (y >= maxy) break;
            ScriptHolderBase holder = entry.getKey();
            if (holder.failTime > 0) {
                y = drawText(vdStuff, font, holder.name + " FAILED", 0, y, 0xFFFF0000);
                if (holder.failException != null) {
                    y = drawText(vdStuff, font, holder.failException.getMessage(), 5, y, 0xFFFF8888);
                }
            } else {
                y = drawText(vdStuff, font, holder.name, 0, y, 0xFFAAAAFF);
            }
            for (AbstractScriptContext context : entry.getValue()) {
                if (y >= maxy) break;
                y = drawText(vdStuff, font,
                    String.format("#%08X (%.4f ms)", context.hashCode(), context.lastExecuteDuration / 1e6),
                    10, y, 0xFFCCCCFF);
                List<Map.Entry<String, Object>> debugInfos = context.getDebugInfo().entryList();
                for (Map.Entry<String, Object> debugInfo : debugInfos) {
                    if (y >= maxy) break;
                    Object value = debugInfo.getValue();
                    if (value instanceof GraphicsTexture) {
                        GraphicsTexture texture = (GraphicsTexture) value;
                        float scale0 = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - 40) / (float) texture.width;
                        float scale1 = font.lineHeight * 5 / (float) texture.height;
                        float scale = Math.min(scale0, scale1);
                        y = drawText(vdStuff, font, debugInfo.getKey() + ": GraphicsTexture", 20, y, 0xFFFFFFFF);
                        blit(vdStuff, texture.identifier, 20, y, (int)(texture.width * scale), (int)(texture.height * scale));
                        y += (int)(texture.height * scale);
                    } else {
                        y = drawText(vdStuff, font, debugInfo.getKey() + ": " + debugInfo.getValue(), 20, y, 0xFFFFFFFF);
                    }
                    y += Mth.ceil(font.lineHeight * 0.2f);
                }
            }
        }

        } finally {
            matrices.popMatrix();
        }
    }

    private static int drawText(GuiGraphicsExtractor guiGraphics, Font font, String text, int x, int y, int color) {
        FormattedText formattedText = FormattedText.of(text);
        List<FormattedCharSequence> lines = font.split(formattedText, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 40);
        for (FormattedCharSequence line : lines) {
            guiGraphics.text(font, line, x, y, color);
            y += Mth.ceil(font.lineHeight * 1.1f);
        }
        return y;
    }
    private static void blit(GuiGraphicsExtractor guiGraphics, Identifier texture, int x, int y, int width, int height) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, width, height);
    }
}
