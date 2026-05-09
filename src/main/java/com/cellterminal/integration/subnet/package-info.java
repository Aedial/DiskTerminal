/**
 * Subnet scanner registry for the Cell Terminal.
 * <p>
 * Implements the registry pattern for discovering sub-networks (subnets) accessible from
 * the current ME network via network interfaces. Each scanner knows how to locate and
 * extract subnet connection data.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.integration.subnet.ISubnetScanner}: Interface defining
 *       how to discover subnets on a grid.</li>
 *   <li>{@link com.cellterminal.integration.subnet.AbstractSubnetScanner}: Base
 *       implementation with shared scanning utilities.</li>
 *   <li>{@link com.cellterminal.integration.subnet.AE2SubnetScanner}: Default scanner
 *       for vanilla AE2 network interface subnets.</li>
 *   <li>{@link com.cellterminal.integration.subnet.SubnetScannerRegistry}: Singleton
 *       registry managing all available subnet scanners; queried by
 *       {@code SubnetDataHandler}.</li>
 * </ul>
 *
 * @see com.cellterminal.container.handler.SubnetDataHandler
 */
package com.cellterminal.integration.subnet;
