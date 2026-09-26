package eu.usrv.enhancedlootbags.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
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
 * Shows how many lootbags of each kind the player has opened, and everything they got out of them. The look follows the
 * GuideME guidebooks: a full height page with a navigation bar on the left, and the statistics of the selected bag as a
 * scrollable document on the right
 */
public class GuiLootBagStats extends GuiScreen {

    private static final ResourceLocation TEX_BACKGROUND = new ResourceLocation(
            EnhancedLootBags.MODID,
            "textures/gui/stats_background.png");
    private static final ResourceLocation TEX_ICONS = new ResourceLocation(
            EnhancedLootBags.MODID,
            "textures/gui/stats_icons.png");

    private static final int ALL_BAGS_ID = Integer.MIN_VALUE;
    private static final int TOP_DROPS_COUNT = 10;

    // Layout
    private static final int MAX_SCREEN_WIDTH = 560;
    private static final int TITLE_BAR_HEIGHT = 26;
    private static final int NAVBAR_WIDTH = 150;
    private static final int NAV_SEARCH_HEIGHT = 22;
    private static final int NAV_FOOTER_HEIGHT = 16;
    private static final int NAV_ROW_HEIGHT = 18;
    private static final int DOC_PADDING = 10;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int ICON_BUTTON_SIZE = 16;
    private static final int SLOT_SIZE = 18;
    private static final int TABLE_ROW_HEIGHT = 18;
    private static final int SCROLL_STEP = 20;

    // Colors (GuideME dark theme)
    private static final int COLOR_SCREEN_TINT = 0xE5FFFFFF;
    private static final int COLOR_DOCUMENT = 0x40333333;
    private static final int COLOR_NAVBAR_TOP = 0xFF000000;
    private static final int COLOR_NAVBAR_BOTTOM = 0x7F000000;
    private static final int COLOR_NAV_HOVER = 0xFF212121;
    private static final int COLOR_NAV_SELECTED = 0x4000D5FF;
    private static final int COLOR_BODY_TEXT = 0xD2D2D2;
    private static final int COLOR_DIM_TEXT = 0x7C7C7C;
    private static final int COLOR_HEADING = 0xFFFFFF;
    private static final int COLOR_LINK = 0x00D5FF;
    private static final int COLOR_ICON = 0xC8C8C8;
    private static final int COLOR_ICON_DISABLED = 0x404040;
    private static final int COLOR_H1_SEPARATOR = 0x7FFFFFFF;
    private static final int COLOR_H2_SEPARATOR = 0x7FD2D2D2;
    private static final int COLOR_TABLE_BORDER = 0xFF7C7C7C;
    private static final int COLOR_TABLE_BAR = 0x3000D5FF;
    private static final int COLOR_CARD = 0x40FFFFFF;
    private static final int COLOR_HOVER = 0x60FFFFFF;
    private static final int COLOR_SCROLL_THUMB_SHADOW = 0xFF808080;
    private static final int COLOR_SCROLL_THUMB = 0xFFC0C0C0;

    private enum SortMode {
        AMOUNT,
        TIMES,
        NAME
    }

    private enum Action {
        SELECT_BAG,
        TOGGLE_FILTER,
        CYCLE_SORT,
        BACK,
        FORWARD,
        CLOSE,
        DOC_SCROLLBAR,
        NAV_SCROLLBAR
    }

    // Remembered while the game is running, so the GUI reopens where the player left it
    private static int sSelectedID = ALL_BAGS_ID;
    private static boolean sShowUnopened = true;
    private static SortMode sSortMode = SortMode.AMOUNT;

    private int mScreenLeft;
    private int mScreenRight;

    private GuiTextField mSearchField;

    private LootBagStats mStats;
    private final List<BagEntry> mAllBags = new ArrayList<>();
    private final List<BagEntry> mVisibleBags = new ArrayList<>();
    private BagEntry mSelected;
    private final List<DropEntry> mDrops = new ArrayList<>();

    private final LinkedList<Integer> mHistoryBack = new LinkedList<>();
    private final LinkedList<Integer> mHistoryForward = new LinkedList<>();

    private int mNavScroll = 0;
    private int mDocScroll = 0;
    private int mDocContentHeight = 0;
    private Action mDragging = null;
    private int mDragOffset = 0;

    /** Clickable areas of the last rendered frame */
    private final List<HitArea> mHitAreas = new ArrayList<>();
    private boolean mClipActive = false;
    private int mClipTop;
    private int mClipBottom;
    /** Item under the mouse in the last rendered frame; Its tooltip is drawn on top of everything */
    private DropEntry mHoveredDrop;
    private List<String> mHoverText;

