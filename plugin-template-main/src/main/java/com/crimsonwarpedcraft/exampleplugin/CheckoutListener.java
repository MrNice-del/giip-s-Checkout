package com.crimsonwarpedcraft.exampleplugin;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class CheckoutListener implements Listener {
  private final CheckoutPlugin plugin;

  CheckoutListener(CheckoutPlugin plugin) {
    this.plugin = plugin;
  }

  void open(Player player) {
    if (plugin.hasPaid(player)) {
      player.sendActionBar(Component.text(plugin.text("paid")));
      return;
    }
    Inventory inv = Bukkit.createInventory(player, 27, Component.text(plugin.menuTitle()));
    CheckoutPlugin.Requirement requirement = plugin.getRequirement(player);
    ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
    for (int i = 0; i < 27; i++) {
      inv.setItem(i, filler);
    }
    long remaining = Math.max(0, plugin.getDeadline(player) - System.currentTimeMillis());
    long daysLeft = TimeUnit.MILLISECONDS.toDays(remaining);
    long hoursLeft = TimeUnit.MILLISECONDS.toHours(remaining) % 24;
    inv.setItem(11, item(Material.PAPER, plugin.text("menu-info"),
        plugin.text("regular-fee"),
        plugin.text("contribution") + requirement.amount() + " " + pretty(requirement.material()),
        plugin.text("rhythm") + plugin.getInterval() / 86_400_000L + plugin.text("days"),
        plugin.text("deadline") + daysLeft + plugin.text("days") + ", " + hoursLeft
            + plugin.text("hours-short"),
        plugin.text("ban-duration") + plugin.getBanDuration() / 86_400_000L
            + plugin.text("ban"),
        plugin.text("deposit-instruction"),
        plugin.text("matching-items"),
        plugin.text("excess-items")));
    inv.setItem(13, null);
    inv.setItem(15, item(requirement.material(), plugin.text("payment-item"),
        plugin.text("place-items"),
        plugin.text("required") + requirement.amount() + " " + pretty(requirement.material())));
    player.openInventory(inv);
  }

  void showTime(Player player) {
    long left = Math.max(0, plugin.getDeadline(player) - System.currentTimeMillis());
    long days = TimeUnit.MILLISECONDS.toDays(left);
    long hours = TimeUnit.MILLISECONDS.toHours(left) % 24;
    long mins = TimeUnit.MILLISECONDS.toMinutes(left) % 60;
    player.sendMessage(plugin.text("next-payment") + days + plugin.text("day-short")
        + hours + "h " + mins + plugin.text("minute-short") + ".");
  }

  @EventHandler
  public void join(PlayerJoinEvent event) {
    plugin.getDeadline(event.getPlayer());
  }

  @EventHandler
  public void click(InventoryClickEvent event) {
    if (!event.getView().title().equals(Component.text(plugin.menuTitle()))) {
      return;
    }
    int slot = event.getRawSlot();
    if (slot >= 0 && slot < event.getView().getTopInventory().getSize() && slot != 13) {
      event.setCancelled(true);
    }
    if (slot == 13 && event.getCursor() != null && event.getCursor().getType() != Material.AIR
        && event.getCursor().getType() != plugin.getRequirement((Player) event.getWhoClicked()).material()) {
      event.setCancelled(true);
    }
    if (event.getClick().isShiftClick()) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void drag(InventoryDragEvent event) {
    if (!event.getView().title().equals(Component.text(plugin.menuTitle()))) {
      return;
    }
    for (int slot : event.getRawSlots()) {
      if (slot < event.getView().getTopInventory().getSize() && slot != 13) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler
  public void close(InventoryCloseEvent event) {
    if (!event.getView().title().equals(Component.text(plugin.menuTitle()))
        || !(event.getPlayer() instanceof Player player)) {
      return;
    }
    ItemStack deposited = event.getView().getTopInventory().getItem(13);
    CheckoutPlugin.Requirement requirement = plugin.getRequirement(player);
    int count = deposited == null || deposited.getType() != requirement.material()
        ? 0 : deposited.getAmount();
    if (count >= requirement.amount()) {
      int remainder = count - requirement.amount();
      if (remainder > 0) {
        returnItems(player, new ItemStack(requirement.material(), remainder));
      }
      plugin.paid(player);
      player.playSound(player, Sound.BLOCK_CHAIN_PLACE, 1, 1);
      player.sendActionBar(Component.text(plugin.text("paid-success")));
    } else {
      if (deposited != null) {
        returnItems(player, deposited);
      }
      player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
      player.sendActionBar(Component.text(plugin.text("paid-failure")));
    }
  }

  private void returnItems(Player player, ItemStack stack) {
    player.getInventory().addItem(stack).values().forEach(item ->
        player.getWorld().dropItemNaturally(player.getLocation(), item));
  }

  private ItemStack item(Material material, String name, String... lore) {
    ItemStack stack = new ItemStack(material);
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(Component.text(name));
    meta.lore(Arrays.stream(lore).map(Component::text).toList());
    if (!stack.setItemMeta(meta)) {
      throw new IllegalStateException("Could not apply item metadata.");
    }
    return stack;
  }

  private String pretty(Material material) {
    return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
  }
}
