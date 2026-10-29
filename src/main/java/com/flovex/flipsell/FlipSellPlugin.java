package com.flovex.flipsell;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FlipSellPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final int GUI_SIZE = 54;
    private static final int INPUT_SLOTS = 45;
    private static final int SELL_SLOT = 53;
    private Economy economy;
    private PriceManager priceManager;
    private NamespacedKey wandKey;
    private NamespacedKey usesKey;
    private NamespacedKey previewKey;
    private final Map<UUID, SellMenuHolder> openMenus = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        priceManager = new PriceManager(this);
        wandKey = new NamespacedKey(this, "sell_wand");
        usesKey = new NamespacedKey(this, "sell_uses");
        previewKey = new NamespacedKey(this, "preview_lore");
        setupEconomy();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("sell") != null) getCommand("sell").setExecutor(this);
        if (getCommand("sellwand") != null) { getCommand("sellwand").setExecutor(this); getCommand("sellwand").setTabCompleter(this); }
        if (getCommand("flipsell") != null) { getCommand("flipsell").setExecutor(this); getCommand("flipsell").setTabCompleter(this); }
        getLogger().info("FlipSell enabled. Sell prices are configured in plugins/FlipSell/price.yml.");
        if (economy == null) getLogger().warning("Vault economy provider not found; sales will be disabled until Vault and an economy plugin are installed.");
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) { economy = null; return false; }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) { economy = null; return false; }
        economy = rsp.getProvider();
        return economy != null;
    }

    @Override public void onDisable() {
        for (UUID uuid : new ArrayList<>(openMenus.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            SellMenuHolder holder = openMenus.get(uuid);
            if (player != null && holder != null) returnContents(player, holder.getInventory());
        }
        openMenus.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("sell")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
            if (!sender.hasPermission("flipsell.sell")) { message(sender, "no-permission"); return true; }
            openSellMenu(player);
            return true;
        }
        if (cmd.equals("sellwand")) {
            if (!sender.hasPermission("flipsell.sellwand.give")) { message(sender, "no-permission"); return true; }
            if (args.length != 3 || !args[0].equalsIgnoreCase("give")) { message(sender, "wand-usage"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(color("&cThat player must be online.")); return true; }
            int uses;
            try { uses = Integer.parseInt(args[2]); }
            catch (NumberFormatException ex) { uses = 0; }
            if (uses <= 0) { message(sender, "wand-invalid-uses"); return true; }
            ItemStack wand = createWand(uses);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(wand);
            for (ItemStack leftover : leftovers.values()) target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            message(sender, "wand-given", "{player}", target.getName(), "{uses}", Integer.toString(uses));
            message(target, "wand-received", "{uses}", Integer.toString(uses));
            return true;
        }
        if (cmd.equals("flipsell")) {
            if (!sender.hasPermission("flipsell.admin")) { message(sender, "no-permission"); return true; }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig(); priceManager.reload(); setupEconomy(); message(sender, "reloaded"); return true;
            }
            sender.sendMessage(color("&eUsage: /flipsell reload"));
            return true;
        }
        return false;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sellwand")) {
            if (args.length == 1) return prefix(List.of("give"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) return prefix(List.of("1", "10", "100", "500"), args[2]);
        }
        if (command.getName().equalsIgnoreCase("flipsell") && args.length == 1) return prefix(List.of("reload"), args[0]);
        return List.of();
    }

    private List<String> prefix(List<String> source, String input) {
        String start = input.toLowerCase(Locale.ROOT);
        return source.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).toList();
    }

    private void openSellMenu(Player player) {
        SellMenuHolder holder = new SellMenuHolder();
        String title = color(getConfig().getString("settings.gui-title", "&8Sell Items"));
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inventory);
        Material filler = parseMaterial(getConfig().getString("settings.filler-material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        ItemStack pane = namedItem(filler, " ", List.of());
        for (int slot = INPUT_SLOTS; slot < GUI_SIZE; slot++) if (slot != SELL_SLOT) inventory.setItem(slot, pane.clone());
        openMenus.put(player.getUniqueId(), holder);
        refreshPreview(inventory);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SellMenuHolder holder)) return;
        int raw = event.getRawSlot();
        if (raw == SELL_SLOT) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) sellGuiItems(player, holder.getInventory());
            return;
        }
        if (raw >= INPUT_SLOTS && raw < GUI_SIZE) { event.setCancelled(true); return; }
        if (raw < 0) { event.setCancelled(true); return; }
        if (event.isShiftClick() || event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.NUMBER_KEY) {
            event.setCancelled(true);
            return;
        }
        // Remove temporary preview lore before Bukkit performs normal stack/merge logic.
        // It is reapplied on the next tick so previews do not interfere with stacking.
        stripPreviewFromInventory(holder.getInventory());
        ItemStack cursor = event.getCursor();
        stripPreview(cursor);
        event.setCursor(cursor);
        Bukkit.getScheduler().runTask(this, () -> {
            if (event.getWhoClicked() instanceof Player player) cleanPlayerPreview(player);
            if (holder.getInventory() != null && holder.getInventory().getViewers().size() > 0) refreshPreview(holder.getInventory());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SellMenuHolder holder)) return;
        for (int raw : event.getRawSlots()) {
            if (raw >= INPUT_SLOTS && raw < GUI_SIZE) { event.setCancelled(true); return; }
        }
        stripPreviewFromInventory(holder.getInventory());
        ItemStack dragCursor = event.getCursor();
        stripPreview(dragCursor);
        event.setCursor(dragCursor);
        Bukkit.getScheduler().runTask(this, () -> {
            if (event.getWhoClicked() instanceof Player player) cleanPlayerPreview(player);
            refreshPreview(holder.getInventory());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SellMenuHolder holder)) return;
        Player player = (Player) event.getPlayer();
        openMenus.remove(player.getUniqueId());
        cleanPlayerPreview(player);
        returnContents(player, holder.getInventory());
    }

    private void sellGuiItems(Player player, Inventory inventory) {
        if (economy == null && !setupEconomy()) { message(player, "no-economy"); return; }
        double total = 0.0;
        int items = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            double price = priceManager.getPrice(item);
            if (price > 0.0) { total += price * item.getAmount(); items += item.getAmount(); }
        }
        if (items == 0 || total <= 0.0) { message(player, "nothing-to-sell"); return; }
        EconomyResponse response = economy.depositPlayer((OfflinePlayer) player, total);
        if (response == null || !response.transactionSuccess()) {
            player.sendMessage(color("&cThe economy rejected the sale. No items were removed."));
            return;
        }
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || priceManager.getPrice(item) <= 0.0) continue;
            inventory.setItem(slot, null);
        }
        message(player, "sold", "{items}", Integer.toString(items), "{total}", money(total));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
        refreshPreview(inventory);
    }

    private void returnContents(Player player, Inventory inventory) {
        if (inventory == null) return;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            stripPreview(item);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack rest : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
            inventory.setItem(slot, null);
        }
    }

    private void refreshPreview(Inventory inventory) {
        if (inventory == null) return;
        double total = 0.0;
        int itemCount = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            stripPreview(item);
            double unit = priceManager.getPrice(item);
            int amount = item.getAmount();
            if (unit > 0) {
                double stackTotal = unit * amount;
                total += stackTotal;
                itemCount += amount;
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(color("&0FS_PREVIEW"));
                    lore.add(color("&8FlipSell Preview"));
                    lore.add(color("&7Price each: &a" + money(unit)));
                    lore.add(color("&7Stack total: &a" + money(stackTotal)));
                    meta.setLore(lore);
                    meta.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
                    item.setItemMeta(meta);
                }
            } else {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(color("&0FS_PREVIEW"));
                    lore.add(color("&cNo sell price configured"));
                    meta.setLore(lore);
                    meta.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
                    item.setItemMeta(meta);
                }
            }
        }
        Material buttonMaterial = parseMaterial(getConfig().getString("settings.sell-button-material", "EMERALD_BLOCK"), Material.EMERALD_BLOCK);
        String name = color(getConfig().getString("settings.sell-button-name", "&a&lSELL ITEMS"));
        final int displayedItems = itemCount;
        final double displayedTotal = total;
        List<String> lore = getConfig().getStringList("settings.sell-button-lore").stream()
                .map(s -> color(s.replace("{items}", Integer.toString(displayedItems)).replace("{total}", money(displayedTotal)))).toList();
        if (lore.isEmpty()) lore = List.of(color("&7Items: &f" + itemCount), color("&7Total: &a" + money(total)), color("&eClick to sell"));
        inventory.setItem(SELL_SLOT, namedItem(buttonMaterial, name, lore));
    }

    private void stripPreviewFromInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) stripPreview(inventory.getItem(slot));
    }

    private void cleanPlayerPreview(Player player) {
        stripPreview(player.getItemOnCursor());
        for (ItemStack item : player.getInventory().getContents()) stripPreview(item);
        for (ItemStack item : player.getInventory().getArmorContents()) stripPreview(item);
        stripPreview(player.getInventory().getItemInOffHand());
    }

    private void stripPreview(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(previewKey, PersistentDataType.BYTE)) return;
        if (meta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : meta.getLore()) {
                if (line.equals(color("&0FS_PREVIEW"))) break;
                lore.add(line);
            }
            meta.setLore(lore.isEmpty() ? null : lore);
        }
        meta.getPersistentDataContainer().remove(previewKey);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand().name().equals("OFF_HAND")) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemStack held = event.getItem();
        if (!isWand(held)) return;
        if (!(event.getClickedBlock().getState() instanceof Chest chest)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (economy == null && !setupEconomy()) { message(player, "no-economy"); return; }
        Inventory container = chest.getInventory(); // includes both halves of a double chest
        int itemCount = 0;
        double total = 0.0;
        for (ItemStack item : container.getStorageContents()) {
            if (item == null || item.getType().isAir()) continue;
            double price = priceManager.getPrice(item);
            if (price > 0.0) { total += price * item.getAmount(); itemCount += item.getAmount(); }
        }
        if (itemCount == 0 || total <= 0.0) { message(player, "wand-empty"); return; }
        EconomyResponse response = economy.depositPlayer((OfflinePlayer) player, total);
        if (response == null || !response.transactionSuccess()) {
            player.sendMessage(color("&cThe economy rejected the sale. Nothing was removed from the chest."));
            return;
        }
        ItemStack[] contents = container.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir() && priceManager.getPrice(item) > 0.0) contents[i] = null;
        }
        container.setStorageContents(contents);
        int uses = Math.max(0, held.getItemMeta().getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, 1) - 1);
        if (uses == 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            message(player, "wand-exhausted");
        } else {
            ItemMeta meta = held.getItemMeta();
            meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
            meta.setLore(wandLore(uses));
            held.setItemMeta(meta);
            player.getInventory().setItemInMainHand(held);
            message(player, "wand-sold", "{items}", Integer.toString(itemCount), "{total}", money(total), "{uses}", Integer.toString(uses));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
    }

    private ItemStack createWand(int uses) {
        ItemStack item = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&d&lSell Wand"));
        meta.setLore(wandLore(uses));
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> wandLore(int uses) {
        return List.of(color("&7Instantly sell priced items inside a chest."), color("&7The chest is not broken."), color(""), color("&7Uses remaining: &d" + uses), color("&eRight-click a chest to sell"));
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }

    private Material parseMaterial(String name, Material fallback) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return fallback; }
    }

    private String money(double amount) {
        int decimals = Math.max(0, Math.min(4, getConfig().getInt("settings.money-decimals", 2)));
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) pattern.append('.').append("0".repeat(decimals));
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat(pattern.toString(), symbols);
        format.setGroupingUsed(true);
        return getConfig().getString("settings.currency-symbol", "$") + format.format(amount);
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    private void message(CommandSender sender, String path, String... replacements) {
        String prefix = color(getConfig().getString("messages.prefix", "&8[&dFlipSell&8] &r"));
        String body = getConfig().getString("messages." + path, "&cMissing message: " + path);
        for (int i = 0; i + 1 < replacements.length; i += 2) body = body.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(prefix + color(body));
    }
}