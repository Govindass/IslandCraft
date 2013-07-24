package com.github.hoqhuuep.islandcraft.bukkit.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public class IslandCraftConfig {
    private final ConfigurationSection config;

    public IslandCraftConfig(final ConfigurationSection config) {
        this.config = config;
    }

    public final int getLocalChatRadius() {
        return config.getInt("local-chat-radius", 128); //$NON-NLS-1$
    }

    public final Material getPurchaseCostItem() {
        final String value = config.getString("purchase-cost-item");
        final Material material = materialByNameOrId(value);
        if (null == material) {
            return Material.DIAMOND;
        }
        return material;
    }

    public final int getPurchaseCostAmount() {
        return config.getInt("purchase-cost-amount");
    }

    public final int getPurchaseCostIncrease() {
        return config.getInt("purchase-cost-increase");
    }

    public final Material getTaxCostItem() {
        final String value = config.getString("tax-cost-item");
        final Material material = materialByNameOrId(value);
        if (null == material) {
            return Material.DIAMOND;
        }
        return material;
    }

    public final int getTaxCostAmount() {
        return config.getInt("tax-cost-amount");
    }

    public final int getTaxCostIncrease() {
        return config.getInt("tax-cost-increase");
    }

    public final WorldConfig getWorldConfig(final String world) {
        return new WorldConfig(config.getConfigurationSection("worlds." + world)); //$NON-NLS-1$
    }

    public final int getMaxIslandsPerPlayer() {
        return config.getInt("max-islands-per-player");
    }

    public final int getResourceIslandRarity() {
        return config.getInt("resource-island-rarity");
    }

    private final Material materialByNameOrId(final String nameOrId) {
        final Material materialByName = Material.getMaterial(nameOrId);
        if (null != materialByName) {
            return materialByName;
        }
        try {
            final int id = Integer.valueOf(nameOrId);
            return Material.getMaterial(id);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
