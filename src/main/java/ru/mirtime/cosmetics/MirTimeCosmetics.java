package ru.mirtime.cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeCosmetics extends JavaPlugin implements CommandExecutor, Listener {

    private record CosmeticItem(String id, String slotType, String title, Material icon, int price, List<String> desc) {}

    private static final List<CosmeticItem> ITEMS = List.of(
            new CosmeticItem("halo", "hat", "&#00E5FF&l✦ Неоновый Нимб из Нитей", Material.SEA_LANTERN, 4500,
                    List.of("&7Двойное парящее кольцо из тонких", "&7светящихся нитей над головой.")),
            new CosmeticItem("chinahat", "hat", "&#00FF66&l⛩ Кибер-Шляпа (16 Лучей)", Material.CYAN_CONCRETE, 6500,
                    List.of("&7Объёмный азиатский конус,", "&7сплетённый из 16 неоновых нитей.")),
            new CosmeticItem("crown", "hat", "&#FFD700&l👑 Корона Императора", Material.GOLD_BLOCK, 9000,
                    List.of("&7Вращающаяся 3D-корона с 8 острыми", "&7золотыми зубцами-нитями.")),
            new CosmeticItem("horns", "hat", "&c&l😈 Алые Демон-Рога", Material.RED_CONCRETE, 7500,
                    List.of("&7Изогнутые объёмные рога из ярких", "&7алых энергетических нитей.")),
            new CosmeticItem("wings", "back", "&#00E5FF&l🕊 Крылья из 14 Перьев-Нитей", Material.FEATHER, 12000,
                    List.of("&7Анимированные крылья за спиной,", "&7плавно взмахивающие при движении.")),
            new CosmeticItem("katanas", "back", "&#9400d3&l⚔ Две Скрещённые Катаны", Material.NETHERITE_SWORD, 10000,
                    List.of("&7Объёмные клинки из аметистовых", "&7нитей крест-накрест за спиной.")),
            new CosmeticItem("cape", "back", "&#0055ff&l🛡 Физический Неоновый Плащ", Material.LIGHT_BLUE_CONCRETE, 8000,
                    List.of("&7Каркасный плащ из светящихся нитей,", "&7развевающийся на ветру при беге.")),
            new CosmeticItem("crystal", "pet", "&d&l💎 Питомец: 3D-Кристалл Пустоты", Material.END_CRYSTAL, 15000,
                    List.of("&7Вращающийся 3D-октаэдр из нитей,", "&7летающий по орбите вокруг вас."))
    );

    private File dataFile;
    private YamlConfiguration data;
    private double tickCounter = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "players.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (getCommand("cosmetics") != null) getCommand("cosmetics").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        // Плавное обновление 3D-нитей каждые 2 тика (10 раз в секунду)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tickCounter += 0.1;
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID id = p.getUniqueId();
                String hat = data.getString(id + ".equipped.hat", "");
                String back = data.getString(id + ".equipped.back", "");
                String pet = data.getString(id + ".equipped.pet", "");

                if (hat.isEmpty() && back.isEmpty() && pet.isEmpty()) {
                    ThreadRenderer.clear(id);
                    continue;
                }
                ThreadRenderer.renderPlayer(p, hat, back, pet, tickCounter);
            }
        }, 5L, 2L);

        getLogger().info("MirTimeCosmetics v2.0.0 (3D-аксессуары из нитей и магазин за токены) запущен!");
    }

    @Override
    public void onDisable() {
        ThreadRenderer.clearAll();
    }
    public void saveData() {
        try { data.save(dataFile); } catch (IOException ignored) {}
    }

    public boolean isOwned(UUID id, String itemId) {
        return data.getStringList(id + ".owned").contains(itemId);
    }

    public void unlockItem(UUID id, String itemId) {
        List<String> list = data.getStringList(id + ".owned");
        if (!list.contains(itemId)) {
            list.add(itemId);
            data.set(id + ".owned", list);
            saveData();
        }
    }

    public boolean isEquipped(UUID id, CosmeticItem item) {
        return item.id().equals(data.getString(id + ".equipped." + item.slotType(), ""));
    }

    public void toggleEquip(Player p, CosmeticItem item) {
        UUID id = p.getUniqueId();
        String path = id + ".equipped." + item.slotType();
        if (item.id().equals(data.getString(path, ""))) {
            data.set(path, "");
            p.sendMessage(color(getConfig().getString("prefix") + "&eВы сняли аксессуар: " + item.title()));
        } else {
            data.set(path, item.id());
            p.sendMessage(color(getConfig().getString("prefix") + "&#00FF66Вы надели 3D-аксессуар: " + item.title() + " &7(Видно всем игрокам!)"));
        }
        saveData();
    }

    public void openShopMenu(Player p) {
        int tokens = getTokensBalance(p);
        Inventory inv = Bukkit.createInventory(null, 45, color("&#0055ff&l⚡ &#00E5FF&lМАГАЗИН 3D-АКСЕССУАРОВ"));

        ItemStack blue = buildItem(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack cyan = buildItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        ItemStack dark = buildItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);

        for (int s : new int[]{0, 8, 36, 44}) inv.setItem(s, blue);
        for (int s : new int[]{1, 2, 3, 5, 6, 7, 9, 17, 18, 26, 27, 35, 37, 38, 39, 41, 42, 43}) inv.setItem(s, cyan);
        for (int s : new int[]{19, 25, 28, 34}) inv.setItem(s, dark);

        inv.setItem(4, buildItem(Material.NETHER_STAR, "&#00E5FF&l✦ 3D-КАСТОМИЗАЦИЯ MIR-TIME ✦", List.of(
                "&7Все аксессуары сплетены из &#00E5FF3D-нитей",
                "&7и отображаются у всех игроков сервера!",
                "",
                "&fВаш баланс: &#00FF66&l" + tokens + " Токенов &a⛁"
        )));

        int[] slots = {11, 12, 13, 14, 20, 21, 22, 31};
        for (int i = 0; i < ITEMS.size(); i++) {
            CosmeticItem it = ITEMS.get(i);
            boolean owned = isOwned(p.getUniqueId(), it.id());
            boolean eq = isEquipped(p.getUniqueId(), it);

            List<String> lore = new ArrayList<>(it.desc());
            lore.add("");
            lore.add("&7Категория: &f" + switch (it.slotType()) {
                case "hat" -> "Головной убор";
                case "back" -> "Аксессуар на спину";
                default -> "Летающий питомец";
            });
            lore.add("");
            if (eq) {
                lore.add("&#00FF66&l✔ НАДЕТО СЕЙЧАС");
                lore.add("&e▶ Нажмите, чтобы снять");
            } else if (owned) {
                lore.add("&#00E5FF✔ Куплено в гардероб");
                lore.add("&#00FF66▶ Нажмите, чтобы надеть");
            } else {
                lore.add("&fЦена: &#00FF66&l" + it.price() + " Токенов &a⛁");
                lore.add(tokens >= it.price() ? "&#FFD700▶ Нажмите, чтобы купить навсегда!" : "&c✖ Недостаточно токенов");
            }

            inv.setItem(slots[i], buildItem(eq ? Material.EMERALD_BLOCK : it.icon(), it.title(), lore));
        }

        inv.setItem(40, buildItem(Material.BARRIER, "&c&l✖ Снять все аксессуары", List.of("&7Мгновенно убирает все 3D-нити с персонажа.")));
        p.openInventory(inv);
    }
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().contains("МАГАЗИН 3D-АКСЕССУАРОВ")) return;
        e.setCancelled(true);

        String prefix = color(getConfig().getString("prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» "));
        int slot = e.getRawSlot();

        if (slot == 40) {
            UUID id = p.getUniqueId();
            data.set(id + ".equipped.hat", "");
            data.set(id + ".equipped.back", "");
            data.set(id + ".equipped.pet", "");
            saveData();
            ThreadRenderer.clear(id);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
            p.sendMessage(prefix + color("&eВсе 3D-аксессуары сняты."));
            openShopMenu(p);
            return;
        }

        int[] slots = {11, 12, 13, 14, 20, 21, 22, 31};
        CosmeticItem clicked = null;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { clicked = ITEMS.get(i); break; }
        }
        if (clicked == null) return;

        UUID id = p.getUniqueId();
        if (isOwned(id, clicked.id())) {
            toggleEquip(p, clicked);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            openShopMenu(p);
            return;
        }

        int bal = getTokensBalance(p);
        if (bal < clicked.price()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage(prefix + color("&cНедостаточно токенов! Нужно &#00FF66" + clicked.price() + " ⛁"));
            return;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "p take " + p.getName() + " " + clicked.price());
        unlockItem(id, clicked.id());
        data.set(id + ".equipped." + clicked.slotType(), clicked.id());
        saveData();

        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        p.sendMessage(prefix + color("&#00FF66&lПОКУПКА УСПЕШНА! &fВы приобрели и надели " + clicked.title() + "&#00FF66!"));
        openShopMenu(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ThreadRenderer.clear(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player p) {
            openShopMenu(p);
        }
        return true;
    }

    public int getTokensBalance(Player p) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
                String res = (String) m.invoke(null, p, "%playerpoints_points%");
                return Integer.parseInt(res.replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(color(s));
                meta.setLore(l);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(meta);
        }
        return is;
    }

    public static String color(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) rep.append('§').append(c);
            m.appendReplacement(sb, rep.toString());
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
