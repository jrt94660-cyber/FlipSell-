package com.flovex.flipsell;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class PriceManager {
    private final JavaPlugin plugin;
    private File file;
    private YamlConfiguration prices;

    public PriceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        file = new File(plugin.getDataFolder(), "price.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try { plugin.saveResource("price.yml", false); }
            catch (IllegalArgumentException ex) {
                try { file.createNewFile(); }
                catch (IOException e) { plugin.getLogger().severe("Could not create price.yml: " + e.getMessage()); }
            }
        }
        prices = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (Material material : Material.values()) {
            if (!material.isItem() || material.isAir()) continue;
            String key = material.name();
            if (!prices.contains(key)) {
                prices.set(key, defaultPrice(material));
                changed = true;
            }
        }
        if (changed || prices.getKeys(false).isEmpty()) save();
    }

    public synchronized void save() {
        try { prices.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save price.yml: " + e.getMessage()); }
    }

    public synchronized double getPrice(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0.0;
        return Math.max(0.0, prices.getDouble(item.getType().name(), 0.0));
    }

    public synchronized boolean isSellable(ItemStack item) { return getPrice(item) > 0.0; }

    private double defaultPrice(Material material) {
        String n = material.name().toLowerCase(Locale.ROOT);
        // Admin-only blocks/items are deliberately listed but disabled by default.
        if (n.contains("command_block") || n.equals("barrier") || n.equals("debug_stick")
                || n.contains("structure_block") || n.equals("structure_void") || n.equals("jigsaw")
                || n.equals("light") || n.equals("bedrock") || n.equals("end_portal_frame")
                || n.equals("knowledge_book") || n.equals("reinforced_deepslate")) return 0.0;
        if (n.contains("netherite") || n.equals("ancient_debris") || n.equals("nether_star")) return 35.0;
        if (n.contains("diamond")) return 12.0;
        if (n.contains("emerald")) return 9.0;
        if (n.contains("amethyst")) return 1.25;
        if (n.contains("gold") || n.contains("raw_gold")) return n.endsWith("block") ? 9.0 : 1.0;
        if (n.contains("iron") || n.contains("raw_iron")) return n.endsWith("block") ? 4.5 : 0.55;
        if (n.contains("copper") || n.contains("raw_copper")) return n.endsWith("block") ? 1.6 : 0.2;
        if (n.contains("coal") || n.contains("charcoal")) return n.endsWith("block") ? 1.8 : 0.2;
        if (n.contains("lapis")) return 0.35;
        if (n.contains("redstone")) return 0.18;
        if (n.contains("quartz")) return 0.4;
        if (n.contains("ore")) return 0.8;
        if (n.contains("spawner")) return 150.0;
        if (n.contains("shulker_shell")) return 4.0;
        if (n.contains("elytra")) return 25.0;
        if (n.contains("trident") || n.contains("mace") || n.contains("heavy_core")) return 15.0;
        if (n.contains("totem_of_undying") || n.contains("netherite_upgrade_smithing_template")) return 8.0;
        if (n.endsWith("_spawn_egg")) return 1.0;
        if (n.contains("wheat") || n.contains("carrot") || n.contains("potato") || n.contains("beetroot")
                || n.contains("melon_slice") || n.contains("pumpkin") || n.contains("sugar_cane")
                || n.contains("cactus") || n.contains("bamboo") || n.contains("cocoa_beans")
                || n.contains("sweet_berries") || n.contains("glow_berries") || n.contains("nether_wart")) return 0.12;
        if (n.endsWith("_log") || n.endsWith("_stem") || n.endsWith("_hyphae")) return 0.12;
        if (n.endsWith("_planks") || n.endsWith("_wood") || n.endsWith("_sign") || n.endsWith("_hanging_sign")) return 0.08;
        if (n.contains("fish") || n.contains("beef") || n.contains("porkchop") || n.contains("chicken")
                || n.contains("mutton") || n.contains("rabbit") || n.contains("cod") || n.contains("salmon")) return 0.22;
        if (n.contains("wither_skeleton_skull") || n.contains("dragon_head") || n.contains("wither_rose")) return 4.0;
        if (n.contains("armor") || n.endsWith("_helmet") || n.endsWith("_chestplate") || n.endsWith("_leggings") || n.endsWith("_boots")) {
            if (n.contains("netherite")) return 30.0;
            if (n.contains("diamond")) return 10.0;
            if (n.contains("iron")) return 2.0;
        }
        if (n.contains("pickaxe") || n.contains("axe") || n.contains("sword") || n.contains("shovel") || n.contains("hoe")) {
            if (n.contains("netherite")) return 20.0;
            if (n.contains("diamond")) return 8.0;
            if (n.contains("iron")) return 1.5;
        }
        if (n.contains("rotten_flesh") || n.contains("string") || n.contains("bone") || n.contains("gunpowder")
                || n.contains("spider_eye") || n.contains("slime_ball") || n.contains("ender_pearl")
                || n.contains("blaze_rod") || n.contains("ghast_tear") || n.contains("leather")) return 0.2;
        if (n.contains("stone") || n.contains("dirt") || n.contains("gravel") || n.contains("sand") || n.contains("netherrack")) return 0.03;
        if (n.contains("bucket") || n.contains("minecart") || n.contains("boat") || n.contains("rail")) return 0.3;
        return 0.1;
    }
}