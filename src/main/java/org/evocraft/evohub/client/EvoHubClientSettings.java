package org.evocraft.evohub.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evohub.gui.EvoHubScreen;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

public class EvoHubClientSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FMLPaths.CONFIGDIR.get().resolve("evohub_client_settings.json").toFile();

    public static void load() {
        Settings settings = new Settings();
        if (FILE.exists()) {
            try (Reader reader = new FileReader(FILE)) {
                Settings loaded = GSON.fromJson(reader, Settings.class);
                if (loaded != null) settings = loaded;
            } catch (Exception e) {
                System.out.println("[EvoHub] Could not load client HUD settings. Using defaults.");
            }
        }

        EvoHubScreen.settingShowScoreboard = settings.showScoreboard;
        EvoHubScreen.sbShowTu = settings.showName;
        EvoHubScreen.sbShowRank = settings.showRank;
        EvoHubScreen.sbShowMoney = settings.showMoney;
        EvoHubScreen.sbShowPlaytime = settings.showPlaytime;
        EvoHubScreen.sbShowCombat = settings.showCombat;
        EvoHubScreen.sbShowJobs = settings.showJobs;
        EvoHubScreen.sbShowInfo = settings.showInfo;
        EvoHubScreen.sbPosition = clamp(settings.position, 0, EvoHubScreen.SB_POSITION_COUNT - 1);
        EvoHubScreen.sbScalePercent = clamp(settings.scalePercent, 70, 130);
        EvoHubScreen.sbWidthMode = clamp(settings.widthMode, 0, EvoHubScreen.SB_WIDTH_MODE_COUNT - 1);
    }

    public static void save() {
        Settings settings = new Settings();
        settings.showScoreboard = EvoHubScreen.settingShowScoreboard;
        settings.showName = EvoHubScreen.sbShowTu;
        settings.showRank = EvoHubScreen.sbShowRank;
        settings.showMoney = EvoHubScreen.sbShowMoney;
        settings.showPlaytime = EvoHubScreen.sbShowPlaytime;
        settings.showCombat = EvoHubScreen.sbShowCombat;
        settings.showJobs = EvoHubScreen.sbShowJobs;
        settings.showInfo = EvoHubScreen.sbShowInfo;
        settings.position = EvoHubScreen.sbPosition;
        settings.scalePercent = EvoHubScreen.sbScalePercent;
        settings.widthMode = EvoHubScreen.sbWidthMode;

        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (Writer writer = new FileWriter(FILE)) {
                GSON.toJson(settings, writer);
            }
        } catch (Exception e) {
            System.out.println("[EvoHub] Could not save client HUD settings.");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class Settings {
        boolean showScoreboard = true;
        boolean showName = true;
        boolean showRank = true;
        boolean showMoney = true;
        boolean showPlaytime = true;
        boolean showCombat = true;
        boolean showJobs = false;
        boolean showInfo = true;
        int position = EvoHubScreen.SB_RIGHT_CENTER;
        int scalePercent = 100;
        int widthMode = EvoHubScreen.SB_WIDTH_NORMAL;
    }
}
