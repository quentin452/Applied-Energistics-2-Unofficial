/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.cache;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.Level;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkChannelChanged;
import appeng.api.networking.events.MENetworkControllerChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.DimensionalCoord;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.stats.Achievements;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.AdHocChannelUpdater;
import appeng.me.pathfinding.BackbonePathSegment;
import appeng.me.pathfinding.ControllerChannelUpdater;
import appeng.me.pathfinding.ControllerValidator;
import appeng.me.pathfinding.IPathItem;
import appeng.me.pathfinding.PathSegment;
import appeng.me.pathfinding.TopologyStage;
import appeng.tile.networking.TileController;
import appeng.util.Platform;

public class PathGridCache implements IPathingGrid {

    private final LinkedList<PathSegment> active = new LinkedList<>();
    private final Map<IPathItem, BackbonePathSegment> backbone = new HashMap<>();
    private final Set<TileController> controllers = new HashSet<>();
    private final Set<IGridNode> requireChannels = new HashSet<>();
    private final Set<IGridNode> blockDense = new HashSet<>();
    private final IGrid myGrid;
    private int channelsInUse = 0;
    private int channelsByBlocks = 0;
    private double channelPowerUsage = 0.0;
    private boolean recalculateControllerNextTick = true;
    private boolean updateNetwork = true;
    private boolean booting = false;
    private ControllerState controllerState = ControllerState.NO_CONTROLLER;
    private int ticksUntilReady = 20;
    private int lastChannels = 0;
    private HashSet<IPathItem> semiOpen = new HashSet<>();

    public PathGridCache(final IGrid g) {
        this.myGrid = g;
    }

    @Override
    public void onUpdateTick() {
        if (this.recalculateControllerNextTick) {
            this.recalcController();
        }

        if (this.updateNetwork) {
            if (!this.booting) {
                this.myGrid.postEvent(new MENetworkBootingStatusChange(true));
            }

            this.booting = true;
            this.updateNetwork = false;
            this.setChannelsInUse(0);

            if (this.controllerState == ControllerState.NO_CONTROLLER) {
                final int requiredChannels = this.calculateRequiredChannels();
                int used = requiredChannels;
                if (AEConfig.instance.isFeatureEnabled(AEFeature.Channels) && requiredChannels > 8) {
                    used = 0;
                }

                final int nodes = this.myGrid.getNodes().size();
                this.setChannelsInUse(used);

                this.ticksUntilReady = 20 + Math.max(0, nodes / 100 - 20);
                this.setChannelsByBlocks(nodes * used);
                this.setChannelPowerUsage(this.getChannelsByBlocks() / 128.0);

                this.myGrid.getPivot().beginVisit(new AdHocChannelUpdater(used));
            } else if (this.controllerState == ControllerState.CONTROLLER_CONFLICT) {
                this.ticksUntilReady = 20;
                this.myGrid.getPivot().beginVisit(new AdHocChannelUpdater(0));
            } else {
                final int nodes = this.myGrid.getNodes().size();
                this.ticksUntilReady = 20 + Math.max(0, nodes / 100 - 20);
                final HashSet<IPathItem> closedList = new HashSet<>();
                this.semiOpen = new HashSet<>();

                // myGrid.getPivot().beginVisit( new AdHocChannelUpdater( 0 )
                // );
                for (final IGridNode node : this.myGrid.getMachines(TileController.class)) {
                    closedList.add((IPathItem) node);
                    for (final IGridConnection gcc : node.getConnections()) {
                        final GridConnection gc = (GridConnection) gcc;
                        if (!(gc.getOtherSide(node).getMachine() instanceof TileController)) {
                            final List<IPathItem> open = new LinkedList<>();
                            closedList.add(gc);
                            open.add(gc);
                            gc.setControllerRoute((GridNode) node, true);
                            this.active.add(new PathSegment(this, open, this.semiOpen, closedList));
                        }
                    }
                }
            }
        }

        if (!this.active.isEmpty() || !backbone.isEmpty() || this.ticksUntilReady > 0) {
            boolean firstStage = !this.active.isEmpty();
            final Iterator<PathSegment> i = this.active.iterator();
            while (i.hasNext()) {
                final PathSegment pat = i.next();
                if (pat.step(backbone, TopologyStage.CONTROLLER_TO_BACKBONE)) {
                    pat.setDead(true);
                    i.remove();
                }
            }
            if (active.isEmpty() && !backbone.isEmpty()) {
                if (firstStage) {
                    for (BackbonePathSegment ps : backbone.values()) {
                        BackbonePathSegment.reset(backbone);
                        // noinspection StatementWithEmptyBody
                        while (!ps.step(backbone, TopologyStage.BACKBONE)) {} // just establish backbone topology, that
                                                                              // is fast
                        ps.selectControllerRoute();
                    }
                    BackbonePathSegment.reset(backbone);
                } else {
                    final Iterator<BackbonePathSegment> bsi = this.backbone.values().iterator();
                    boolean hasAliveSegments = false;
                    while (bsi.hasNext()) {
                        final PathSegment pat = bsi.next();
                        if (pat.isDead()) continue;
                        hasAliveSegments = true;
                        if (pat.step(backbone, TopologyStage.PERIPHERALS)) pat.setDead(true);
                    }
                    if (AEConfig.instance.debugPathFinding) {
                        AELog.debug("Pathfinding: Backbone 2nd phase, hasAliveSegments = %b", hasAliveSegments);
                    }
                    if (!hasAliveSegments) backbone.clear();
                }
            }

            this.ticksUntilReady--;

            if (this.active.isEmpty() && this.backbone.isEmpty() && this.ticksUntilReady <= 0) {
                if (this.controllerState == ControllerState.CONTROLLER_ONLINE) {
                    final Iterator<TileController> controllerIterator = this.controllers.iterator();
                    if (controllerIterator.hasNext()) {
                        final TileController controller = controllerIterator.next();
                        controller.getGridNode(ForgeDirection.UNKNOWN).beginVisit(new ControllerChannelUpdater());
                    }
                }

                // check for achievements
                this.achievementPost();

                this.booting = false;
                this.setChannelPowerUsage(this.getChannelsByBlocks() / 128.0);
                this.myGrid.postEvent(new MENetworkBootingStatusChange(false));
            }
        }
    }

