package com.tileman.runelite;

import com.tileman.managers.*;
import com.tileman.multiplayer.GroupTilemanProfile;
import com.tileman.multiplayer.TilemanMultiplayerService;
import com.tileman.TilemanGameMode;
import com.tileman.TilemanProfile;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.util.ArrayList;
import java.util.List;

import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class TilemanPluginPanel extends PluginPanel {

    private final int PORT = 7777;

    private static final int MIN_EXP_PER_TILE = 250;
    private static final int MAX_EXP_PER_TILE = 100000;

    private static final int MIN_TILE_OFFSET = Integer.MIN_VALUE;
    private static final int MAX_TILE_OFFSET = Integer.MAX_VALUE;

    private final TilemanModePlugin plugin;
    private final TilemanStateManager stateManager;
    private final Client client;
    private final RunelitePersistenceManager persistenceManager;

    private boolean showExportInfo = false;
    private boolean gameModeOpen = false;
    private boolean advancedOpen = false;
    private boolean isMultiplayerOpen = false;

    public TilemanPluginPanel(TilemanModePlugin plugin, Client client, TilemanStateManager stateManager, RunelitePersistenceManager persistenceManager) {
        this.plugin = plugin;
        this.client = client;
        this.stateManager = stateManager;
        this.persistenceManager = persistenceManager;
        build();
    }

    @Override
    public void onActivate() {
        rebuild();
    }

    public void rebuild() {
        SwingUtilities.invokeLater(() -> {
            build();
            revalidate();
            repaint();
        });
    }

    private void build() {
        this.removeAll();
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        {
            JPanel titlePanel = new JPanel(new BorderLayout());
            titlePanel.setBorder(BorderFactory.createLineBorder(Color.black));
            titlePanel.setBorder(new EmptyBorder(1, 0, 10, 0));

            JLabel title = new JLabel();
            title.setText("Tileman Mode");
            title.setForeground(Color.WHITE);
            titlePanel.add(title, BorderLayout.NORTH);

            this.add(titlePanel, BorderLayout.NORTH);
        }

        {
            JPanel bodyPanel = new JPanel();
            addVerticalLayout(bodyPanel);

            bodyPanel.add(buildProfilePanel());
            bodyPanel.add(buildGameRulesPanel());
            bodyPanel.add(buildMultiplayerPanel());
            bodyPanel.add(buildAdvancedOptionsPanel());

            this.add(bodyPanel, BorderLayout.CENTER);
        }
    }

    private JPanel buildProfilePanel() {
        TilemanProfile activeProfile = stateManager.getActiveProfile();
        boolean isLoggedIn = plugin.isLoggedIn();

        JPanel profilePanel = new JPanel();
        profilePanel.setBorder(BorderFactory.createLineBorder(Color.black));
        addVerticalLayout(profilePanel);
        {
            JLabel profileLabel = new JLabel();
            profileLabel.setAlignmentX(CENTER_ALIGNMENT);

            addSpacer(profilePanel);

            if (!isLoggedIn) {
                profileLabel.setText("Login to start");
            } else {
                if (!activeProfile.equals(TilemanProfile.NONE)) {
                    profileLabel.setText(activeProfile.getProfileName());
                } else {
                    profileLabel.setText("Create a profile to start");
                }
            }
            profilePanel.add(profileLabel);

            if (!stateManager.getActiveGroupProfile().equals(GroupTilemanProfile.NONE)) {
                JLabel groupLabel = new JLabel(stateManager.getActiveGroupProfile().getGroupName());
                groupLabel.setAlignmentX(CENTER_ALIGNMENT);
                profilePanel.add(groupLabel);
            }

            if (activeProfile.equals(TilemanProfile.NONE) && isLoggedIn) {
                JButton createProfileButton = new JButton("Create");
                createProfileButton.setAlignmentX(CENTER_ALIGNMENT);
                createProfileButton.addActionListener(l -> {
                    TilemanProfile profile = TilemanProfile.NONE;

                    String rsn = client.getLocalPlayer().getName();

                    Object[] options = new Object[] {"New Profile", "Import Existing Data"};
                    int choice = JOptionPane.showOptionDialog(null, "Create a profile:", "Create Profile", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, null);

                    if (choice == 0) {
                        profile = TilemanProfileUtil.createProfile(client.getAccountHash(), rsn, persistenceManager);
                    } else if (choice == 1) {
                        options = new Object[] {"Import Old Tile Data", "Import Ground Marker Data", "Manual Import"};
                        choice = JOptionPane.showOptionDialog(null, "Choose how to import existing tile data:", "Import Existing Data", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, null);
                        if (choice == 0) {
                            profile = TilemanProfileUtil.createProfileWithLegacyData(client.getAccountHash(), rsn, persistenceManager);
                        } else if (choice == 1) {
                            profile = TilemanProfileUtil.createProfileWithGroundMarkerData(client.getAccountHash(), rsn, persistenceManager);
                        } else if (choice == 2) {
                            showProfileImportPanel();
                            return;
                        }
                    }
                    stateManager.setActiveProfile(profile);
                });
                profilePanel.add(createProfileButton);
            }
            addSpacer(profilePanel);
        }
        return profilePanel;
    }

    private JPanel buildGameRulesPanel() {
        // Callback queue, so we can properly manage enabling/disabling interactions without worrying about component build order.
        List<Runnable> callbacks = new ArrayList<>();
        boolean hasActiveProfile = !stateManager.getActiveProfile().equals(TilemanProfile.NONE);

        TilemanGameRulesManager gameRules = stateManager.getGameRulesManager();

        JPanel gameRulesPanel = new JPanel();
        gameRulesPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        {
            gameRulesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            addVerticalLayout(gameRulesPanel);
            callbacks.add(() -> setJComponentEnabled(gameRulesPanel, hasActiveProfile));

            {
                JPanel gameModeSelectPanel = new JPanel();
                addVerticalLayout(gameModeSelectPanel);

                JPanel gameModeDropdownPanel = new JPanel();
                addFlowLayout(gameModeDropdownPanel);

                {
                    JLabel gameModeSelectLabel = new JLabel("Game Mode");

                    JComboBox<TilemanGameMode> gameModeSelect = new JComboBox<>(TilemanGameMode.values());
                    gameModeSelect.setSelectedItem(gameRules.getGameMode());
                    gameModeSelect.addActionListener(l -> gameRules.setGameMode((TilemanGameMode) gameModeSelect.getSelectedItem()));

                    gameModeDropdownPanel.add(gameModeSelectLabel);
                    gameModeDropdownPanel.add(gameModeSelect);
                }

                gameModeSelectPanel.add(gameModeDropdownPanel);
                gameRulesPanel.add(gameModeSelectPanel);
            }


            {
                JCollapsePanel customGameModeCollapsable = new JCollapsePanel("Custom Game Mode", gameModeOpen, (Boolean state) -> this.gameModeOpen = state);
                customGameModeCollapsable.setBorder(BorderFactory.createLineBorder(Color.black));
                customGameModeCollapsable.setInnerLayout(new BorderLayout());

                gameRulesPanel.add(customGameModeCollapsable);

                {
                    JCheckBox customGameMode = createCheckbox("Enable Custom Game Mode", gameRules.isEnableCustomGameMode(), enabled -> {
                        gameRules.setEnableCustomGameMode(enabled);
                        rebuild();
                    });
                    customGameModeCollapsable.add(customGameMode, BorderLayout.NORTH);
                }

                {
                    JPanel rulesPanel = new JPanel();
                    addVerticalLayout(rulesPanel);
                    callbacks.add(() -> setJComponentEnabled(rulesPanel, gameRules.isEnableCustomGameMode()));

                    customGameModeCollapsable.add(rulesPanel, BorderLayout.CENTER);

                    rulesPanel.add(createCheckbox("Allow Tile Deficit", gameRules.isAllowTileDeficit(), gameRules::setAllowTileDeficit));
                    rulesPanel.add(createCheckbox("Tiles From Levels", gameRules.isTilesFromTotalLevel(), gameRules::setTilesFromTotalLevel));
                    rulesPanel.add(createCheckbox("Tiles From Exp", gameRules.isTilesFromExp(), gameRules::setTilesFromExp));

                    {
                        JPanel tileOffsetPanel = new JPanel();
                        addFlowLayout(tileOffsetPanel);

                        JLabel tileOffsetLabel = new JLabel("Tile Offset");
                        tileOffsetPanel.add(tileOffsetLabel);

                        SpinnerNumberModel numberModel = new SpinnerNumberModel(gameRules.getTilesOffset(), MIN_TILE_OFFSET, MAX_TILE_OFFSET, 1);
                        JSpinner tilesOffsetSpinner = new JSpinner(numberModel);
                        ((JSpinner.DefaultEditor)tilesOffsetSpinner.getEditor()).getTextField().setColumns(7); // Makes the width of the spinner reasonable
                        tilesOffsetSpinner.addChangeListener(l -> gameRules.setTilesOffset(numberModel.getNumber().intValue()));
                        tileOffsetPanel.add(tilesOffsetSpinner);
                        rulesPanel.add(tileOffsetPanel);
                    }

                    {
                        JPanel xpPanel = new JPanel();
                        addFlowLayout(xpPanel);

                        JLabel expPerTileLabel = new JLabel("Exp Per Tile");

                        SpinnerNumberModel numberModel = new SpinnerNumberModel(gameRules.getExpPerTile(), MIN_EXP_PER_TILE, MAX_EXP_PER_TILE, 1);
                        JSpinner expPerTileSpinner = new JSpinner(numberModel);
                        expPerTileSpinner.addChangeListener(l -> gameRules.setExpPerTile(numberModel.getNumber().intValue()));

                        xpPanel.add(expPerTileLabel);
                        xpPanel.add(expPerTileSpinner);

                        rulesPanel.add(xpPanel);
                    }
                }
            }

        }

        callbacks.forEach(Runnable::run);
        return gameRulesPanel;
    }

    private JPanel buildMultiplayerPanel() {
        JCollapsePanel multiplayerPanel = new JCollapsePanel("Multiplayer", isMultiplayerOpen && stateManager.hasActiveProfile(), isOpen -> this.isMultiplayerOpen = isOpen);
        multiplayerPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        multiplayerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addVerticalLayout(multiplayerPanel.getContentPanel());
        {
            if (stateManager.hasActiveProfile() && stateManager.getActiveProfile().isGroupTileman()) {

            } else {
                JButton createGroupButton = new JButton("Create Group");
                createGroupButton.addActionListener(l -> {
                    String name = JOptionPane.showInputDialog("Group Name:");
                    while (name == null || name.length() < 3 || name.equals(GroupTilemanProfile.NONE.getGroupName())) {
                        name = JOptionPane.showInputDialog("Group Name:");
                    }

                    if (name == null) {
                        return;
                    }

                    GroupTilemanProfile groupProfile = new GroupTilemanProfile(name, client.getAccountHash(), client.getLocalPlayer().getName());
                    stateManager.assignGroupProfile(stateManager.getActiveProfile(), groupProfile);
                });

                JButton joinGroupButton = new JButton("Join Group");
                joinGroupButton.addActionListener(l -> {

                });

                multiplayerPanel.add(createGroupButton);
                multiplayerPanel.add(joinGroupButton);

            }
        }

        setJComponentEnabled(multiplayerPanel, stateManager.hasActiveProfile());
        return multiplayerPanel;
    }

    private void doIpPanel(JPanel multiplayerPanel) {
        if (TilemanMultiplayerService.isHosting()) {
            JLabel serverLabel = new JLabel("Server is running on port " + TilemanMultiplayerService.getServerPort());
            multiplayerPanel.add(serverLabel);

            JButton shutdownButton = new JButton("Shutdown Server");
            shutdownButton.addActionListener(e -> TilemanMultiplayerService.stopServer());
            multiplayerPanel.add(shutdownButton);

            multiplayerPanel.add(Box.createVerticalStrut(20));
        }

        if (TilemanMultiplayerService.isConnected()) {
            JLabel label = new JLabel("CONNECTED~!");
            multiplayerPanel.add(label);

            JButton disconnectButton = new JButton("Disconnect");
            disconnectButton.addActionListener(e -> TilemanMultiplayerService.disconnect());
            multiplayerPanel.add(disconnectButton);
        } else {
            JTextField ipInput = new JTextField("IP Address");
            multiplayerPanel.add(ipInput);

            JButton connectButton = new JButton("Connect");
            connectButton.addActionListener(e -> TilemanMultiplayerService.connect(stateManager, ipInput.getText(), PORT, "password"));
            multiplayerPanel.add(connectButton);

            JButton startServerButton = new JButton("Launch Server");
            startServerButton.addActionListener(e -> TilemanMultiplayerService.startServer(stateManager, persistenceManager, "password", PORT));
            multiplayerPanel.add(startServerButton);
        }
    }


    private JPanel buildAdvancedOptionsPanel() {
        if (!plugin.isShowAdvancedOptions()) {
            return new JPanel();
        }

        JCollapsePanel advancedOptions = new JCollapsePanel("Advanced Options", advancedOpen, (Boolean isOpen) -> this.advancedOpen = isOpen);
        advancedOptions.setBorder(BorderFactory.createLineBorder(Color.black));
        addVerticalLayout(advancedOptions.getContentPanel());
        {
            JButton exportProfileButton = new JButton("Export Profile");
            exportProfileButton.setAlignmentX(CENTER_ALIGNMENT);
            exportProfileButton.setEnabled(stateManager.hasActiveProfile());
            advancedOptions.add(exportProfileButton);

            JLabel exportInfo = new JLabel("Copied to clipboard!");
            exportInfo.setAlignmentX(CENTER_ALIGNMENT);
            exportInfo.setVisible(showExportInfo);
            showExportInfo = false;
            advancedOptions.add(exportInfo);

            exportProfileButton.addActionListener(l -> {
                if (stateManager.hasActiveProfile()) {
                    copyToClipboard(TilemanProfileUtil.exportProfileJson(stateManager.getActiveProfile(), stateManager.getActiveProfileTileData()));
                    showExportInfo = true;
                    rebuild();
                }
            });

            JButton deleteProfileButton = new JButton("Delete Profile");
            deleteProfileButton.setAlignmentX(CENTER_ALIGNMENT);
            deleteProfileButton.setEnabled(stateManager.hasActiveProfile());
            advancedOptions.add(deleteProfileButton);

            deleteProfileButton.addActionListener(l -> {
                if (stateManager.hasActiveProfile()) {
                    int choice = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete this profile?\nAll Tile data will be lost.", "Delete Profile?", JOptionPane.YES_NO_OPTION);
                    if (choice == 0) {
                        choice = JOptionPane.showConfirmDialog(null, "This action cannot be undone!\nPress 'Yes' to delete the profile and associated data.", "Are you sure?", JOptionPane.YES_NO_OPTION);
                        if (choice == 0) {
                            TilemanProfileUtil.deleteProfile(stateManager.getActiveProfile(), persistenceManager);
                            ProfileTileDataUtil.deleteProfileTileData(stateManager.getActiveProfile(), stateManager.getActiveProfileTileData(), persistenceManager);
                            stateManager.setActiveProfile(TilemanProfile.NONE);
                            rebuild();
                        }
                    }
                }
            });
        }
        return advancedOptions;
    }

    private void showProfileImportPanel() {
        if (!stateManager.hasActiveProfile()) {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            JLabel instructions = new JLabel("Paste (Ctrl-V) your exported profile data here:");
            panel.add(instructions, BorderLayout.NORTH);

            JTextArea importText = new JTextArea("");
            importText.setLineWrap(true);
            importText.setColumns(5);

            JScrollPane scrollPane = new JScrollPane(importText);
            scrollPane.setPreferredSize(new Dimension(225, 120));
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
            scrollPane.setAlignmentX(CENTER_ALIGNMENT);

            panel.add(scrollPane, BorderLayout.CENTER);

            int choice = JOptionPane.showConfirmDialog(null,
                    panel,
                    "Import Profile",
                    JOptionPane.YES_NO_OPTION);

            if (choice == 0) {
                String maybeJson = importText.getText();
                TilemanProfile profile = TilemanProfileUtil.importProfileJson(maybeJson, client.getAccountHash(), client.getLocalPlayer().getName(), persistenceManager);
                if (profile.equals(TilemanProfile.NONE)) {
                    JOptionPane.showMessageDialog(null, "An error occured while trying to import the profile data.", "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    stateManager.setActiveProfile(profile);
                }
            }
        }
    }

    private static void addVerticalLayout(JComponent element) {
        element.setLayout(new BoxLayout(element, BoxLayout.PAGE_AXIS));
    }

    private static void addFlowLayout(JComponent element) {
        element.setLayout(new FlowLayout());
    }

    private static void addSpacer(JComponent element) {
        addSpacer(element, 10);
    }

    private static void addSpacer(JComponent element, int height) {
        element.add(Box.createVerticalStrut(height));
    }

    private static JCheckBox createCheckbox(String text, boolean defaultState, Consumer<Boolean> consumer) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setAlignmentX(CENTER_ALIGNMENT);
        checkBox.setSelected(defaultState);
        checkBox.addActionListener(l -> consumer.accept(checkBox.isSelected()));
        return checkBox;
    }

    private void setJComponentEnabled(JComponent element, boolean state) {
        element.setEnabled(state);
        Component[] children = element.getComponents();
        for (Component child : children) {
            if (child instanceof JPanel) {
                setJComponentEnabled((JPanel)child, state);
            }
            child.setEnabled(state);
        }
    }

    private static void copyToClipboard(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }

    public static class JCollapsePanel extends JPanel {

        private String title;
        private boolean isOpen;

        private final JPanel contentPanel;
        private final JButton toggleCollapseButton;

        private Consumer<Boolean> onToggle;

        public JCollapsePanel(String title, boolean isOpen, Consumer<Boolean> onToggle) {
            super();
            super.setLayout(new BorderLayout());

            this.title = title;
            this.onToggle = onToggle;
            this.contentPanel = new JPanel();
            super.add(contentPanel, BorderLayout.CENTER);

            toggleCollapseButton = new JButton();
            toggleCollapseButton.setFocusPainted(false);
            toggleCollapseButton.setHorizontalAlignment(SwingConstants.LEFT);
            toggleCollapseButton.addActionListener(l -> {
                setOpen(!this.isOpen);
                if (onToggle != null){
                    onToggle.accept(this.isOpen);
                }
            });
            super.add(toggleCollapseButton, BorderLayout.NORTH);

            setOpen(isOpen);
        }

        @Override
        public Component add(Component component) {
            return contentPanel.add(component);
        }

        @Override
        public void add(@NonNull Component component, Object constraints) {
            contentPanel.add(component, constraints);
        }

        public JPanel getContentPanel() {
            return contentPanel;
        }

        public void setInnerLayout(LayoutManager layoutManager) {
            contentPanel.setLayout(layoutManager);
        }

        public void setOpen(boolean isOpen) {
            this.isOpen = isOpen;
            contentPanel.setVisible(isOpen);
            toggleCollapseButton.setText((isOpen ?"▼" :  "▶") + "    " + title);
        }
    }
}