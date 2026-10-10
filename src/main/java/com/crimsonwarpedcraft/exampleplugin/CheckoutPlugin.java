package com.crimsonwarpedcraft.exampleplugin;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Periodic player server-fee checkout. */
public final class CheckoutPlugin extends JavaPlugin implements Listener {
  private long interval;
  private long banDuration;
  private int amount;
  private Material currency;
  private String language;
  private CheckoutListener checkout;
  private final SecureRandom random = new SecureRandom();

  record Requirement(Material material, int amount) { }
  private record Candidate(Material material, int min, int max, double weight) { }

  @Override
  public void onEnable() {
    saveDefaultConfig();
    getConfig().options().copyDefaults(true);
    saveConfig();
    if (!loadSettings()) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    checkout = new CheckoutListener(this);
    getServer().getPluginManager().registerEvents(checkout, this);
    getServer().getPluginManager().registerEvents(this, this);
    java.util.Objects.requireNonNull(getCommand("payout")).setExecutor((sender, command, label, args) -> {
      if (!(sender instanceof Player player)) return true;
      // Pick up edits made directly in config.yml before building the menu.
      reloadConfig();
      if (!loadSettings()) {
        player.sendMessage(text("config-error"));
        return true;
      }
      checkout.open(player);
      return true;
    });
    java.util.Objects.requireNonNull(getCommand("checkouttime")).setExecutor((sender, command, label, args) -> {
      if (sender instanceof Player player) checkout.showTime(player);
      return true;
    });
    java.util.Objects.requireNonNull(getCommand("checkouttest")).setExecutor((sender, command, label, args) -> {
      if (!(sender instanceof Player player)) {
        sender.sendMessage("This test command can only be used in-game.");
        return true;
      }
      if (!player.hasPermission("giipscheckout.test")) {
        player.sendMessage(text("no-permission"));
        return true;
      }
      banForMissedCheckout(player);
      return true;
    });
    java.util.Objects.requireNonNull(getCommand("checkoutreload")).setExecutor((sender, command, label, args) -> {
      if (!sender.hasPermission("giipscheckout.reload")) {
        sender.sendMessage(text("no-permission"));
        return true;
      }
      reloadConfig();
      if (loadSettings()) {
        sender.sendMessage(text("reload-ok"));
      } else {
        sender.sendMessage(text("reload-error"));
      }
      return true;
    });
    java.util.Objects.requireNonNull(getCommand("checkoutpayments")).setExecutor((sender, command, label, args) -> {
      if (!sender.hasPermission("giipscheckout.payments")) {
        sender.sendMessage(text("no-permission"));
        return true;
      }
      showPayments(sender);
      return true;
    });
    Bukkit.getScheduler().runTaskTimer(this, this::enforceDeadlines, 20L, 20L * 20L);
  }

