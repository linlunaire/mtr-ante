package cn.zbx1425.mtrsteamloco.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.data.IGui;
import mtr.mappings.RenderBufferSource;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import mtr.screen.WidgetBetterTextField;
import java.util.ArrayList;
import java.util.List;

public interface IGraphics {
    private static PoseStack guiPose(GuiGraphicsExtractor graphics) {
        var m = graphics.pose();
        var pose = new PoseStack();
        pose.mulPose(new org.joml.Matrix4f().m00(m.m00()).m01(m.m01())
                .m10(m.m10()).m11(m.m11()).m30(m.m20()).m31(m.m21()));
        return pose;
    }

    default void drawStringWithFont(GuiGraphicsExtractor gg, Font textRenderer, RenderBufferSource immediate, String text, float x, float y, int light) {
        drawStringWithFont(gg, textRenderer, immediate, text, IGui.HorizontalAlignment.CENTER, IGui.VerticalAlignment.CENTER,
                x, y, -1, -1, 1, IGui.ARGB_WHITE, true, light, null);
	}

	default void drawStringWithFont(GuiGraphicsExtractor gg, Font textRenderer, RenderBufferSource immediate, String text, IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment, float x, float y, float maxWidth, float maxHeight, float scale, int textColor, boolean shadow, int light, DrawingCallback drawingCallback) {
		drawStringWithFont(gg, textRenderer, immediate, text, horizontalAlignment, verticalAlignment, horizontalAlignment, x, y, maxWidth, maxHeight, scale, textColor, shadow, light, drawingCallback);
	}

	default void drawStringWithFont(GuiGraphicsExtractor gg, Font textRenderer, RenderBufferSource immediate, String text, IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment, IGui.HorizontalAlignment xAlignment, float x, float y, float maxWidth, float maxHeight, float scale, int textColor, boolean shadow, int light, DrawingCallback drawingCallback) {
		drawStringWithFont(gg, textRenderer, immediate, text, horizontalAlignment, verticalAlignment, xAlignment, x, y, maxWidth, maxHeight, scale, textColor, textColor, 2, shadow, light, drawingCallback);
	}

