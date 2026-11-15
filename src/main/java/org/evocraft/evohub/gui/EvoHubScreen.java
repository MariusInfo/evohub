package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.evocraft.evocore.client.ClientBalanceData;
import org.evocraft.evocore.client.ClientStatsData;
import org.evocraft.evocore.util.EvoCurrencyFormatter;
import org.evocraft.evohub.client.ClientCratesData;
import org.evocraft.evohub.client.EvoHubClientSettings;
import org.evocraft.evohub.network.EvoHubPacketHandler;
import org.evocraft.evojobs.client.ClientJobsData;
import org.evocraft.evojobs.client.ClientJobsData.JobInfo;
import org.evocraft.evojobs.JobManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EvoHubScreen extends Screen {

    private int currentTab;

    // VARIABILA ACTUALIZATĂ DE SERVER
    public static String currentServerName = "Identifying...";

    // VERIFICĂ DACĂ E SERVERUL DE CREATIVE
    public static boolean isCreative() {
        return "Creative".equalsIgnoreCase(currentServerName);
    }

    public static class TopEntry {
        public String name; public double value;
        public TopEntry(String n, double v) { name = n; value = v; }
    }
    public static List<TopEntry> currentTopList = new ArrayList<>();
    public static int currentTopPage = 1;
    public static String currentTopCategory = "bani";

    public static boolean isLoadingTop = false;

    private final List<String> onlinePlayers = new ArrayList<>();
    private int tpaPage = 0;
    private String selectedTpaPlayer = null;

    private boolean isSelectingJobTop = false;
    private boolean isEditingScoreboard = false;

    public static boolean settingShowScoreboard = true;
    public static boolean settingAllowTPA = true;
    public static boolean settingAllowTrade = true;

    public static boolean sbShowHeader = true;
    public static boolean sbShowTu = true;
    public static boolean sbShowRank = true;
    public static boolean sbShowMoney = true;
    public static boolean sbShowPlaytime = true;
    public static boolean sbShowCombat = true;
    public static boolean sbShowParty = true;
    public static boolean sbShowJobs = false;
    public static boolean sbShowInfo = true;
    public static boolean sbShowVote = true;

    public static final int SB_TOP_LEFT = 0;
    public static final int SB_TOP_RIGHT = 1;
    public static final int SB_BOTTOM_LEFT = 2;
    public static final int SB_BOTTOM_RIGHT = 3;
    public static final int SB_RIGHT_CENTER = 4;
    public static final int SB_POSITION_COUNT = 5;

    public static final int SB_WIDTH_COMPACT = 0;
    public static final int SB_WIDTH_NORMAL = 1;
    public static final int SB_WIDTH_WIDE = 2;
    public static final int SB_WIDTH_MODE_COUNT = 3;

    public static int sbPosition = SB_RIGHT_CENTER;
    public static int sbScalePercent = 100;
    public static int sbWidthMode = SB_WIDTH_NORMAL;

    static {
        EvoHubClientSettings.load();
    }

    private EditBox blockInput;
    private EditBox homeNameInput;
    private final List<CustomButton> buttons = new ArrayList<>();

    private final String[] tabNames = {
            "Servers", "My Profile", "My Career", "Leaderboards",
            "Protections", "Teleport", "My Homes", "Kits", "Warps", "Settings", "Crates"
    };

    public EvoHubScreen(int startTab) {
        super(Component.literal("EvoCraft Hub"));
        this.currentTab = startTab;
    }

    public EvoHubScreen() {
        // Pornim cu valoarea -1, care reprezintă "Meniul Principal (Acasă)"
        this(-1);
    }

    private float getScale() {
        double guiScale = this.minecraft != null ? this.minecraft.getWindow().getGuiScale() : 1.0;
        if (guiScale >= 4) return 0.65f;
        if (guiScale >= 3) return 0.85f;
        return 1.0f;
    }

    private List<Integer> getVisibleMainTabs() {
        List<Integer> visibleTabs = new ArrayList<>();
        for (int i = 0; i < tabNames.length; i++) {
            if (isCreative() && (i == 2 || i == 7 || i == 8 || i == 10)) continue;
            visibleTabs.add(i);
        }
        return visibleTabs;
    }

    private int getMainMenuStartY(int sh, int visibleCount) {
        int cols = 4;
        int boxH = 92;
        int gap = 14;
        int rows = Math.max(1, (visibleCount + cols - 1) / cols);
        int totalH = rows * boxH + (rows - 1) * gap;
        return Math.max(72, (sh - totalH) / 2 + 48);
    }

    public static float getScoreboardUserScale() {
        return Math.max(70, Math.min(130, sbScalePercent)) / 100.0f;
    }

    public static int getScoreboardWidth() {
        return switch (sbWidthMode) {
            case SB_WIDTH_COMPACT -> 160;
            case SB_WIDTH_WIDE -> 215;
            default -> 180;
        };
    }

    public static String getScoreboardPositionName() {
        return switch (sbPosition) {
            case SB_TOP_LEFT -> "Top left";
            case SB_TOP_RIGHT -> "Top right";
            case SB_BOTTOM_LEFT -> "Bottom left";
            case SB_BOTTOM_RIGHT -> "Bottom right";
            default -> "Right center";
        };
    }

    public static String getScoreboardWidthName() {
        return switch (sbWidthMode) {
            case SB_WIDTH_COMPACT -> "Compact";
            case SB_WIDTH_WIDE -> "Wide";
            default -> "Normal";
        };
    }

    private void saveHudSettings() {
        EvoHubClientSettings.save();
    }

    private void cycleScoreboardPosition(int delta) {
        sbPosition = Math.floorMod(sbPosition + delta, SB_POSITION_COUNT);
        saveHudSettings();
        this.init();
    }

    private void cycleScoreboardWidth(int delta) {
        sbWidthMode = Math.floorMod(sbWidthMode + delta, SB_WIDTH_MODE_COUNT);
        saveHudSettings();
        this.init();
    }

    private void adjustScoreboardScale(int delta) {
        sbScalePercent = Math.max(70, Math.min(130, sbScalePercent + delta));
        saveHudSettings();
        this.init();
    }

    private void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private void outlineRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private ItemStack getIconForTab(int index) {
        return switch (index) {
            case 0 -> new ItemStack(Items.COMPASS);
            case 1 -> new ItemStack(Items.BOOK);
            case 2 -> new ItemStack(Items.IRON_PICKAXE);
            case 3 -> new ItemStack(Items.EMERALD);
            case 4 -> new ItemStack(Items.GOLDEN_SHOVEL);
            case 5 -> new ItemStack(Items.ENDER_PEARL);
            case 6 -> new ItemStack(Items.RED_BED);
            case 7 -> new ItemStack(Items.DIAMOND_CHESTPLATE);
            case 8 -> new ItemStack(Items.END_PORTAL_FRAME);
            case 9 -> new ItemStack(Items.REPEATER);
            case 10 -> new ItemStack(Items.ENDER_CHEST);
            default -> new ItemStack(Items.DIRT);
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (ClientCratesData.needsRefresh && currentTab == 1) {
            ClientCratesData.needsRefresh = false;
            this.init();
        }
    }

    @Override
    protected void init() {
        if (!isCreative()) EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestCrates());
        this.clearWidgets();
        buttons.clear();

        onlinePlayers.clear();
        if (this.minecraft != null && this.minecraft.getConnection() != null && this.minecraft.player != null) {
            String myName = this.minecraft.player.getGameProfile().getName();
            this.minecraft.getConnection().getOnlinePlayers().forEach(p -> {
                if (p.getProfile() == null || p.getProfile().getId() == null) return;
                if (p.getProfile().getId().version() == 2) return;
                if (org.evocraft.evocore.client.ClientVanishData.vanishedPlayers.contains(p.getProfile().getId())) return;
                String pName = p.getProfile().getName();
                if (!pName.equals(myName)) onlinePlayers.add(pName);
            });
            onlinePlayers.sort(String::compareToIgnoreCase);
        }

        float scale = getScale();
        int sw = (int)(this.width / scale);
        int sh = (int)(this.height / scale);

        // ==== LOGICA PENTRU MENIUL PRINCIPAL (ACASĂ) ====
        if (currentTab == -1) {
            List<Integer> visibleTabs = getVisibleMainTabs();

            int cols = 4;
            int boxW = 112;
            int boxH = 92;
            int gap = 14;
            int totalCols = Math.min(visibleTabs.size(), cols);
            int totalW = totalCols * boxW + (totalCols - 1) * gap;
            int startX = (sw - totalW) / 2;
            int startY = getMainMenuStartY(sh, visibleTabs.size());

            for (int idx = 0; idx < visibleTabs.size(); idx++) {
                int tabIndex = visibleTabs.get(idx);
                int r = idx / cols;
                int c = idx % cols;
                int bx = startX + c * (boxW + gap);
                int by = startY + r * (boxH + gap);

                String tName = tabNames[tabIndex];

                buttons.add(new IconButton(tName, bx, by, boxW, boxH, getIconForTab(tabIndex), () -> {
                    if (tabIndex == 7) { org.evocraft.evohub.client.ClientEvents.openKitsScreen(this.minecraft, this); return; }
                    if (tabIndex == 8) { if (this.minecraft != null) this.minecraft.setScreen(new EvoWarpScreen(this)); return; }
                    if (tabIndex == 10) { if (this.minecraft != null) this.minecraft.setScreen(new EvoCratesScreen(this)); return; }

                    this.currentTab = tabIndex;
                    this.isSelectingJobTop = false;
                    this.isEditingScoreboard = false;
                    if (tabIndex == 3) {
                        isLoadingTop = true;
                        if (isCreative() && (currentTopCategory.equals("kills") || currentTopCategory.equals("decese") || currentTopCategory.startsWith("job_"))) {
                            currentTopCategory = "bani";
                        }
                        EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage));
                    }
                    this.init();
                }));
            }
            return; // Oprim procesarea pentru interior
        }

        // ==== LOGICA PENTRU INTERIORUL FIȘELOR ====
        int w = 550; int h = 350;
        int x = (sw - w) / 2; int y = (sh - h) / 2;
        int menuWidth = 120;

        // Buton de Back (Meniu Principal)
        buttons.add(new SidebarButton("⬅ Main Menu", x + 5, y + 5, menuWidth - 10, 22, false, () -> {
            this.currentTab = -1;
            this.init();
        }));

        int btnY = y + 35; // Lăsăm loc pentru butonul de Meniu Principal

        for (int i = 0; i < tabNames.length; i++) {
            final int tabIndex = i;
            String tName = tabNames[i];

            boolean hideTab = isCreative() && (tabIndex == 2 || tabIndex == 7 || tabIndex == 8 || tabIndex == 10);

            SidebarButton tabBtn = new SidebarButton(tName, x + 5, btnY, menuWidth - 10, 24, this.currentTab == i, () -> {
                if (tabIndex == 7) { org.evocraft.evohub.client.ClientEvents.openKitsScreen(this.minecraft, this); return; }
                if (tabIndex == 8) { if (this.minecraft != null) this.minecraft.setScreen(new EvoWarpScreen(this)); return; }
                if (tabIndex == 10) { if (this.minecraft != null) this.minecraft.setScreen(new EvoCratesScreen(this)); return; }

                this.currentTab = tabIndex;
                this.isSelectingJobTop = false;
                this.isEditingScoreboard = false;
                if (tabIndex == 3) {
                    isLoadingTop = true;
                    if (isCreative() && (currentTopCategory.equals("kills") || currentTopCategory.equals("decese") || currentTopCategory.startsWith("job_"))) {
                        currentTopCategory = "bani";
                    }
                    EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage));
                }
                this.init();
            });

            if (hideTab) {
                tabBtn.visible = false;
            } else {
                btnY += 28;
            }
            buttons.add(tabBtn);
        }

        int contentX = x + menuWidth + 20; int contentY = y + 30;

        if (currentTab == 0) {
            buttons.add(new IconButton("Survival", contentX + 55, contentY + 60, 112, 92, new ItemStack(Items.GRASS_BLOCK), () -> {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.connection.sendCommand("server survival");
                    this.minecraft.setScreen(null);
                }
            }));
            buttons.add(new IconButton("Creative", contentX + 195, contentY + 60, 112, 92, new ItemStack(Items.BRICKS), () -> {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.connection.sendCommand("server creative");
                    this.minecraft.setScreen(null);
                }
            }));
        }
        else if (currentTab == 3) {
            if (isSelectingJobTop && !isCreative()) {
                buttons.add(new CustomButton("< Back to Categories", contentX, contentY, 150, 20, () -> {
                    isSelectingJobTop = false; this.init();
                }));
                String[] jobIds = {"miner", "woodcutter", "digger", "hunter", "farmer", "fisherman", "builder", "crafter", "trader", "somer"};
                String[] jobNames = {"Miner", "Lumberjack", "Digger", "Hunter", "Farmer", "Fisherman", "Builder", "Crafter", "Trader", "Unemployed"};
                int jX = contentX; int jY = contentY + 30;
                for (int i = 0; i < jobIds.length; i++) {
                    final String jId = jobIds[i];
                    buttons.add(new CustomButton(jobNames[i], jX, jY, 70, 20, () -> {
                        currentTopCategory = "job_" + jId; currentTopPage = 1;
                        isSelectingJobTop = false; isLoadingTop = true;
                        EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage));
                        this.init();
                    }));
                    jX += 75;
                    if (jX > contentX + 280) { jX = contentX; jY += 25; }
                }
            } else {
                int nextX = contentX;
                buttons.add(new CustomButton("Money", nextX, contentY, 55, 20, () -> { currentTopCategory = "bani"; currentTopPage = 1; isLoadingTop = true; EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage)); this.init(); }));
                nextX += 55;
                if (!isCreative()) {
                    buttons.add(new CustomButton("Kills", nextX, contentY, 50, 20, () -> { currentTopCategory = "kills"; currentTopPage = 1; isLoadingTop = true; EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage)); this.init(); }));
                    nextX += 55;
                    buttons.add(new CustomButton("Deaths", nextX, contentY, 58, 20, () -> { currentTopCategory = "decese"; currentTopPage = 1; isLoadingTop = true; EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage)); this.init(); }));
                    nextX += 60;
                }
                buttons.add(new CustomButton("Hours", nextX, contentY, 50, 20, () -> { currentTopCategory = "ore"; currentTopPage = 1; isLoadingTop = true; EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage)); this.init(); }));
                nextX += 50;
                buttons.add(new CustomButton("Claims", nextX, contentY, 65, 20, () -> { currentTopCategory = "claimuri"; currentTopPage = 1; isLoadingTop = true; EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage)); this.init(); }));
                nextX += 70;
                if (!isCreative()) {
                    buttons.add(new CustomButton("Jobs ⛏", nextX, contentY, 70, 20, () -> { isSelectingJobTop = true; this.init(); }));
                }

                buttons.add(new CustomButton("<", contentX, y + h - 30, 25, 20, () -> { if (currentTopPage > 1) { currentTopPage--; isLoadingTop = true; EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage)); this.init(); } }));
                buttons.add(new CustomButton(">", contentX + 380, y + h - 30, 25, 20, () -> { if (currentTopList.size() == EvoHubPacketHandler.TOP_ENTRIES_PER_PAGE) { currentTopPage++; isLoadingTop = true; EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestTop(currentTopCategory, currentTopPage)); this.init(); } }));
            }
        }
        else if (currentTab == 4) {
            buttons.add(new CustomButton("§aOpen Protection Map", contentX, contentY + 50, 180, 25, () -> {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.connection.sendCommand("claim");
                }
            }));
        }
        else if (currentTab == 5) {
            buttons.add(new CustomButton("<", contentX, contentY + 20, 20, 20, () -> {
                if (tpaPage > 0) { tpaPage--; this.init(); }
            }));
            buttons.add(new CustomButton(">", contentX + 260, contentY + 20, 20, 20, () -> {
                if ((tpaPage + 1) * 5 < onlinePlayers.size()) { tpaPage++; this.init(); }
            }));

            if (selectedTpaPlayer != null) {
                buttons.add(new CustomButton("§aTPA (You -> Them)", contentX + 20, contentY + 180, 125, 25, () -> {
                    org.evocraft.evoessentials.network.EssentialsPacketHandler.INSTANCE.sendToServer(new org.evocraft.evoessentials.network.C2S_TpaActionPacket("tpa", selectedTpaPlayer));
                    if (this.minecraft != null) this.minecraft.setScreen(null);
                }));
                buttons.add(new CustomButton("§eTPA Here (Them -> You)", contentX + 155, contentY + 180, 145, 25, () -> {
                    org.evocraft.evoessentials.network.EssentialsPacketHandler.INSTANCE.sendToServer(new org.evocraft.evoessentials.network.C2S_TpaActionPacket("tpahere", selectedTpaPlayer));
                    if (this.minecraft != null) this.minecraft.setScreen(null);
                }));
            }

            int listY = contentY + 45;
            int maxItems = Math.min((tpaPage + 1) * 5, onlinePlayers.size());

            for (int i = tpaPage * 5; i < maxItems; i++) {
                String pName = onlinePlayers.get(i);
                boolean isSelected = pName.equals(selectedTpaPlayer);
                String btnText = (isSelected ? "§a» " : "") + pName + (isSelected ? " §a«" : "");

                buttons.add(new CustomButton(btnText, contentX + 50, listY, 180, 22, () -> {
                    selectedTpaPlayer = pName; this.init();
                }));
                listY += 26;
            }
        }
        else if (currentTab == 6) {
            List<String> playerHomes = org.evocraft.evoessentials.client.ClientHomeData.playerHomes;

            homeNameInput = new EditBox(this.font, contentX, contentY + 50, 150, 20, Component.literal("Home Name"));
            this.addRenderableWidget(homeNameInput);

            buttons.add(new CustomButton("§aSet Home", contentX + 160, contentY + 50, 100, 20, () -> {
                String homeName = homeNameInput.getValue().trim();
                if (!homeName.isEmpty()) {
                    org.evocraft.evoessentials.network.EssentialsPacketHandler.INSTANCE.sendToServer(new org.evocraft.evoessentials.network.C2S_HomeActionPacket("set", homeName));
                    homeNameInput.setValue("");
                }
            }));

            buttons.add(new CustomButton("§cDelete Home", contentX + 270, contentY + 50, 100, 20, () -> {
                String homeName = homeNameInput.getValue().trim();
                if (!homeName.isEmpty()) {
                    org.evocraft.evoessentials.network.EssentialsPacketHandler.INSTANCE.sendToServer(new org.evocraft.evoessentials.network.C2S_HomeActionPacket("del", homeName));
                    homeNameInput.setValue("");
                }
            }));

            int hX = contentX + 10;
            int hY = contentY + 125;

            if (playerHomes != null) {
                for(String home : playerHomes) {
                    if (home == null || home.isBlank()) continue;
                    String display = home.length() > 9 ? home.substring(0, 7) + ".." : home;
                    buttons.add(new CustomButton("🏠 " + display, hX, hY, 85, 22, () -> {
                        org.evocraft.evoessentials.network.EssentialsPacketHandler.INSTANCE.sendToServer(new org.evocraft.evoessentials.network.C2S_HomeActionPacket("tp", home));
                    }));
                    hX += 90;
                    if(hX > contentX + 350) {
                        hX = contentX + 10; hY += 28;
                    }
                }
            }
        }
        else if (currentTab == 9) {
            if (!isEditingScoreboard) {
                buttons.add(new CustomButton(settingShowScoreboard ? "§aYES" : "§cNO", contentX + 180, contentY + 15, 45, 20, () -> { settingShowScoreboard = !settingShowScoreboard; saveHudSettings(); this.init(); }));
                buttons.add(new CustomButton("✎ Edit", contentX + 225, contentY + 15, 50, 20, () -> { if (this.minecraft != null) this.minecraft.setScreen(new ScoreboardLayoutScreen()); }));
                buttons.add(new CustomButton("Elements", contentX + 280, contentY + 15, 70, 20, () -> { isEditingScoreboard = true; this.init(); }));

                buttons.add(new CustomButton(settingAllowTPA ? "§aYES" : "§cNO", contentX + 180, contentY + 45, 45, 20, () -> { settingAllowTPA = !settingAllowTPA; org.evocraft.evocore.network.PacketHandler.INSTANCE.sendToServer(new org.evocraft.evocore.network.PacketHandler.C2S_SaveSettings(settingAllowTPA, settingAllowTrade)); this.init(); }));
                buttons.add(new CustomButton(settingAllowTrade ? "§aYES" : "§cNO", contentX + 180, contentY + 75, 45, 20, () -> { settingAllowTrade = !settingAllowTrade; org.evocraft.evocore.network.PacketHandler.INSTANCE.sendToServer(new org.evocraft.evocore.network.PacketHandler.C2S_SaveSettings(settingAllowTPA, settingAllowTrade)); this.init(); }));

                blockInput = new EditBox(this.font, contentX, contentY + 130, 100, 20, Component.literal("Player Name"));
                this.addRenderableWidget(blockInput);

                buttons.add(new CustomButton("Block", contentX + 105, contentY + 130, 70, 20, () -> { if (!blockInput.getValue().isEmpty()) { org.evocraft.evocore.network.PacketHandler.INSTANCE.sendToServer(new org.evocraft.evocore.network.PacketHandler.C2S_BlockPlayer(blockInput.getValue(), true)); blockInput.setValue(""); } }));
                buttons.add(new CustomButton("Unblock", contentX + 180, contentY + 130, 80, 20, () -> { if (!blockInput.getValue().isEmpty()) { org.evocraft.evocore.network.PacketHandler.INSTANCE.sendToServer(new org.evocraft.evocore.network.PacketHandler.C2S_BlockPlayer(blockInput.getValue(), false)); blockInput.setValue(""); } }));
            } else {
                buttons.add(new CustomButton("< Back", contentX, contentY - 10, 60, 20, () -> { isEditingScoreboard = false; this.init(); }));
                int ey = contentY + 15;
                buttons.add(new CustomButton(sbShowHeader ? "§aYES" : "§cNO", contentX + 130, ey, 45, 20, () -> { sbShowHeader = !sbShowHeader; saveHudSettings(); this.init(); })); ey += 22;
                buttons.add(new CustomButton(sbShowTu ? "§aYES" : "§cNO", contentX + 130, ey, 45, 20, () -> { sbShowTu = !sbShowTu; saveHudSettings(); this.init(); })); ey += 22;
                buttons.add(new CustomButton(sbShowRank ? "§aYES" : "§cNO", contentX + 130, ey, 45, 20, () -> { sbShowRank = !sbShowRank; saveHudSettings(); this.init(); })); ey += 22;
                buttons.add(new CustomButton(sbShowMoney ? "§aYES" : "§cNO", contentX + 130, ey, 45, 20, () -> { sbShowMoney = !sbShowMoney; saveHudSettings(); this.init(); })); ey += 22;
                buttons.add(new CustomButton(sbShowPlaytime ? "§aYES" : "§cNO", contentX + 130, ey, 45, 20, () -> { sbShowPlaytime = !sbShowPlaytime; saveHudSettings(); this.init(); })); ey += 22;

                int ex2 = contentX + 180; int ey2 = contentY + 15;
                buttons.add(new CustomButton(sbShowCombat ? "§aYES" : "§cNO", ex2 + 80, ey2, 45, 20, () -> { sbShowCombat = !sbShowCombat; saveHudSettings(); this.init(); })); ey2 += 22;
                buttons.add(new CustomButton(sbShowParty ? "§aYES" : "§cNO", ex2 + 80, ey2, 45, 20, () -> { sbShowParty = !sbShowParty; saveHudSettings(); this.init(); })); ey2 += 22;
                buttons.add(new CustomButton(sbShowJobs ? "§aYES" : "§cNO", ex2 + 80, ey2, 45, 20, () -> { sbShowJobs = !sbShowJobs; saveHudSettings(); this.init(); })); ey2 += 22;
                buttons.add(new CustomButton(sbShowInfo ? "§aYES" : "§cNO", ex2 + 80, ey2, 45, 20, () -> { sbShowInfo = !sbShowInfo; saveHudSettings(); this.init(); })); ey2 += 22;
                buttons.add(new CustomButton(sbShowVote ? "§aYES" : "§cNO", ex2 + 80, ey2, 45, 20, () -> { sbShowVote = !sbShowVote; saveHudSettings(); this.init(); }));

                buttons.add(new CustomButton("✎ Move on Screen", contentX + 130, contentY + 132, 140, 20, () -> { if (this.minecraft != null) this.minecraft.setScreen(new ScoreboardLayoutScreen()); }));
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        float scale = getScale();
        g.pose().pushPose(); g.pose().scale(scale, scale, 1.0f);
        int smx = (int)(mouseX / scale); int smy = (int)(mouseY / scale);
        int sw = (int)(this.width / scale); int sh = (int)(this.height / scale);

        // ==== RANDARE MENIU PRINCIPAL (ACASĂ) ====
        if (currentTab == -1) {
            int titleY = Math.max(18, getMainMenuStartY(sh, getVisibleMainTabs().size()) - 48);
            g.drawCenteredString(this.font, "§l⚡ EVO CRAFT HUB", sw / 2, titleY, 0xFF83B755);
            g.drawCenteredString(this.font, "§7Choose a section to continue", sw / 2, titleY + 15, 0xAAAAAA);
            for (CustomButton b : buttons) { b.render(g, smx, smy, this.font); }
            g.pose().popPose();
            return;
        }

        // ==== RANDARE INTERIOR FIȘE ====
        int w = 550; int h = 350;
        int x = (sw - w) / 2; int y = (sh - h) / 2;
        int menuWidth = 120;

        // Background general mai modern, glassmorphism dark
        fillRounded(g, x, y, w, h, 0xEE050805);
        outlineRounded(g, x, y, w, h, 0xFF3A592D);

        // Delimitator meniu stânga subtil
        g.fill(x + menuWidth, y + 10, x + menuWidth + 1, y + h - 10, 0xFF3A592D);

        int contentX = x + menuWidth + 15; int contentY = y + 30;

        if (currentTab == 0) renderTabServere(g, contentX, contentY);
        else if (currentTab == 1) renderTabProfil(g, contentX, contentY, smx, smy);
        else if (currentTab == 2 && !isCreative()) renderTabJoburi(g, contentX, contentY);
        else if (currentTab == 3) renderTabTopuri(g, contentX, contentY, x + menuWidth, y, w - menuWidth, h);
        else if (currentTab == 4) {
            g.drawString(this.font, "§a§lProtection Management", contentX, contentY, 0xFFFFFF, false);
            g.drawString(this.font, "§7View and manage your protected chunks here", contentX, contentY + 20, 0xAAAAAA, false);
        } else if (currentTab == 5) {
            g.drawString(this.font, "§d§lTeleport System (TPA)", contentX, contentY, 0xFFFFFF, false);
            if (onlinePlayers.isEmpty()) {
                g.drawCenteredString(this.font, "§cNobody is online!", contentX + 140, contentY + 100, 0xFFFFFF);
            } else {
                g.drawCenteredString(this.font, "Page " + (tpaPage + 1), contentX + 140, contentY + 25, 0xAAAAAA);
            }
            fillRounded(g, contentX, contentY + 42, 280, 135, 0xAA141C14);
            outlineRounded(g, contentX, contentY + 42, 280, 135, 0xFF3A592D);
        } else if (currentTab == 6) {
            int cCount = org.evocraft.evoessentials.client.ClientHomeData.currentHomesCount;
            int mCount = org.evocraft.evoessentials.client.ClientHomeData.maxHomesCount;
            g.drawString(this.font, "§e§lMy Homes §7(" + cCount + "/" + mCount + ")", contentX, contentY, 0xFFFFFF, false);
            g.drawString(this.font, "§7Type a name below to save a home.", contentX, contentY + 20, 0xAAAAAA, false);
            fillRounded(g, contentX, contentY + 100, 380, 160, 0xAA141C14);
            outlineRounded(g, contentX, contentY + 100, 380, 160, 0xFF3A592D);
        }
        else if (currentTab == 9) {
            if (!isEditingScoreboard) {
                g.drawString(this.font, "§8§lPersonal Settings", contentX, contentY, 0xFFFFFF, false);
                g.drawString(this.font, "Show Scoreboard", contentX, contentY + 20, 0xFFFFFF, false);
                g.drawString(this.font, "Allow TPA Requests", contentX, contentY + 50, 0xFFFFFF, false);
                g.drawString(this.font, "Allow Trades", contentX, contentY + 80, 0xFFFFFF, false);
                g.drawString(this.font, "§c§lBlock List", contentX, contentY + 105, 0xFFFFFF, false);
                g.drawString(this.font, "Type a name to block them from TPA/Trade:", contentX, contentY + 118, 0xAAAAAA, false);
            } else {
                g.drawString(this.font, "§e§lVisible scoreboard elements:", contentX + 80, contentY - 5, 0xFFFFFF, false);
                int ey = contentY + 20;
                g.drawString(this.font, "Server", contentX, ey, 0xFFFFFF, false); ey += 22;
                g.drawString(this.font, "My Name", contentX, ey, 0xFFFFFF, false); ey += 22;
                g.drawString(this.font, "Rank", contentX, ey, 0xFFFFFF, false); ey += 22;
                g.drawString(this.font, "Money", contentX, ey, 0xFFFFFF, false); ey += 22;
                g.drawString(this.font, "Playtime", contentX, ey, 0xFFFFFF, false);
                int ex2 = contentX + 180; int ey2 = contentY + 20;
                g.drawString(this.font, "Kills/Deaths", ex2, ey2, 0xFFFFFF, false); ey2 += 22;
                g.drawString(this.font, "Party", ex2, ey2, 0xFFFFFF, false); ey2 += 22;
                g.drawString(this.font, "Jobs Section", ex2, ey2, 0xFFFFFF, false); ey2 += 22;
                g.drawString(this.font, "Server Info", ex2, ey2, 0xFFFFFF, false); ey2 += 22;
                g.drawString(this.font, "Vote", ex2, ey2, 0xFFFFFF, false);
            }
        }

        for (CustomButton b : buttons) { b.render(g, smx, smy, this.font); }
        super.render(g, smx, smy, partialTick);
        g.pose().popPose();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = getScale(); int smx = (int)(mouseX / scale); int smy = (int)(mouseY / scale);
        for (CustomButton b : buttons) { if (b.checkClick(smx, smy)) return true; }
        return super.mouseClicked(mouseX / scale, mouseY / scale, button);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { float s = getScale(); return super.mouseReleased(mouseX / s, mouseY / s, button); }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { float s = getScale(); return super.mouseDragged(mouseX / s, mouseY / s, button, dragX / s, dragY / s); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) { float s = getScale(); return super.mouseScrolled(mouseX / s, mouseY / s, delta); }

    private void renderTabServere(GuiGraphics g, int x, int y) {
        g.drawString(this.font, "§lSelect Section", x, y, 0xFFFFFF, false);
        g.drawString(this.font, "You are currently on: §a" + currentServerName, x, y + 20, 0xAAAAAA, false);
    }

    private void renderTabProfil(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        fillRounded(g, x, y, 90, 140, 0xAA141C14);
        outlineRounded(g, x, y, 90, 140, 0xFF3A592D);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 45, y + 125, 55, (float)(x + 45 - mouseX), (float)(y + 80 - mouseY), this.minecraft.player);
        }
        int infoX = x + 100;
        String name = (this.minecraft != null && this.minecraft.player != null) ? this.minecraft.player.getGameProfile().getName() : "Unknown";

        g.drawString(this.font, "Name: §f" + name, infoX, y, 0xAAAAAA, false);
        g.drawString(this.font, "Rank: §f" + ClientStatsData.rank, infoX, y + 15, 0xAAAAAA, false);
        g.drawString(this.font, "Money: §e" + EvoCurrencyFormatter.formatWithCurrency(ClientBalanceData.getBalance()), infoX, y + 30, 0xAAAAAA, false);

        int totalSecs = ClientStatsData.playtimeSeconds;
        String timeStr = (totalSecs < 3600) ? (totalSecs / 60) + " min" : (totalSecs / 3600) + " HOURS";

        g.drawString(this.font, "Playtime: §3" + timeStr, infoX, y + 45, 0xAAAAAA, false);

        if (!isCreative()) {
            g.drawString(this.font, "§cCombat", infoX, y + 70, 0xFFFFFF, false);
            g.drawString(this.font, "Kills: §f" + ClientStatsData.kills + " §8| §7Deaths: §f" + ClientStatsData.deaths, infoX, y + 85, 0xAAAAAA, false);

            double kd = ClientStatsData.deaths == 0 ? ClientStatsData.kills : (double) ClientStatsData.kills / ClientStatsData.deaths;
            g.drawString(this.font, "K/D Ratio: §f" + String.format("%.2f", kd), infoX, y + 100, 0xAAAAAA, false);

            int keyY = y + 125;
            g.drawString(this.font, "§d§lCrate Keys:", infoX, keyY, 0xFFFFFF, false);
            keyY += 15;

            if (ClientCratesData.playerKeys.isEmpty()) {
                g.drawString(this.font, "§7You do not have any keys right now.", infoX, keyY, 0xAAAAAA, false);
            } else {
                for (Map.Entry<String, Integer> entry : ClientCratesData.playerKeys.entrySet()) {
                    String rawKey = entry.getKey();
                    if (rawKey == null || rawKey.isEmpty()) continue;
                    String crateType = rawKey.substring(0, 1).toUpperCase() + rawKey.substring(1);
                    int amount = entry.getValue() != null ? entry.getValue() : 0;
                    g.drawString(this.font, "§8• §f" + crateType + ": §a" + amount + " pcs.", infoX, keyY, 0xFFFFFF, false);
                    keyY += 12;
                }
            }
        }
    }

    private void renderTabJoburi(GuiGraphics g, int x, int y) {
        g.drawString(this.font, "§bYour Career - All Jobs", x, y + 8, 0xFFFFFF, false);

        String[] allJobIds = {"miner", "woodcutter", "digger", "hunter", "farmer", "fisherman", "builder", "crafter", "trader", "somer"};
        String[] displayNames = {"Miner", "Lumberjack", "Digger", "Hunter", "Farmer", "Fisherman", "Builder", "Crafter", "Trader", "Unemployed"};
        net.minecraft.world.item.ItemStack[] allJobIcons = {
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SHOVEL), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BRICKS), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAINTING)
        };

        List<JobInfo> activeJobs = ClientJobsData.activeJobs;
        int startX = x + 10; int startY = y + 25;

        for (int i = 0; i < allJobIds.length; i++) {
            String jId = allJobIds[i]; String dName = displayNames[i]; net.minecraft.world.item.ItemStack jIcon = allJobIcons[i];
            long jLevel = 0L; double jXp = 0; double jReq = 100; boolean jActive = false;

            for (JobInfo info : activeJobs) {
                if (info.icon.getItem() == jIcon.getItem()) { jLevel = info.level; jXp = info.currentXp; jReq = info.requiredXp; jActive = info.isActive; break; }
            }

            int col = i % 2; int row = i / 2;
            int drawX = startX + col * 200; int drawY = startY + row * 52;
            g.renderItem(jIcon, drawX, drawY + 4);

            String color = jActive ? "§a" : (jLevel > 0 ? "§f" : "§7");
            g.drawString(this.font, color + "§l" + dName + " §8[Lv." + jLevel + "]", drawX + 22, drawY, 0xFFFFFF, false);

            JobManager.RankInfo rankInfo = JobManager.getRankInfo(jId, jLevel);
            String rankColor = jLevel > 0 ? "§6" : "§8";
            g.drawString(this.font, rankColor + rankInfo.title, drawX + 22, drawY + 11, 0xFFFFFF, false);

            if (jActive) { g.pose().pushPose(); g.pose().translate(drawX + 22, drawY + 23, 0); g.pose().scale(0.65f, 0.65f, 1.0f); g.drawString(this.font, "§2■ ACTIVE JOB", 0, 0, 0xFFFFFF, false); g.pose().popPose(); }

            if (rankInfo.boostPercent > 0) {
                String boostTxt = "§a+" + (int)rankInfo.boostPercent + "% Money";
                if (jLevel < 100) boostTxt += " §7(Next: Lv." + rankInfo.nextLevelReq + ")"; else boostTxt += " §e(MAX)";
                g.pose().pushPose(); g.pose().translate(drawX + 175, drawY + 23, 0); g.pose().scale(0.6f, 0.6f, 0.6f); g.drawString(this.font, boostTxt, -this.font.width(boostTxt), 0, 0xFFFFFF, false); g.pose().popPose();
            } else if (jLevel < 10) {
                g.pose().pushPose(); g.pose().translate(drawX + 175, drawY + 23, 0); g.pose().scale(0.6f, 0.6f, 0.6f); g.drawString(this.font, "§7Boost at Lv.10", -this.font.width("§7Boost at Lv.10"), 0, 0xFFFFFF, false); g.pose().popPose();
            }

            int barY = drawY + 31; g.renderOutline(drawX - 1, barY - 1, 182, 6, 0xFF83B755);
            float progress = jReq > 0.0 ? (float) (jXp / jReq) : 0.0f;
            progress = Math.min(1.0f, Math.max(0.0f, progress));
            g.fill(drawX, barY, drawX + (int)(180 * progress), barY + 4, 0xFF83B755);

            String xpTxt = (int)jXp + "/" + (int)jReq; g.pose().pushPose(); g.pose().translate(drawX + 180, barY + 6, 0); g.pose().scale(0.6f, 0.6f, 0.6f); g.drawString(this.font, xpTxt, -this.font.width(xpTxt), 0, 0xAAAAAA, false); g.pose().popPose();
        }
    }

    private void renderTabTopuri(GuiGraphics g, int x, int y, int areaX, int areaY, int areaW, int areaH) {
        if (isSelectingJobTop) return;
        String cleanCategory = currentTopCategory != null ? currentTopCategory.replace("job_", "") : "bani";
        String title = cleanCategory.isEmpty() ? "Top" : "Top " + cleanCategory.substring(0, 1).toUpperCase() + cleanCategory.substring(1);
        g.drawCenteredString(this.font, "§l" + title, areaX + (areaW / 2), y + 30, 0xFF83B755);
        g.drawCenteredString(this.font, "Page " + currentTopPage, areaX + (areaW / 2), areaY + areaH - 25, 0xAAAAAA);
        int listY = y + 55;

        if (isLoadingTop) {
            g.drawCenteredString(this.font, "§eLoading data...", areaX + (areaW / 2), listY + 20, 0xFFFFFF);
        } else if (currentTopList.isEmpty()) {
            g.drawCenteredString(this.font, "§cNo results!", areaX + (areaW / 2), listY + 20, 0xFFFFFF);
        } else {
            int displayedIndex = (Math.max(1, currentTopPage) - 1) * EvoHubPacketHandler.TOP_ENTRIES_PER_PAGE + 1;
            int displayedCount = 0;
            for (TopEntry entry : currentTopList) {
                if (entry == null || entry.name == null || entry.name.isBlank() || entry.name.startsWith("§") || entry.name.contains("00000000") || entry.name.toLowerCase().contains("spawn")) {
                    continue;
                }

                String valStr = currentTopCategory.equals("ore") ? String.format("%,.1f Hours", entry.value) : currentTopCategory.equals("bani") ? EvoCurrencyFormatter.formatWithCurrency(entry.value) : currentTopCategory.startsWith("job_") ? "Level " + (int)entry.value : String.valueOf((int)entry.value);
                String color = displayedIndex == 1 ? "§e§l" : (displayedIndex == 2 ? "§f§l" : (displayedIndex == 3 ? "§6§l" : "§7"));
                g.drawString(this.font, color + displayedIndex + ". " + entry.name, x, listY, 0xFFFFFF, false);
                int valWidth = this.font.width(valStr); g.drawString(this.font, "§a" + valStr, x + areaW - 25 - valWidth, listY, 0xFFFFFF, false);
                listY += 20;
                displayedIndex++;
                displayedCount++;

                if (displayedCount >= EvoHubPacketHandler.TOP_ENTRIES_PER_PAGE) break;
            }
        }
    }

    // CLASA DE BAZĂ PENTRU BUTOANE
    class CustomButton {
        String text; int x, y, w, h; Runnable action; boolean visible = true;
        public CustomButton(String text, int x, int y, int w, int h, Runnable action) { this.text = text; this.x = x; this.y = y; this.w = w; this.h = h; this.action = action; }
        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            if(!visible) return;
            boolean hover = (mx >= x && mx <= x + w && my >= y && my <= y + h);
            fillRounded(g, x, y, w, h, 0xAA141C14);
            outlineRounded(g, x, y, w, h, hover ? 0xFF6C9945 : 0xFF3A592D);
            g.pose().pushPose(); g.pose().translate(x + w / 2f, y + (h - 8) / 2f, 0);
            g.drawCenteredString(font, text, 0, 0, hover ? 0xFFFFFF : 0xFFDDDDDD); g.pose().popPose();
        }
        public boolean checkClick(int mx, int my) { if (visible && mx >= x && mx <= x + w && my >= y && my <= y + h) { action.run(); return true; } return false; }
    }

    // BUTOANE PENTRU SIDEBAR (Stânga - mai elegante)
    class SidebarButton extends CustomButton {
        boolean isSelected;
        public SidebarButton(String text, int x, int y, int w, int h, boolean isSelected, Runnable action) {
            super(text, x, y, w, h, action);
            this.isSelected = isSelected;
        }
        @Override
        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            if(!visible) return;
            boolean hover = isSelected || (mx >= x && mx <= x + w && my >= y && my <= y + h);

            // Design fluid pentru stânga (fără contur, doar o bară la selecție/hover)
            fillRounded(g, x, y, w, h, hover ? 0x6683B755 : 0x00000000);
            if (hover) g.fill(x, y + 2, x + 2, y + h - 2, 0xFF83B755); // Bară accent

            g.drawString(font, text, x + 8, y + (h - 8) / 2, hover ? 0xFFFFFF : 0xAAAAAA, false);
        }
    }

    // BUTOANE MARI CU ICONIȚE (Pentru ecranul Acasă / Servere)
    class IconButton extends CustomButton {
        ItemStack icon;
        public IconButton(String text, int x, int y, int w, int h, ItemStack icon, Runnable action) {
            super(text, x, y, w, h, action);
            this.icon = icon;
        }
        @Override
        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            if(!visible) return;
            boolean hover = (mx >= x && mx <= x + w && my >= y && my <= y + h);
            EvoHubTheme.drawIconCard(g, font, text, getMainCardSubtitle(text), icon, x, y, w, h, hover);
        }
    }

    private String getMainCardSubtitle(String text) {
        return switch (text) {
            case "Servers" -> "Choose world";
            case "My Profile" -> "Stats";
            case "My Career" -> "Jobs";
            case "Leaderboards" -> "Top lists";
            case "Protections" -> "Claims";
            case "Teleport" -> "TPA";
            case "My Homes" -> "Homes";
            case "Kits" -> "Packs";
            case "Warps" -> "Locations";
            case "Settings" -> "Preferences";
            case "Crates" -> "Keys";
            default -> "";
        };
    }
}
