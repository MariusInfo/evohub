package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evocraft.evohub.client.EvoHubClientSettings;
import org.evocraft.evohub.client.ScoreboardOverlay;
import org.evocraft.evohub.client.ScoreboardOverlay.HudElement;
import org.evocraft.evohub.client.ScoreboardOverlay.HudElementBounds;

import java.util.List;

public class ScoreboardLayoutScreen extends Screen {

    private HudElement selected = HudElement.HEADER;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public ScoreboardLayoutScreen() {
        super(Component.literal("Scoreboard Editor"));
    }

    @Override
    protected void init() {
        int y = 10;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            EvoHubClientSettings.save();
            if (this.minecraft != null) this.minecraft.setScreen(new EvoHubScreen(9));
        }).bounds(10, y, 55, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Reset element"), button -> {
            if (selected != null) EvoHubClientSettings.resetHudElement(selected.id);
        }).bounds(70, y, 95, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Reset all"), button -> EvoHubClientSettings.resetHudLayout()).bounds(170, y, 85, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Scale -"), button -> adjustSelectedScale(-10)).bounds(270, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Scale +"), button -> adjustSelectedScale(10)).bounds(335, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Width -"), button -> adjustSelectedWidth(-10)).bounds(410, y, 65, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Width +"), button -> adjustSelectedWidth(10)).bounds(480, y, 65, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) return;

        ScoreboardOverlay.updateOnlinePlayers(this.minecraft);
        ensureSelectedVisible();
        ScoreboardOverlay.renderElements(g, this.minecraft, this.width, this.height, true, selected);

        g.fill(6, 6, Math.min(this.width - 6, 560), 58, 0xDD050805);
        drawOutline(g, 6, 6, Math.min(this.width - 12, 554), 52, 0xFF3A592D);
        String selectedName = selected != null ? selected.label : "None";
        EvoHubClientSettings.HudElementSettings settings = selected != null ? EvoHubClientSettings.getHudElement(selected.id) : null;
        String sizeText = settings != null ? "Scale: " + settings.scalePercent + "%  Width: " + settings.widthPercent + "%" : "";
        g.drawString(this.font, "§a§lScoreboard Editor §7- drag the selected module", 12, 36, 0xFFFFFF, false);
        g.drawString(this.font, "§fSelected: §e" + selectedName + " §8| §7" + sizeText, 12, 48, 0xFFFFFF, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.minecraft == null || button != 0) return false;

        List<HudElementBounds> bounds = ScoreboardOverlay.getVisibleElementBounds(this.minecraft, this.width, this.height);
        for (int i = bounds.size() - 1; i >= 0; i--) {
            HudElementBounds elementBounds = bounds.get(i);
            if (elementBounds.contains(mouseX, mouseY)) {
                selected = elementBounds.element;
                dragging = true;
                dragOffsetX = mouseX - elementBounds.x;
                dragOffsetY = mouseY - elementBounds.y;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging || selected == null || this.minecraft == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        HudElementBounds bounds = ScoreboardOverlay.findBounds(this.minecraft, this.width, this.height, selected);
        if (bounds == null) return false;

        int newX = clamp((int) Math.round(mouseX - dragOffsetX), 0, Math.max(0, this.width - bounds.width));
        int newY = clamp((int) Math.round(mouseY - dragOffsetY), 0, Math.max(0, this.height - bounds.height));
        EvoHubClientSettings.HudElementSettings settings = EvoHubClientSettings.getHudElement(selected.id);
        settings.x = newX / (double) Math.max(1, this.width);
        settings.y = newY / (double) Math.max(1, this.height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            EvoHubClientSettings.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selected != null) {
            adjustSelectedScale(delta > 0 ? 5 : -5);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        EvoHubClientSettings.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void ensureSelectedVisible() {
        if (this.minecraft == null) return;
        List<HudElementBounds> bounds = ScoreboardOverlay.getVisibleElementBounds(this.minecraft, this.width, this.height);
        for (HudElementBounds elementBounds : bounds) {
            if (elementBounds.element == selected) return;
        }
        selected = bounds.isEmpty() ? null : bounds.get(0).element;
    }

    private void adjustSelectedScale(int delta) {
        if (selected == null) return;
        EvoHubClientSettings.HudElementSettings settings = EvoHubClientSettings.getHudElement(selected.id);
        settings.scalePercent = clamp(settings.scalePercent + delta, 60, 180);
        EvoHubClientSettings.save();
    }

    private void adjustSelectedWidth(int delta) {
        if (selected == null) return;
        EvoHubClientSettings.HudElementSettings settings = EvoHubClientSettings.getHudElement(selected.id);
        settings.widthPercent = clamp(settings.widthPercent + delta, 70, 160);
        EvoHubClientSettings.save();
    }

    private void drawOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y, x + 1, y + height, color);
        g.fill(x + width - 1, y, x + width, y + height, color);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
