package com.cellterminal.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import appeng.client.gui.widgets.MEGuiTextField;


/**
 * Modal search bar that opens as an overlay with a larger text field.
 * Syncs with the normal search bar and closes on Esc or clicking outside.
 * Text wraps intelligently: on space if within 20 characters, or on any character when needed.
 */
public class GuiModalSearchBar {

    // TODO: add syntax highlighting

    private static final int LINE_HEIGHT = 12;
    private static final int HORIZONTAL_MARGIN = 20;
    private static final int VERTICAL_MARGIN = 10;
    private static final int PADDING = 6;
    private static final int BACKGROUND_COLOR = 0xF0303030;
    private static final int FIELD_BACKGROUND = 0xFF1A1A1A;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int TEXT_COLOR = 0xE0E0E0;
    private static final int WRAP_THRESHOLD = 20;  // Wrap on space if within this many chars
    private static final int MIN_LINES = 1;
    private static final int MAX_LINES = 6;

    private final FontRenderer fontRenderer;
    private final MEGuiTextField sourceField;
    private final Runnable onTextChanged;

    private boolean visible = false;
    private StringBuilder text = new StringBuilder();
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int cursorBlinkCounter = 0;

    // Calculated dimensions
    private int x, width;
    private int screenHeight;

    // Cached wrapped lines for cursor/selection calculations
    private List<String> cachedLines = new ArrayList<>();
    private int[] lineStartIndices = new int[0];

    public GuiModalSearchBar(FontRenderer fontRenderer, MEGuiTextField sourceField, Runnable onTextChanged) {
        this.fontRenderer = fontRenderer;
        this.sourceField = sourceField;
        this.onTextChanged = onTextChanged;
    }

    /**
     * Open the modal search bar at the bottom of the screen.
     */
    public void open(int screenY) {
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
        int screenWidth = res.getScaledWidth();
        this.screenHeight = res.getScaledHeight();

        this.width = screenWidth - (HORIZONTAL_MARGIN * 2);
        this.x = HORIZONTAL_MARGIN;

        // Sync text from source field
        this.text = new StringBuilder(sourceField.getText());
        this.cursorPosition = text.length();
        this.selectionStart = -1;
        this.visible = true;

        updateCachedLines();

        Keyboard.enableRepeatEvents(true);
    }

    /**
     * Close the modal search bar and sync text back to source.
     */
    public void close() {
        if (!visible) return;

        visible = false;
        sourceField.setText(text.toString());
        onTextChanged.run();
        Keyboard.enableRepeatEvents(false);
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * Draw the modal search bar.
     */
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        cursorBlinkCounter++;
        updateCachedLines();

        int numLines = Math.max(MIN_LINES, Math.min(cachedLines.size(), MAX_LINES));
        int textAreaHeight = numLines * LINE_HEIGHT + 8;
        int height = textAreaHeight + (PADDING * 2);
        int y = screenHeight - height - VERTICAL_MARGIN;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 1000);  // Above everything

        // Reset GL state for proper color rendering
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw background with border
        int bgX = x - PADDING;
        int bgY = y - PADDING;
        int bgWidth = width + (PADDING * 2);
        int bgHeight = height + (PADDING * 2);

        Gui.drawRect(bgX - 1, bgY - 1, bgX + bgWidth + 1, bgY + bgHeight + 1, BORDER_COLOR);
        Gui.drawRect(bgX, bgY, bgX + bgWidth, bgY + bgHeight, BACKGROUND_COLOR);

