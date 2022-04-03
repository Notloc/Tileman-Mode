package com.tileman;
import com.tileman.multiplayer.TilemanMultiplayerService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
@Singleton
public class TilemanPluginPanel extends PluginPanel {
    private final TilemanModePlugin plugin;
    private final int PORT = 7777;

    public TilemanPluginPanel(TilemanModePlugin plugin) {
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel northPanel = new JPanel(new BorderLayout());
        {
            northPanel.setBorder(new EmptyBorder(1, 0, 10, 0));

            JLabel title = new JLabel();
            title.setText("Tileman Mode Import Panel");
            title.setForeground(Color.WHITE);

            northPanel.add(title, BorderLayout.NORTH);
        }

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.PAGE_AXIS));
        {
            JPanel multiplayerPanel = new JPanel();
            multiplayerPanel.setLayout(new BoxLayout(multiplayerPanel, BoxLayout.PAGE_AXIS));
            {
                multiplayerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

                JTextField ipInput = new JTextField("IP Address");
                multiplayerPanel.add(ipInput);

                JButton connectButton = new JButton("Connect");
                connectButton.addActionListener(e -> TilemanMultiplayerService.connect(plugin.getClient(), ipInput.getText(), PORT));
                multiplayerPanel.add(connectButton);

                JButton startServerButton = new JButton("Launch Server");
                startServerButton.addActionListener(e -> TilemanMultiplayerService.startServer(PORT));
                multiplayerPanel.add(startServerButton);
            }

            JPanel importPanel = new JPanel();
            importPanel.setLayout(new BoxLayout(importPanel, BoxLayout.PAGE_AXIS));
            {
                JPanel infoPanel = new JPanel();
                {
                    infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    infoPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
                    infoPanel.setLayout(new GridLayout(0, 1));
                    JLabel info = new JLabel(htmlLabel("Clicking the Import button below will migrate all tiles marked with the Ground Marker plugin into the Tileman Mode plugin. They will NOT be removed from the Ground Marker Plugin.", "#FFFFFF"));
                    JLabel warning = new JLabel(htmlLabel("WARNING: This directly modifies RuneLite's settings.properties file. You should make a back up before importing.", "#FFFF00"));
                    infoPanel.add(info);
                    infoPanel.add(warning);
                }

                JPanel importButtonPanel = new JPanel(new BorderLayout());
                {
                    importButtonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
                    JButton importButton = new JButton("Import");
                    importButton.addActionListener(l -> plugin.importGroundMarkerTiles());
                    importButton.setToolTipText("Import Ground Markers");
                    importButtonPanel.add(importButton, BorderLayout.SOUTH);
                }

                importPanel.add(infoPanel, BorderLayout.NORTH);
                importPanel.add(importButtonPanel, BorderLayout.SOUTH);
            }

            centerPanel.add(multiplayerPanel);
            centerPanel.add(importPanel);
        }

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;

        add(northPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }


    private static String htmlLabel(String key, String color)
    {
        return "<html><body style = 'color:" + color + "'>" + key + "</body></html>";
    }
}