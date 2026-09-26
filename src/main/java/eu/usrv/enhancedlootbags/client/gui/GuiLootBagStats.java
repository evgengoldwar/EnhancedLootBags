package eu.usrv.enhancedlootbags.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import eu.usrv.enhancedlootbags.EnhancedLootBags;
import eu.usrv.enhancedlootbags.StatHelper;
import eu.usrv.enhancedlootbags.client.ClientLootBagStats;
import eu.usrv.enhancedlootbags.client.LootBagStatsClientHandler;
import eu.usrv.enhancedlootbags.core.LootGroupsHandler;
import eu.usrv.enhancedlootbags.core.serializer.LootGroups;
import eu.usrv.enhancedlootbags.core.serializer.LootGroups.LootGroup;
import eu.usrv.enhancedlootbags.core.stats.LootBagStats;
import eu.usrv.enhancedlootbags.core.stats.LootBagStats.DropStats;
import eu.usrv.enhancedlootbags.core.stats.LootBagStats.GroupStats;

/**
 * Shows how many lootbags of each kind the player has opened, and everything they got out of them. Minimal flat look in
 * the colors of the GuideME guidebooks: a list of bags on the left, the received items of the selected bag on the right
 */
public class GuiLootBagStats extends GuiScreen {

    private static final int ALL_BAGS_ID = Integer.MIN_VALUE;

    // Layout
    private static final int MAX_SCREEN_WIDTH = 480;
    private static final int HEADER_HEIGHT = 24;
    private static final int NAV_WIDTH = 140;
    private static final int NAV_SEARCH_HEIGHT = 20;
    private static final int NAV_FOOTER_HEIGHT = 16;
    private static final int NAV_ROW_HEIGHT = 18;
    private static final int PADDING = 10;
    private static final int SLOT_SIZE = 18;
    private static final int SCROLL_STEP = 18;

    // Colors
    private static final int COLOR_OUTSIDE = 0x80000000;
    private static final int COLOR_BACKGROUND = 0xE8121212;
    private static final int COLOR_LINE = 0x30FFFFFF;
    private static final int COLOR_HOVER = 0x18FFFFFF;
    private static final int COLOR_ACCENT = 0x00D5FF;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_BODY = 0xD2D2D2;
    private static final int COLOR_DIM = 0x808080;
    private static final int COLOR_FAINT = 0x505050;
    private static final int COLOR_SCROLLBAR = 0x50FFFFFF;

    private enum SortMode {
        AMOUNT,
        TIMES,
        NAME
    }

    // Remembered while the game is running, so the GUI reopens where the player left it
    private static int sSelectedID = ALL_BAGS_ID;
    private static boolean sShowUnopened = true;
    private static SortMode sSortMode = SortMode.AMOUNT;

    private int mLeft;
    private int mRight;

    private GuiTextField mSearchField;

    private LootBagStats mStats;
    private final List<BagEntry> mAllBags = new ArrayList<>();
    private final List<BagEntry> mVisibleBags = new ArrayList<>();
    private BagEntry mSelected;
    private final List<DropEntry> mDrops = new ArrayList<>();

    private int mNavScroll = 0;
    private int mGridScroll = 0;
    private boolean mDraggingNav = false;
    private boolean mDraggingGrid = false;

