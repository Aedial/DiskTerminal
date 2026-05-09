package com.cellterminal.integration.storagebus;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IPriorityHost;

import com.cells.api.IInterfaceHost;
import com.cells.api.IInterfaceProvider;
import com.cells.api.IUpgradeable;
import com.cellterminal.client.StorageType;
import com.cellterminal.container.handler.StorageBusDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.cellterminal.integration.CellsIntegration;


/**
 * Scanner for CELLS hosts exposed through the public CELLS API.
 */
public class CellsStorageBusScanner extends AbstractStorageBusScanner {

    private static final int BASE_CONFIG_SLOTS = 36;
    private static final int SLOTS_PER_CAPACITY_CARD = 36;
    private static final int MAX_CONFIG_SLOTS = 180;

    public static final CellsStorageBusScanner INSTANCE = new CellsStorageBusScanner();

    private CellsStorageBusScanner() {}

    @Override
    public String getId() {
        return "cells";
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded("cells");
    }

    @Override
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        if (grid == null) return;

        for (IGridNode node : grid.getNodes()) {
            if (!node.isActive()) continue;

            Object machine = node.getMachine();
            List<IInterfaceHost> interfaceHosts = getInterfaceHosts(machine);
            if (interfaceHosts.isEmpty()) continue;

            TileEntity hostTile = CellsIntegration.getHostTile(machine);
            if (hostTile == null || hostTile.getWorld() == null) continue;

            for (int hostIndex = 0; hostIndex < interfaceHosts.size(); hostIndex++) {
                IInterfaceHost bus = interfaceHosts.get(hostIndex);
                if (bus == null) continue;

                StorageType storageType = CellsIntegration.toStorageType(bus.getResourceType());
                EnumFacing primaryFacing = bus.getPrimaryFacing();
                if (primaryFacing == null) primaryFacing = EnumFacing.NORTH;

                long busId = createLogicalBusId(trackerMap, hostTile, primaryFacing, storageType, hostIndex);

                NBTTagCompound busData = new NBTTagCompound();
                busData.setLong("id", busId);
                busData.setLong("pos", hostTile.getPos().toLong());
                busData.setInteger("dim", hostTile.getWorld().provider.getDimension());
                busData.setInteger("side", primaryFacing.ordinal());
                busData.setInteger("priority", machine instanceof IPriorityHost ? ((IPriorityHost) machine).getPriority() : 0);
                busData.setInteger("access", bus.isExport() ? 2 : 1);
                if (bus.isDirectionalView()) {
                    // IO (paired import+export) interface: show "Import" / "Export" prefix.
                    busData.setString(
                        "namePrefixKey",
                        bus.isExport()
                            ? "gui.cellterminal.storage_bus.prefix.export"
                            : "gui.cellterminal.storage_bus.prefix.import"
                    );
                } else if (bus.isTypeLabeled()) {
                    // Universal (combined multi-type) interface: show resource type as prefix
                    // so "Item", "Fluid", "Gas", or "Essentia" appears before the interface name.
                    busData.setString(
                        "namePrefixKey",
                        "gui.cellterminal.storage_bus.type." + bus.getResourceType().name().toLowerCase(Locale.ROOT)
                    );
                }
                storageType.writeToNBT(busData);

                applyCapabilities(busData, machine instanceof IPriorityHost, false);
                applySlotParameters(busData, BASE_CONFIG_SLOTS, SLOTS_PER_CAPACITY_CARD, MAX_CONFIG_SLOTS);

                // Use the host's own upgrade inventory when available (IO interfaces expose
                // per-direction upgrades this way). Fall back to the machine-level IUpgradeable
                // for single-direction interfaces which track upgrades on the machine itself.
                IItemHandler busUpgradeInv = bus.getUpgradeInventory();
                if (busUpgradeInv != null) {
                    addUpgradesData(busData, busUpgradeInv);
                } else if (machine instanceof IUpgradeable) {
                    addUpgradesData(busData, ((IUpgradeable) machine).getUpgradeInventory());
                } else {
                    busData.setInteger("upgradeSlotCount", 0);
                }

                if (machine instanceof ICustomNameObject) {
                    ICustomNameObject nameable = (ICustomNameObject) machine;
                    if (nameable.hasCustomInventoryName()) {
                        String customName = nameable.getCustomInventoryName();
                        if (customName != null && !customName.isEmpty()) busData.setString("customName", customName);
                    }
                }

                ItemStack connectedIcon = CellsIntegration.getHostDisplayStack(machine);
                String connectedName = connectedIcon.isEmpty() ? null : connectedIcon.getDisplayName();

                if (connectedName != null && !connectedName.isEmpty()) busData.setString("connectedName", connectedName);

                if (!connectedIcon.isEmpty()) {
                    NBTTagCompound iconNbt = new NBTTagCompound();
                    connectedIcon.writeToNBT(iconNbt);
                    busData.setTag("connectedIcon", iconNbt);
                }

                busData.setTag("partition", CellsIntegration.createFilterNBT(bus));
                busData.setTag("contents", CellsIntegration.createPreviewNBT(
                    CellsIntegration.collectInterfacePreviewEntries(bus, Integer.MAX_VALUE), 0, null));

                out.appendTag(busData);
                trackerMap.put(busId, new StorageBusTracker(
                    busId,
                    machine,
                    hostTile,
                    primaryFacing.ordinal(),
                    storageType,
                    bus
                ));
            }
        }
    }

    private long createLogicalBusId(Map<Long, StorageBusTracker> trackerMap,
                                    TileEntity hostTile,
                                    EnumFacing primaryFacing,
                                    StorageType storageType,
                                    int hostIndex) {
        long baseId = StorageBusDataHandler.createBusId(hostTile, primaryFacing.ordinal(), storageType.ordinal());

        // Host index disambiguates multiple logical interfaces on the same machine.
        // Use additive mixing (not XOR) then probe if needed to avoid accidental collisions.
        long candidateId = baseId + ((long) hostIndex * 0x9E3779B97F4A7C15L);
        if (!trackerMap.containsKey(candidateId)) return candidateId;

        // Fallback: deterministic open addressing to guarantee uniqueness in this scan pass.
        long step = 0x632BE59BD9B4E019L;
        while (trackerMap.containsKey(candidateId)) candidateId += step;

        return candidateId;
    }

    private List<IInterfaceHost> getInterfaceHosts(Object machine) {
        if (machine instanceof IInterfaceProvider) {
            List<IInterfaceHost> hosts = ((IInterfaceProvider) machine).getInterfaceHosts();
            return hosts != null ? hosts : Collections.emptyList();
        }

        if (machine instanceof IInterfaceHost) {
            return Collections.singletonList((IInterfaceHost) machine);
        }

        return Collections.emptyList();
    }

    private void addUpgradesData(NBTTagCompound busData, IItemHandler upgradesInv) {
        if (upgradesInv == null) {
            busData.setInteger("upgradeSlotCount", 0);
            return;
        }

        busData.setInteger("upgradeSlotCount", upgradesInv.getSlots());

        NBTTagList upgradeList = new NBTTagList();
        for (int slot = 0; slot < upgradesInv.getSlots(); slot++) {
            ItemStack upgrade = upgradesInv.getStackInSlot(slot);
            if (upgrade.isEmpty()) continue;

            NBTTagCompound upgradeNbt = new NBTTagCompound();
            upgrade.writeToNBT(upgradeNbt);
            upgradeNbt.setInteger("slot", slot);
            upgradeList.appendTag(upgradeNbt);
        }

        busData.setTag("upgrades", upgradeList);
    }
}