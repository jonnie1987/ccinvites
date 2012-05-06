package com.king6887.plugins.ccinvites;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.Listener;

/**
 * Player listener for CCInvites plugin.
 * @author King
 */
public class CCIPlayerListener implements Listener {
    private final CCInvites plugin;

    /**
     * @param plugin The Plugin. 
     */
    public CCIPlayerListener(CCInvites plugin){
        this.plugin = plugin;
    }

    /**
     * Calls methods to add/update player when joining.
     * @param event Bukkit event.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.updatePlayer(event.getPlayer().getDisplayName());
    }
    
}
