package com.tileman;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

class TilemanBankInfoOverlay extends OverlayPanel {
    private final Client client;
    private final TilemanModeConfig config;
    private final TilemanModePlugin plugin;

    private static final String SLOTS_USED_TEXT = "Slots in Use:";
    private static final String SLOTS_UNLOCKED_TEXT = "Bank Slots Unlocked:";
    private static final String FOR_NEXT_SLOT_TEXT = "Tiles For Next Slot:";

    @Inject
    private TilemanBankInfoOverlay(Client client, TilemanModeConfig config, TilemanModePlugin plugin) {
        super(plugin);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_RIGHT);
        setPriority(OverlayPriority.MED);
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Tileman Mode overlay"));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        TilemanProfileManager profileManager = plugin.getProfileManager();
        if (!profileManager.hasActiveProfile() || !profileManager.isTilesUnlockBankSlots()) {
            return super.render(graphics);
        }

        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        if (bank == null) {
            return super.render(graphics);
        }

        int unlockedSlots = profileManager.getUnlockedBankSlots();
        String unlockedSlotsString = Util.addCommasToNumber(unlockedSlots);

        int tilesForNextSlot = profileManager.getTilesForNextBankSlot();
        String tilesForNextSlotString = Util.addCommasToNumber(tilesForNextSlot);

        int itemCount = bank.getItems().length;

        String itemCountText = String.valueOf(itemCount);
        boolean deficit = itemCount > unlockedSlots;
        if (deficit) {
            itemCountText += " (" + (unlockedSlots - itemCount) + ")";
        }

        LineComponent.LineComponentBuilder slotsInUseBuilder = LineComponent.builder().left(SLOTS_USED_TEXT).right(itemCountText);
        if (deficit) {
            slotsInUseBuilder.rightColor(Color.red);
        }
        panelComponent.getChildren().add(slotsInUseBuilder.build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(SLOTS_UNLOCKED_TEXT)
                .right(unlockedSlotsString)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(FOR_NEXT_SLOT_TEXT)
                .right(tilesForNextSlotString)
                .build());

        panelComponent.setPreferredSize(new Dimension(
                Util.getLongestStringWidth(graphics, SLOTS_UNLOCKED_TEXT, FOR_NEXT_SLOT_TEXT)
                        + Util.getLongestStringWidth(graphics, unlockedSlotsString, tilesForNextSlotString),
                0));

        return super.render(graphics);
    }
}
