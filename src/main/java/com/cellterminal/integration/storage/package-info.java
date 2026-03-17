/**
 * Storage device scanner registry for the Cell Terminal.
 * <p>
 * Implements the registry pattern for discovering ME storage devices (ME Drives, ME Chests,
 * and modded equivalents) on the network. Each scanner knows how to locate and extract data
 * from a specific type of storage device.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.integration.storage.IStorageScanner} — Interface defining
 *       how to discover storage devices on a grid.</li>
 *   <li>{@link com.cellterminal.integration.storage.AbstractStorageScanner} — Base
 *       implementation with shared scanning utilities.</li>
 *   <li>{@link com.cellterminal.integration.storage.AE2StorageScanner} — Default scanner
 *       for vanilla AE2 ME Drives and ME Chests.</li>
 *   <li>{@link com.cellterminal.integration.storage.StorageScannerRegistry} — Singleton
 *       registry managing all available storage scanners; queried by
 *       {@code CellDataHandler}.</li>
 * </ul>
 *
 * @see com.cellterminal.container.handler.CellDataHandler
 */
package com.cellterminal.integration.storage;
