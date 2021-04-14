/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, ConorLeckey <https://github.com/ConorLeckey>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tileman;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.Collection;

public class TilemanModeOverlay extends Overlay {

    private final Client client;
    private final TilemanModePlugin plugin;

    private static final int LOCAL_TILE_SIZE = Perspective.LOCAL_TILE_SIZE;
    private static final int CHUNK_SIZE = 8;

    @Inject
    private TilemanModeConfig config;

    @Inject
    private TilemanModeOverlay(Client client, TilemanModeConfig config, TilemanModePlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        final Collection<WorldPoint> points = plugin.getPoints();
        for (final WorldPoint point : points) {
            if (point.getPlane() != client.getPlane()) {
                continue;
            }

            drawTile(graphics, point);
        }

        return null;
    }

    private void drawTile(Graphics2D graphics, WorldPoint point) {
        WorldPoint wp = client.getLocalPlayer().getWorldLocation();
        int startX = wp.getX() - config.tilesOffset();
        int startY = wp.getY() - config.tilesOffset();

        graphics.setStroke(new BasicStroke(3));
        Color c = Color.BLACK;
        graphics.setColor(c);

        GeneralPath path = new GeneralPath();

        path = drawYLine(path, startX, wp);

        path = drawYLine(path, startX + 2 * config.tilesOffset() + 1, wp);

		path = drawXLine(path, startY, wp);

		path = drawXLine(path, startY + 2 * config.tilesOffset() + 1, wp);

		graphics.draw(path);

	}

    private GeneralPath drawYLine(GeneralPath path, int startX, WorldPoint wp) {
            LocalPoint lp1 = LocalPoint.fromWorld(client, startX, wp.getY() - config.tilesOffset());
            LocalPoint lp2 = LocalPoint.fromWorld(client, startX, wp.getY() + config.tilesOffset() + 1);

            boolean first = true;
            for (int y = lp1.getY(); y <= lp2.getY(); y += LOCAL_TILE_SIZE) {
                Point p = Perspective.localToCanvas(client,
                        new LocalPoint(lp1.getX() - LOCAL_TILE_SIZE / 2, y - LOCAL_TILE_SIZE / 2),
                        client.getPlane());
                if (p != null) {
                    if (first) {
                        path.moveTo(p.getX(), p.getY());
                        first = false;
                    } else {
                        path.lineTo(p.getX(), p.getY());
                    }
                }
            }
        return path;
    }

    private GeneralPath drawXLine(GeneralPath path, int startY, WorldPoint wp) {
			LocalPoint lp1 = LocalPoint.fromWorld(client, wp.getX() - config.tilesOffset(), startY);
			LocalPoint lp2 = LocalPoint.fromWorld(client, wp.getX() + config.tilesOffset() + 1, startY);

			boolean first = true;
			for (int x = lp1.getX(); x <= lp2.getX(); x += LOCAL_TILE_SIZE) {
				Point p = Perspective.localToCanvas(client,
						new LocalPoint(x - LOCAL_TILE_SIZE / 2, lp1.getY() - LOCAL_TILE_SIZE / 2),
						client.getPlane());
				if (p != null) {
					if (first) {
						path.moveTo(p.getX(), p.getY());
						first = false;
					} else {
						path.lineTo(p.getX(), p.getY());
					}
				}
			}

		return path;
    }


    private Color getTileColor() {
        if (config.enableTileWarnings()) {
            if (plugin.getRemainingTiles() <= 0) {
                return Color.RED;
            } else if (plugin.getRemainingTiles() <= config.warningLimit()) {
                return new Color(255, 153, 0);
            }
        }
        return config.markerColor();
    }
}
