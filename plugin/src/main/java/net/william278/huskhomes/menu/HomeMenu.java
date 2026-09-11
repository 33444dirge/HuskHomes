package net.william278.huskhomes.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickEvent;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.william278.huskhomes.PaperHuskHomes;
import net.william278.huskhomes.command.DelHomeCommand;
import net.william278.huskhomes.command.EditHomeCommand;
import net.william278.huskhomes.command.PrivateHomeCommand;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.user.BukkitUser;
import net.william278.huskhomes.user.OnlineUser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/** A configurable, paginated inventory menu for a player's homes. */
public final class HomeMenu implements Listener {
    private static final char HOME_SLOT = 'H';
    private static final char PREVIOUS_PAGE = 'P';
    private static final char NEXT_PAGE = 'N';
    private static final String ICON_TAG = "home_menu_icon";

    private final PaperHuskHomes plugin;
    private final NamespacedKey homeKey;
    private final NamespacedKey navigationKey;
    private volatile Layout layout;
    private volatile YamlConfiguration dialogConfig;
    private volatile Map<String, String> worldNames;

    public HomeMenu(@NotNull PaperHuskHomes plugin) {
        this.plugin = plugin;
        this.homeKey = new NamespacedKey(plugin, "home_menu_home");
        this.navigationKey = new NamespacedKey(plugin, "home_menu_navigation");
        this.reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        plugin.saveResource("gui.yml", false);
        plugin.saveResource("dialog.yml", false);
        final File file = new File(plugin.getDataFolder(), "gui.yml");
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        final List<String> configuredRows = config.getStringList("layout");
        if (configuredRows.isEmpty() || configuredRows.size() > 6 || configuredRows.stream().anyMatch(row -> row.length() > 9)) {
            throw new IllegalStateException("gui.yml layout must contain 1-6 rows of at most 9 characters");
        }
        final List<String> rows = configuredRows.stream()
                .map(row -> row + " ".repeat(9 - row.length()))
                .toList();

        final Map<Character, ItemTemplate> items = new HashMap<>();
        final ConfigurationSection section = config.getConfigurationSection("items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (key.length() != 1) {
                    continue;
                }
                final ConfigurationSection item = section.getConfigurationSection(key);
                if (item != null) {
                    items.put(key.charAt(0), ItemTemplate.from(item));
                }
            }
        }
        this.layout = new Layout(
                rows,
                config.getString("title", "<dark_gray>Homes"),
                ItemTemplate.from(Objects.requireNonNull(config.getConfigurationSection("home-item"))),
                ItemTemplate.from(config.getConfigurationSection("empty-home-item")),
                ItemTemplate.from(Objects.requireNonNull(config.getConfigurationSection("previous-page"))),
                ItemTemplate.from(config.getConfigurationSection("previous-page-disabled")),
                ItemTemplate.from(Objects.requireNonNull(config.getConfigurationSection("next-page"))),
                ItemTemplate.from(config.getConfigurationSection("next-page-disabled")),
                items
        );
        final ConfigurationSection worlds = config.getConfigurationSection("world-names");
        final Map<String, String> configuredWorldNames = new HashMap<>();
        if (worlds != null) {
            for (String key : worlds.getKeys(false)) {
                configuredWorldNames.put(key, worlds.getString(key, key));
            }
        }
        this.worldNames = Map.copyOf(configuredWorldNames);
        this.dialogConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "dialog.yml"));
    }

    public void open(@NotNull OnlineUser user) {
        open(user, 0);
    }

    private void open(@NotNull OnlineUser user, int page) {
        plugin.runAsync(() -> {
            final List<Home> homes = plugin.getDatabase().getHomes(user);
            plugin.runSync(() -> render(user, homes, page), user);
        });
    }

    private void render(@NotNull OnlineUser user, @NotNull List<Home> homes, int requestedPage) {
        if (!(user instanceof BukkitUser bukkitUser) || !bukkitUser.getPlayer().isOnline()) {
            return;
        }
        final Layout current = layout;
        final int pageSize = current.homeSlots().size();
        if (pageSize == 0) {
            plugin.log(Level.WARNING, "gui.yml has no H home slots; unable to open the home menu");
            return;
        }
        final int maxPage = Math.max(0, (homes.size() - 1) / pageSize);
        final int page = Math.clamp(requestedPage, 0, maxPage);
        final Inventory inventory = plugin.getServer().createInventory(
                new HomeMenuHolder(user.getUuid(), page), current.rows().size() * 9,
                MiniMessage.miniMessage().deserialize(current.title())
        );

        for (int slot = 0; slot < current.rows().size() * 9; slot++) {
            final char marker = current.markerAt(slot);
            final ItemTemplate item = current.items().get(marker);
            if (item != null) {
                inventory.setItem(slot, item.create(Map.of()));
            }
        }

        if (current.emptyHomeItem() != null) {
            current.homeSlots().forEach(slot -> inventory.setItem(slot, current.emptyHomeItem().create(Map.of())));
        }

        for (int index = 0; index < pageSize; index++) {
            final int homeIndex = page * pageSize + index;
            if (homeIndex >= homes.size()) {
                break;
            }
            final Home home = homes.get(homeIndex);
            final Map<String, String> variables = Map.of(
                    "home", home.getName(),
                    "world", worldNames.getOrDefault(home.getWorld().getName(), home.getWorld().getName()),
                    "x", Integer.toString((int) Math.floor(home.getX())),
                    "y", Integer.toString((int) Math.floor(home.getY())),
                    "z", Integer.toString((int) Math.floor(home.getZ())),
                    "description", home.getMeta().getDescription().isBlank() ? "-" : home.getMeta().getDescription(),
                    "privacy", home.isPublic() ? "<green>公开" : "<red>私有"
            );
            final ItemStack configuredItem = current.homeItem().create(variables);
            final ItemStack item = loadIcon(home).orElseGet(configuredItem::clone);
            if (!item.isSimilar(configuredItem)) {
                final ItemMeta meta = item.getItemMeta();
                final ItemMeta configuredMeta = configuredItem.getItemMeta();
                meta.displayName(configuredMeta.displayName());
                meta.lore(configuredMeta.lore());
                item.setItemMeta(meta);
            }
            item.setAmount(1);
            final ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(homeKey, PersistentDataType.STRING, home.getName());
            item.setItemMeta(meta);
            inventory.setItem(current.homeSlots().get(index), item);
        }

        if (page > 0) {
            current.slotsFor(PREVIOUS_PAGE).forEach(slot -> inventory.setItem(slot,
                    createNavigationItem(current.previousPage(), "previous")));
        } else if (current.previousPageDisabled() != null) {
            current.slotsFor(PREVIOUS_PAGE).forEach(slot -> inventory.setItem(slot,
                    current.previousPageDisabled().create(Map.of())));
        }
        if (page < maxPage) {
            current.slotsFor(NEXT_PAGE).forEach(slot -> inventory.setItem(slot,
                    createNavigationItem(current.nextPage(), "next")));
        } else if (current.nextPageDisabled() != null) {
            current.slotsFor(NEXT_PAGE).forEach(slot -> inventory.setItem(slot,
                    current.nextPageDisabled().create(Map.of())));
        }
        bukkitUser.getPlayer().openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        final InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
        final boolean menuOpen = inventoryHolder instanceof HomeMenuHolder || inventoryHolder instanceof ConfirmationHolder;
        final boolean fromPlayerInventory = event.getRawSlot() >= event.getView().getTopInventory().getSize();
        if (menuOpen && (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
            || (fromPlayerInventory && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY))) {
            event.setCancelled(true);
            return;
        }
        if (inventoryHolder instanceof ConfirmationHolder holder) {
            handleConfirmationClick(event, holder);
            return;
        }
        if (!(inventoryHolder instanceof HomeMenuHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.player())) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        event.setCancelled(true);

        final char marker = layout.markerAt(event.getRawSlot());
        final ItemStack item = event.getCurrentItem();
        final String navigation = item != null && item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(navigationKey, PersistentDataType.STRING)
                : null;
        if (marker == PREVIOUS_PAGE && "previous".equals(navigation) && holder.page() > 0) {
            open(plugin.getOnlineUser(player), holder.page() - 1);
            return;
        }
        if (marker == NEXT_PAGE && "next".equals(navigation)) {
            open(plugin.getOnlineUser(player), holder.page() + 1);
            return;
        }
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        final String home = item.getItemMeta().getPersistentDataContainer().get(homeKey, PersistentDataType.STRING);
        if (home == null) {
            return;
        }
        if (event.isShiftClick()) {
            updateIcon(plugin.getOnlineUser(player), home, null);
            return;
        }
        final ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            updateIcon(plugin.getOnlineUser(player), home, cursor.clone());
            return;
        }
        if (event.isLeftClick()) {
            player.closeInventory();
            plugin.getCommand(PrivateHomeCommand.class)
                    .ifPresent(command -> command.onExecuted(plugin.getOnlineUser(player), new String[]{home}));
        } else if (event.isRightClick()) {
            openEditor(plugin.getOnlineUser(player), home);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HomeMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof ConfirmationHolder) {
            final int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
                event.setCancelled(true);
            }
        }
    }

    public void openConfirmation(@NotNull OnlineUser user, @NotNull ConfirmationAction action, @NotNull String home) {
        plugin.runSync(() -> renderConfirmation(user, action, home), user);
    }

    private void renderConfirmation(@NotNull OnlineUser user, @NotNull ConfirmationAction action, @NotNull String home) {
        if (!(user instanceof BukkitUser bukkitUser) || !bukkitUser.getPlayer().isOnline()) {
            return;
        }
        final Inventory inventory = plugin.getServer().createInventory(
                new ConfirmationHolder(user.getUuid(), action, home), 27,
                dialogComponent(action == ConfirmationAction.DELETE ? "confirm.delete-title" : "confirm.relocate-title",
                        action == ConfirmationAction.DELETE ? "确认删除家" : "确认迁移家", Map.of("home", home))
        );
        final ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(11, namedItem(Material.LIME_WOOL, dialogText("confirm.accept", "<green>确认")));
        inventory.setItem(15, namedItem(Material.RED_WOOL, dialogText("confirm.cancel", "<red>取消")));
        bukkitUser.getPlayer().openInventory(inventory);
    }

    private void handleConfirmationClick(@NotNull InventoryClickEvent event, @NotNull ConfirmationHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.player())) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() == 15) {
            player.closeInventory();
            openEditor(plugin.getOnlineUser(player), holder.home());
            return;
        }
        if (event.getRawSlot() != 11) {
            return;
        }
        player.closeInventory();
        final OnlineUser user = plugin.getOnlineUser(player);
        if (holder.action() == ConfirmationAction.DELETE) {
            plugin.getCommand(DelHomeCommand.class)
                    .ifPresent(command -> command.onExecuted(user, new String[]{holder.home()}));
        } else {
            plugin.getCommand(EditHomeCommand.class)
                    .ifPresent(command -> command.onExecuted(user, new String[]{holder.home(), "relocate"}));
        }
    }

    private void openEditor(@NotNull OnlineUser user, @NotNull String homeName) {
        plugin.runAsync(() -> plugin.getDatabase().getHome(user, homeName)
                .ifPresent(home -> plugin.runSync(() -> showEditor(user, home), user)));
    }

    private void updateIcon(@NotNull OnlineUser user, @NotNull String homeName, @Nullable ItemStack icon) {
        final String serialized = icon == null ? null : Base64.getEncoder().encodeToString(icon.serializeAsBytes());
        plugin.runAsync(() -> plugin.getDatabase().getHome(user, homeName).ifPresent(home -> {
            final Map<String, String> tags = new HashMap<>(home.getMeta().getTags());
            if (serialized == null) {
                tags.remove(ICON_TAG);
            } else {
                tags.put(ICON_TAG, serialized);
            }
            plugin.getManager().homes().setHomeMetaTags(home, tags);
            open(user);
        }));
    }

    @NotNull
    private java.util.Optional<ItemStack> loadIcon(@NotNull Home home) {
        final String serialized = home.getMeta().getTags().get(ICON_TAG);
        if (serialized == null || serialized.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(ItemStack.deserializeBytes(Base64.getDecoder().decode(serialized)));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private void showEditor(@NotNull OnlineUser user, @NotNull Home home) {
        if (!(user instanceof BukkitUser bukkitUser) || !bukkitUser.getPlayer().isOnline()) {
            return;
        }
        final String homeName = home.getName();
        final String encodedHomeName = encodeHomeName(homeName);
        final List<DialogInput> inputs = List.of(
                DialogInput.text("name", 250, dialogComponent("editor.name-label", "家名称", Map.of()), true, homeName, 32, null),
                DialogInput.text("description", 250, dialogComponent("editor.description-label", "家描述", Map.of()), true,
                        home.getMeta().getDescription(), 256, TextDialogInput.MultilineOptions.create(4, 80))
        );
        final List<ActionButton> actions = List.of(
                button(dialogComponent("editor.save", "保存更改", Map.of()), "huskhomes:homemenu save " + encodedHomeName + " $(name) $(description)"),
                button(dialogComponent("editor.relocate", "迁移到当前位置", Map.of()), "huskhomes:homemenu confirm relocate " + encodedHomeName),
                button(dialogComponent(home.isPublic() ? "editor.make-private" : "editor.make-public",
                                home.isPublic() ? "设为私有" : "设为公开", Map.of()),
                        "huskhomes:homemenu privacy " + encodedHomeName + " " + (home.isPublic() ? "private" : "public")),
                button(dialogComponent("editor.delete", "删除家", Map.of()), "huskhomes:homemenu confirm delete " + encodedHomeName)
        );
        final Dialog dialog = Dialog.create(factory -> {
            final DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.create(
                        dialogComponent("editor.title", "管理家: %home%", Map.of("home", homeName)), null, true, false,
                        DialogBase.DialogAfterAction.CLOSE,
                        List.of(DialogBody.plainMessage(dialogComponent("editor.message",
                                "修改后点击保存更改；删除和迁移需要二次确认。", Map.of()))), inputs
                ));
            builder.type(DialogType.multiAction(actions,
                    button(dialogComponent("editor.back", "返回家列表", Map.of()), "huskhomes:homemenu open"), 1));
        });
        bukkitUser.getPlayer().showDialog(dialog);
    }

    @NotNull
    private static ActionButton button(@NotNull Component label, @NotNull String command) {
        final DialogAction action = command.contains("$(")
                ? DialogAction.commandTemplate(command)
                : DialogAction.staticAction(ClickEvent.runCommand("/" + command));
        return ActionButton.create(label, null, 300, action);
    }

    @NotNull
    private ItemStack createNavigationItem(@NotNull ItemTemplate template, @NotNull String direction) {
        final ItemStack item = template.create(Map.of());
        final ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(navigationKey, PersistentDataType.STRING, direction);
        item.setItemMeta(meta);
        return item;
    }

    @NotNull
    private static String encodeHomeName(@NotNull String homeName) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(homeName.getBytes(StandardCharsets.UTF_8));
    }

    private record HomeMenuHolder(@NotNull UUID player, int page) implements InventoryHolder {
        @Override
        public @Nullable Inventory getInventory() {
            return null;
        }
    }

    private record ConfirmationHolder(@NotNull UUID player, @NotNull ConfirmationAction action,
                                      @NotNull String home) implements InventoryHolder {
        @Override
        public @Nullable Inventory getInventory() {
            return null;
        }
    }

    public enum ConfirmationAction {
        DELETE, RELOCATE;

        @Nullable
        public static ConfirmationAction from(@NotNull String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private record Layout(@NotNull List<String> rows, @NotNull String title, @NotNull ItemTemplate homeItem,
                          @Nullable ItemTemplate emptyHomeItem,
                          @NotNull ItemTemplate previousPage, @Nullable ItemTemplate previousPageDisabled,
                          @NotNull ItemTemplate nextPage, @Nullable ItemTemplate nextPageDisabled,
                          @NotNull Map<Character, ItemTemplate> items) {
        private char markerAt(int slot) {
            return rows.get(slot / 9).charAt(slot % 9);
        }

        private List<Integer> homeSlots() {
            return slotsFor(HOME_SLOT);
        }

        private List<Integer> slotsFor(char marker) {
            final List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < rows.size() * 9; slot++) {
                if (markerAt(slot) == marker) {
                    slots.add(slot);
                }
            }
            return slots;
        }
    }

    private record ItemTemplate(@NotNull Material material, @NotNull String name, @NotNull List<String> lore) {
        @Nullable
        private static ItemTemplate from(@Nullable ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            final Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
            return new ItemTemplate(material == null ? Material.BARRIER : material, section.getString("name", ""), section.getStringList("lore"));
        }

        private ItemStack create(@NotNull Map<String, String> variables) {
            final ItemStack item = new ItemStack(material);
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }
            meta.displayName(MiniMessage.miniMessage().deserialize(replace(name, variables))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> MiniMessage.miniMessage().deserialize(replace(line, variables))
                    .decoration(TextDecoration.ITALIC, false)).toList());
            item.setItemMeta(meta);
            return item;
        }

        private static String replace(@NotNull String text, @NotNull Map<String, String> variables) {
            String result = text;
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            return result;
        }
    }

    @NotNull
    private static ItemStack namedItem(@NotNull Material material, @NotNull String name) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MiniMessage.miniMessage().deserialize(name));
        item.setItemMeta(meta);
        return item;
    }

    @NotNull
    private String dialogText(@NotNull String key, @NotNull String fallback) {
        return dialogConfig.getString(key, fallback);
    }

    @NotNull
    private Component dialogComponent(@NotNull String key, @NotNull String fallback, @NotNull Map<String, String> variables) {
        return MiniMessage.miniMessage().deserialize(ItemTemplate.replace(dialogText(key, fallback), variables));
    }
}
