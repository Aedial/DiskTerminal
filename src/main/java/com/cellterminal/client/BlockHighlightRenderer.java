package com.cellterminal.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;


/**
 * Renders block highlight outlines that are visible through walls.
 */
public class BlockHighlightRenderer {

    private static final Map<BlockPos, Long> highlightedBlocks = new HashMap<>();
    private static final Map<BlockPos, HighlightInfo> priorityHighlights = new HashMap<>();

    /**
     * Info for priority highlights with linear fade.
     */
    private static class HighlightInfo {
        final long startTime;
        final long duration;

        HighlightInfo(long duration) {
            this.startTime = System.currentTimeMillis();
            this.duration = duration;
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= duration) return 0f;

            return 1.0f - ((float) elapsed / duration);
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= startTime + duration;
        }
    }

    /**
     * Add a block to be highlighted for the specified duration.
     * @param pos Block position to highlight
     * @param durationMs Duration in milliseconds
     */
    public static void addHighlight(BlockPos pos, long durationMs) {
        highlightedBlocks.put(pos, System.currentTimeMillis() + durationMs);
    }

    /**
     * Remove a highlight for a specific block.
     */
    public static void removeHighlight(BlockPos pos) {
        highlightedBlocks.remove(pos);
    }

    /**
     * Clear all highlights.
     */
    public static void clearHighlights() {
        highlightedBlocks.clear();
    }

    /**
     * Add a priority highlight (green, linear fade) for the specified duration.
     * @param pos Block position to highlight
     * @param durationMs Duration in milliseconds
     */
    public static void addPriorityHighlight(BlockPos pos, long durationMs) {
        priorityHighlights.put(pos, new HighlightInfo(durationMs));
    }

    /**
     * Remove a priority highlight for a specific block.
     */
    public static void removePriorityHighlight(BlockPos pos) {
        priorityHighlights.remove(pos);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        boolean hasStandardHighlights = !highlightedBlocks.isEmpty();
        boolean hasPriorityHighlights = !priorityHighlights.isEmpty();

        if (!hasStandardHighlights && !hasPriorityHighlights) return;

        long now = System.currentTimeMillis();

        // Remove expired standard highlights
        if (hasStandardHighlights) {
            Iterator<Map.Entry<BlockPos, Long>> iter = highlightedBlocks.entrySet().iterator();
            while (iter.hasNext()) {
                if (iter.next().getValue() < now) iter.remove();
            }

            hasStandardHighlights = !highlightedBlocks.isEmpty();
        }

        // Remove expired priority highlights
        if (hasPriorityHighlights) {
            Iterator<Map.Entry<BlockPos, HighlightInfo>> iter = priorityHighlights.entrySet().iterator();
            while (iter.hasNext()) {
                if (iter.next().getValue().isExpired()) iter.remove();
            }

            hasPriorityHighlights = !priorityHighlights.isEmpty();
        }

        if (!hasStandardHighlights && !hasPriorityHighlights) return;

        EntityPlayer player = Minecraft.getMinecraft().player;
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(3.0F);

        for (Map.Entry<BlockPos, Long> entry : highlightedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            long expireTime = entry.getValue();

            // Calculate alpha for pulsing effect and fade-out
            long remaining = expireTime - now;
            float alpha = 0.7f + 0.3f * (float) Math.sin(now / 500.0);

            renderBlockOutline(pos, 0.0f, 1.0f, 0.5f, alpha);
        }

        // Render priority highlights (green, linear fade)
        for (Map.Entry<BlockPos, HighlightInfo> entry : priorityHighlights.entrySet()) {
            BlockPos pos = entry.getKey();
            float alpha = entry.getValue().getAlpha();

            renderBlockOutline(pos, 0.0f, 1.0f, 0.0f, alpha);
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void renderBlockOutline(BlockPos pos, float red, float green, float blue, float alpha) {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.002);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Bottom face
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Top face
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // Vertical edges
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();
    }
}
