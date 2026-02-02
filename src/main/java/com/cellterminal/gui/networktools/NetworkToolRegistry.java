package com.cellterminal.gui.networktools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Registry for network tools.
 * Maintains the list of available tools in the Network Tools tab.
 */
public class NetworkToolRegistry {

    private static final Map<String, INetworkTool> tools = new LinkedHashMap<>();

    static {
        registerTool(new AttributeUniqueTool());
        registerTool(new MassPartitionCellTool());
        registerTool(new MassPartitionBusTool());
    }

    private NetworkToolRegistry() {}

    /**
     * Register a network tool.
     * @param tool The tool to register
     */
    public static void registerTool(INetworkTool tool) {
        tools.put(tool.getId(), tool);
    }

    /**
     * Get a tool by ID.
     * @param id The tool ID
     * @return The tool, or null if not found
     */
    public static INetworkTool getTool(String id) {
        return tools.get(id);
    }

    /**
     * Get all registered tools in order.
     * @return List of all tools
     */
    public static List<INetworkTool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * Get the number of registered tools.
     * @return The tool count
     */
    public static int getToolCount() {
        return tools.size();
    }
}