  private void showPayments(org.bukkit.command.CommandSender sender) {
    org.bukkit.configuration.ConfigurationSection players = getConfig().getConfigurationSection("players");
    sender.sendMessage(text("payments-title"));
    if (players == null || players.getKeys(false).isEmpty()) {
      sender.sendMessage(text("payments-empty"));
      return;
    }
    List<String> playerIds = new ArrayList<>(players.getKeys(false));
    playerIds.sort((first, second) -> playerName(first).compareToIgnoreCase(playerName(second)));
    for (String id : playerIds) {
      String path = "players." + id;
      String name = playerName(id);
      long due = getConfig().getLong(path + ".due");
      boolean paid = getConfig().getBoolean(path + ".paid") && due > System.currentTimeMillis();
      String status = paid
          ? ("de".equals(language) ? "§aBEZAHLT" : "§aPAID")
          : ("de".equals(language) ? "§cOFFEN" : "§cDUE");
      Requirement requirement = storedRequirement(path);
      String dueText = due > 0
          ? new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(due))
          : ("de".equals(language) ? "unbekannt" : "unknown");
      String dueLabel = "de".equals(language) ? "Fällig" : "Due";
      String paymentLabel = "de".equals(language) ? "Beitrag" : "Payment";
      sender.sendMessage("§8➔ §f" + name + " §7| " + status + " §7| " + dueLabel + ": §e" + dueText
          + " §7| " + paymentLabel + ": §f" + requirement.amount() + " " + requirement.material().name());
    }
  }

  private String playerName(String id) {
    String configuredName = getConfig().getString("players." + id + ".name", id);
    return java.util.Objects.requireNonNullElse(configuredName, id);
  }

  private Requirement storedRequirement(String playerPath) {
    String materialName = getConfig().getString(playerPath + ".requirement.material");
    Material material = materialName == null ? currency : Material.matchMaterial(materialName);
    if (material == null || !material.isItem()) material = currency;
    int requestedAmount = getConfig().getInt(playerPath + ".requirement.amount", amount);
    int safeAmount = Math.max(1, Math.min(requestedAmount, material.getMaxStackSize()));
    return new Requirement(material, safeAmount);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void customizeCheckoutBanScreen(PlayerLoginEvent event) {
    var entry = Bukkit.getBanList(BanList.Type.NAME).getBanEntry(event.getPlayer().getName());
    if (entry == null) {
      return;
    }
    String reason = entry.getReason();
    if (reason == null || !reason.contains("SERVER-CHECKOUT")) return;
    event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
    event.kickMessage(LegacyComponentSerializer.legacySection().deserialize(reason));
  }

  private boolean loadSettings() {
    FileConfiguration config = getConfig();
    if (config.contains("checkout.randomized.categories")) {
      // The weighted pool is intentionally internal and is no longer exposed in config.yml.
      config.set("checkout.randomized.categories", null);
      saveConfig();
    }
    long loadedInterval = Math.max(1, config.getLong("checkout.interval-days", 7)) * 86_400_000L;
    long loadedBanDuration = Math.max(1, config.getLong("ban.duration-days", 7)) * 86_400_000L;
    int loadedAmount = Math.max(1, config.getInt("checkout.amount", 5));
    Material loadedCurrency = Material.matchMaterial(java.util.Objects.requireNonNullElse(
        config.getString("checkout.item", "EMERALD"), "EMERALD"));
    if (loadedCurrency == null || !loadedCurrency.isItem()) {
      getLogger().severe("checkout.item must be a valid item.");
      return false;
    }
    interval = loadedInterval;
    banDuration = loadedBanDuration;
    amount = loadedAmount;
    currency = loadedCurrency;
    language = java.util.Objects.requireNonNullElse(
        config.getString("language", "en"), "en").equalsIgnoreCase("de") ? "de" : "en";
    return true;
  }

  String text(String key) {
    boolean de = "de".equals(language);
    return switch (key) {
      case "config-error" -> de ? "§cDie Checkout-Config ist ungültig. Bitte informiere das Serverteam."
          : "§cThe checkout config is invalid. Please contact a server administrator.";
      case "no-permission" -> de ? "§cDafür hast du keine Berechtigung."
          : "§cYou do not have permission to do that.";
      case "reload-ok" -> de ? "§aCheckout-Config wurde neu geladen."
          : "§aCheckout configuration reloaded.";
      case "reload-error" -> de ? "§cUngültige Config. Die bisherigen Werte bleiben aktiv."
          : "§cInvalid config. Previous settings remain active.";
      case "payments-title" -> de ? "§8§m---------------- §a§lCheckout-Zahlungen §8§m----------------"
          : "§8§m---------------- §a§lCheckout Payments §8§m----------------";
      case "payments-empty" -> de ? "§7Bisher wurden keine Spieler erfasst."
          : "§7No players have been tracked yet.";
      case "paid" -> de ? "§e sɪᴇ ʜᴀʙᴇɴ sᴄʜᴏɴ ɢᴇᴢᴀʜʟᴛ."
          : "§eYou have already paid.";
      case "menu-info" -> de ? "§e§lɪɴғᴏʀᴍᴀᴛɪᴏɴᴇɴ" : "§e§lɪɴғᴏʀᴍᴀᴛɪᴏɴ";
      case "regular-fee" -> de ? "§7Ein regelmäßiger Beitrag, der den Server unterstützt."
          : "§7A regular contribution that helps support the server.";
      case "contribution" -> de ? "§8➔ §fDein Beitrag: §a" : "§8➔ §fYour contribution: §a";
      case "rhythm" -> de ? "§8➔ §fRhythmus: §ealle " : "§8➔ §fSchedule: §eevery ";
      case "days" -> de ? " Tage" : " days";
      case "deadline" -> de ? "§8➔ §fDeine Frist: §e" : "§8➔ §fYour deadline: §e";
      case "hours-short" -> de ? " Std." : " hrs";
      case "ban-duration" -> de ? "§8➔ §fBei Fristablauf: §c" : "§8➔ §fIf overdue: §c";
      case "ban" -> de ? " Tage Bann" : " days ban";
      case "deposit-instruction" -> de ? "§7Lege deine Zahlung in den mittleren Slot."
          : "§7Place your payment in the center slot.";
      case "matching-items" -> de ? "§7Nur passende Items zählen zur Zahlung."
          : "§7Only the requested item counts toward payment.";
      case "excess-items" -> de ? "§7Überschuss kommt zurück; der Beitrag wird eingezogen."
          : "§7Extra items are returned; the required amount is collected.";
      case "payment-item" -> de ? "§a§lZahlung" : "§a§lPayment";
      case "place-items" -> de ? "§7Lege hier deine Items ab." : "§7Place your items here.";
      case "required" -> de ? "§8➔ §fBenötigt: §a" : "§8➔ §fRequired: §a";
      case "next-payment" -> de ? "§8[§aCheckout§8] §7Nächste Zahlung in §f"
          : "§8[§aCheckout§8] §7Next payment in §f";
      case "day-short" -> de ? "T " : "d ";
      case "minute-short" -> de ? "Min." : "min";
      case "paid-success" -> de ? "§aɪʜʀ ʙᴇᴛʀᴀɢ ɪsᴛ ɢᴇᴢᴀʜʟᴛ, ᴅᴇʀ sᴇʀᴠᴇʀ ᴅᴀɴᴋᴛ!"
          : "§aYour payment is complete. The server thanks you!";
      case "paid-failure" -> de ? "§c sɪᴇ ʜᴀʙᴇɴ ɴɪᴄʜᴛ ɢᴇɴᴜɢ, ᴜᴍ ᴅᴇɴ ʙᴇᴛʀᴀɢ ᴢᴜ ᴢᴀʜʟᴇɴ."
          : "§cYou do not have enough items to make this payment.";
      case "warning" -> de ? "ᴄʜᴇᴄᴋᴏᴜᴛ ғʀɪsᴛ ᴡɪʀᴅ ʙᴀʟᴅ ᴀʙʟᴀᴜғᴇɴ.\n/ʙᴇᴢᴀʜʟᴇɴ ᴜᴍ ᴢᴜ ᴢᴀʜʟᴇɴ."
          : "YOUR CHECKOUT DEADLINE IS APPROACHING.\nUSE /PAYOUT TO PAY.";
      case "ban-screen" -> de ? "§cDeine Zahlungsfrist ist abgelaufen.\n§7Du kannst dem Server wieder beitreten am\n"
          : "§cYour checkout deadline has expired.\n§7You can rejoin the server on\n";
      case "ban-footer" -> de ? "\n\n§8Wenn du glaubst, dass dies ein Fehler ist, wende dich an das Serverteam."
          : "\n\n§8If you believe this is a mistake, please contact the server team.";
      default -> key;
    };
  }

  String menuTitle() {
    return "§8" + ("de".equals(language)
        ? "ʙᴇᴢᴀʜʟᴜɴɢ ᴅᴇs sᴇʀᴠᴇʀs" : "sᴇʀᴠᴇʀ ᴄʜᴇᴄᴋᴏᴜᴛ");
  }

  Requirement getRequirement(Player player) {
    String path = "players." + player.getUniqueId() + ".requirement";
    String stored = getConfig().getString(path + ".material");
    Material material = stored == null ? null : Material.matchMaterial(stored);
    int count = getConfig().getInt(path + ".amount");
    if (material == null || count < 1 || count > material.getMaxStackSize()) {
      Requirement generated = generateRequirement();
      getConfig().set(path + ".material", generated.material().name());
      getConfig().set(path + ".amount", generated.amount());
      getConfig().set("players." + player.getUniqueId() + ".name", player.getName());
      saveConfig();
      return generated;
    }
    return new Requirement(material, count);
  }

  private Requirement generateRequirement() {
    if (!getConfig().getBoolean("checkout.randomized.enabled", true)) {
      return new Requirement(currency, Math.min(amount, currency.getMaxStackSize()));
    }
    List<Candidate> candidates = new ArrayList<>();
    addCandidates(candidates, 1.0,
        new Candidate(Material.EMERALD, 4, 8, 1.0),
        new Candidate(Material.IRON_INGOT, 16, 32, 1.0),
        new Candidate(Material.GOLD_INGOT, 10, 20, 1.0),
        new Candidate(Material.COPPER_INGOT, 32, 64, 1.0),
        new Candidate(Material.RAW_IRON, 24, 48, 1.0),
        new Candidate(Material.RAW_GOLD, 16, 32, 1.0),
        new Candidate(Material.RAW_COPPER, 32, 64, 1.0),
        new Candidate(Material.COAL, 32, 64, 1.0),
        new Candidate(Material.REDSTONE, 32, 64, 1.0),
        new Candidate(Material.LAPIS_LAZULI, 24, 48, 1.0),
        new Candidate(Material.DIAMOND, 2, 4, 1.0),
        new Candidate(Material.QUARTZ, 24, 48, 1.0),
        new Candidate(Material.AMETHYST_SHARD, 16, 32, 1.0),
        new Candidate(Material.OBSIDIAN, 8, 16, 1.0),
        new Candidate(Material.BRICK, 32, 64, 1.0),
        new Candidate(Material.GLASS, 32, 64, 1.0),
        new Candidate(Material.PRISMARINE_SHARD, 8, 16, 1.0),
        new Candidate(Material.SEA_LANTERN, 4, 8, 1.0),
        new Candidate(Material.PACKED_ICE, 16, 32, 1.0),
        new Candidate(Material.COBWEB, 8, 16, 1.0),
        new Candidate(Material.OAK_LOG, 32, 64, 1.0),
        new Candidate(Material.SPRUCE_LOG, 32, 64, 1.0),
        new Candidate(Material.BAMBOO, 32, 64, 1.0),
        new Candidate(Material.BOOK, 8, 16, 1.0),
        new Candidate(Material.BONE, 24, 48, 1.0),
        new Candidate(Material.STRING, 24, 48, 1.0),
        new Candidate(Material.GUNPOWDER, 16, 32, 1.0),
        new Candidate(Material.ROTTEN_FLESH, 32, 64, 1.0),
        new Candidate(Material.SLIME_BALL, 8, 16, 1.0),
        new Candidate(Material.ENDER_PEARL, 3, 6, 1.0),
        new Candidate(Material.PHANTOM_MEMBRANE, 4, 8, 1.0),
        new Candidate(Material.BREEZE_ROD, 4, 8, 1.0),
        new Candidate(Material.WHEAT, 32, 64, 1.0),
        new Candidate(Material.CARROT, 32, 64, 1.0),
        new Candidate(Material.POTATO, 32, 64, 1.0),
        new Candidate(Material.PUMPKIN, 8, 16, 1.0),
        new Candidate(Material.SUGAR_CANE, 32, 64, 1.0),
        new Candidate(Material.GOLDEN_APPLE, 1, 2, 1.0),
        new Candidate(Material.NETHER_WART, 16, 32, 1.0),
        new Candidate(Material.NETHERRACK, 64, 64, 1.0),
        new Candidate(Material.BLAZE_ROD, 4, 8, 1.0),
        new Candidate(Material.GHAST_TEAR, 1, 2, 1.0));
    addCandidates(candidates, 0.3,
        new Candidate(Material.SPONGE, 1, 2, 1.0),
        new Candidate(Material.NETHERITE_SCRAP, 1, 2, 1.0),
        new Candidate(Material.NETHER_STAR, 1, 1, 0.5));
    if (getConfig().getBoolean("checkout.randomized.include-end-items", false)) {
      addCandidates(candidates, 1.0,
          new Candidate(Material.END_STONE, 32, 64, 1.0),
          new Candidate(Material.CHORUS_FRUIT, 8, 16, 1.0),
          new Candidate(Material.PURPUR_BLOCK, 16, 32, 1.0),
          new Candidate(Material.END_ROD, 4, 8, 1.0),
          new Candidate(Material.SHULKER_SHELL, 2, 4, 0.3),
          new Candidate(Material.DRAGON_BREATH, 1, 3, 0.3),
          new Candidate(Material.END_CRYSTAL, 1, 2, 0.3));
    }
    double totalWeight = candidates.stream().mapToDouble(Candidate::weight).sum();
    if (totalWeight <= 0) return new Requirement(currency, Math.min(amount, currency.getMaxStackSize()));
    double draw = random.nextDouble(totalWeight);
    Candidate selected = candidates.get(candidates.size() - 1);
    for (Candidate candidate : candidates) {
      draw -= candidate.weight();
      if (draw < 0) {
        selected = candidate;
        break;
      }
    }
    int count = random.nextInt(selected.min(), selected.max() + 1);
    return new Requirement(selected.material(), count);
  }

  private void addCandidates(List<Candidate> pool, double groupWeight, Candidate... candidates) {
    for (Candidate candidate : candidates) {
      pool.add(new Candidate(candidate.material(), candidate.min(), candidate.max(),
          groupWeight * candidate.weight()));
    }
  }

  private void enforceDeadlines() {
    org.bukkit.configuration.ConfigurationSection players = getConfig().getConfigurationSection("players");
    if (players == null) return;
    for (String key : players.getKeys(false)) {
      String path = "players." + key;
      if (!getConfig().contains(path + ".due")) {
        // Old or partial config entries must never be interpreted as an expired deadline.
        Player trackedOnline = playerForKey(key);
        if (trackedOnline != null) {
          getDeadline(trackedOnline);
        }
        continue;
      }
      long due = getConfig().getLong(path + ".due");
      if (due <= 0) {
        Player trackedOnline = playerForKey(key);
        if (trackedOnline != null) getDeadline(trackedOnline);
        continue;
      }
      if (due <= System.currentTimeMillis()) {
        String name = java.util.Objects.requireNonNullElse(
            getConfig().getString("players." + key + ".name", key), key);
        Player online = playerForKey(key);
        banForMissedCheckout(name, online);
        getConfig().set("players." + key, null);
      } else {
        long remaining = due - System.currentTimeMillis();
        if (remaining <= 86_400_000L) {
          // The last 24 hours are the next checkout window, even if the previous cycle was paid.
          getConfig().set(path + ".paid", false);
        }
        if (remaining <= 86_400_000L && !getConfig().getBoolean(path + ".paid")
            && getConfig().getLong(path + ".reminded-due") != due) {
          Player online = playerForKey(key);
          if (online != null) {
            String warning = alternateColors(text("warning"));
            online.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(warning));
            online.playSound(online, org.bukkit.Sound.BLOCK_NOTE_BLOCK_GUITAR, 1.0f, 1.0f);
            getConfig().set(path + ".reminded-due", due);
          }
        }
      }
    }
    saveConfig();
  }

  private Player playerForKey(String key) {
    try {
      return Bukkit.getPlayer(java.util.UUID.fromString(key));
    } catch (IllegalArgumentException exception) {
      getLogger().warning("Ignoring invalid player UUID in checkout config: " + key);
      return null;
    }
  }

  private String alternateColors(String message) {
    StringBuilder colored = new StringBuilder();
    boolean red = true;
    for (int offset = 0; offset < message.length();) {
      int codePoint = message.codePointAt(offset);
      if (Character.isLetter(codePoint)) {
        colored.append(red ? "§c" : "§6");
        red = !red;
      }
      colored.appendCodePoint(codePoint);
      offset += Character.charCount(codePoint);
    }
    return colored.toString();
  }

  private void banForMissedCheckout(Player player) {
    banForMissedCheckout(player.getName(), player);
  }

  private void banForMissedCheckout(String name, Player online) {
    Date expires = new Date(System.currentTimeMillis() + banDuration);
    String expiry = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(expires);
    String reason = "§c☠ §lSERVER CHECKOUT §c☠\n\n" + text("ban-screen")
        + "§c§l" + expiry + text("ban-footer");
    Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, expires, "Server-Checkout");
    if (online != null) {
      getConfig().set("players." + online.getUniqueId(), null);
      saveConfig();
      online.kickPlayer(reason);
    }
  }

  long getInterval() { return interval; }
  long getBanDuration() { return banDuration; }
  int getAmount() { return amount; }
  Material getCurrency() { return currency; }
  long getDeadline(Player player) {
    String path = "players." + player.getUniqueId();
    long currentDue = getConfig().getLong(path + ".due", 0L);
    if (currentDue <= 0) {
      getConfig().set(path + ".due", System.currentTimeMillis() + interval);
      getConfig().set(path + ".name", player.getName());
      getConfig().set(path + ".paid", false);
      saveConfig();
    }
    getConfig().set(path + ".name", player.getName());
    saveConfig();
    return getConfig().getLong(path + ".due");
  }
  void paid(Player player) {
    Requirement next = generateRequirement();
    getConfig().set("players." + player.getUniqueId() + ".name", player.getName());
    getConfig().set("players." + player.getUniqueId() + ".due", System.currentTimeMillis() + interval);
    getConfig().set("players." + player.getUniqueId() + ".paid", true);
    getConfig().set("players." + player.getUniqueId() + ".requirement.material", next.material().name());
    getConfig().set("players." + player.getUniqueId() + ".requirement.amount", next.amount());
    getConfig().set("players." + player.getUniqueId() + ".reminded-due", null);
    saveConfig();
  }

  boolean hasPaid(Player player) {
    String path = "players." + player.getUniqueId();
    return getConfig().getBoolean(path + ".paid")
        && getConfig().getLong(path + ".due") > System.currentTimeMillis();
  }
}
