package me.realized.duels.teleport;

import me.realized.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class TeleportThread implements Runnable {

    private final Player player;
    private final Location location;

    public TeleportThread(Player player, Location location) {
        this.player = player;
        this.location = location;
    }

    @Override
    public void run() {
        Chunk chunk = this.location.getChunk();
        chunk.load(true);
        Bukkit.getScheduler().runTaskLater(DuelsPlugin.getPlugin(), () -> TeleportThread.this.player.teleport(TeleportThread.this.location), 10);
    }

}
