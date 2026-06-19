package com.denis.serverpass.listener;

import com.denis.serverpass.ServerPassPlugin;
import com.denis.serverpass.audit.AuditService;
import com.denis.serverpass.auth.AuthSessionManager;
import com.denis.serverpass.config.PasswordConfigStore;
import com.denis.serverpass.message.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import java.time.Instant;

public final class LockdownListener implements Listener {
    private final ServerPassPlugin plugin;
    private final PasswordConfigStore passwordStore;
    private final MessageService messages;
    private final AuditService auditService;
    private final AuthSessionManager sessionManager;

    public LockdownListener(
        ServerPassPlugin plugin,
        PasswordConfigStore passwordStore,
        MessageService messages,
        AuditService auditService,
        AuthSessionManager sessionManager
    ) {
        this.plugin = plugin;
        this.passwordStore = passwordStore;
        this.messages = messages;
        this.auditService = auditService;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!passwordStore.authEnabled()) {
            sessionManager.authenticate(player);
            return;
        }
        if (sessionManager.hasBypass(player)) {
            sessionManager.authenticate(player);
            auditService.record(player.getName(), "success", "bypass");
            return;
        }
        if (!passwordStore.isConfigured()) {
            if (sessionManager.canConfigurePassword(player)) {
                sessionManager.requireAuthentication(player, Instant.now());
                plugin.getServer().getScheduler().runTask(plugin, () -> messages.send(player, "setupAdmin"));
            } else {
                auditService.record(player.getName(), "kick", "not_configured");
                plugin.getServer().getScheduler().runTask(plugin, () -> player.kick(messages.raw("notConfiguredKick")));
            }
            return;
        }
        sessionManager.requireAuthentication(player, Instant.now());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.prompt(player, true);
            messages.send(player, "loginPrompt");
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessionManager.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (!passwordStore.blockMovement() || !sessionManager.isLocked(event.getPlayer())) {
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || samePosition(from, to)) {
            return;
        }
        if (passwordStore.allowLookAround()) {
            Location corrected = from.clone();
            corrected.setYaw(to.getYaw());
            corrected.setPitch(to.getPitch());
            event.setTo(corrected);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (passwordStore.blockBlockBreak() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (passwordStore.blockBlockPlace() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onFish(PlayerFishEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (passwordStore.blockInventory() && event.getPlayer() instanceof Player player && sessionManager.isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (passwordStore.blockInventory() && event.getWhoClicked() instanceof Player player && sessionManager.isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (passwordStore.blockInventory() && event.getWhoClicked() instanceof Player player && sessionManager.isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (passwordStore.blockItemDrop() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (passwordStore.blockItemDrop() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEditBook(PlayerEditBookEvent event) {
        if (passwordStore.blockInteract() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (passwordStore.blockItemPickup() && event.getEntity() instanceof Player player && sessionManager.isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (passwordStore.blockItemPickup() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        if (passwordStore.blockChat() && sessionManager.isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (passwordStore.protectFromDamage() && event.getEntity() instanceof Player player && sessionManager.isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (passwordStore.blockDamage() && damager instanceof Player player && sessionManager.isLocked(player)) {
            event.setCancelled(true);
            return;
        }
        if (passwordStore.protectFromDamage() && event.getEntity() instanceof Player player && sessionManager.isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (passwordStore.blockInteract() && event.getEntered() instanceof Player player && sessionManager.isLocked(player)) {
            event.setCancelled(true);
        }
    }

    private boolean samePosition(Location from, Location to) {
        return from.getWorld() == to.getWorld()
            && Double.compare(from.getX(), to.getX()) == 0
            && Double.compare(from.getY(), to.getY()) == 0
            && Double.compare(from.getZ(), to.getZ()) == 0;
    }
}