    @Override
    public void initGui() {
        int tWidth = Math.min(width, MAX_SCREEN_WIDTH);
        mLeft = (width - tWidth) / 2;
        mRight = mLeft + tWidth;

        Keyboard.enableRepeatEvents(true);
        String tOldSearch = mSearchField == null ? "" : mSearchField.getText();
        mSearchField = new GuiTextField(
                fontRendererObj,
                mLeft + PADDING,
                HEADER_HEIGHT + 6,
                NAV_WIDTH - 2 * PADDING,
                10);
        mSearchField.setEnableBackgroundDrawing(false);
        mSearchField.setMaxStringLength(64);
        mSearchField.setTextColor(COLOR_BODY);
        mSearchField.setText(tOldSearch);

        reloadStats();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        mSearchField.updateCursorCounter();
        // Pick up new statistics if a bag has been opened by other means while the GUI is shown
        if (ClientLootBagStats.getStats() != mStats) reloadStats();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------------------------------------------------

    private void reloadStats() {
        mStats = ClientLootBagStats.getStats();
        mAllBags.clear();

        Map<Integer, BagEntry> tByID = new HashMap<>();
        LootGroups tGroups = EnhancedLootBags.LootGroupHandler.getLootGroupsClient();
        if (tGroups != null) {
            for (LootGroup tGrp : tGroups.getLootTable()) {
                BagEntry tEntry = new BagEntry(
                        tGrp.getGroupID(),
                        StatCollector.translateToLocal(tGrp.getGroupName()),
                        tGrp.getGroupRarity(),
                        createBagStack(tGrp.getGroupID()),
                        mStats.getGroup(tGrp.getGroupID()));
                tByID.put(tEntry.mID, tEntry);
                mAllBags.add(tEntry);
            }
        }
        // Bags that have been opened but are no longer configured
        for (GroupStats tGrpStats : mStats.getGroups()) {
            if (tByID.containsKey(tGrpStats.getGroupID())) continue;
            BagEntry tEntry = new BagEntry(
                    tGrpStats.getGroupID(),
                    String.format(StatHelper.get("gui.stats.unknown_bag"), tGrpStats.getGroupID()),
                    EnumRarity.common,
                    createBagStack(tGrpStats.getGroupID()),
                    tGrpStats);
            tByID.put(tEntry.mID, tEntry);
            mAllBags.add(tEntry);
        }

        mAllBags.sort(
                Comparator.comparingInt((BagEntry e) -> -e.getOpened())
                        .thenComparing(e -> e.mName, String.CASE_INSENSITIVE_ORDER));
        mAllBags.add(
                0,
                new BagEntry(
                        ALL_BAGS_ID,
                        StatHelper.get("gui.stats.all_bags"),
                        EnumRarity.common,
                        new ItemStack(Blocks.chest),
                        null));

        updateVisibleBags();

        mSelected = mAllBags.get(0);
        for (BagEntry tEntry : mAllBags) if (tEntry.mID == sSelectedID) mSelected = tEntry;
        updateDrops();
    }

    private static ItemStack createBagStack(int pGroupID) {
        Item tBag = LootGroupsHandler.getLootBagItem();
        return tBag == null ? new ItemStack(Blocks.chest) : new ItemStack(tBag, 1, pGroupID);
    }

    private void updateVisibleBags() {
        mVisibleBags.clear();
        String tFilter = mSearchField.getText().trim().toLowerCase(Locale.ROOT);
        for (BagEntry tEntry : mAllBags) {
            if (tEntry.mID != ALL_BAGS_ID) {
                if (!sShowUnopened && tEntry.getOpened() == 0) continue;
                if (!tFilter.isEmpty() && !tEntry.mName.toLowerCase(Locale.ROOT).contains(tFilter)) continue;
            }
            mVisibleBags.add(tEntry);
        }
        mNavScroll = clamp(mNavScroll, 0, getNavMaxScroll());
    }

    private void updateDrops() {
        mDrops.clear();
        if (mSelected.mID == ALL_BAGS_ID) {
            // Merge identical items from all bags
            Map<String, DropEntry> tMerged = new HashMap<>();
            for (GroupStats tGrp : mStats.getGroups()) {
                for (DropStats tDrop : tGrp.getDrops()) {
                    String tKey = getStackKey(tDrop.getDisplayStack());
                    DropEntry tEntry = tMerged.get(tKey);
                    if (tEntry == null) {
                        tEntry = new DropEntry(tDrop.getDisplayStack());
                        tMerged.put(tKey, tEntry);
                        mDrops.add(tEntry);
                    }
                    tEntry.mItemCount += tDrop.getItemCount();
                    tEntry.mTimesDropped += tDrop.getTimesDropped();
                }
            }
        } else if (mSelected.mStats != null) {
            for (DropStats tDrop : mSelected.mStats.getDrops()) {
                DropEntry tEntry = new DropEntry(tDrop.getDisplayStack());
                tEntry.mItemCount = tDrop.getItemCount();
                tEntry.mTimesDropped = tDrop.getTimesDropped();
                mDrops.add(tEntry);
            }
        }

        switch (sSortMode) {
            case AMOUNT:
                mDrops.sort(Comparator.comparingLong((DropEntry e) -> -e.mItemCount).thenComparing(e -> e.mName));
                break;
            case TIMES:
                mDrops.sort(Comparator.comparingInt((DropEntry e) -> -e.mTimesDropped).thenComparing(e -> e.mName));
                break;
            case NAME:
                mDrops.sort(Comparator.comparing((DropEntry e) -> e.mName, String.CASE_INSENSITIVE_ORDER));
                break;
        }
        mGridScroll = clamp(mGridScroll, 0, getGridMaxScroll());
    }

    private static String getStackKey(ItemStack pStack) {
        return String.format(
                "%s:%d:%s",
                Item.itemRegistry.getNameForObject(pStack.getItem()),
                pStack.getItemDamage(),
                pStack.stackTagCompound == null ? "" : pStack.stackTagCompound.toString());
    }

    private long getSelectedOpened() {
        return mSelected.mID == ALL_BAGS_ID ? mStats.getTotalOpened() : mSelected.getOpened();
    }

    private long getSelectedTotalItems() {
        long tTotal = 0;
        for (DropEntry tDrop : mDrops) tTotal += tDrop.mItemCount;
        return tTotal;
    }

    private long getSelectedTotalTimes() {
        long tTotal = 0;
        for (DropEntry tDrop : mDrops) tTotal += tDrop.mTimesDropped;
        return tTotal;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------------------------

    private int getNavTop() {
        return HEADER_HEIGHT + NAV_SEARCH_HEIGHT;
    }

    private int getNavBottom() {
        return height - NAV_FOOTER_HEIGHT;
    }

    private int getNavMaxScroll() {
        return Math.max(0, mVisibleBags.size() * NAV_ROW_HEIGHT - (getNavBottom() - getNavTop()));
    }

    private int getContentLeft() {
        return mLeft + NAV_WIDTH + PADDING;
    }

    private int getContentRight() {
        return mRight - PADDING;
    }

    private int getGridTop() {
        return HEADER_HEIGHT + PADDING + 30;
    }

    private int getGridBottom() {
        return height - PADDING;
    }

    private int getGridColumns() {
        return Math.max(1, (getContentRight() - getContentLeft()) / SLOT_SIZE);
    }

    private int getGridContentHeight() {
        return (mDrops.size() + getGridColumns() - 1) / getGridColumns() * SLOT_SIZE;
    }

    private int getGridMaxScroll() {
        return Math.max(0, getGridContentHeight() - (getGridBottom() - getGridTop()));
    }

    private static int clamp(int pValue, int pMin, int pMax) {
        return Math.max(pMin, Math.min(pValue, pMax));
    }

    private static boolean isInside(int pX, int pY, int pLeft, int pTop, int pRight, int pBottom) {
        return pX >= pLeft && pX < pRight && pY >= pTop && pY < pBottom;
    }

    private BagEntry getBagAt(int pX, int pY) {
        if (!isInside(pX, pY, mLeft, getNavTop(), mLeft + NAV_WIDTH, getNavBottom())) return null;
        int tIndex = (pY - getNavTop() + mNavScroll) / NAV_ROW_HEIGHT;
        return tIndex < mVisibleBags.size() ? mVisibleBags.get(tIndex) : null;
    }

    private DropEntry getDropAt(int pX, int pY) {
        int tColumns = getGridColumns();
        if (!isInside(pX, pY, getContentLeft(), getGridTop(), getContentLeft() + tColumns * SLOT_SIZE, getGridBottom()))
            return null;
        int tIndex = (pY - getGridTop() + mGridScroll) / SLOT_SIZE * tColumns + (pX - getContentLeft()) / SLOT_SIZE;
        return tIndex < mDrops.size() ? mDrops.get(tIndex) : null;
    }

    private String getSortLabel() {
        return StatHelper.get("gui.stats.sort_" + sSortMode.name().toLowerCase(Locale.ROOT));
    }

    private String getFilterLabel() {
        return StatHelper.get(sShowUnopened ? "gui.stats.filter_all" : "gui.stats.filter_opened");
    }

    private boolean isOverSortLink(int pX, int pY) {
        if (mDrops.isEmpty()) return false;
        int tWidth = fontRendererObj.getStringWidth(getSortLabel());
        int tY = HEADER_HEIGHT + PADDING + 12;
        return isInside(pX, pY, getContentRight() - tWidth - 1, tY - 1, getContentRight() + 1, tY + 9);
    }

    private boolean isOverFilterLink(int pX, int pY) {
        int tY = height - NAV_FOOTER_HEIGHT + 4;
        int tWidth = fontRendererObj.getStringWidth(getFilterLabel());
        return isInside(pX, pY, mLeft + PADDING - 1, tY - 1, mLeft + PADDING + tWidth + 1, tY + 9);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected void keyTyped(char pChar, int pKey) {
        if (mSearchField.textboxKeyTyped(pChar, pKey)) {
            updateVisibleBags();
            return;
        }
        // Key code 0 is sent for characters without a key of their own (e.g. non-latin keyboard layouts)
        if (pKey == Keyboard.KEY_ESCAPE
                || (pKey != Keyboard.KEY_NONE && (pKey == mc.gameSettings.keyBindInventory.getKeyCode()
                        || pKey == LootBagStatsClientHandler.KEY_OPEN_STATS.getKeyCode()))) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void mouseClicked(int pX, int pY, int pButton) {
        mSearchField.mouseClicked(pX, pY, pButton);

        // Right click into the search field clears it
        if (pButton == 1 && isInside(pX, pY, mLeft, HEADER_HEIGHT, mLeft + NAV_WIDTH, getNavTop())) {
            mSearchField.setText("");
            updateVisibleBags();
            return;
        }
        if (pButton != 0) return;

        if (isOverSortLink(pX, pY)) {
            sSortMode = SortMode.values()[(sSortMode.ordinal() + 1) % SortMode.values().length];
            updateDrops();
            return;
        }
        if (isOverFilterLink(pX, pY)) {
            sShowUnopened = !sShowUnopened;
            updateVisibleBags();
            return;
        }
        // The thin scrollbars can be dragged; Their grab area is a bit wider than the bar itself
        if (getNavMaxScroll() > 0
                && isInside(pX, pY, mLeft + NAV_WIDTH - 5, getNavTop(), mLeft + NAV_WIDTH, getNavBottom())) {
            mDraggingNav = true;
            mouseClickMove(pX, pY, pButton, 0);
            return;
        }
        if (getGridMaxScroll() > 0 && isInside(pX, pY, mRight - 5, getGridTop(), mRight, getGridBottom())) {
            mDraggingGrid = true;
            mouseClickMove(pX, pY, pButton, 0);
            return;
        }

        BagEntry tBag = getBagAt(pX, pY);
        if (tBag != null && tBag != mSelected) {
            mSelected = tBag;
            sSelectedID = tBag.mID;
            mGridScroll = 0;
            updateDrops();
        }
    }

    @Override
    protected void mouseClickMove(int pX, int pY, int pButton, long pTime) {
        if (mDraggingNav) {
            float tPos = (pY - getNavTop()) / (float) (getNavBottom() - getNavTop());
            mNavScroll = clamp(Math.round(tPos * getNavMaxScroll()), 0, getNavMaxScroll());
        } else if (mDraggingGrid) {
            float tPos = (pY - getGridTop()) / (float) (getGridBottom() - getGridTop());
            mGridScroll = clamp(Math.round(tPos * getGridMaxScroll()), 0, getGridMaxScroll());
        }
    }

    @Override
    protected void mouseMovedOrUp(int pX, int pY, int pButton) {
        super.mouseMovedOrUp(pX, pY, pButton);
        if (pButton >= 0) {
            mDraggingNav = false;
            mDraggingGrid = false;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int tWheel = Mouse.getEventDWheel();
        if (tWheel == 0) return;

        int tX = Mouse.getEventX() * width / mc.displayWidth;
        int tDelta = (tWheel > 0 ? -1 : 1) * SCROLL_STEP * (GuiScreen.isShiftKeyDown() ? 5 : 1);
        if (tX < mLeft + NAV_WIDTH) mNavScroll = clamp(mNavScroll + tDelta, 0, getNavMaxScroll());
        else mGridScroll = clamp(mGridScroll + tDelta, 0, getGridMaxScroll());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int pMouseX, int pMouseY, float pPartialTicks) {
        drawRect(0, 0, width, height, COLOR_OUTSIDE);
        drawRect(mLeft, 0, mRight, height, COLOR_BACKGROUND);

        // Header
        fontRendererObj.drawString(StatHelper.get("gui.stats.title"), mLeft + PADDING, 8, COLOR_TEXT);
        String tTotal = String.format(StatHelper.get("gui.stats.total_opened"), formatFull(mStats.getTotalOpened()));
        fontRendererObj.drawString(tTotal, mRight - PADDING - fontRendererObj.getStringWidth(tTotal), 8, COLOR_DIM);
        drawRect(mLeft, HEADER_HEIGHT - 1, mRight, HEADER_HEIGHT, COLOR_LINE);
        drawRect(mLeft + NAV_WIDTH - 1, HEADER_HEIGHT, mLeft + NAV_WIDTH, height, COLOR_LINE);

        drawNavigation(pMouseX, pMouseY);
        DropEntry tHovered = drawContent(pMouseX, pMouseY);

        if (tHovered != null) drawDropTooltip(tHovered, pMouseX, pMouseY);
        else {
            BagEntry tBag = getBagAt(pMouseX, pMouseY);
            if (tBag != null && fontRendererObj.getStringWidth(tBag.mName) > getNavNameWidth(tBag)) {
                List<String> tTip = new ArrayList<>();
                tTip.add(tBag.mRarity.rarityColor + tBag.mName);
                drawHoveringText(tTip, pMouseX, pMouseY, fontRendererObj);
            }
        }
    }

    private int getNavNameWidth(BagEntry pEntry) {
        return NAV_WIDTH - PADDING - 22 - 6 - fontRendererObj.getStringWidth(getNavCount(pEntry));
    }

    private String getNavCount(BagEntry pEntry) {
        return formatCompact(pEntry.mID == ALL_BAGS_ID ? mStats.getTotalOpened() : pEntry.getOpened());
    }

    private void drawNavigation(int pMouseX, int pMouseY) {
        // Search field: just the text and a thin underline
        mSearchField.drawTextBox();
        if (mSearchField.getText().isEmpty() && !mSearchField.isFocused()) {
            fontRendererObj.drawString(
                    StatHelper.get("gui.stats.search"),
                    mSearchField.xPosition,
                    mSearchField.yPosition,
                    COLOR_FAINT);
        }
        int tLineY = HEADER_HEIGHT + 17;
        drawRect(
                mLeft + PADDING,
                tLineY,
                mLeft + NAV_WIDTH - PADDING,
                tLineY + 1,
                mSearchField.isFocused() ? 0xFF000000 | COLOR_ACCENT : COLOR_LINE);

        int tTop = getNavTop();
        int tBottom = getNavBottom();
        BagEntry tHovered = getBagAt(pMouseX, pMouseY);

        beginScissor(mLeft, tTop, NAV_WIDTH - 1, tBottom - tTop);
        for (int i = 0; i < mVisibleBags.size(); i++) {
            int tY = tTop + i * NAV_ROW_HEIGHT - mNavScroll;
            if (tY + NAV_ROW_HEIGHT <= tTop || tY >= tBottom) continue;

            BagEntry tEntry = mVisibleBags.get(i);
            boolean tSelected = tEntry == mSelected;
            boolean tOpened = tEntry.mID == ALL_BAGS_ID || tEntry.getOpened() > 0;

            if (tSelected) drawRect(mLeft, tY, mLeft + 2, tY + NAV_ROW_HEIGHT, 0xFF000000 | COLOR_ACCENT);
            if (tEntry == tHovered) drawRect(mLeft, tY, mLeft + NAV_WIDTH - 1, tY + NAV_ROW_HEIGHT, COLOR_HOVER);

            drawItem(tEntry.mIcon, mLeft + PADDING - 2, tY + 1, null);

            int tNameColor = tSelected ? COLOR_TEXT
                    : tEntry == tHovered ? COLOR_ACCENT : tOpened ? COLOR_BODY : COLOR_DIM;
            fontRendererObj.drawString(
                    fontRendererObj.trimStringToWidth(tEntry.mName, getNavNameWidth(tEntry)),
                    mLeft + PADDING + 18,
                    tY + 5,
                    tNameColor);
            String tCount = getNavCount(tEntry);
            fontRendererObj.drawString(
                    tCount,
                    mLeft + NAV_WIDTH - 6 - fontRendererObj.getStringWidth(tCount),
                    tY + 5,
                    tOpened ? COLOR_DIM : COLOR_FAINT);
        }
        endScissor();

        drawScrollbar(mLeft + NAV_WIDTH - 3, tTop, tBottom, mNavScroll, mVisibleBags.size() * NAV_ROW_HEIGHT);

        String tFilter = getFilterLabel();
        fontRendererObj.drawString(
                tFilter,
                mLeft + PADDING,
                height - NAV_FOOTER_HEIGHT + 4,
                isOverFilterLink(pMouseX, pMouseY) ? COLOR_ACCENT : COLOR_DIM);
    }

    private DropEntry drawContent(int pMouseX, int pMouseY) {
        int tLeft = getContentLeft();
        int tRight = getContentRight();
        int tY = HEADER_HEIGHT + PADDING;

        // Bag name and a single line of numbers
        fontRendererObj.drawString(
                mSelected.mRarity.rarityColor + fontRendererObj.trimStringToWidth(mSelected.mName, tRight - tLeft),
                tLeft,
                tY,
                COLOR_TEXT);
        String tSummary = String.format(
                StatHelper.get("gui.stats.summary"),
                formatFull(getSelectedOpened()),
                formatFull(getSelectedTotalItems()),
                formatFull(mDrops.size()));
        String tSort = getSortLabel();
        int tSortWidth = fontRendererObj.getStringWidth(tSort);
        fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(tSummary, tRight - tLeft - tSortWidth - 8),
                tLeft,
                tY + 12,
                COLOR_DIM);
        if (!mDrops.isEmpty()) fontRendererObj.drawString(
                tSort,
                tRight - tSortWidth,
                tY + 12,
                isOverSortLink(pMouseX, pMouseY) ? COLOR_ACCENT : COLOR_DIM);

        int tGridTop = getGridTop();
        int tGridBottom = getGridBottom();

        if (mDrops.isEmpty()) {
            String tMessage = StatHelper
                    .get(getSelectedOpened() == 0 ? "gui.stats.never_opened" : "gui.stats.nothing_dropped");
            @SuppressWarnings("unchecked")
            List<String> tLines = fontRendererObj.listFormattedStringToWidth(tMessage, tRight - tLeft);
            int tTextY = tGridTop + (tGridBottom - tGridTop) / 2 - tLines.size() * 5;
            for (String tLine : tLines) {
                fontRendererObj.drawString(
                        tLine,
                        (tLeft + tRight - fontRendererObj.getStringWidth(tLine)) / 2,
                        tTextY,
                        COLOR_FAINT);
                tTextY += 10;
            }
            return null;
        }

        // Item grid, no slot frames
        int tColumns = getGridColumns();
        DropEntry tHovered = getDropAt(pMouseX, pMouseY);
        beginScissor(tLeft, tGridTop, mRight - tLeft, tGridBottom - tGridTop);
        for (int i = 0; i < mDrops.size(); i++) {
            int tX = tLeft + (i % tColumns) * SLOT_SIZE;
            int tSlotY = tGridTop + (i / tColumns) * SLOT_SIZE - mGridScroll;
            if (tSlotY + SLOT_SIZE <= tGridTop || tSlotY >= tGridBottom) continue;

            DropEntry tDrop = mDrops.get(i);
            if (tDrop == tHovered) drawRect(tX, tSlotY, tX + SLOT_SIZE, tSlotY + SLOT_SIZE, COLOR_HOVER);
            drawItem(tDrop.mStack, tX, tSlotY, formatCompact(tDrop.mItemCount));
        }
        endScissor();

        drawScrollbar(mRight - 3, tGridTop, tGridBottom, mGridScroll, getGridContentHeight());
        return tHovered;
    }

    private void drawScrollbar(int pX, int pTop, int pBottom, int pScroll, int pContentHeight) {
        int tViewHeight = pBottom - pTop;
        int tMaxScroll = pContentHeight - tViewHeight;
        if (tMaxScroll <= 0) return;
        int tThumbHeight = Math.max(10, tViewHeight * tViewHeight / pContentHeight);
        int tThumbTop = pTop + (tViewHeight - tThumbHeight) * pScroll / tMaxScroll;
        drawRect(pX, tThumbTop, pX + 2, tThumbTop + tThumbHeight, COLOR_SCROLLBAR);
    }

    private void drawItem(ItemStack pStack, int pX, int pY, String pOverlay) {
        GL11.glPushMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        itemRender.zLevel = 100.0F;
        try {
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), pStack, pX + 1, pY + 1);
            if (pOverlay != null) itemRender.renderItemOverlayIntoGUI(
                    fontRendererObj,
                    mc.getTextureManager(),
                    pStack,
                    pX + 1,
                    pY + 1,
                    pOverlay);
        } catch (Exception e) {
            // Some modded items do not like being rendered outside of an inventory; Don't break the whole GUI
        }
        itemRender.zLevel = 0.0F;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @SuppressWarnings("unchecked")
    private void drawDropTooltip(DropEntry pDrop, int pMouseX, int pMouseY) {
        List<String> tTip = new ArrayList<>();
        List<String> tItemTip;
        try {
            tItemTip = pDrop.mStack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        } catch (Exception e) {
            tItemTip = new ArrayList<>();
            tItemTip.add(pDrop.mName);
        }
        for (int i = 0; i < tItemTip.size(); i++) {
            if (i == 0) tTip.add(pDrop.mStack.getRarity().rarityColor + tItemTip.get(i));
            else tTip.add(EnumChatFormatting.GRAY + tItemTip.get(i));
        }

        long tOpened = getSelectedOpened();
        long tTotalTimes = getSelectedTotalTimes();
        tTip.add("");
        tTip.add(String.format(StatHelper.get("gui.stats.tip_received"), formatFull(pDrop.mItemCount)));
        tTip.add(String.format(StatHelper.get("gui.stats.tip_times"), formatFull(pDrop.mTimesDropped)));
        if (tOpened > 0) tTip.add(
                String.format(
                        StatHelper.get("gui.stats.tip_per_bag"),
                        String.format("%.2f", (double) pDrop.mItemCount / tOpened)));
        if (tTotalTimes > 0) tTip.add(
                String.format(
                        StatHelper.get("gui.stats.tip_share"),
                        String.format("%.1f%%", 100.0D * pDrop.mTimesDropped / tTotalTimes)));

        drawHoveringText(tTip, pMouseX, pMouseY, fontRendererObj);
    }

    private void beginScissor(int pX, int pY, int pWidth, int pHeight) {
        int tScale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                pX * tScale,
                mc.displayHeight - (pY + pHeight) * tScale,
                Math.max(0, pWidth * tScale),
                Math.max(0, pHeight * tScale));
    }

    private static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static String formatFull(long pNumber) {
        return String.format("%,d", pNumber);
    }

    /**
     * Short number representation that fits into an item slot
     */
    private static String formatCompact(long pNumber) {
        if (pNumber < 1000) return Long.toString(pNumber);
        if (pNumber < 10000) return String.format(Locale.ROOT, "%.1fk", pNumber / 1000.0D);
        if (pNumber < 1000000) return (pNumber / 1000) + "k";
        if (pNumber < 10000000) return String.format(Locale.ROOT, "%.1fM", pNumber / 1000000.0D);
        if (pNumber < 1000000000) return (pNumber / 1000000) + "M";
        return (pNumber / 1000000000) + "G";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Entries
    // ---------------------------------------------------------------------------------------------------------------

    private static class BagEntry {

        private final int mID;
        private final String mName;
        private final EnumRarity mRarity;
        private final ItemStack mIcon;
        private final GroupStats mStats;

        private BagEntry(int pID, String pName, EnumRarity pRarity, ItemStack pIcon, GroupStats pStats) {
            mID = pID;
            mName = pName;
            mRarity = pRarity;
            mIcon = pIcon;
            mStats = pStats;
        }

        private int getOpened() {
            return mStats == null ? 0 : mStats.getOpened();
        }
    }

    private static class DropEntry {

        private final ItemStack mStack;
        private final String mName;
        private long mItemCount;
        private int mTimesDropped;

        private DropEntry(ItemStack pStack) {
            mStack = pStack;
            String tName;
            try {
                tName = pStack.getDisplayName();
            } catch (Exception e) {
                tName = String.valueOf(Item.itemRegistry.getNameForObject(pStack.getItem()));
            }
            mName = tName;
        }
    }
}
