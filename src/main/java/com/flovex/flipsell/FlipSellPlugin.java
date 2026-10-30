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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private NamespacedKey worthLoreKey;
    private NamespacedKey worthLineKey;
    private NamespacedKey worthIndexKey;
    private final Map<UUID, SellMenuHolder> openMenus = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        priceManager = new PriceManager(this);
        wandKey = new NamespacedKey(this, "sell_wand");
        usesKey = new NamespacedKey(this, "sell_uses");
        previewKey = new NamespacedKey(this, "preview_lore");
        worthLoreKey = new NamespacedKey(this, "worth_lore");
        worthLineKey = new NamespacedKey(this, "worth_lore_line");
        worthIndexKey = new NamespacedKey(this, "worth_lore_index");
        setupEconomy();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("sell") != null) getCommand("sell").setExecutor(this);
        if (getCommand("sellwand") != null) { getCommand("sellwand").setExecutor(this); getCommand("sellwand").setTabCompleter(this); }
        if (getCommand("flipsell") != null) { getCommand("flipsell").setExecutor(this); getCommand("flipsell").setTabCompleter(this); }
        // Apply worth lore to player inventories and ender chests. The periodic pass
        // also refreshes stack totals after consuming, splitting, or otherwise changing amounts.
        Bukkit.getScheduler().runTaskTimer(this, this::updateAllPlayerWorthLore, 20L, 20L);
        for (Player online : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTask(this, () -> updatePlayerWorthLore(online));
        }
        getLogger().info("FlipSell enabled. Worth lore is maintained across player inventories.");
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
        // Leave the GUI background empty: no filler glass panes.
        openMenus.put(player.getUniqueId(), holder);
        refreshPreview(inventory);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnyInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        ItemStack current = event.getCurrentItem();

        // Never rewrite the entire PlayerInventory during a click. In particular, do not
        // call setItem() on armor slots while vanilla is moving armor; this can desync clicks.
        // Vanilla compares item metadata when stacking. Remove our generated worth
        // metadata from all ordinary storage candidates before Bukkit processes any click
        // (including shift-click, number-key swap, double-click collect and cursor merges).
        // Armor/offhand are deliberately excluded here to avoid mutating equipped slots.
        stripWorthFromPlayerStorage(player);
        stripWorthLore(event.getCursor());
        if (event.getClickedInventory() != null && event.getClickedInventory() != player.getInventory()) {
            stripWorthFromInventory(event.getClickedInventory());
        }
        if (top.getHolder(false) instanceof SellMenuHolder) {
            stripPreviewFromInventory(top);
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            stripWorthLore(player.getInventory().getItem(event.getHotbarButton()));
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            updatePlayerWorthLore(player);
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof SellMenuHolder holder) {
                refreshPreview(holder.getInventory());
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnyInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        ItemStack cursor = event.getOldCursor();
        stripWorthLore(cursor);
        stripWorthFromPlayerStorage(player);
        stripWorthFromInventory(top);
        if (top.getHolder(false) instanceof SellMenuHolder) stripPreviewFromInventory(top);
        for (int raw : event.getRawSlots()) {
            if (raw >= top.getSize()) {
                int playerSlot = event.getView().convertSlot(raw);
                if (playerSlot >= 0 && playerSlot < 36) stripWorthLore(player.getInventory().getItem(playerSlot));
            }
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            updatePlayerWorthLore(player);
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof SellMenuHolder holder) {
                refreshPreview(holder.getInventory());
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        ItemStack source = event.getEntity().getItemStack();
        ItemStack target = event.getTarget().getItemStack();
        stripWorthLore(source);
        stripWorthLore(target);
        event.getEntity().setItemStack(source);
        event.getTarget().setItemStack(target);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack dropped = event.getItem().getItemStack();
        stripWorthFromMatchingPlayerStorage(player, dropped);
        stripWorthLore(dropped);
        event.getItem().setItemStack(dropped);
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) updatePlayerWorthLore(player);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();
        stripWorthFromMatchingPlayerStorage(player, dropped);
        stripWorthLore(dropped);
        event.getItemDrop().setItemStack(dropped);
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) updatePlayerWorthLore(player);
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) updatePlayerWorthLore(player);
        });
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
        if (event.isShiftClick()) {
            // Shift-click from the player's inventory is a supported way to add items to the sell menu.
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                ItemStack current = event.getCurrentItem();
                if (current != null && !current.getType().isAir()) {
                    if (raw >= GUI_SIZE) {
                        // Clear temporary worth lore before stack similarity checks, so equal items merge normally.
                        stripPreviewFromInventory(holder.getInventory());
                        ItemStack moving = current.clone();
                        stripPreview(moving);
                        int moved = moveIntoSellInput(holder.getInventory(), moving);
                        if (moved >= current.getAmount()) {
                            event.setCurrentItem(null);
                        } else if (moved > 0) {
                            current.setAmount(current.getAmount() - moved);
                            event.setCurrentItem(current);
                        }
                    } else if (raw >= 0 && raw < INPUT_SLOTS) {
                        ItemStack moving = current.clone();
                        stripPreview(moving);
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(moving);
                        int left = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                        int moved = current.getAmount() - left;
                        if (moved >= current.getAmount()) {
                            event.setCurrentItem(null);
                        } else if (moved > 0) {
                            current.setAmount(current.getAmount() - moved);
                            event.setCurrentItem(current);
                        }
                    }
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    cleanPlayerPreview(player);
                    refreshPreview(holder.getInventory());
                });
            }
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.NUMBER_KEY) {
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
            double unit = priceManager.getPrice(item); // Custom display names do not affect material pricing.
            int amount = item.getAmount();
            double stackTotal = unit * amount;
            if (unit > 0) {
                total += stackTotal;
                itemCount += amount;
            }
            // The same worth-lore updater runs for menu items and normal player inventories.
            updateWorthLore(item);
            inventory.setItem(slot, item);
        }
        Material buttonMaterial = parseMaterial(getConfig().getString("settings.sell-button-material", "LIME_STAINED_GLASS_PANE"), Material.LIME_STAINED_GLASS_PANE);
        String name = color(getConfig().getString("settings.sell-button-name", "&a&lSELL ITEMS"));
        // The sell button has exactly one lore line, with no extra text or item count.
        // Example: "&a$ &f12.01k"
        List<String> lore = List.of(color("&a$ &f" + compactAmount(total)));
        inventory.setItem(SELL_SLOT, namedItem(buttonMaterial, name, lore));
    }

    private int moveIntoSellInput(Inventory gui, ItemStack moving) {
        int initial = moving.getAmount();
        int remaining = initial;
        // Merge into matching stacks first.
        for (int slot = 0; slot < INPUT_SLOTS && remaining > 0; slot++) {
            ItemStack existing = gui.getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(moving)) continue;
            int room = Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            if (room == 0) continue;
            int transfer = Math.min(room, remaining);
            existing.setAmount(existing.getAmount() + transfer);
            remaining -= transfer;
        }
        // Then use empty sell slots.
        for (int slot = 0; slot < INPUT_SLOTS && remaining > 0; slot++) {
            ItemStack existing = gui.getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;
            int transfer = Math.min(moving.getMaxStackSize(), remaining);
            ItemStack placed = moving.clone();
            placed.setAmount(transfer);
            stripPreview(placed);
            gui.setItem(slot, placed);
            remaining -= transfer;
        }
        return initial - remaining;
    }

    private void updateAllPlayerWorthLore() {
        for (Player player : Bukkit.getOnlinePlayers()) updatePlayerWorthLore(player);
    }

    private void updatePlayerWorthLore(Player player) {
        // PlayerInventory mixes storage, armor and offhand slots. Update them via their
        // dedicated APIs instead of looping setItem(0..size), which can duplicate armor
        // during inventory interactions on some server versions.
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();

        ItemStack[] storage = inventory.getStorageContents();
        boolean storageChanged = false;
        for (ItemStack item : storage) {
            if (item != null && updateWorthLore(item)) storageChanged = true;
        }
        if (storageChanged) inventory.setStorageContents(storage);

        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (ItemStack item : armor) {
            if (item != null && updateWorthLore(item)) armorChanged = true;
        }
        if (armorChanged) inventory.setArmorContents(armor);

        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand != null && updateWorthLore(offhand)) inventory.setItemInOffHand(offhand);

        updateInventoryWorthLore(player.getEnderChest());

        ItemStack cursor = player.getItemOnCursor();
        if (updateWorthLore(cursor)) player.setItemOnCursor(cursor);
    }

    private void updateInventoryWorthLore(Inventory inventory) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            if (updateWorthLore(item)) inventory.setItem(slot, item);
        }
    }

    private void updateWorthLoreInPlace(ItemStack item) {
        updateWorthLore(item);
    }

    private boolean updateWorthLore(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        stripPreview(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        var pdc = meta.getPersistentDataContainer();
        String previousLine = pdc.get(worthLineKey, PersistentDataType.STRING);
        Integer savedIndex = pdc.get(worthIndexKey, PersistentDataType.INTEGER);
        double totalWorth = priceManager.getPrice(item) * item.getAmount();
        String newLine = color("&a$ &f" + formatAmount(totalWorth));
        boolean hasMarker = pdc.has(worthLoreKey, PersistentDataType.BYTE);

        int previousIndex = previousLine == null ? -1
                : (savedIndex != null ? savedIndex : lore.lastIndexOf(previousLine));
        int categoryIndexBeforeMigration = findCategoryLoreIndex(lore);
        boolean legacyAppendedLine = previousLine != null && savedIndex == null
                && categoryIndexBeforeMigration >= 0 && categoryIndexBeforeMigration != previousIndex;

        if (previousLine != null && hasMarker && previousIndex >= 0 && previousIndex < lore.size()
                && lore.get(previousIndex).equals(previousLine) && !legacyAppendedLine) {
            if (previousLine.equals(newLine)) return false;
            lore.set(previousIndex, newLine);
            meta.setLore(lore);
            pdc.set(worthLineKey, PersistentDataType.STRING, newLine);
            pdc.set(worthIndexKey, PersistentDataType.INTEGER, previousIndex);
            item.setItemMeta(meta);
            return true;
        }

        // Migrate old appended worth lines to the category line's position, where possible.
        if (legacyAppendedLine && previousIndex >= 0 && previousIndex < lore.size()) {
            lore.remove(previousIndex);
        } else if (previousLine != null && previousIndex >= 0 && previousIndex < lore.size()
                && lore.get(previousIndex).equals(previousLine)) {
            // Keep a blank placeholder at this saved position while the stack is being moved.
            lore.set(previousIndex, "");
        }
        int categoryIndex = findCategoryLoreIndex(lore);
        if (categoryIndex >= 0) {
            lore.set(categoryIndex, newLine);
            pdc.set(worthIndexKey, PersistentDataType.INTEGER, categoryIndex);
        } else if (savedIndex != null && savedIndex >= 0 && savedIndex < lore.size()
                && lore.get(savedIndex).isEmpty()) {
            lore.set(savedIndex, newLine);
        } else {
            lore.add(newLine);
            pdc.set(worthIndexKey, PersistentDataType.INTEGER, lore.size() - 1);
        }

        meta.setLore(lore);
        pdc.set(worthLoreKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(worthLineKey, PersistentDataType.STRING, newLine);
        item.setItemMeta(meta);
        return true;
    }

    private int findCategoryLoreIndex(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String line = ChatColor.stripColor(lore.get(i));
            if (line == null) continue;
            String normalized = line.trim().toLowerCase(Locale.ROOT)
                    .replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
            normalized = normalized.replaceFirst("^category\\s*:\\s*", "").trim();
            if (java.util.Set.of(
                    "building blocks", "colored blocks", "natural blocks", "functional blocks",
                    "redstone blocks", "redstone", "transportation", "miscellaneous",
                    "foodstuffs", "food and drink", "food & drink", "ingredients",
                    "tools", "tools and utilities", "tools & utilities", "combat",
                    "decorations", "decorative blocks", "spawn eggs", "nature",
                    "building", "blocks", "equipment"
            ).contains(normalized)) return i;
        }
        return -1;
    }

    private void stripWorthFromPlayerStorage(Player player) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            stripWorthLore(inventory.getItem(slot));
        }
    }

    // Only touch the player's 36 storage slots here. Do not use a generic loop over
    // PlayerInventory.getSize(), because that also exposes armor/offhand implementation slots.
    private void stripWorthFromMatchingPlayerStorage(Player player, ItemStack match) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            if (match != null && !match.getType().isAir() && wouldStackIgnoringWorth(item, match)) {
                stripWorthLore(item);
            }
        }
    }

    private void stripWorthFromMatchingInventory(Inventory inventory, ItemStack match) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            if (match != null && !match.getType().isAir() && wouldStackIgnoringWorth(item, match)) {
                stripWorthLore(item);
            }
        }
    }

    private boolean wouldStackIgnoringWorth(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.getType() != second.getType()) return false;
        ItemStack a = first.clone();
        ItemStack b = second.clone();
        stripWorthLore(a);
        stripWorthLore(b);
        stripPreview(a);
        stripPreview(b);
        return a.isSimilar(b);
    }

    private void stripWorthFromInventory(Inventory inventory) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            stripWorthLore(inventory.getItem(slot));
        }
    }

    private boolean stripWorthLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        var pdc = meta.getPersistentDataContainer();
        String previousLine = pdc.get(worthLineKey, PersistentDataType.STRING);
        Integer savedIndex = pdc.get(worthIndexKey, PersistentDataType.INTEGER);
        boolean hadMarker = pdc.has(worthLoreKey, PersistentDataType.BYTE);
        if (previousLine == null && !hadMarker) return false;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (previousLine != null) {
            int index = savedIndex != null ? savedIndex : lore.lastIndexOf(previousLine);
            if (savedIndex != null && index >= 0 && index < lore.size() && lore.get(index).equals(previousLine)) {
                // Keep a blank at the replaced category/worth position so the category never reappears.
                lore.set(index, "");
                pdc.set(worthIndexKey, PersistentDataType.INTEGER, index);
            } else {
                // Legacy versions appended the worth line at the end. Remove that old line cleanly.
                int fallback = lore.lastIndexOf(previousLine);
                if (fallback >= 0) lore.remove(fallback);
            }
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        pdc.remove(worthLoreKey);
        pdc.remove(worthLineKey);
        item.setItemMeta(meta);
        return true;
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
            List<String> currentLore = new ArrayList<>(meta.getLore());
            int marker = currentLore.indexOf(color("&0FS_PREVIEW"));
            if (marker >= 0) {
                // Clean up preview lore created by the previous plugin version.
                currentLore = new ArrayList<>(currentLore.subList(0, marker));
            } else if (!currentLore.isEmpty()) {
                // New previews append exactly one payout line, so remove only that line.
                currentLore.remove(currentLore.size() - 1);
            }
            meta.setLore(currentLore.isEmpty() ? null : currentLore);
        }
        meta.getPersistentDataContainer().remove(previewKey);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand().name().equals("OFF_HAND")) return;
        Action action = event.getAction();
        if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) || event.getClickedBlock() == null) return;
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
        meta.setDisplayName(color("&d&lˢᵉˡˡʷᵃⁿᵈ"));
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
        return List.of(color("&7Instantly sell priced items inside a chest."), color("&7The chest is not broken."), color(""), color("&7Uses remaining: &d" + uses), color("&eLeft- or right-click a chest to sell"));
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

    private String formatAmount(double amount) {
        int decimals = Math.max(0, Math.min(4, getConfig().getInt("settings.money-decimals", 2)));
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) pattern.append('.').append("0".repeat(decimals));
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat(pattern.toString(), symbols);
        format.setGroupingUsed(true);
        return format.format(amount);
    }

    private String compactAmount(double amount) {
        double value = Math.max(0.0, amount);
        String[] suffixes = {"", "k", "m", "b", "t"};
        int suffix = 0;
        while (value >= 1000.0 && suffix < suffixes.length - 1) {
            value /= 1000.0;
            suffix++;
        }
        // Normalize rounding boundaries (e.g. 999,999 -> 1.00m instead of 1000.00k).
        if (suffix < suffixes.length - 1 && value >= 999.995) {
            value /= 1000.0;
            suffix++;
        }
        DecimalFormat compact = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return compact.format(value) + suffixes[suffix];
    }

    private String money(double amount) {
        return getConfig().getString("settings.currency-symbol", "$") + formatAmount(amount);
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    private void message(CommandSender sender, String path, String... replacements) {
        String prefix = color(getConfig().getString("messages.prefix", "&8[&dFlipSell&8] &r"));
        String body = getConfig().getString("messages." + path, "&cMissing message: " + path);
        for (int i = 0; i + 1 < replacements.length; i += 2) body = body.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(prefix + color(body));
    }
}