    @Override
    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        if (AEConfig.instance.debugPathFinding) {
            final String coordinates = gridNode.getGridBlock().getLocation().toString();
            AELog.info("Repath is triggered by removing a node at [%s]", coordinates);
            AELog.printStackTrace(Level.INFO);
        }

        if (machine instanceof TileController) {
            this.controllers.remove(machine);
            this.recalculateControllerNextTick = true;
        }

        final EnumSet<GridFlags> flags = gridNode.getGridBlock().getFlags();

        if (flags.contains(GridFlags.REQUIRE_CHANNEL)) {
            this.requireChannels.remove(gridNode);
        }

        if (flags.contains(GridFlags.CANNOT_CARRY_COMPRESSED)) {
            this.blockDense.remove(gridNode);
        }

        this.repath();
    }

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        if (AEConfig.instance.debugPathFinding) {
            final String coordinates = gridNode.getGridBlock().getLocation().toString();
            AELog.info("Repath is triggered by adding a node at [%s]", coordinates);
            AELog.printStackTrace(Level.INFO);
        }

        if (machine instanceof TileController) {
            this.controllers.add((TileController) machine);
            this.recalculateControllerNextTick = true;
        }

        final EnumSet<GridFlags> flags = gridNode.getGridBlock().getFlags();

        if (flags.contains(GridFlags.REQUIRE_CHANNEL)) {
            this.requireChannels.add(gridNode);
        }

        if (flags.contains(GridFlags.CANNOT_CARRY_COMPRESSED)) {
            this.blockDense.add(gridNode);
        }

        this.repath();
    }

    @Override
    public void onSplit(final IGridStorage storageB) {}

    @Override
    public void onJoin(final IGridStorage storageB) {}

    @Override
    public void populateGridStorage(final IGridStorage storage) {}

    private void recalcController() {
        this.recalculateControllerNextTick = false;
        final ControllerState old = this.controllerState;

        if (this.controllers.isEmpty()) {
            this.controllerState = ControllerState.NO_CONTROLLER;
        } else {
            final IGridNode startingNode = this.controllers.iterator().next().getGridNode(ForgeDirection.UNKNOWN);
            if (startingNode == null) {
                this.controllerState = ControllerState.CONTROLLER_CONFLICT;
                return;
            }

            final DimensionalCoord dc = startingNode.getGridBlock().getLocation();
            final ControllerValidator cv = new ControllerValidator(dc.x, dc.y, dc.z);

            startingNode.beginVisit(cv);

            if (cv.isValid() && cv.getFound() == this.controllers.size()) {
                this.controllerState = ControllerState.CONTROLLER_ONLINE;
            } else {
                this.controllerState = ControllerState.CONTROLLER_CONFLICT;
            }
        }

        if (old != this.controllerState) {
            this.myGrid.postEvent(new MENetworkControllerChange());
        }
    }

    private int calculateRequiredChannels() {
        this.semiOpen.clear();

        int depth = 0;
        for (final IGridNode nodes : this.requireChannels) {
            if (!this.semiOpen.contains(nodes)) {
                final IGridBlock gb = nodes.getGridBlock();
                final EnumSet<GridFlags> flags = gb.getFlags();

                if (flags.contains(GridFlags.COMPRESSED_CHANNEL) && !this.blockDense.isEmpty()) {
                    return 9;
                }

                depth++;

                if (flags.contains(GridFlags.MULTIBLOCK)) {
                    final IGridMultiblock gmb = (IGridMultiblock) gb;
                    final Iterator<IGridNode> i = gmb.getMultiblockNodes();
                    while (i.hasNext()) {
                        this.semiOpen.add((IPathItem) i.next());
                    }
                }
            }
        }

        return depth;
    }

    private void achievementPost() {
        if (this.lastChannels != this.getChannelsInUse() && AEConfig.instance.isFeatureEnabled(AEFeature.Channels)) {
            final Achievements currentBracket = this.getAchievementBracket(this.getChannelsInUse());
            final Achievements lastBracket = this.getAchievementBracket(this.lastChannels);
            if (currentBracket != lastBracket && currentBracket != null) {
                final Set<Integer> players = new HashSet<>();
                for (final IGridNode n : this.requireChannels) {
                    players.add(n.getPlayerID());
                }

                for (final int id : players) {
                    Platform.addStat(id, currentBracket.getAchievement());
                }
            }
        }
        this.lastChannels = this.getChannelsInUse();
    }

    private Achievements getAchievementBracket(final int ch) {
        if (ch < 8) {
            return null;
        }

        if (ch < 128) {
            return Achievements.Networking1;
        }

        if (ch < 2048) {
            return Achievements.Networking2;
        }

        return Achievements.Networking3;
    }

    @MENetworkEventSubscribe
    void updateNodReq(final MENetworkChannelChanged ev) {
        final IGridNode gridNode = ev.node;

        if (AEConfig.instance.debugPathFinding) {
            final String coordinates = gridNode.getGridBlock().getLocation().toString();
            AELog.info("Repath is triggered by changing a node at [%s]", coordinates);
            AELog.printStackTrace(Level.INFO);
        }

        if (gridNode.getGridBlock().getFlags().contains(GridFlags.REQUIRE_CHANNEL)) {
            this.requireChannels.add(gridNode);
        } else {
            this.requireChannels.remove(gridNode);
        }

        this.repath();
    }

    @Override
    public boolean isNetworkBooting() {
        return (!this.active.isEmpty() || !this.backbone.isEmpty()) && !this.booting;
    }

    @Override
    public ControllerState getControllerState() {
        return this.controllerState;
    }

    @Override
    public void repath() {
        // clean up...
        this.active.clear();
        this.backbone.clear();
        this.setChannelsByBlocks(0);
        this.updateNetwork = true;
    }

    double getChannelPowerUsage() {
        return this.channelPowerUsage;
    }

    private void setChannelPowerUsage(final double channelPowerUsage) {
        this.channelPowerUsage = channelPowerUsage;
    }

    public int getChannelsByBlocks() {
        return this.channelsByBlocks;
    }

    public void setChannelsByBlocks(final int channelsByBlocks) {
        this.channelsByBlocks = channelsByBlocks;
    }

    public int getChannelsInUse() {
        return this.channelsInUse;
    }

    public void setChannelsInUse(final int channelsInUse) {
        this.channelsInUse = channelsInUse;
    }

    public boolean isValidBackboneConnection(IPathItem pi) {
        BackbonePathSegment bs = backbone.get(pi);
        if (bs == null) return false;
        return bs.isValid();
    }

    public void repathBackboneConnection(IPathItem pi) {
        BackbonePathSegment bs = backbone.get(pi);
        if (bs == null) return;
        if (!bs.switchControllerRoute()) bs.transferToNeighbours();
    }
}
