package io.github.feydk.colorfall;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final ColorfallPlugin plugin;

    // Called whenever a player joins. This could be after a player disconnect during a game, for instance.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.enter(player);
        if (plugin.getGame() == null) {
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        GamePlayer gp = plugin.getGamePlayer(player);
        gp.setDisconnectedTicks(0);
        plugin.getScoreboard().addPlayer(player);
        if (gp.isSpectator()) {
            gp.setSpectator();
            return;
        }
        if (plugin.getGame() == null) return;
        switch (plugin.getGame().getState()) {
        case STARTED:
            gp.setSpectator();
            break;
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN_TO_START:
            break;
        default:
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.getPlayer().setWalkSpeed(0.2f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (plugin.getGame() == null) return;
        if (event.getCause() != TeleportCause.ENDER_PEARL) {
            return;
        }
        if (!plugin.getGame().getGameMap().isBlockWithinCuboid(event.getTo().getBlock())) {
            event.setCancelled(true);
        } else {
            plugin.getGamePlayer(event.getPlayer()).addEnderpearl();
        }
    }

    @EventHandler
    public void onProjectileThrownEvent(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getEntity();
            if (snowball.getShooter() instanceof Player) {
                Player playerThrower = (Player) snowball.getShooter();
                plugin.getGamePlayer(playerThrower).addSnowball();
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (event.getDamager() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getDamager();
            if (snowball.getShooter() instanceof Player) {
                Player playerThrower = (Player) snowball.getShooter();
                plugin.getGamePlayer(playerThrower).addSnowballHit();
            }
        }
        Player player = (Player) event.getEntity();
        player.setHealth(20);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        GamePlayer gp = plugin.getGamePlayer(player);
        // Ok, this isn't pretty. But..
        // It seems that when a player is teleported, any fall damage he is due to take is inflicted immediately. Even when falling into the void.
        // This peculiarity leads to the player dying twice, once by falling out of the world and then by taking fall damage.
        // So, to avoid double deaths I check if the player last died less than 500 ms ago.
        if ((gp.getLastDeath() > 0 && System.currentTimeMillis() - gp.getLastDeath() <= 500) || gp.diedThisRound()) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == DamageCause.VOID) {
            event.setCancelled(true);
            player.setHealth(20);
            player.setVelocity(new Vector().zero());
            Location location = gp.getSpawnLocation();
            if (location == null) location = player.getWorld().getSpawnLocation();
            player.teleport(location);
            player.setHealth(20.0);
            player.setGameMode(GameMode.SPECTATOR);
            if (plugin.getGame() != null && plugin.getGame().getState() == GameState.STARTED && gp.isPlayer()) {
                gp.died();
            }
        } else {
            player.setHealth(20);
            if (event.getCause() != DamageCause.ENTITY_ATTACK && event.getCause() != DamageCause.PROJECTILE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (plugin.getGame() == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        switch (event.getAction()) {
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            final Player player = event.getPlayer();
            if (plugin.getGame().onPlayerRightClick(player, player.getInventory().getItemInMainHand(), event.getClickedBlock())) {
                event.setCancelled(true);
            }
        default: return;
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getGame() == null) return;
        if (plugin.getGame().tryUseItemInHand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (plugin.getGame() == null) return;
        if (plugin.getGame().tryUseItemInHand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (plugin.getGame() == null) return;
        if (plugin.getGame().isDisallowPistons()) event.setCancelled(true);
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (plugin.getGame() == null || plugin.getGame().getGameMap() == null) {
            event.setSpawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        } else {
            event.setSpawnLocation(plugin.getGame().getSpawnLocation(event.getPlayer()));
        }
    }

    @EventHandler
    public void onProjectileCollide(ProjectileCollideEvent event) {
        if (plugin.getGame() == null) return;
        Projectile proj = event.getEntity();
        if (!(proj instanceof Snowball)) return;
        Entity target = event.getCollidedWith();
        if (!(target instanceof Player)) return;
        Player victim = (Player) target;
        event.setCancelled(true);
        if (plugin.getGame().getRoundState() != RoundState.RUNNING) return;
        Vector velo = proj.getVelocity().normalize().setY(0);
        if (velo.length() < 0.01) return;
        velo = velo.setY(0.25).normalize();
        victim.setVelocity(velo.multiply(3.0));
        victim.getWorld().spawnParticle(Particle.SNOWBALL, proj.getLocation(), 48, 0.2, 0.2, 0.2, 0.0);
        victim.getWorld().playSound(proj.getLocation(), Sound.BLOCK_SNOW_BREAK, SoundCategory.MASTER, 2.0f, 1.0f);
        if (proj.getShooter() instanceof Player) {
            Player launcher = (Player) proj.getShooter();
            launcher.playSound(launcher.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.MASTER, 1.0f, 1.0f);
            victim.sendMessage(ChatColor.RED + launcher.getName() + " hit you with a snowball");
        }
    }

    @EventHandler
    void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
        event.setCancelled(true);
    }

    @EventHandler
    void onHangingBreak(HangingBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        event.setCancelled(true);
    }
}
