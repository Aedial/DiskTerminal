package com.cellterminal.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

import com.cellterminal.config.CellTerminalClientConfig;


/**
 * Renders block highlight outlines that are visible through walls.
 */
public class BlockHighlightRenderer {

    private static final Map<BlockPos, Long> highlightedBlocks = new HashMap<>();

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

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (highlightedBlocks.isEmpty()) return;

        long now = System.currentTimeMillis();

        // Remove expired standard highlights
        Iterator<Map.Entry<BlockPos, Long>> iter = highlightedBlocks.entrySet().iterator();
        while (iter.hasNext()) {
            if (iter.next().getValue() < now) iter.remove();
        }

        // Keep only highlights within max view distance (local list to keep highlights persistent)
        // TODO: change max view distance to show arrows instead of removing highlights
        List<BlockPos> validHighlights = new ArrayList<>();
        int maxDist = CellTerminalClientConfig.getInstance().getMaxHighlightDistance();
        double maxDistSq = maxDist > 0 ? (double) maxDist * maxDist : -1;

        for (BlockPos pos : highlightedBlocks.keySet()) {
            BlockPos playerPos = Minecraft.getMinecraft().player.getPosition();
            boolean withinMaxDist = maxDistSq < 0 || pos.distanceSq(playerPos) <= maxDistSq;
            if (withinMaxDist) validHighlights.add(pos);
        }

        if (validHighlights.isEmpty()) return;

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

        for (BlockPos pos : validHighlights) {
            float alpha = 0.7f + 0.3f * (float) Math.sin(now / 500.0);
            renderBlockOutline(pos, 0.0f, 1.0f, 0.5f, alpha);
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
