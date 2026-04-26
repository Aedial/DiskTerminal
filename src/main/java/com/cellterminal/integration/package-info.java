/**
 * Optional mod integration layer for the Cell Terminal.
 * <p>
 * Each integration class provides optional support for an external mod, guarded by
 * {@code @Optional.Method} annotations or runtime class detection. Integrations extend
 * the Cell Terminal's scanning capabilities to cover cells, storage buses, and subnets
 * from other mods.
 * <p>
 * Scanning is implemented via the registry-based scanner pattern, where each integration
 * registers one or more scanner implementations for the relevant device types.
 * Said scanners are implemented in the {@code storage/}, {@code storagebus/}, and {@code subnet/} sub-packages,
 * but CrazyAE and ECO AE Extension integrations have not been refactored to use the registry system correctly,
 * and still contain direct scanner implementations in the main integration package.
 * This is subject to change in the future as those integrations are refactored.
 * <p>
 * <b>Direct integrations:</b>
 * <ul>
 *   <li>{@link com.cellterminal.integration.AE2WUTIntegration}: Wireless Universal Terminal
 *       mode registration for AE2UEL-WUT.</li>
 *   <li>{@link com.cellterminal.integration.CellTerminalJEIPlugin}: JEI ghost ingredient
 *       handlers and runtime access for partition drag-and-drop.</li>
 *   <li>{@link com.cellterminal.integration.CrazyAEIntegration}: Crazy AE mod storage
 *       device support.</li>
 *   <li>{@link com.cellterminal.integration.ECOAEExtensionIntegration}: ECO AE Extension
 *       mod support.</li>
 *   <li>{@link com.cellterminal.integration.StorageDrawersIntegration}: Storage Drawers mod
 *       support for storage bus connected inventories.</li>
 *   <li>{@link com.cellterminal.integration.ThaumicEnergisticsIntegration}: Thaumic
 *       Energistics essentia cell and bus support.</li>
 *   <li>{@link com.cellterminal.integration.WUTModeSwitcher}: Helper for managing Cell
 *       Terminal mode within the Wireless Universal Terminal.</li>
 * </ul>
 * <p>
 * <b>Scanner sub-packages:</b> The {@code storage/}, {@code storagebus/}, and {@code subnet/}
 * sub-packages implement the registry-based scanner pattern for discovering network devices.
 *
 * @see com.cellterminal.integration.storage
 * @see com.cellterminal.integration.storagebus
 * @see com.cellterminal.integration.subnet
 */
package com.cellterminal.integration;