    @Override
    public void initGui() {
        int tWidth = Math.min(width, MAX_SCREEN_WIDTH);
        mScreenLeft = (width - tWidth) / 2;
        mScreenRight = mScreenLeft + tWidth;

        Keyboard.enableRepeatEvents(true);
        String tOldSearch = mSearchField == null ? "" : mSearchField.getText();
        mSearchField = new GuiTextField(fontRendererObj, mScreenLeft + 10, TITLE_BAR_HEIGHT + 7, NAVBAR_WIDTH - 20, 10);
        mSearchField.setEnableBackgroundDrawing(false);
        mSearchField.setMaxStringLength(64);
        mSearchField.setTextColor(COLOR_BODY_TEXT);
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
                        EnumRarity.epic,
                        new ItemStack(Blocks.chest),
                        null));

        updateVisibleBags();

        BagEntry tSelected = findBag(sSelectedID);
        mSelected = tSelected == null ? mAllBags.get(0) : tSelected;
        updateDrops();
    }

    private BagEntry findBag(int pID) {
        for (BagEntry tEntry : mAllBags) if (tEntry.mID == pID) return tEntry;
        return null;
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

    private int getOpenedBagKinds() {
        int tCount = 0;
        for (BagEntry tEntry : mAllBags) if (tEntry.mID != ALL_BAGS_ID && tEntry.getOpened() > 0) tCount++;
        return tCount;
    }

    private void selectBag(BagEntry pEntry, boolean pRecordHistory) {
        if (pEntry == mSelected) return;
        if (pRecordHistory) {
            mHistoryBack.push(mSelected.mID);
            mHistoryForward.clear();
        }
        mSelected = pEntry;
        sSelectedID = pEntry.mID;
        mDocScroll = 0;
        updateDrops();
    }

    private void navigateHistory(LinkedList<Integer> pFrom, LinkedList<Integer> pTo) {
        while (!pFrom.isEmpty()) {
            BagEntry tEntry = findBag(pFrom.pop());
            if (tEntry != null && tEntry != mSelected) {
                pTo.push(mSelected.mID);
                selectBag(tEntry, false);
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout helpers
    // ---------------------------------------------------------------------------------------------------------------

    private int getNavListTop() {
        return TITLE_BAR_HEIGHT + NAV_SEARCH_HEIGHT;
    }

    private int getNavListBottom() {
        return height - NAV_FOOTER_HEIGHT;
    }

    private int getNavMaxScroll() {
        return Math.max(0, mVisibleBags.size() * NAV_ROW_HEIGHT - (getNavListBottom() - getNavListTop()));
    }

    private int getDocLeft() {
        return mScreenLeft + NAVBAR_WIDTH;
    }

    private int getDocViewHeight() {
        return height - TITLE_BAR_HEIGHT;
    }

    private int getDocMaxScroll() {
        return Math.max(0, mDocContentHeight - getDocViewHeight());
    }

    private static int clamp(int pValue, int pMin, int pMax) {
        return Math.max(pMin, Math.min(pValue, pMax));
    }

    private static boolean isInside(int pX, int pY, int pLeft, int pTop, int pWidth, int pHeight) {
        return pX >= pLeft && pX < pLeft + pWidth && pY >= pTop && pY < pTop + pHeight;
    }

    private void addHitArea(int pX, int pY, int pWidth, int pHeight, Action pAction, BagEntry pBag) {
        // Only the visible part of an area inside a scrolled region can be clicked
        if (mClipActive) {
            int tTop = Math.max(pY, mClipTop);
            int tBottom = Math.min(pY + pHeight, mClipBottom);
            if (tBottom <= tTop) return;
            pY = tTop;
            pHeight = tBottom - tTop;
        }
        mHitAreas.add(new HitArea(pX, pY, pWidth, pHeight, pAction, pBag));
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
        } else if (pKey == Keyboard.KEY_BACK) {
            navigateHistory(mHistoryBack, mHistoryForward);
        }
    }

    @Override
    protected void mouseClicked(int pX, int pY, int pButton) {
        mSearchField.mouseClicked(pX, pY, pButton);

        // Right click into the search field clears it
        if (pButton == 1 && isInside(pX, pY, mScreenLeft, TITLE_BAR_HEIGHT, NAVBAR_WIDTH, NAV_SEARCH_HEIGHT)) {
            mSearchField.setText("");
            updateVisibleBags();
            return;
        }
        if (pButton != 0) return;

        for (HitArea tArea : new ArrayList<>(mHitAreas)) {
            if (!isInside(pX, pY, tArea.mX, tArea.mY, tArea.mWidth, tArea.mHeight)) continue;
            switch (tArea.mAction) {
                case SELECT_BAG:
                    selectBag(tArea.mBag, true);
                    break;
                case TOGGLE_FILTER:
                    sShowUnopened = !sShowUnopened;
                    updateVisibleBags();
                    break;
                case CYCLE_SORT:
                    sSortMode = SortMode.values()[(sSortMode.ordinal() + 1) % SortMode.values().length];
                    updateDrops();
                    break;
                case BACK:
                    navigateHistory(mHistoryBack, mHistoryForward);
                    break;
                case FORWARD:
                    navigateHistory(mHistoryForward, mHistoryBack);
                    break;
                case CLOSE:
                    mc.displayGuiScreen(null);
                    break;
                case DOC_SCROLLBAR:
                case NAV_SCROLLBAR:
                    mDragging = tArea.mAction;
                    mDragOffset = pY - tArea.mY;
                    return; // No click sound for the scrollbar
            }
            playClickSound();
            return;
        }
    }

    @Override
    protected void mouseClickMove(int pX, int pY, int pButton, long pTime) {
        if (mDragging == Action.DOC_SCROLLBAR) {
            mDocScroll = dragScroll(pY, TITLE_BAR_HEIGHT, getDocViewHeight(), mDocContentHeight);
        } else if (mDragging == Action.NAV_SCROLLBAR) {
            mNavScroll = dragScroll(
                    pY,
                    getNavListTop(),
                    getNavListBottom() - getNavListTop(),
                    mVisibleBags.size() * NAV_ROW_HEIGHT);
        }
    }

    private int dragScroll(int pMouseY, int pTrackTop, int pTrackHeight, int pContentHeight) {
        int tMaxScroll = Math.max(0, pContentHeight - pTrackHeight);
        int tThumbHeight = getThumbHeight(pTrackHeight, pContentHeight);
        if (tMaxScroll == 0 || tThumbHeight >= pTrackHeight) return 0;
        int tThumbTop = pMouseY - mDragOffset - pTrackTop;
        return clamp(tThumbTop * tMaxScroll / (pTrackHeight - tThumbHeight), 0, tMaxScroll);
    }

    @Override
    protected void mouseMovedOrUp(int pX, int pY, int pButton) {
        super.mouseMovedOrUp(pX, pY, pButton);
        if (pButton >= 0) mDragging = null;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int tWheel = Mouse.getEventDWheel();
        if (tWheel == 0) return;

        int tX = Mouse.getEventX() * width / mc.displayWidth;
        int tDelta = (tWheel > 0 ? -1 : 1) * SCROLL_STEP * (GuiScreen.isShiftKeyDown() ? 5 : 1);

        if (tX < getDocLeft()) mNavScroll = clamp(mNavScroll + tDelta, 0, getNavMaxScroll());
        else mDocScroll = clamp(mDocScroll + tDelta, 0, getDocMaxScroll());
    }

    private void playClickSound() {
        mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int pMouseX, int pMouseY, float pPartialTicks) {
        mHitAreas.clear();
        mHoveredDrop = null;
        mHoverText = null;

        // Darken the world around the page, like GuideME does when the page is narrower than the screen
        drawRect(0, 0, width, height, 0x80000000);
        drawTiledBackground(mScreenLeft, 0, mScreenRight - mScreenLeft, height);

        drawDocument(pMouseX, pMouseY);
        drawNavbar(pMouseX, pMouseY);
        drawTitleBar(pMouseX, pMouseY);

        if (mHoveredDrop != null) drawDropTooltip(mHoveredDrop, pMouseX, pMouseY);
        else if (mHoverText != null) drawHoveringText(mHoverText, pMouseX, pMouseY, fontRendererObj);
    }

    private void drawTiledBackground(int pX, int pY, int pWidth, int pHeight) {
        mc.getTextureManager().bindTexture(TEX_BACKGROUND);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColor(COLOR_SCREEN_TINT);
        Tessellator tTess = Tessellator.instance;
        tTess.startDrawingQuads();
        tTess.addVertexWithUV(pX, pY + pHeight, 0, pX / 16.0D, (pY + pHeight) / 16.0D);
        tTess.addVertexWithUV(pX + pWidth, pY + pHeight, 0, (pX + pWidth) / 16.0D, (pY + pHeight) / 16.0D);
        tTess.addVertexWithUV(pX + pWidth, pY, 0, (pX + pWidth) / 16.0D, pY / 16.0D);
        tTess.addVertexWithUV(pX, pY, 0, pX / 16.0D, pY / 16.0D);
        tTess.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawTitleBar(int pMouseX, int pMouseY) {
        int tTitleLeft = mScreenLeft + 8;
        int tToolbarLeft = mScreenRight - 3 * (ICON_BUTTON_SIZE + 2) - 6;

        // Page title
        String tTitle = StatHelper.get("gui.stats.title") + EnumChatFormatting.DARK_GRAY
                + "  »  "
                + EnumChatFormatting.RESET
                + mSelected.mName;
        float tScale = 1.3F;
        int tMaxWidth = (int) ((tToolbarLeft - tTitleLeft - 4) / tScale);
        drawScaledString(
                fontRendererObj.trimStringToWidth(tTitle, tMaxWidth),
                tTitleLeft,
                (TITLE_BAR_HEIGHT - 11) / 2 + 1,
                tScale,
                COLOR_HEADING,
                true);

        // Toolbar
        int tY = (TITLE_BAR_HEIGHT - ICON_BUTTON_SIZE) / 2;
        drawIconButton(0, tToolbarLeft, tY, !mHistoryBack.isEmpty(), Action.BACK, "gui.stats.back", pMouseX, pMouseY);
        drawIconButton(
                1,
                tToolbarLeft + ICON_BUTTON_SIZE + 2,
                tY,
                !mHistoryForward.isEmpty(),
                Action.FORWARD,
                "gui.stats.forward",
                pMouseX,
                pMouseY);
        drawIconButton(
                2,
                tToolbarLeft + 2 * (ICON_BUTTON_SIZE + 2),
                tY,
                true,
                Action.CLOSE,
                "gui.stats.close",
                pMouseX,
                pMouseY);

        // H1 separator spanning the whole page
        drawRect(mScreenLeft, TITLE_BAR_HEIGHT - 1, mScreenRight, TITLE_BAR_HEIGHT, COLOR_H1_SEPARATOR);
    }

    private void drawIconButton(int pIcon, int pX, int pY, boolean pEnabled, Action pAction, String pTooltipKey,
            int pMouseX, int pMouseY) {
        boolean tHovered = pEnabled && isInside(pMouseX, pMouseY, pX, pY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE);
        int tColor = !pEnabled ? COLOR_ICON_DISABLED : tHovered ? COLOR_LINK : COLOR_ICON;

        mc.getTextureManager().bindTexture(TEX_ICONS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColor(0xFF000000 | tColor);
        func_146110_a(pX, pY, pIcon * 16, 0, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, 48, 16);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (pEnabled) addHitArea(pX, pY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, pAction, null);
        if (tHovered) setHoverText(StatHelper.get(pTooltipKey));
    }

    private void drawNavbar(int pMouseX, int pMouseY) {
        int tLeft = mScreenLeft;
        int tRight = mScreenLeft + NAVBAR_WIDTH;
        drawGradientRect(tLeft, TITLE_BAR_HEIGHT, tRight, height, COLOR_NAVBAR_TOP, COLOR_NAVBAR_BOTTOM);

        // Search box
        int tBoxTop = TITLE_BAR_HEIGHT + 4;
        int tBorder = mSearchField.isFocused() ? 0xFF000000 | COLOR_LINK : COLOR_TABLE_BORDER;
        drawRect(tLeft + 6, tBoxTop, tRight - 6, tBoxTop + 14, tBorder);
        drawRect(tLeft + 7, tBoxTop + 1, tRight - 7, tBoxTop + 13, 0xFF101010);
        mSearchField.drawTextBox();
        if (mSearchField.getText().isEmpty() && !mSearchField.isFocused()) {
            fontRendererObj.drawString(
                    StatHelper.get("gui.stats.search"),
                    mSearchField.xPosition,
                    mSearchField.yPosition,
                    COLOR_DIM_TEXT);
        }

        // Bag list
        int tListTop = getNavListTop();
        int tListBottom = getNavListBottom();
        boolean tHasScrollbar = getNavMaxScroll() > 0;
        int tRowRight = tRight - (tHasScrollbar ? 4 : 0);
        beginScissor(tLeft, tListTop, NAVBAR_WIDTH, tListBottom - tListTop);
        for (int i = 0; i < mVisibleBags.size(); i++) {
            int tY = tListTop + i * NAV_ROW_HEIGHT - mNavScroll;
            if (tY + NAV_ROW_HEIGHT <= tListTop || tY >= tListBottom) continue;

            BagEntry tEntry = mVisibleBags.get(i);
            boolean tHovered = pMouseY >= tListTop && pMouseY < tListBottom
                    && isInside(pMouseX, pMouseY, tLeft, tY, tRowRight - tLeft, NAV_ROW_HEIGHT);
            boolean tSelected = tEntry == mSelected;
            boolean tOpened = tEntry.mID == ALL_BAGS_ID || tEntry.getOpened() > 0;

            if (tSelected) {
                drawRect(tLeft, tY, tRowRight, tY + NAV_ROW_HEIGHT, COLOR_NAV_SELECTED);
                drawRect(tLeft, tY, tLeft + 2, tY + NAV_ROW_HEIGHT, 0xFF000000 | COLOR_LINK);
            } else if (tHovered) {
                drawRect(tLeft, tY, tRowRight, tY + NAV_ROW_HEIGHT, COLOR_NAV_HOVER);
            }

            drawItem(tEntry.mIcon, tLeft + 5, tY + 1, null);

            String tCount = formatCompact(
                    tEntry.mID == ALL_BAGS_ID ? mStats.getTotalOpened() : (long) tEntry.getOpened());
            int tCountWidth = fontRendererObj.getStringWidth(tCount);
            int tNameLeft = tLeft + 24;
            int tNameWidth = tRowRight - 6 - tCountWidth - 4 - tNameLeft;
            String tName = fontRendererObj.trimStringToWidth(tEntry.mName, tNameWidth);
            int tNameColor = tHovered || tSelected ? COLOR_LINK : tOpened ? COLOR_BODY_TEXT : COLOR_DIM_TEXT;
            fontRendererObj.drawString(tName, tNameLeft, tY + 5, tNameColor);
            fontRendererObj.drawString(
                    tCount,
                    tRowRight - 6 - tCountWidth,
                    tY + 5,
                    tOpened ? COLOR_BODY_TEXT : COLOR_DIM_TEXT);

            if (tHovered) {
                addHitArea(tLeft, tY, tRowRight - tLeft, NAV_ROW_HEIGHT, Action.SELECT_BAG, tEntry);
                if (!tName.equals(tEntry.mName)) setHoverText(tEntry.mRarity.rarityColor + tEntry.mName);
            }
        }
        endScissor();

        drawScrollbar(
                tRight - 4,
                tListTop,
                4,
                tListBottom - tListTop,
                mNavScroll,
                mVisibleBags.size() * NAV_ROW_HEIGHT,
                Action.NAV_SCROLLBAR);

        // Footer with the filter link
        String tFilter = StatHelper.get(sShowUnopened ? "gui.stats.filter_all" : "gui.stats.filter_opened");
        drawLink(tFilter, tLeft + 6, height - NAV_FOOTER_HEIGHT + 4, Action.TOGGLE_FILTER, pMouseX, pMouseY);
    }

    private void drawDocument(int pMouseX, int pMouseY) {
        int tLeft = getDocLeft();
        int tTop = TITLE_BAR_HEIGHT;
        int tViewHeight = getDocViewHeight();
        drawRect(tLeft, tTop, mScreenRight, height, COLOR_DOCUMENT);

        mDocScroll = clamp(mDocScroll, 0, getDocMaxScroll());
        boolean tMouseInDoc = isInside(pMouseX, pMouseY, tLeft, tTop, mScreenRight - tLeft, tViewHeight);

        beginScissor(tLeft, tTop, mScreenRight - tLeft, tViewHeight);
        int tContentLeft = tLeft + DOC_PADDING;
        int tContentWidth = mScreenRight - SCROLLBAR_WIDTH - DOC_PADDING - tContentLeft;
        int tY = tTop + DOC_PADDING - mDocScroll;

        tY = drawHero(tContentLeft, tY, tContentWidth);
        tY = drawStatCards(tContentLeft, tY + 8, tContentWidth);

        if (mDrops.isEmpty()) {
            tY = drawHeading(StatHelper.get("gui.stats.drops"), null, tContentLeft, tY + 12, tContentWidth, 0, 0);
            String tMessage = StatHelper
                    .get(getSelectedOpened() == 0 ? "gui.stats.never_opened" : "gui.stats.nothing_dropped");
            tY = drawParagraph(tMessage, tContentLeft, tY, tContentWidth, COLOR_DIM_TEXT);
        } else {
            String tSort = StatHelper.get("gui.stats.sort_" + sSortMode.name().toLowerCase(Locale.ROOT));
            tY = drawHeading(
                    StatHelper.get("gui.stats.drops"),
                    tSort,
                    tContentLeft,
                    tY + 12,
                    tContentWidth,
                    tMouseInDoc ? pMouseX : -1,
                    pMouseY);
            tY = drawDropGrid(tContentLeft, tY, tContentWidth, tMouseInDoc, pMouseX, pMouseY);

            tY = drawHeading(
                    String.format(StatHelper.get("gui.stats.top_drops"), Math.min(TOP_DROPS_COUNT, mDrops.size())),
                    null,
                    tContentLeft,
                    tY + 12,
                    tContentWidth,
                    0,
                    0);
            tY = drawTopDropsTable(tContentLeft, tY, tContentWidth, tMouseInDoc, pMouseX, pMouseY);
        }
        endScissor();

        mDocContentHeight = tY + mDocScroll + DOC_PADDING - tTop;
        drawScrollbar(
                mScreenRight - SCROLLBAR_WIDTH,
                tTop,
                SCROLLBAR_WIDTH,
                tViewHeight,
                mDocScroll,
                mDocContentHeight,
                Action.DOC_SCROLLBAR);
    }

    /**
     * Big icon, name and a short description of the selected bag
     */
    private int drawHero(int pX, int pY, int pWidth) {
        drawLargeSlot(pX, pY);
        GL11.glPushMatrix();
        GL11.glTranslatef(pX + 5, pY + 5, 0);
        GL11.glScalef(2.0F, 2.0F, 1.0F);
        drawItem(mSelected.mIcon, -1, -1, null);
        GL11.glPopMatrix();

        int tTextLeft = pX + 48;
        int tTextWidth = pWidth - 48;
        float tScale = 1.5F;
        drawScaledString(
                mSelected.mRarity.rarityColor
                        + fontRendererObj.trimStringToWidth(mSelected.mName, (int) (tTextWidth / tScale)),
                tTextLeft,
                pY + 6,
                tScale,
                COLOR_HEADING,
                true);

        String tSubtitle;
        if (mSelected.mID == ALL_BAGS_ID) tSubtitle = String
                .format(StatHelper.get("gui.stats.all_subtitle"), getOpenedBagKinds(), mAllBags.size() - 1);
        else tSubtitle = String.format(StatHelper.get("gui.stats.bag_subtitle"), mSelected.mID);
        fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(tSubtitle, tTextWidth),
                tTextLeft,
                pY + 24,
                COLOR_DIM_TEXT);
        return pY + 42;
    }

    /**
     * Three cards with the key numbers of the selected bag
     */
    private int drawStatCards(int pX, int pY, int pWidth) {
        String[] tLabels = { StatHelper.get("gui.stats.card_opened"), StatHelper.get("gui.stats.card_items"),
                StatHelper.get("gui.stats.card_unique") };
        long[] tValues = { getSelectedOpened(), getSelectedTotalItems(), mDrops.size() };
        int tGap = 6;
        int tCardWidth = (pWidth - 2 * tGap) / 3;
        int tCardHeight = 36;

        for (int i = 0; i < 3; i++) {
            int tX = pX + i * (tCardWidth + tGap);
            drawRect(tX, pY, tX + tCardWidth, pY + tCardHeight, COLOR_CARD);
            drawRect(tX, pY, tX + tCardWidth, pY + 1, COLOR_TABLE_BORDER);
            drawRect(tX, pY + tCardHeight - 1, tX + tCardWidth, pY + tCardHeight, COLOR_TABLE_BORDER);
            drawRect(tX, pY, tX + 1, pY + tCardHeight, COLOR_TABLE_BORDER);
            drawRect(tX + tCardWidth - 1, pY, tX + tCardWidth, pY + tCardHeight, COLOR_TABLE_BORDER);

            String tValue = formatNumber(tValues[i]);
            float tScale = 1.5F;
            int tValueWidth = (int) (fontRendererObj.getStringWidth(tValue) * tScale);
            drawScaledString(
                    tValue,
                    tX + (tCardWidth - tValueWidth) / 2,
                    pY + 6,
                    tScale,
                    i == 0 ? COLOR_LINK : COLOR_HEADING,
                    true);
            String tLabel = fontRendererObj.trimStringToWidth(tLabels[i], tCardWidth - 4);
            fontRendererObj.drawString(
                    tLabel,
                    tX + (tCardWidth - fontRendererObj.getStringWidth(tLabel)) / 2,
                    pY + 24,
                    COLOR_BODY_TEXT);
        }
        return pY + tCardHeight;
    }

    /**
     * H2 heading with separator, optionally with a link aligned to the right
     */
    private int drawHeading(String pText, String pLink, int pX, int pY, int pWidth, int pMouseX, int pMouseY) {
        drawScaledString(pText, pX, pY, 1.15F, COLOR_HEADING, true);
        if (pLink != null) {
            drawLink(
                    pLink,
                    pX + pWidth - fontRendererObj.getStringWidth(pLink),
                    pY + 2,
                    Action.CYCLE_SORT,
                    pMouseX,
                    pMouseY);
        }
        drawRect(pX, pY + 13, pX + pWidth, pY + 14, COLOR_H2_SEPARATOR);
        return pY + 20;
    }

    private int drawParagraph(String pText, int pX, int pY, int pWidth, int pColor) {
        @SuppressWarnings("unchecked")
        List<String> tLines = fontRendererObj.listFormattedStringToWidth(pText, pWidth);
        for (String tLine : tLines) {
            fontRendererObj.drawString(tLine, pX, pY, pColor);
            pY += 10;
        }
        return pY;
    }

    private void drawLink(String pText, int pX, int pY, Action pAction, int pMouseX, int pMouseY) {
        int tWidth = fontRendererObj.getStringWidth(pText);
        boolean tHovered = isInside(pMouseX, pMouseY, pX - 1, pY - 1, tWidth + 2, 10);
        fontRendererObj.drawString(tHovered ? EnumChatFormatting.UNDERLINE + pText : pText, pX, pY, COLOR_LINK);
        addHitArea(pX - 1, pY - 1, tWidth + 2, 10, pAction, null);
    }

    /**
     * All received items as slots in a GuideME styled window
     */
    private int drawDropGrid(int pX, int pY, int pWidth, boolean pMouseInDoc, int pMouseX, int pMouseY) {
        int tColumns = Math.max(1, (pWidth - 14) / SLOT_SIZE);
        int tRows = (mDrops.size() + tColumns - 1) / tColumns;
        int tGridWidth = Math.min(mDrops.size(), tColumns) * SLOT_SIZE;
        int tWindowWidth = tGridWidth + 14;
        int tWindowHeight = tRows * SLOT_SIZE + 14;
        drawWindow(pX, pY, tWindowWidth, tWindowHeight);

        for (int i = 0; i < mDrops.size(); i++) {
            DropEntry tDrop = mDrops.get(i);
            int tX = pX + 7 + (i % tColumns) * SLOT_SIZE;
            int tY = pY + 7 + (i / tColumns) * SLOT_SIZE;
            if (tY + SLOT_SIZE < TITLE_BAR_HEIGHT || tY > height) continue;

            drawSlot(tX, tY);
            boolean tHovered = pMouseInDoc && isInside(pMouseX, pMouseY, tX, tY, SLOT_SIZE, SLOT_SIZE);
            drawItem(tDrop.mStack, tX, tY, formatCompact(tDrop.mItemCount));
            if (tHovered) {
                drawSlotHighlight(tX + 1, tY + 1);
                mHoveredDrop = tDrop;
            }
        }
        return pY + tWindowHeight;
    }

    /**
     * Table with the most frequent drops, including a bar that shows the share of all drops
     */
    private int drawTopDropsTable(int pX, int pY, int pWidth, boolean pMouseInDoc, int pMouseX, int pMouseY) {
        List<DropEntry> tTop = new ArrayList<>(mDrops);
        tTop.sort(Comparator.comparingInt((DropEntry e) -> -e.mTimesDropped));
        if (tTop.size() > TOP_DROPS_COUNT) tTop = tTop.subList(0, TOP_DROPS_COUNT);

        long tTotalTimes = getSelectedTotalTimes();
        int tMaxTimes = tTop.get(0).mTimesDropped;
        int tAmountWidth = 50;
        int tShareWidth = 50;
        int tShareLeft = pX + pWidth - tShareWidth;
        int tAmountLeft = tShareLeft - tAmountWidth;

        // Header
        int tY = pY;
        drawRect(pX, tY, pX + pWidth, tY + 14, 0x40000000);
        fontRendererObj.drawString(StatHelper.get("gui.stats.table_item"), pX + 4, tY + 3, COLOR_HEADING);
        drawRightAligned(StatHelper.get("gui.stats.table_amount"), tShareLeft - 4, tY + 3, COLOR_HEADING);
        drawRightAligned(StatHelper.get("gui.stats.table_share"), pX + pWidth - 4, tY + 3, COLOR_HEADING);
        tY += 14;

        for (DropEntry tDrop : tTop) {
            drawRect(pX, tY, pX + pWidth, tY + 1, COLOR_TABLE_BORDER);

            // Share bar behind the row
            int tBarWidth = (int) ((long) (pWidth - 2) * tDrop.mTimesDropped / Math.max(1, tMaxTimes));
            drawRect(pX + 1, tY + 1, pX + 1 + tBarWidth, tY + TABLE_ROW_HEIGHT, COLOR_TABLE_BAR);

            boolean tHovered = pMouseInDoc && isInside(pMouseX, pMouseY, pX, tY, pWidth, TABLE_ROW_HEIGHT);
            if (tHovered) {
                drawRect(pX + 1, tY + 1, pX + pWidth - 1, tY + TABLE_ROW_HEIGHT, 0x20FFFFFF);
                mHoveredDrop = tDrop;
            }

            drawItem(tDrop.mStack, pX + 2, tY + 1, null);
            String tName = fontRendererObj.trimStringToWidth(tDrop.mName, tAmountLeft - pX - 26);
            fontRendererObj.drawString(tName, pX + 22, tY + 6, tHovered ? COLOR_LINK : COLOR_BODY_TEXT);
            drawRightAligned(formatNumber(tDrop.mItemCount), tShareLeft - 4, tY + 6, COLOR_BODY_TEXT);
            drawRightAligned(formatPercent(tDrop.mTimesDropped, tTotalTimes), pX + pWidth - 4, tY + 6, COLOR_BODY_TEXT);
            tY += TABLE_ROW_HEIGHT;
        }

        // Table border and column separators
        drawRect(pX, tY, pX + pWidth, tY + 1, COLOR_TABLE_BORDER);
        drawRect(pX, pY, pX + pWidth, pY + 1, COLOR_TABLE_BORDER);
        drawRect(pX, pY, pX + 1, tY + 1, COLOR_TABLE_BORDER);
        drawRect(pX + pWidth - 1, pY, pX + pWidth, tY + 1, COLOR_TABLE_BORDER);
        drawRect(tAmountLeft, pY, tAmountLeft + 1, tY, COLOR_TABLE_BORDER);
        drawRect(tShareLeft, pY, tShareLeft + 1, tY, COLOR_TABLE_BORDER);
        return tY + 1;
    }

    private void drawRightAligned(String pText, int pRight, int pY, int pColor) {
        fontRendererObj.drawString(pText, pRight - fontRendererObj.getStringWidth(pText), pY, pColor);
    }

    /**
     * Dark beveled window, drawn like GuideME's dark mode recipe window
     */
    private void drawWindow(int pX, int pY, int pWidth, int pHeight) {
        int tRight = pX + pWidth;
        int tBottom = pY + pHeight;
        // Black outline with cut corners
        drawRect(pX + 2, pY, tRight - 2, pY + 1, 0xFF000000);
        drawRect(pX + 2, tBottom - 1, tRight - 2, tBottom, 0xFF000000);
        drawRect(pX, pY + 2, pX + 1, tBottom - 2, 0xFF000000);
        drawRect(tRight - 1, pY + 2, tRight, tBottom - 2, 0xFF000000);
        drawRect(pX + 1, pY + 1, pX + 2, pY + 2, 0xFF000000);
        drawRect(tRight - 2, pY + 1, tRight - 1, pY + 2, 0xFF000000);
        drawRect(pX + 1, tBottom - 2, pX + 2, tBottom - 1, 0xFF000000);
        drawRect(tRight - 2, tBottom - 2, tRight - 1, tBottom - 1, 0xFF000000);
        // Fill, highlight (top left) and shadow (bottom right)
        drawRect(pX + 1, pY + 1, tRight - 1, tBottom - 1, 0xFF636363);
        drawRect(pX + 2, pY + 1, tRight - 3, pY + 3, 0xFF808080);
        drawRect(pX + 1, pY + 2, pX + 3, tBottom - 3, 0xFF808080);
        drawRect(pX + 3, tBottom - 3, tRight - 2, tBottom - 1, 0xFF2B2B2B);
        drawRect(tRight - 3, pY + 3, tRight - 1, tBottom - 2, 0xFF2B2B2B);
    }

    private void drawSlot(int pX, int pY) {
        drawRect(pX, pY, pX + SLOT_SIZE, pY + SLOT_SIZE, 0xFF8B8B8B);
        drawRect(pX, pY, pX + SLOT_SIZE - 1, pY + 1, 0xFF373737);
        drawRect(pX, pY, pX + 1, pY + SLOT_SIZE - 1, 0xFF373737);
        drawRect(pX + 1, pY + SLOT_SIZE - 1, pX + SLOT_SIZE, pY + SLOT_SIZE, 0xFFFFFFFF);
        drawRect(pX + SLOT_SIZE - 1, pY + 1, pX + SLOT_SIZE, pY + SLOT_SIZE, 0xFFFFFFFF);
    }

    private void drawLargeSlot(int pX, int pY) {
        int tSize = 42;
        drawRect(pX, pY, pX + tSize, pY + tSize, 0xFF8B8B8B);
        drawRect(pX, pY, pX + tSize - 1, pY + 1, 0xFF373737);
        drawRect(pX, pY, pX + 1, pY + tSize - 1, 0xFF373737);
        drawRect(pX + 1, pY + tSize - 1, pX + tSize, pY + tSize, 0xFFFFFFFF);
        drawRect(pX + tSize - 1, pY + 1, pX + tSize, pY + tSize, 0xFFFFFFFF);
    }

    private void drawSlotHighlight(int pX, int pY) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColorMask(true, true, true, false);
        drawGradientRect(pX, pY, pX + 16, pY + 16, 0x80FFFFFF, 0x80FFFFFF);
        GL11.glColorMask(true, true, true, true);
    }

    private int getThumbHeight(int pTrackHeight, int pContentHeight) {
        if (pContentHeight <= 0) return pTrackHeight;
        return Math.max(10, pTrackHeight * pTrackHeight / pContentHeight);
    }

    private void drawScrollbar(int pX, int pY, int pWidth, int pHeight, int pScroll, int pContentHeight,
            Action pAction) {
        int tMaxScroll = pContentHeight - pHeight;
        if (tMaxScroll <= 0) return;

        int tThumbHeight = getThumbHeight(pHeight, pContentHeight);
        int tThumbTop = pY + (pHeight - tThumbHeight) * pScroll / tMaxScroll;
        drawRect(pX, tThumbTop, pX + pWidth, tThumbTop + tThumbHeight, COLOR_SCROLL_THUMB_SHADOW);
        drawRect(pX, tThumbTop, pX + pWidth - 1, tThumbTop + tThumbHeight - 1, COLOR_SCROLL_THUMB);
        addHitArea(pX, tThumbTop, pWidth, tThumbHeight, pAction, null);
    }

    private void drawScaledString(String pText, int pX, int pY, float pScale, int pColor, boolean pShadow) {
        GL11.glPushMatrix();
        GL11.glTranslatef(pX, pY, 0.0F);
        GL11.glScalef(pScale, pScale, 1.0F);
        fontRendererObj.drawString(pText, 0, 0, pColor, pShadow);
        GL11.glPopMatrix();
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

    private void setHoverText(String pText) {
        mHoverText = new ArrayList<>();
        mHoverText.add(pText);
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
        tTip.add("");
        tTip.add(String.format(StatHelper.get("gui.stats.tip_received"), formatFull(pDrop.mItemCount)));
        tTip.add(String.format(StatHelper.get("gui.stats.tip_times"), formatFull(pDrop.mTimesDropped)));
        if (tOpened > 0) tTip.add(
                String.format(
                        StatHelper.get("gui.stats.tip_per_bag"),
                        String.format("%.2f", (double) pDrop.mItemCount / tOpened)));
        tTip.add(
                String.format(
                        StatHelper.get("gui.stats.tip_share"),
                        formatPercent(pDrop.mTimesDropped, getSelectedTotalTimes())));

        drawHoveringText(tTip, pMouseX, pMouseY, fontRendererObj);
    }

    private static void setColor(int pARGB) {
        GL11.glColor4f(
                (pARGB >> 16 & 255) / 255.0F,
                (pARGB >> 8 & 255) / 255.0F,
                (pARGB & 255) / 255.0F,
                (pARGB >>> 24) / 255.0F);
    }

    private void beginScissor(int pX, int pY, int pWidth, int pHeight) {
        ScaledResolution tRes = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int tScale = tRes.getScaleFactor();
        mClipActive = true;
        mClipTop = pY;
        mClipBottom = pY + pHeight;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                pX * tScale,
                mc.displayHeight - (pY + pHeight) * tScale,
                Math.max(0, pWidth * tScale),
                Math.max(0, pHeight * tScale));
    }

    private void endScissor() {
        mClipActive = false;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Number formatting
    // ---------------------------------------------------------------------------------------------------------------

    private static String formatFull(long pNumber) {
        return String.format("%,d", pNumber);
    }

    private static String formatNumber(long pNumber) {
        return pNumber < 1000000 ? formatFull(pNumber) : formatCompact(pNumber);
    }

    private static String formatPercent(long pPart, long pTotal) {
        return pTotal <= 0 ? "-" : String.format("%.1f%%", 100.0D * pPart / pTotal);
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

    private static class HitArea {

        private final int mX;
        private final int mY;
        private final int mWidth;
        private final int mHeight;
        private final Action mAction;
        private final BagEntry mBag;

        private HitArea(int pX, int pY, int pWidth, int pHeight, Action pAction, BagEntry pBag) {
            mX = pX;
            mY = pY;
            mWidth = pWidth;
            mHeight = pHeight;
            mAction = pAction;
            mBag = pBag;
        }
    }
}
