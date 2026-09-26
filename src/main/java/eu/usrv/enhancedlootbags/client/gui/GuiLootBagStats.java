package eu.usrv.enhancedlootbags.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
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
 * Shows how many lootbags of each kind the player has opened, and everything they got out of them
 */
public class GuiLootBagStats extends GuiScreen {

    private static final int ALL_BAGS_ID = Integer.MIN_VALUE;

    private static final int MAX_WIDTH = 440;
    private static final int MAX_HEIGHT = 280;
    private static final int PADDING = 6;
    private static final int TITLE_HEIGHT = 16;
    private static final int FOOTER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 20;
    private static final int SLOT_SIZE = 18;
    private static final int HEADER_HEIGHT = 34;

    private static final int COLOR_BACKGROUND = 0xF0100010;
    private static final int COLOR_BORDER_TOP = 0x505000FF;
    private static final int COLOR_BORDER_BOTTOM = 0x5028007F;
    private static final int COLOR_PANEL = 0x60000000;
    private static final int COLOR_SLOT = 0x40FFFFFF;
    private static final int COLOR_HOVER = 0x30FFFFFF;
    private static final int COLOR_SELECTED = 0x505000FF;
    private static final int COLOR_SCROLLBAR = 0x80FFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0x808080;
    private static final int COLOR_TEXT_GOLD = 0xFFAA00;

    private enum SortMode {
        AMOUNT,
        TIMES,
        NAME
    }

    // Remembered while the game is running, so the GUI reopens where the player left it
    private static int sSelectedID = ALL_BAGS_ID;
    private static boolean sShowUnopened = true;
    private static SortMode sSortMode = SortMode.AMOUNT;

    private int mGuiLeft;
    private int mGuiTop;
    private int mGuiWidth;
    private int mGuiHeight;
    private int mListWidth;

    private GuiTextField mSearchField;
    private GuiButton mFilterButton;
    private GuiButton mSortButton;

    private LootBagStats mStats;
    private final List<BagEntry> mAllBags = new ArrayList<>();
    private final List<BagEntry> mVisibleBags = new ArrayList<>();
    private BagEntry mSelected;
    private final List<DropEntry> mDrops = new ArrayList<>();
    private int mListScroll = 0;
    private int mGridScroll = 0;