        // Draw text field background
        Gui.drawRect(x, y, x + width, y + height, 0xFF404040);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, FIELD_BACKGROUND);

        // Draw selection highlight across wrapped lines
        if (selectionStart != -1 && selectionStart != cursorPosition) {
            int selStart = Math.min(selectionStart, cursorPosition);
            int selEnd = Math.max(selectionStart, cursorPosition);
            drawSelectionHighlight(selStart, selEnd, y);
        }

        // Draw wrapped text
        int lineY = y + PADDING;
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (int i = 0; i < numLines && i < cachedLines.size(); i++) {
            String line = cachedLines.get(i);
            fontRenderer.drawStringWithShadow(line, x + 4, lineY, TEXT_COLOR);
            lineY += LINE_HEIGHT;
        }

        // Draw cursor on the correct line
        if ((cursorBlinkCounter / 40) % 2 == 0) {
            GlStateManager.disableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawCursor(y);
        }

        // Restore GL state
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /**
     * Draw the cursor at the correct position within wrapped lines.
     */
    private void drawCursor(int baseY) {
        int[] lineInfo = getCursorLineInfo();
        int cursorLine = lineInfo[0];
        int cursorOffset = lineInfo[1];

        if (cursorLine >= MAX_LINES) return;

        String lineText = cursorLine < cachedLines.size() ? cachedLines.get(cursorLine) : "";
        String textBeforeCursor = lineText.substring(0, Math.min(cursorOffset, lineText.length()));
        int cursorX = x + 4 + fontRenderer.getStringWidth(textBeforeCursor);
        int cursorY = baseY + PADDING + (cursorLine * LINE_HEIGHT);

        Gui.drawRect(cursorX, cursorY, cursorX + 1, cursorY + LINE_HEIGHT - 2, 0xFFFFFFFF);
    }

    /**
     * Get the line number and offset within that line for the cursor position.
     * @return int array: [lineNumber, offsetWithinLine]
     */
    private int[] getCursorLineInfo() {
        if (cachedLines.isEmpty()) return new int[]{0, 0};

        for (int i = 0; i < cachedLines.size(); i++) {
            int lineStart = lineStartIndices[i];
            int lineEnd = (i + 1 < lineStartIndices.length) ? lineStartIndices[i + 1] : text.length();

            // Cursor is on this line if it's within range, or at the end of the last line
            if (cursorPosition >= lineStart && cursorPosition < lineEnd) {
                return new int[]{i, cursorPosition - lineStart};
            }

            // Special case: cursor at very end belongs to last line
            if (i == cachedLines.size() - 1 && cursorPosition == text.length()) {
                return new int[]{i, cachedLines.get(i).length()};
            }
        }

        return new int[]{cachedLines.size() - 1, cachedLines.get(cachedLines.size() - 1).length()};
    }

    /**
     * Draw selection highlight across wrapped lines.
     */
    private void drawSelectionHighlight(int selStart, int selEnd, int baseY) {
        for (int i = 0; i < cachedLines.size() && i < MAX_LINES; i++) {
            int lineStart = lineStartIndices[i];
            int lineEnd = (i + 1 < lineStartIndices.length) ? lineStartIndices[i + 1] : text.length();

            if (selEnd <= lineStart || selStart >= lineEnd) continue;

            int highlightStart = Math.max(selStart, lineStart) - lineStart;
            int highlightEnd = Math.min(selEnd, lineEnd) - lineStart;

            String line = cachedLines.get(i);
            String beforeHighlight = line.substring(0, Math.min(highlightStart, line.length()));
            String highlightText = line.substring(
                Math.min(highlightStart, line.length()),
                Math.min(highlightEnd, line.length())
            );

            int hlX1 = x + 4 + fontRenderer.getStringWidth(beforeHighlight);
            int hlX2 = hlX1 + fontRenderer.getStringWidth(highlightText);
            int hlY = baseY + PADDING + (i * LINE_HEIGHT);

            Gui.drawRect(hlX1, hlY, hlX2, hlY + LINE_HEIGHT - 2, 0xFF3366CC);
        }
    }

    /**
     * Update cached wrapped lines and their start indices.
     */
    private void updateCachedLines() {
        cachedLines = wrapText(text.toString(), width - 8);

        lineStartIndices = new int[cachedLines.size() + 1];
        int charIndex = 0;

        for (int i = 0; i < cachedLines.size(); i++) {
            lineStartIndices[i] = charIndex;
            charIndex += cachedLines.get(i).length();

            // Account for spaces that were consumed during wrapping
            if (i < cachedLines.size() - 1) {
                int nextLineStart = charIndex;

                // Check if there was a space wrap
                if (nextLineStart < text.length() && text.charAt(nextLineStart) == ' ') {
                    charIndex++;
                }
            }
        }

        lineStartIndices[cachedLines.size()] = text.length();
    }

    /**
     * Wrap text intelligently: on space if within threshold, or on any character.
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");

            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        int lineWidth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charWidth = fontRenderer.getCharWidth(c);

            if (lineWidth + charWidth > maxWidth) {
                // Need to wrap
                String lineStr = currentLine.toString();
                int wrapPos = -1;

                // Look for space within threshold
                if (lineStr.length() > 0) {
                    int searchStart = Math.max(0, lineStr.length() - WRAP_THRESHOLD);

                    for (int j = lineStr.length() - 1; j >= searchStart; j--) {
                        if (lineStr.charAt(j) == ' ') {
                            wrapPos = j;
                            break;
                        }
                    }
                }

                if (wrapPos > 0) {
                    // Wrap at space
                    lines.add(lineStr.substring(0, wrapPos));
                    currentLine = new StringBuilder(lineStr.substring(wrapPos + 1));
                    currentLine.append(c);
                    lineWidth = fontRenderer.getStringWidth(currentLine.toString());
                } else {
                    // Wrap at character boundary
                    lines.add(lineStr);
                    currentLine = new StringBuilder();
                    currentLine.append(c);
                    lineWidth = charWidth;
                }
            } else {
                currentLine.append(c);
                lineWidth += charWidth;
            }
        }

        if (currentLine.length() > 0) lines.add(currentLine.toString());

        return lines;
    }

    /**
     * Handle mouse click. Returns true if click was inside the modal.
     */
    public boolean handleMouseClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        int numLines = Math.max(MIN_LINES, Math.min(cachedLines.size(), MAX_LINES));
        int textAreaHeight = numLines * LINE_HEIGHT + 8;
        int height = textAreaHeight + (PADDING * 2);
        int y = screenHeight - height - VERTICAL_MARGIN;

        int bgX = x - PADDING;
        int bgY = y - PADDING;
        int bgWidth = width + (PADDING * 2);
        int bgHeight = height + (PADDING * 2);

        boolean insideModal = mouseX >= bgX && mouseX < bgX + bgWidth
            && mouseY >= bgY && mouseY < bgY + bgHeight;

        if (!insideModal) {
            close();

            return true;  // Consume click to prevent other actions
        }

        // Right-click clears text
        if (mouseButton == 1) {
            text = new StringBuilder();
            cursorPosition = 0;
            selectionStart = -1;
            updateCachedLines();
            syncToSource();

            return true;
        }

        // Left-click positions cursor based on click location
        int clickedLine = (mouseY - y - PADDING) / LINE_HEIGHT;
        clickedLine = Math.max(0, Math.min(clickedLine, cachedLines.size() - 1));

        if (clickedLine < cachedLines.size()) {
            String line = cachedLines.get(clickedLine);
            int lineStartX = x + 4;
            int clickX = mouseX - lineStartX;

            int charOffset = 0;
            for (int i = 0; i <= line.length(); i++) {
                int charWidth = fontRenderer.getStringWidth(line.substring(0, i));

                if (charWidth >= clickX) {
                    charOffset = i;
                    break;
                }

                charOffset = i;
            }

            cursorPosition = lineStartIndices[clickedLine] + charOffset;
            cursorPosition = Math.min(cursorPosition, text.length());
            selectionStart = -1;
        }

        return true;
    }

    /**
     * Handle key typed. Returns true if key was handled.
     */
    public boolean handleKeyTyped(char typedChar, int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();

            return true;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            close();

            return true;
        }

        if (keyCode == Keyboard.KEY_BACK) {
            if (selectionStart != -1 && selectionStart != cursorPosition) {
                // Delete selection
                int start = Math.min(selectionStart, cursorPosition);
                int end = Math.max(selectionStart, cursorPosition);
                text.delete(start, end);
                cursorPosition = start;
                selectionStart = -1;
                syncToSource();
            } else if (cursorPosition > 0) {
                text.deleteCharAt(cursorPosition - 1);
                cursorPosition--;
                syncToSource();
            }

            return true;
        }

        if (keyCode == Keyboard.KEY_DELETE) {
            if (selectionStart != -1 && selectionStart != cursorPosition) {
                // Delete selection
                int start = Math.min(selectionStart, cursorPosition);
                int end = Math.max(selectionStart, cursorPosition);
                text.delete(start, end);
                cursorPosition = start;
                selectionStart = -1;
                syncToSource();
            } else if (cursorPosition < text.length()) {
                text.deleteCharAt(cursorPosition);
                syncToSource();
            }

            return true;
        }

        if (keyCode == Keyboard.KEY_LEFT) {
            if (GuiScreen.isShiftKeyDown()) {
                if (selectionStart == -1) selectionStart = cursorPosition;
            } else {
                selectionStart = -1;
            }
            if (cursorPosition > 0) cursorPosition--;

            return true;
        }

        if (keyCode == Keyboard.KEY_RIGHT) {
            if (GuiScreen.isShiftKeyDown()) {
                if (selectionStart == -1) selectionStart = cursorPosition;
            } else {
                selectionStart = -1;
            }
            if (cursorPosition < text.length()) cursorPosition++;

            return true;
        }

        if (keyCode == Keyboard.KEY_UP) {
            if (GuiScreen.isShiftKeyDown()) {
                if (selectionStart == -1) selectionStart = cursorPosition;
            } else {
                selectionStart = -1;
            }
            moveCursorVertically(-1);

            return true;
        }

        if (keyCode == Keyboard.KEY_DOWN) {
            if (GuiScreen.isShiftKeyDown()) {
                if (selectionStart == -1) selectionStart = cursorPosition;
            } else {
                selectionStart = -1;
            }
            moveCursorVertically(1);

            return true;
        }

        if (keyCode == Keyboard.KEY_HOME) {
            if (GuiScreen.isShiftKeyDown()) {
                if (selectionStart == -1) selectionStart = cursorPosition;
            } else {
                selectionStart = -1;
            }
            cursorPosition = 0;

            return true;
        }

        if (keyCode == Keyboard.KEY_END) {
            if (GuiScreen.isShiftKeyDown()) {
                if (selectionStart == -1) selectionStart = cursorPosition;
            } else {
                selectionStart = -1;
            }
            cursorPosition = text.length();

            return true;
        }

        if (keyCode == Keyboard.KEY_PRIOR) {  // PageUp
            if (GuiScreen.isShiftKeyDown()) {
                if (selectionStart == -1) selectionStart = cursorPosition;
            } else {
                selectionStart = -1;
            }
            cursorPosition = 0;

            return true;
        }

        if (keyCode == Keyboard.KEY_NEXT) {  // PageDown
            if (GuiScreen.isShiftKeyDown()) {
                if (selectionStart == -1) selectionStart = cursorPosition;
            } else {
                selectionStart = -1;
            }
            cursorPosition = text.length();

            return true;
        }

        // Ctrl+A: select all
        if (GuiScreen.isCtrlKeyDown() && keyCode == Keyboard.KEY_A) {
            selectionStart = 0;
            cursorPosition = text.length();

            return true;
        }

        // Ctrl+C: copy
        if (GuiScreen.isCtrlKeyDown() && keyCode == Keyboard.KEY_C) {
            if (selectionStart != -1 && selectionStart != cursorPosition) {
                int start = Math.min(selectionStart, cursorPosition);
                int end = Math.max(selectionStart, cursorPosition);
                GuiScreen.setClipboardString(text.substring(start, end));
            }

            return true;
        }

        // Ctrl+X: cut
        if (GuiScreen.isCtrlKeyDown() && keyCode == Keyboard.KEY_X) {
            if (selectionStart != -1 && selectionStart != cursorPosition) {
                int start = Math.min(selectionStart, cursorPosition);
                int end = Math.max(selectionStart, cursorPosition);
                GuiScreen.setClipboardString(text.substring(start, end));
                text.delete(start, end);
                cursorPosition = start;
                selectionStart = -1;
                syncToSource();
            }

            return true;
        }

        // Ctrl+V: paste
        if (GuiScreen.isCtrlKeyDown() && keyCode == Keyboard.KEY_V) {
            // Delete selection first if any
            if (selectionStart != -1 && selectionStart != cursorPosition) {
                int start = Math.min(selectionStart, cursorPosition);
                int end = Math.max(selectionStart, cursorPosition);
                text.delete(start, end);
                cursorPosition = start;
                selectionStart = -1;
            }

            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null) {
                // Filter out newlines
                clipboard = clipboard.replace("\n", " ").replace("\r", "");
                text.insert(cursorPosition, clipboard);
                cursorPosition += clipboard.length();
                syncToSource();
            }

            return true;
        }

        // Regular character input
        if (typedChar >= 32 && typedChar != 127) {
            // Delete selection first if any
            if (selectionStart != -1 && selectionStart != cursorPosition) {
                int start = Math.min(selectionStart, cursorPosition);
                int end = Math.max(selectionStart, cursorPosition);
                text.delete(start, end);
                cursorPosition = start;
                selectionStart = -1;
            }

            text.insert(cursorPosition, typedChar);
            cursorPosition++;
            syncToSource();

            return true;
        }

        return true;  // Consume all key events while modal is open
    }

    /**
     * Move cursor up or down by the specified number of lines.
     * @param direction -1 for up, 1 for down
     */
    private void moveCursorVertically(int direction) {
        if (cachedLines.isEmpty()) return;

        int[] lineInfo = getCursorLineInfo();
        int currentLine = lineInfo[0];
        int offsetInLine = lineInfo[1];

        int targetLine = currentLine + direction;

        // At first line going up -> go to start
        if (targetLine < 0) {
            cursorPosition = 0;

            return;
        }

        // At last line going down -> go to end
        if (targetLine >= cachedLines.size()) {
            cursorPosition = text.length();

            return;
        }

        // Calculate pixel offset for current position
        String currentLineText = cachedLines.get(currentLine);
        String beforeCursor = currentLineText.substring(0, Math.min(offsetInLine, currentLineText.length()));
        int pixelOffset = fontRenderer.getStringWidth(beforeCursor);

        // Find closest character position on target line
        String targetLineText = cachedLines.get(targetLine);
        int targetOffset = 0;

        for (int i = 0; i <= targetLineText.length(); i++) {
            int charX = fontRenderer.getStringWidth(targetLineText.substring(0, i));

            if (charX >= pixelOffset) {
                // Check if previous char is closer
                if (i > 0) {
                    int prevX = fontRenderer.getStringWidth(targetLineText.substring(0, i - 1));

                    if (pixelOffset - prevX < charX - pixelOffset) {
                        targetOffset = i - 1;
                    } else {
                        targetOffset = i;
                    }
                } else {
                    targetOffset = i;
                }

                break;
            }

            targetOffset = i;
        }

        cursorPosition = lineStartIndices[targetLine] + targetOffset;
        cursorPosition = Math.min(cursorPosition, text.length());
    }

    private void syncToSource() {
        sourceField.setText(text.toString(), true);
        onTextChanged.run();
    }
}
