/**
 * Storage bus scanner registry for the Cell Terminal.
 * <p>
 * Implements the registry pattern for discovering ME storage buses (item, fluid, essentia,
 * and gas variants) on the network. Each scanner knows how to locate and extract data
 * from a specific type of storage bus.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.integration.storagebus.IStorageBusScanner}: Interface defining
 *       how to discover storage buses on a grid.</li>
 *   <li>{@link com.cellterminal.integration.storagebus.AbstractStorageBusScanner}: Base
 *       implementation with shared scanning utilities.</li>
 *   <li>{@link com.cellterminal.integration.storagebus.AE2StorageBusScanner}: Default scanner
 *       for vanilla AE2 item and fluid storage buses.</li>
 *   <li>{@link com.cellterminal.integration.storagebus.ThaumicEnergisticsBusScanner}: Scanner
 *       for Thaumic Energistics essentia storage buses.</li>
 *   <li>{@link com.cellterminal.integration.storagebus.StorageBusScannerRegistry}: Singleton
 *       registry managing all available storage bus scanners; queried by
 *       {@code StorageBusDataHandler}.</li>
 * </ul>
 *
 * @see com.cellterminal.container.handler.StorageBusDataHandler
 */
package com.cellterminal.integration.storagebus;