    @Override
    public void initGui() {
        mGuiWidth = Math.min(width - 16, MAX_WIDTH);
        mGuiHeight = Math.min(height - 16, MAX_HEIGHT);
        mGuiLeft = (width - mGuiWidth) / 2;
        mGuiTop = (height - mGuiHeight) / 2;
        mListWidth = Math.max(110, mGuiWidth * 2 / 5);

        Keyboard.enableRepeatEvents(true);
        String tOldSearch = mSearchField == null ? "" : mSearchField.getText();
        mSearchField = new GuiTextField(
                fontRendererObj,
                getListLeft() + 1,
                mGuiTop + PADDING + TITLE_HEIGHT + 1,
                mListWidth - 2,
                12);
        mSearchField.setMaxStringLength(64);
        mSearchField.setText(tOldSearch);

        int tButtonY = mGuiTop + mGuiHeight - FOOTER_HEIGHT + 2;
        buttonList.clear();
        mFilterButton = new GuiButton(0, getListLeft(), tButtonY, mListWidth, 20, "");
        mSortButton = new GuiButton(1, mGuiLeft + mGuiWidth - PADDING - 100, tButtonY, 100, 20, "");
        buttonList.add(mFilterButton);
        buttonList.add(mSortButton);
        updateButtonLabels();

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
                        EnumRarity.epic,
                        new ItemStack(Blocks.chest),
                        null));

        updateVisibleBags();

        mSelected = null;
        for (BagEntry tEntry : mAllBags) if (tEntry.mID == sSelectedID) mSelected = tEntry;
        if (mSelected == null) mSelected = mAllBags.get(0);
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
        mListScroll = clampScroll(mListScroll, mVisibleBags.size(), getVisibleRows());
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
        mGridScroll = clampScroll(mGridScroll, getGridRowCount(), getGridVisibleRows());
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

    private void updateButtonLabels() {
        mFilterButton.displayString = StatHelper
                .get(sShowUnopened ? "gui.stats.filter_all" : "gui.stats.filter_opened");
        mSortButton.displayString = StatHelper.get("gui.stats.sort_" + sSortMode.name().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------------------------

    private int getListLeft() {
        return mGuiLeft + PADDING;
    }

    private int getListTop() {
        return mGuiTop + PADDING + TITLE_HEIGHT + 16;
    }

    private int getContentBottom() {
        return mGuiTop + mGuiHeight - FOOTER_HEIGHT - 2;
    }

    private int getVisibleRows() {
        return Math.max(1, (getContentBottom() - getListTop()) / ROW_HEIGHT);
    }

    private int getDetailLeft() {
        return getListLeft() + mListWidth + PADDING;
    }

    private int getDetailRight() {
        return mGuiLeft + mGuiWidth - PADDING;
    }

    private int getDetailTop() {
        return mGuiTop + PADDING + TITLE_HEIGHT;
    }

    private int getGridLeft() {
        return getDetailLeft() + 2;
    }

    private int getGridTop() {
        return getDetailTop() + HEADER_HEIGHT;
    }

    private int getGridColumns() {
        return Math.max(1, (getDetailRight() - getGridLeft() - 6) / SLOT_SIZE);
    }

    private int getGridVisibleRows() {
        return Math.max(1, (getContentBottom() - getGridTop()) / SLOT_SIZE);
    }

    private int getGridRowCount() {
        return (mDrops.size() + getGridColumns() - 1) / getGridColumns();
    }

    private static int clampScroll(int pScroll, int pTotal, int pVisible) {
        return Math.max(0, Math.min(pScroll, pTotal - pVisible));
    }

    private static boolean isInside(int pX, int pY, int pLeft, int pTop, int pWidth, int pHeight) {
        return pX >= pLeft && pX < pLeft + pWidth && pY >= pTop && pY < pTop + pHeight;
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
        super.mouseClicked(pX, pY, pButton);
        mSearchField.mouseClicked(pX, pY, pButton);

        // Right click into the search field clears it
        if (pButton == 1 && isInside(pX, pY, getListLeft(), mGuiTop + PADDING + TITLE_HEIGHT, mListWidth, 14)) {
            mSearchField.setText("");
            updateVisibleBags();
            return;
        }

        if (pButton != 0) return;
        int tRow = getBagRowAt(pX, pY);
        if (tRow >= 0) {
            mSelected = mVisibleBags.get(tRow);
            sSelectedID = mSelected.mID;
            mGridScroll = 0;
            updateDrops();
            mc.getSoundHandler()
                    .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
        }
    }

    @Override
    protected void actionPerformed(GuiButton pButton) {
        if (pButton == mFilterButton) {
            sShowUnopened = !sShowUnopened;
            updateVisibleBags();
        } else if (pButton == mSortButton) {
            sSortMode = SortMode.values()[(sSortMode.ordinal() + 1) % SortMode.values().length];
            updateDrops();
        }
        updateButtonLabels();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int tWheel = Mouse.getEventDWheel();
        if (tWheel == 0) return;

        int tX = Mouse.getEventX() * width / mc.displayWidth;
        int tY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int tDelta = tWheel > 0 ? -1 : 1;
        if (GuiScreen.isShiftKeyDown()) tDelta *= 5;

        if (tX < getDetailLeft()) {
            mListScroll = clampScroll(mListScroll + tDelta, mVisibleBags.size(), getVisibleRows());
        } else {
            mGridScroll = clampScroll(mGridScroll + tDelta, getGridRowCount(), getGridVisibleRows());
        }
    }

    private int getBagRowAt(int pX, int pY) {
        if (!isInside(pX, pY, getListLeft(), getListTop(), mListWidth, getVisibleRows() * ROW_HEIGHT)) return -1;
        int tRow = (pY - getListTop()) / ROW_HEIGHT + mListScroll;
        return tRow < mVisibleBags.size() ? tRow : -1;
    }

    private DropEntry getDropAt(int pX, int pY) {
        int tColumns = getGridColumns();
        if (!isInside(pX, pY, getGridLeft(), getGridTop(), tColumns * SLOT_SIZE, getGridVisibleRows() * SLOT_SIZE))
            return null;
        int tCol = (pX - getGridLeft()) / SLOT_SIZE;
        int tRow = (pY - getGridTop()) / SLOT_SIZE + mGridScroll;
        int tIndex = tRow * tColumns + tCol;
        return tIndex < mDrops.size() ? mDrops.get(tIndex) : null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int pMouseX, int pMouseY, float pPartialTicks) {
        drawDefaultBackground();
        drawFrame(mGuiLeft, mGuiTop, mGuiLeft + mGuiWidth, mGuiTop + mGuiHeight);

        // Title
        drawString(
                fontRendererObj,
                EnumChatFormatting.BOLD + StatHelper.get("gui.stats.title"),
                mGuiLeft + PADDING,
                mGuiTop + PADDING + 1,
                COLOR_TEXT);
        String tTotal = String.format(StatHelper.get("gui.stats.total_opened"), formatNumber(mStats.getTotalOpened()));
        drawString(
                fontRendererObj,
                tTotal,
                getDetailRight() - fontRendererObj.getStringWidth(tTotal),
                mGuiTop + PADDING + 1,
                COLOR_TEXT_GOLD);

        drawBagList(pMouseX, pMouseY);
        drawDetails();
        DropEntry tHovered = drawDropGrid(pMouseX, pMouseY);

        super.drawScreen(pMouseX, pMouseY, pPartialTicks);

        if (tHovered != null) drawDropTooltip(tHovered, pMouseX, pMouseY);
        else {
            int tRow = getBagRowAt(pMouseX, pMouseY);
            if (tRow >= 0) {
                BagEntry tEntry = mVisibleBags.get(tRow);
                if (fontRendererObj.getStringWidth(tEntry.mName) > getBagNameWidth(tEntry)) {
                    List<String> tTip = new ArrayList<>();
                    tTip.add(tEntry.mRarity.rarityColor + tEntry.mName);
                    drawHoveringText(tTip, pMouseX, pMouseY, fontRendererObj);
                }
            }
        }
    }

    private void drawFrame(int pLeft, int pTop, int pRight, int pBottom) {
        drawRect(pLeft, pTop, pRight, pBottom, COLOR_BACKGROUND);
        drawGradientRect(pLeft, pTop, pLeft + 1, pBottom, COLOR_BORDER_TOP, COLOR_BORDER_BOTTOM);
        drawGradientRect(pRight - 1, pTop, pRight, pBottom, COLOR_BORDER_TOP, COLOR_BORDER_BOTTOM);
        drawRect(pLeft, pTop, pRight, pTop + 1, COLOR_BORDER_TOP);
        drawRect(pLeft, pBottom - 1, pRight, pBottom, COLOR_BORDER_BOTTOM);
    }

    private void drawBagList(int pMouseX, int pMouseY) {
        // Search field with placeholder
        mSearchField.drawTextBox();
        if (mSearchField.getText().isEmpty() && !mSearchField.isFocused()) {
            drawString(
                    fontRendererObj,
                    StatHelper.get("gui.stats.search"),
                    getListLeft() + 5,
                    mGuiTop + PADDING + TITLE_HEIGHT + 3,
                    COLOR_TEXT_DIM);
        }

        int tLeft = getListLeft();
        int tTop = getListTop();
        int tRows = getVisibleRows();
        drawRect(tLeft, tTop, tLeft + mListWidth, tTop + tRows * ROW_HEIGHT, COLOR_PANEL);

        int tHoveredRow = getBagRowAt(pMouseX, pMouseY);
        for (int i = 0; i < tRows && i + mListScroll < mVisibleBags.size(); i++) {
            int tIndex = i + mListScroll;
            BagEntry tEntry = mVisibleBags.get(tIndex);
            int tY = tTop + i * ROW_HEIGHT;

            if (tEntry == mSelected) drawRect(tLeft, tY, tLeft + mListWidth, tY + ROW_HEIGHT, COLOR_SELECTED);
            else if (tIndex == tHoveredRow) drawRect(tLeft, tY, tLeft + mListWidth, tY + ROW_HEIGHT, COLOR_HOVER);

            drawItem(tEntry.mIcon, tLeft + 2, tY + 2, null);

            boolean tOpened = tEntry.mID == ALL_BAGS_ID || tEntry.getOpened() > 0;
            String tName = fontRendererObj.trimStringToWidth(tEntry.mName, getBagNameWidth(tEntry));
            drawString(
                    fontRendererObj,
                    tOpened ? tEntry.mRarity.rarityColor + tName : tName,
                    tLeft + 21,
                    tY + 6,
                    tOpened ? COLOR_TEXT : COLOR_TEXT_DIM);

            String tCount = getBagCountLabel(tEntry);
            drawString(
                    fontRendererObj,
                    tCount,
                    tLeft + mListWidth
                            - 4
                            - (mVisibleBags.size() > tRows ? 3 : 0)
                            - fontRendererObj.getStringWidth(tCount),
                    tY + 6,
                    tOpened ? COLOR_TEXT_GOLD : COLOR_TEXT_DIM);
        }

        drawScrollbar(tLeft + mListWidth - 2, tTop, tRows * ROW_HEIGHT, mListScroll, tRows, mVisibleBags.size());
    }

    private String getBagCountLabel(BagEntry pEntry) {
        long tOpened = pEntry.mID == ALL_BAGS_ID ? mStats.getTotalOpened() : pEntry.getOpened();
        return "x" + formatNumber(tOpened);
    }

    private int getBagNameWidth(BagEntry pEntry) {
        return mListWidth - 21 - 10 - fontRendererObj.getStringWidth(getBagCountLabel(pEntry));
    }

    private void drawDetails() {
        int tLeft = getDetailLeft();
        int tTop = getDetailTop();
        int tRight = getDetailRight();

        drawRect(tLeft, tTop, tRight, tTop + HEADER_HEIGHT - 4, COLOR_PANEL);
        drawItem(mSelected.mIcon, tLeft + 4, tTop + 7, null);

        int tTextLeft = tLeft + 24;
        int tTextWidth = tRight - tTextLeft - 4;
        drawString(
                fontRendererObj,
                mSelected.mRarity.rarityColor + fontRendererObj.trimStringToWidth(mSelected.mName, tTextWidth),
                tTextLeft,
                tTop + 4,
                COLOR_TEXT);

        String tInfo = String.format(StatHelper.get("gui.stats.opened"), formatFull(getSelectedOpened()))
                + EnumChatFormatting.DARK_GRAY
                + "  |  "
                + EnumChatFormatting.RESET
                + String.format(StatHelper.get("gui.stats.items"), formatFull(getSelectedTotalItems()));
        drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(tInfo, tTextWidth),
                tTextLeft,
                tTop + 16,
                COLOR_TEXT_DIM);

        if (mDrops.isEmpty()) {
            String tMessage = StatHelper
                    .get(getSelectedOpened() == 0 ? "gui.stats.never_opened" : "gui.stats.nothing_dropped");
            List<String> tLines = fontRendererObj.listFormattedStringToWidth(tMessage, tRight - tLeft - 8);
            int tY = getGridTop() + (getContentBottom() - getGridTop()) / 2 - tLines.size() * 5;
            for (String tLine : tLines) {
                drawCenteredString(fontRendererObj, tLine, (tLeft + tRight) / 2, tY, COLOR_TEXT_DIM);
                tY += 10;
            }
        }
    }

    private DropEntry drawDropGrid(int pMouseX, int pMouseY) {
        int tColumns = getGridColumns();
        int tRows = getGridVisibleRows();
        int tLeft = getGridLeft();
        int tTop = getGridTop();
        DropEntry tHovered = getDropAt(pMouseX, pMouseY);

        if (mDrops.isEmpty()) return null;

        for (int tRow = 0; tRow < tRows; tRow++) {
            for (int tCol = 0; tCol < tColumns; tCol++) {
                int tIndex = (tRow + mGridScroll) * tColumns + tCol;
                if (tIndex >= mDrops.size()) break;
                DropEntry tDrop = mDrops.get(tIndex);
                int tX = tLeft + tCol * SLOT_SIZE;
                int tY = tTop + tRow * SLOT_SIZE;

                drawRect(tX, tY, tX + SLOT_SIZE - 1, tY + SLOT_SIZE - 1, COLOR_SLOT);
                if (tDrop == tHovered) drawRect(tX, tY, tX + SLOT_SIZE - 1, tY + SLOT_SIZE - 1, COLOR_HOVER);
                drawItem(tDrop.mStack, tX, tY, formatCompact(tDrop.mItemCount));
            }
        }

        drawScrollbar(tLeft + tColumns * SLOT_SIZE + 2, tTop, tRows * SLOT_SIZE, mGridScroll, tRows, getGridRowCount());
        return tHovered;
    }

    private void drawScrollbar(int pX, int pTop, int pHeight, int pScroll, int pVisible, int pTotal) {
        if (pTotal <= pVisible) return;
        int tBarHeight = Math.max(8, pHeight * pVisible / pTotal);
        int tBarTop = pTop + (pHeight - tBarHeight) * pScroll / (pTotal - pVisible);
        drawRect(pX, pTop, pX + 2, pTop + pHeight, COLOR_PANEL);
        drawRect(pX, tBarTop, pX + 2, tBarTop + tBarHeight, COLOR_SCROLLBAR);
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
            itemRender.renderItemOverlayIntoGUI(
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
                        String.format("%.2f", 100.0D * pDrop.mTimesDropped / tTotalTimes)));

        drawHoveringText(tTip, pMouseX, pMouseY, fontRendererObj);
    }

    private static String formatFull(long pNumber) {
        return String.format("%,d", pNumber);
    }

    private static String formatNumber(long pNumber) {
        return pNumber < 100000 ? formatFull(pNumber) : formatCompact(pNumber);
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