	default void drawStringWithFont(GuiGraphicsExtractor graphics, Font textRenderer, RenderBufferSource immediate, String text, IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment, IGui.HorizontalAlignment xAlignment, float x, float y, float maxWidth, float maxHeight, float scale, int textColorCjk, int textColor, float fontSizeRatio, boolean shadow, int light, DrawingCallback drawingCallback) {
        if (maxWidth == 0 || maxHeight == 0 || scale <= 0 || !Float.isFinite(scale) || fontSizeRatio <= 0) return;
        final List<FormattedCharSequence> lines = new ArrayList<>();
        final List<Boolean> cjkLines = new ArrayList<>();
        float totalHeight = 0, totalWidth = 0;
        for (String line : text.replaceAll("\\|+", "|").split("\\|")) {
            boolean cjk = IGui.isCjk(line);
            var sequence = Text.literal(line).getVisualOrderText();
            lines.add(sequence); cjkLines.add(cjk);
            float extra = cjk ? fontSizeRatio : 1;
            totalHeight += IGui.LINE_HEIGHT * extra;
            totalWidth = Math.max(totalWidth, textRenderer.width(sequence) * extra);
        }
        if (lines.isEmpty()) return;
        if (maxHeight > 0 && totalHeight / scale > maxHeight) scale = totalHeight / maxHeight;
        final float scaleX = maxWidth > 0 && totalWidth > maxWidth * scale ? totalWidth / maxWidth : scale;
        final float renderedWidth = maxWidth > 0 ? Math.min(totalWidth / scale, maxWidth) : totalWidth / scale;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(1 / scaleX, 1 / scale);
            float offset = verticalAlignment.getOffset(y * scale, totalHeight);
            for (int i = 0; i < lines.size(); i++) {
                boolean cjk = cjkLines.get(i);
                float extra = cjk ? fontSizeRatio : 1;
                float xOffset = horizontalAlignment.getOffset(xAlignment.getOffset(x * scaleX, totalWidth),
                        textRenderer.width(lines.get(i)) * extra - totalWidth);
                int color = cjk ? textColorCjk : textColor;
                float shade = light == IGui.MAX_LIGHT_GLOWING ? 1 : Math.min(LightCoordsUtil.block(light) / 16F * 0.1F + 0.7F, 1);
                color = color & 0xFF000000 | (int) ((color >>> 16 & 255) * shade) << 16
                        | (int) ((color >>> 8 & 255) * shade) << 8 | (int) ((color & 255) * shade);
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(xOffset, offset);
                    graphics.pose().scale(extra, extra);
                    graphics.text(textRenderer, lines.get(i), 0, 0, color, shadow);
                } finally { graphics.pose().popMatrix(); }
                offset += IGui.LINE_HEIGHT * extra;
            }
        } finally { graphics.pose().popMatrix(); }
        if (drawingCallback != null) {
            float x1 = xAlignment.getOffset(x, renderedWidth);
            float y1 = verticalAlignment.getOffset(y, totalHeight / scale);
            drawingCallback.drawingCallback(x1, y1, x1 + renderedWidth, y1 + totalHeight / scale);
        }
    }

	default void drawLine(GuiGraphicsExtractor matrices, RenderBufferSource vertexConsumers, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b) {
        drawLine(guiPose(matrices), vertexConsumers, x1, y1, z1, x2, y2, z2, r, g, b);
	}

	default void drawTexture(GuiGraphicsExtractor gg, VertexConsumer vertexConsumer, float x1, float y1, float z1, float x2, float y2, float z2, Direction facing, int color, int light) {
		drawTexture(guiPose(gg), vertexConsumer, x1, y1, z1, x2, y2, z2, 0, 0, 1, 1, facing, color, light);
	}

	default void drawTexture(GuiGraphicsExtractor gg, VertexConsumer vertexConsumer, float x, float y, float width, float height, Direction facing, int light) {
		drawTexture(guiPose(gg), vertexConsumer, x, y, 0, x + width, y + height, 0, 0, 0, 1, 1, facing, -1, light);
	}

	default void drawTexture(GuiGraphicsExtractor gg, VertexConsumer vertexConsumer, float x, float y, float width, float height, float u1, float v1, float u2, float v2, Direction facing, int color, int light) {
		drawTexture(guiPose(gg), vertexConsumer, x, y, 0, x + width, y + height, 0, u1, v1, u2, v2, facing, color, light);
	}

	default void drawTexture(GuiGraphicsExtractor gg, VertexConsumer vertexConsumer, float x1, float y1, float z1, float x2, float y2, float z2, float u1, float v1, float u2, float v2, Direction facing, int color, int light) {
		drawTexture(guiPose(gg), vertexConsumer, x1, y2, z1, x2, y2, z2, x2, y1, z2, x1, y1, z1, u1, v1, u2, v2, facing, color, light);
	}

	default void drawTexture(GuiGraphicsExtractor gg, VertexConsumer vertexConsumer, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, Direction facing, int color, int light) {
        drawTexture(guiPose(gg), vertexConsumer, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2, facing, color, light);
	}
	default void drawStringWithFont(PoseStack matrices, Font textRenderer, RenderBufferSource immediate, String text, float x, float y, int light) {
		drawStringWithFont(matrices, textRenderer, immediate, text, IGui.HorizontalAlignment.CENTER, IGui.VerticalAlignment.CENTER, x, y, -1, -1, 1, IGui.ARGB_WHITE, true, light, null);
	}

	default void drawStringWithFont(PoseStack matrices, Font textRenderer, RenderBufferSource immediate, String text, IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment, float x, float y, float maxWidth, float maxHeight, float scale, int textColor, boolean shadow, int light, DrawingCallback drawingCallback) {
		drawStringWithFont(matrices, textRenderer, immediate, text, horizontalAlignment, verticalAlignment, horizontalAlignment, x, y, maxWidth, maxHeight, scale, textColor, shadow, light, drawingCallback);
	}

	default void drawStringWithFont(PoseStack matrices, Font textRenderer, RenderBufferSource immediate, String text, IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment, IGui.HorizontalAlignment xAlignment, float x, float y, float maxWidth, float maxHeight, float scale, int textColor, boolean shadow, int light, DrawingCallback drawingCallback) {
		drawStringWithFont(matrices, textRenderer, immediate, text, horizontalAlignment, verticalAlignment, xAlignment, x, y, maxWidth, maxHeight, scale, textColor, textColor, 2, shadow, light, drawingCallback);
	}

	default void drawStringWithFont(PoseStack matrices, Font textRenderer, RenderBufferSource immediate, String text, IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment, IGui.HorizontalAlignment xAlignment, float x, float y, float maxWidth, float maxHeight, float scale, int textColorCjk, int textColor, float fontSizeRatio, boolean shadow, int light, DrawingCallback drawingCallback) {
		final Style style = Style.EMPTY;

		while (text.contains("||")) {
			text = text.replace("||", "|");
		}
		final String[] stringSplit = text.split("\\|");

		final List<Boolean> isCJKList = new ArrayList<>();
		final List<FormattedCharSequence> orderedTexts = new ArrayList<>();
		int totalHeight = 0, totalWidth = 0;
		for (final String stringSplitPart : stringSplit) {
			final boolean isCJK = IGui.isCjk(stringSplitPart);
			isCJKList.add(isCJK);

			final FormattedCharSequence orderedText = Text.literal(stringSplitPart).setStyle(style).getVisualOrderText();
			orderedTexts.add(orderedText);

			totalHeight += IGui.LINE_HEIGHT * (isCJK ? fontSizeRatio : 1);
			final int width = (int) Math.ceil(textRenderer.width(orderedText) * (isCJK ? fontSizeRatio : 1));
			if (width > totalWidth) {
				totalWidth = width;
			}
		}

		if (maxHeight >= 0 && totalHeight / scale > maxHeight) {
			scale = totalHeight / maxHeight;
		}

		matrices.pushPose();

		final float totalWidthScaled;
		final float scaleX;
		if (maxWidth >= 0 && totalWidth > maxWidth * scale) {
			totalWidthScaled = maxWidth * scale;
			scaleX = totalWidth / maxWidth;
		} else {
			totalWidthScaled = totalWidth;
			scaleX = scale;
		}
		matrices.scale(1 / scaleX, 1 / scale, 1 / scale);

		float offset = verticalAlignment.getOffset(y * scale, totalHeight);
		for (int i = 0; i < orderedTexts.size(); i++) {
			final boolean isCJK = isCJKList.get(i);
			final float extraScale = isCJK ? fontSizeRatio : 1;
			if (isCJK) {
				matrices.pushPose();
				matrices.scale(extraScale, extraScale, 1);
			}

			final float xOffset = horizontalAlignment.getOffset(xAlignment.getOffset(x * scaleX, totalWidth), textRenderer.width(orderedTexts.get(i)) * extraScale - totalWidth);

			final float shade = light == IGui.MAX_LIGHT_GLOWING ? 1 : Math.min(LightCoordsUtil.block(light) / 16F * 0.1F + 0.7F, 1);
			final int a = ((isCJK ? textColorCjk : textColor) >> 24) & 0xFF;
			final int r = (int) ((((isCJK ? textColorCjk : textColor) >> 16) & 0xFF) * shade);
			final int g = (int) ((((isCJK ? textColorCjk : textColor) >> 8) & 0xFF) * shade);
			final int b = (int) (((isCJK ? textColorCjk : textColor) & 0xFF) * shade);

			if (immediate != null) {
				immediate.drawText(orderedTexts.get(i), xOffset / extraScale, offset / extraScale, (a << 24) | (r << 16) | (g << 8) | b, shadow, matrices.last().pose(), 0, light);
			}

			if (isCJK) {
				matrices.popPose();
			}

			offset += IGui.LINE_HEIGHT * extraScale;
		}

		matrices.popPose();

		if (drawingCallback != null) {
			final float x1 = xAlignment.getOffset(x, totalWidthScaled / scale);
			final float y1 = verticalAlignment.getOffset(y, totalHeight / scale);
			drawingCallback.drawingCallback(x1, y1, x1 + totalWidthScaled / scale, y1 + totalHeight / scale);
		}
	}

	default void drawLine(PoseStack matrices, RenderBufferSource vertexConsumers, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b) {
		final VertexConsumer vertexConsumer = vertexConsumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines());
		final PoseStack.Pose pose = matrices.last();
		vertexConsumer.addVertex(pose.pose(), x1, y1, z1).setColor(r, g, b, 0xFF).setNormal(pose, 0, 1, 0);
		vertexConsumer.addVertex(pose.pose(), x2, y2, z2).setColor(r, g, b, 0xFF).setNormal(pose, 0, 1, 0);
	}

	default void drawRectangle(VertexConsumer vertexConsumer, double x1, double y1, double x2, double y2, int color) {
		final int a = (color >> 24) & 0xFF;
		final int r = (color >> 16) & 0xFF;
		final int g = (color >> 8) & 0xFF;
		final int b = color & 0xFF;
		if (a == 0) {
			return;
		}
		vertexConsumer.addVertex((float) x1, (float) y1, 0).setColor(r, g, b, a);
		vertexConsumer.addVertex((float) x1, (float) y2, 0).setColor(r, g, b, a);
		vertexConsumer.addVertex((float) x2, (float) y2, 0).setColor(r, g, b, a);
		vertexConsumer.addVertex((float) x2, (float) y1, 0).setColor(r, g, b, a);
	}

	default void drawTexture(PoseStack matrices, VertexConsumer vertexConsumer, float x1, float y1, float z1, float x2, float y2, float z2, Direction facing, int color, int light) {
		drawTexture(matrices, vertexConsumer, x1, y1, z1, x2, y2, z2, 0, 0, 1, 1, facing, color, light);
	}

	default void drawTexture(PoseStack matrices, VertexConsumer vertexConsumer, float x, float y, float width, float height, Direction facing, int light) {
		drawTexture(matrices, vertexConsumer, x, y, 0, x + width, y + height, 0, 0, 0, 1, 1, facing, -1, light);
	}

	default void drawTexture(PoseStack matrices, VertexConsumer vertexConsumer, float x, float y, float width, float height, float u1, float v1, float u2, float v2, Direction facing, int color, int light) {
		drawTexture(matrices, vertexConsumer, x, y, 0, x + width, y + height, 0, u1, v1, u2, v2, facing, color, light);
	}

	default void drawTexture(PoseStack matrices, VertexConsumer vertexConsumer, float x1, float y1, float z1, float x2, float y2, float z2, float u1, float v1, float u2, float v2, Direction facing, int color, int light) {
		drawTexture(matrices, vertexConsumer, x1, y2, z1, x2, y2, z2, x2, y1, z2, x1, y1, z1, u1, v1, u2, v2, facing, color, light);
	}

	default void drawTexture(PoseStack matrices, VertexConsumer vertexConsumer, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, Direction facing, int color, int light) {
		final Vec3i vec3i = facing.getUnitVec3i();
		final PoseStack.Pose pose = matrices.last();
		final int a = (color >> 24) & 0xFF;
		final int r = (color >> 16) & 0xFF;
		final int g = (color >> 8) & 0xFF;
		final int b = color & 0xFF;
		if (a == 0) {
			return;
		}
		vertexConsumer.addVertex(pose.pose(), x1, y1, z1).setColor(r, g, b, a).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, vec3i.getX(), vec3i.getY(), vec3i.getZ());
		vertexConsumer.addVertex(pose.pose(), x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, vec3i.getX(), vec3i.getY(), vec3i.getZ());
		vertexConsumer.addVertex(pose.pose(), x3, y3, z3).setColor(r, g, b, a).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, vec3i.getX(), vec3i.getY(), vec3i.getZ());
		vertexConsumer.addVertex(pose.pose(), x4, y4, z4).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, vec3i.getX(), vec3i.getY(), vec3i.getZ());
	}

	default void setPositionAndWidth(AbstractWidget widget, int x, int y, int widgetWidth) {
		UtilitiesClient.setWidgetX(widget, x);
		UtilitiesClient.setWidgetY(widget, y);
		widget.setWidth(Mth.clamp(widgetWidth, 0, 380 - (widget instanceof WidgetBetterTextField ? IGui.TEXT_FIELD_PADDING : 0)));
	}

	@FunctionalInterface
	interface DrawingCallback {
		void drawingCallback(float x1, float y1, float x2, float y2);
	}


    default void drawText(GuiGraphicsExtractor guiGraphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        guiGraphics.text(font, text, x, y, color);
    }

    default void blit(GuiGraphicsExtractor guiGraphics, Identifier texture, int x, int y, int width, int height) {
        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, 0F, 0F, width, height, width, height);
    }

    default void IFill(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, width, height, color);
    }

    default void drawCenteredString(GuiGraphicsExtractor guiGraphics, Font font, String text, int x, int y, int color) {
        guiGraphics.centeredText(font, text, x, y, color);
    }

    default void renderDirtBackground(Screen screen, GuiGraphicsExtractor guiGraphics) {
        screen.extractBackground(guiGraphics, 0, 0, 0);
    }

}
