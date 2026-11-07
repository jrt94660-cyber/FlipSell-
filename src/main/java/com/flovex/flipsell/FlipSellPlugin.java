package com.flovex.flipsell;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.loot.Lootable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
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
import org.bukkit.event.inventory.InventoryOpenEvent;
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
    private static final int WORTH_SEARCH_SLOT = 13;
    private Economy economy;
    private PriceManager priceManager;
    private FileConfiguration sellWandConfig;
    private NamespacedKey wandKey;
    private NamespacedKey usesKey;
    private NamespacedKey previewKey;
    private NamespacedKey worthLoreKey;
    private NamespacedKey worthLineKey;
    private NamespacedKey worthIndexKey;
    private final Map<UUID, SellMenuHolder> openMenus = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        reloadSellWandConfig();
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
        if (getCommand("worth") != null) getCommand("worth").setExecutor(this);
        if (getCommand("sellwand") != null) { getCommand("sellwand").setExecutor(this); getCommand("sellwand").setTabCompleter(this); }
        if (getCommand("flipsell") != null) { getCommand("flipsell").setExecutor(this); getCommand("flipsell").setTabCompleter(this); }
        // Restore display lore once inventories are loaded; refresh only after transactions finish.
        for (Player online : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTask(this, () -> updatePlayerWorthLore(online));
        }
        // Repair any items introduced by crafting, pickups, plugin rewards or
        // older versions. The updater is idempotent and only writes changed items.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) updatePlayerWorthLore(online);
        }, 20L, 20L);
        getLogger().info("FlipSell enabled. Worth lore updates run after inventory transactions.");
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
        if (cmd.equals("worth")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
            if (args.length >= 2 && args[0].equalsIgnoreCase("search")) {
                showWorthResult(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            } else {
                openWorthMenu(player);
            }
            return true;
        }
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
                reloadConfig(); reloadSellWandConfig(); priceManager.reload(); setupEconomy(); message(sender, "reloaded"); return true;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClickWorthRefresh(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Do not mutate items during the click event. Let Minecraft finish its
        // transaction first, then refresh the visible worth lore on the next tick.
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            // Decorate the player's inventory and only vanilla block-backed
            // containers. Never touch custom plugin shop inventories (e.g. SMPShop).
            updatePlayerWorthLore(player);
            updateVanillaContainerWorthLore(player.getOpenInventory().getTopInventory());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDragWorthRefresh(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            // Refresh the player inventory and a vanilla block container after the drag.
            updatePlayerWorthLore(player);
            updateVanillaContainerWorthLore(player.getOpenInventory().getTopInventory());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpenWorthRefresh(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder(false) instanceof org.bukkit.block.BlockState)) return;
        Bukkit.getScheduler().runTask(this, () -> updateInventoryWorthLore(inventory));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickupWorthRefresh(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) updatePlayerWorthLore(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDropWorthRefresh(PlayerDropItemEvent event) {
        var dropped = event.getItemDrop();
        Bukkit.getScheduler().runTask(this, () -> {
            if (!dropped.isValid()) return;
            ItemStack stack = dropped.getItemStack();
            if (updateWorthLore(stack)) dropped.setItemStack(stack);
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
        if (event.getView().getTopInventory().getHolder(false) instanceof WorthMenuHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == WORTH_SEARCH_SLOT && event.getWhoClicked() instanceof Player player) showWorthSearchDialog(player);
            return;
        }
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
                        // Work from a clone; never rewrite existing GUI stacks during the click.
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
        // Do not change item metadata while Bukkit is processing this click.
        Bukkit.getScheduler().runTask(this, () -> {
            if (event.getWhoClicked() instanceof Player player) cleanPlayerPreview(player);
            if (holder.getInventory() != null && holder.getInventory().getViewers().size() > 0) refreshPreview(holder.getInventory());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof WorthMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SellMenuHolder holder)) return;
        for (int raw : event.getRawSlots()) {
            if (raw >= INPUT_SLOTS && raw < GUI_SIZE) { event.setCancelled(true); return; }
        }
        // Do not change item metadata while Bukkit is processing this drag.
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
            boolean cleanedLegacyPreview = stripPreview(item);
            if (cleanedLegacyPreview) inventory.setItem(slot, item);
            double unit = priceManager.getPrice(item); // Custom display names do not affect material pricing.
            int amount = item.getAmount();
            double stackTotal = unit * amount;
            if (unit > 0) {
                total += stackTotal;
                itemCount += amount;
            }
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

    private void updateVanillaContainerWorthLore(Inventory inventory) {
        if (inventory == null || inventory.getHolder(false) instanceof SellMenuHolder
                || inventory.getHolder(false) instanceof WorthMenuHolder) return;
        if (inventory.getHolder(false) instanceof org.bukkit.block.BlockState) {
            updateInventoryWorthLore(inventory);
        }
    }

    private void openWorthMenu(Player player) {
        openWorthMenu(player, null);
    }

    private void openWorthMenu(Player player, Material result) {
        WorthMenuHolder holder = new WorthMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&8Worth Menu"));
        holder.setInventory(inventory);
        inventory.setItem(WORTH_SEARCH_SLOT, namedItem(Material.COMPASS,
                color("&b&lSEARCH ITEM"),
                List.of(color("&7Click to search an item's worth"), color("&8Uses your configured price.yml"))));
        if (result != null) {
            double unit = priceManager.getPrice(new ItemStack(result));
            inventory.setItem(15, namedItem(result,
                    color("&e&l" + result.name().toLowerCase(Locale.ROOT).replace('_', ' ')),
                    List.of(color("&7Per item: &a$ &f" + compactAmount(unit)),
                            color("&7Full stack: &a$ &f" + compactAmount(unit * result.getMaxStackSize())))));
        }
        player.openInventory(inventory);
    }

    private void showWorthSearchDialog(Player player) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Search Item Worth"))
                        .inputs(List.of(DialogInput.text("item", Component.text("Item name or material"))
                                .width(240).maxLength(64).labelVisible(true).initial("").build()))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Confirm"), Component.text("Search this item"), 100,
                                DialogAction.commandTemplate("worth search $(item)")),
                        ActionButton.create(Component.text("Cancel"), Component.text("Close without searching"), 100, null)
                )));
        player.showDialog(dialog);
    }

    private void showWorthResult(Player player, String input) {
        String normalized = input == null ? "" : input.trim().replace(' ', '_');
        Material material = Material.matchMaterial(normalized);
        if (material == null || !material.isItem() || material.isAir()) {
            player.sendMessage(color("&cUnknown item. Try a material name such as DIAMOND or DIAMOND_SWORD."));
            openWorthMenu(player);
            return;
        }
        double unit = priceManager.getPrice(new ItemStack(material));
        double stack = unit * material.getMaxStackSize();
        String display = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        player.sendMessage(color("&8&m--------------------------------"));
        player.sendMessage(color("&b&lWORTH &8» &f" + display));
        player.sendMessage(color("&7Per item: &a$ &f" + compactAmount(unit)));
        player.sendMessage(color("&7Full stack (" + material.getMaxStackSize() + "): &a$ &f" + compactAmount(stack)));
        player.sendMessage(color("&8&m--------------------------------"));
        openWorthMenu(player, material);
    }

    private void updatePlayerWorthLore(Player player) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && updateWorthLore(item)) inventory.setItem(slot, item);
        }
        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (int slot = 0; slot < armor.length; slot++) {
            ItemStack item = armor[slot];
            if (item != null && updateWorthLore(item)) armorChanged = true;
        }
        if (armorChanged) inventory.setArmorContents(armor);
        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand != null && updateWorthLore(offhand)) inventory.setItemInOffHand(offhand);
        // The cursor is refreshed only after the transaction has completed.
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && updateWorthLore(cursor)) player.setItemOnCursor(cursor);
    }

    private void updateInventoryWorthLore(Inventory inventory) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir() && updateWorthLore(item)) inventory.setItem(slot, item);
        }
    }

    private boolean updateWorthLore(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        var pdc = meta.getPersistentDataContainer();
        boolean hadWorth = pdc.has(worthLoreKey, PersistentDataType.BYTE);
        String oldLine = pdc.get(worthLineKey, PersistentDataType.STRING);
        Integer legacyIndex = pdc.get(worthIndexKey, PersistentDataType.INTEGER);

        double unitPrice = priceManager.getPrice(item);
        if (unitPrice <= 0.0) {
            if (!hadWorth && oldLine == null && legacyIndex == null) return false;
            if (oldLine != null) {
                lore.remove(oldLine);
            } else if (legacyIndex != null && legacyIndex >= 0 && legacyIndex < lore.size()) {
                String legacy = ChatColor.stripColor(lore.get(legacyIndex));
                if (legacy != null && legacy.startsWith("$ ") && legacy.endsWith(" each")) lore.remove((int) legacyIndex);
            }
            pdc.remove(worthLoreKey);
            pdc.remove(worthLineKey);
            pdc.remove(worthIndexKey);
            meta.setLore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
            return true;
        }

        // A fixed per-item line is intentional: stack-total lore changes with
        // amount and makes otherwise identical Minecraft stacks incompatible.
        String line = color("&a$ &f" + compactAmount(unitPrice) + " &7each");
        boolean alreadyCanonical = hadWorth && line.equals(oldLine)
                && lore.contains(line)
                && !pdc.has(worthIndexKey, PersistentDataType.INTEGER);
        if (alreadyCanonical) return false;

        // Migrate older lore versions to one canonical line at the end. The
        // same material + same original metadata now gets identical lore/PDC,
        // regardless of stack size or the old insertion index.
        if (oldLine != null) {
            lore.remove(oldLine);
        } else if (legacyIndex != null && legacyIndex >= 0 && legacyIndex < lore.size()) {
            String possibleLegacyLine = lore.get(legacyIndex);
            String plain = ChatColor.stripColor(possibleLegacyLine);
            if (plain != null && plain.startsWith("$ ") && plain.endsWith(" each")) {
                lore.remove((int) legacyIndex);
            }
        }
        lore.remove(line);
        lore.add(line);
        pdc.set(worthLoreKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(worthLineKey, PersistentDataType.STRING, line);
        pdc.remove(worthIndexKey);
        meta.setLore(lore);
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

    private void stripPreviewFromInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) stripPreview(inventory.getItem(slot));
    }

    private void cleanPlayerPreview(Player player) {
        stripPreview(player.getItemOnCursor());
        for (ItemStack item : player.getInventory().getContents()) stripPreview(item);
        for (ItemStack item : player.getInventory().getArmorContents()) stripPreview(item);
        stripPreview(player.getInventory().getItemInOffHand());
    }

    private boolean stripPreview(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(previewKey, PersistentDataType.BYTE)) return false;
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
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand().name().equals("OFF_HAND")) return;
        Action action = event.getAction();
        if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) || event.getClickedBlock() == null) return;
        ItemStack held = event.getItem();
        if (!isWand(held)) return;
        // Any vanilla block-backed container is supported: chests (including trapped
        // and naturally generated loot chests), barrels, shulker boxes, hoppers,
        // dispensers, droppers, furnaces and other Container blocks. Ender chests
        // are deliberately excluded because they are player-owned inventories.
        if (!(event.getClickedBlock().getState() instanceof Container blockContainer)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Inventory container = blockContainer.getInventory();
        if (container == null) return;

        // Loot-table chests generate their contents when first opened. Open them
        // once and sell on the following tick so the generated loot is included.
        if (blockContainer instanceof Lootable lootable && lootable.getLootTable() != null) {
            player.openInventory(container);
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.getOpenInventory().getTopInventory() == container) player.closeInventory();
                sellContainerContents(player, held, container);
            });
            return;
        }
        sellContainerContents(player, held, container);
    }

    private void sellContainerContents(Player player, ItemStack held, Inventory container) {
        if (!player.isOnline() || !isWand(held)) return;
        if (economy == null && !setupEconomy()) { message(player, "no-economy"); return; }
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
            player.sendMessage(color("&cThe economy rejected the sale. Nothing was removed from the container."));
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
            if (player.getInventory().getItemInMainHand().isSimilar(held)) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            message(player, "wand-exhausted");
        } else {
            ItemMeta meta = held.getItemMeta();
            meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
            meta.setLore(wandLore(uses));
            held.setItemMeta(meta);
            if (player.getInventory().getItemInMainHand().isSimilar(held)) player.getInventory().setItemInMainHand(held);
            message(player, "wand-sold", "{items}", Integer.toString(itemCount), "{total}", money(total), "{uses}", Integer.toString(uses));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
    }

    private void reloadSellWandConfig() {
        File file = new File(getDataFolder(), "sellwand.yml");
        if (!file.exists()) {
            getDataFolder().mkdirs();
            try { saveResource("sellwand.yml", false); }
            catch (IllegalArgumentException ex) { getLogger().severe("Missing bundled sellwand.yml resource."); }
        }
        sellWandConfig = YamlConfiguration.loadConfiguration(file);
    }

    private ItemStack createWand(int uses) {
        Material material = parseMaterial(sellWandConfig.getString("item.material", "NETHERITE_AXE"), Material.NETHERITE_AXE);
        if (!material.isItem() || material.isAir()) material = Material.NETHERITE_AXE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(sellWandConfig.getString("item.name", "&#A503FCᴀᴍᴇᴛʜʏsᴛ sᴇʟʟᴀxᴇ")));
        meta.setLore(wandLore(uses));
        for (String line : sellWandConfig.getStringList("item.enchantments")) {
            String[] parts = line.split(":", 2);
            if (parts.length != 2) continue;
            Enchantment enchantment = Enchantment.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
            if (enchantment == null) continue;
            try {
                int level = Integer.parseInt(parts[1].trim());
                if (level > 0) meta.addEnchant(enchantment, level, true);
            } catch (NumberFormatException ignored) { }
        }
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> wandLore(int uses) {
        List<String> lore = new ArrayList<>();
        for (String line : sellWandConfig.getStringList("item.lore")) {
            lore.add(color(line.replace("{uses}", Integer.toString(uses))));
        }
        return lore;
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
        // Donut-style compact currency: 950, 1.25k, 3.4m, 2b, 7t, 1q.
        // q is the largest suffix; values above that remain expressed in q.
        String[] suffixes = {"", "k", "m", "b", "t", "q"};
        int suffix = 0;
        while (value >= 1000.0 && suffix < suffixes.length - 1) {
            value /= 1000.0;
            suffix++;
        }
        int decimals = Math.max(0, Math.min(4, getConfig().getInt("settings.money-decimals", 2)));
        DecimalFormat compact = new DecimalFormat(
                decimals == 0 ? "0" : "0." + "#".repeat(decimals),
                DecimalFormatSymbols.getInstance(Locale.US)
        );
        compact.setGroupingUsed(false);
        return compact.format(value) + suffixes[suffix];
    }

    private String money(double amount) {
        return getConfig().getString("settings.currency-symbol", "$") + compactAmount(amount);
    }

    private String color(String text) {
        String input = text == null ? "" : text;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)&#([0-9a-f]{6})").matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(output);
        return ChatColor.translateAlternateColorCodes('&', output.toString());
    }

    private void message(CommandSender sender, String path, String... replacements) {
        String prefix = color(getConfig().getString("messages.prefix", "&8[&dFlipSell&8] &r"));
        String body = getConfig().getString("messages." + path, "&cMissing message: " + path);
        for (int i = 0; i + 1 < replacements.length; i += 2) body = body.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(prefix + color(body));
    }
}