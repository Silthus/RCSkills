package de.raidcraft.skills.util;

import de.raidcraft.skills.SkillsPlugin;
import de.slikey.effectlib.effect.DnaEffect;
import lombok.NonNull;
import org.bukkit.entity.Player;

public final class Effects {

    private Effects() {
    }

    public static void playerUnlockSkill(@NonNull Player player) {

        SkillsPlugin plugin = SkillsPlugin.instance();

        DnaEffect effect = new DnaEffect(plugin.getEffectManager());
        effect.setLocation(player.getLocation());
        effect.setTargetLocation(player.getLocation().add(0, 2, 0));
        effect.duration = 2000;
        effect.start();
        playSound(player, plugin.getPluginConfig().getSounds().getSkillUnlock());
    }

    public static void activateSkill(Player player) {

        playSound(player, SkillsPlugin.instance().getPluginConfig().getSounds().getSkillActivate());
    }

    public static void levelUp(Player player) {

        playSound(player, SkillsPlugin.instance().getPluginConfig().getSounds().getLevelUp());
    }

    public static void unlockSlot(Player player) {

        playSound(player, SkillsPlugin.instance().getPluginConfig().getSounds().getSlotUnlock());
    }

    public static void activateSlot(Player player) {

        playSound(player, SkillsPlugin.instance().getPluginConfig().getSounds().getSlotActivate());
    }

    public static void skillReset(Player player) {

        playSound(player, SkillsPlugin.instance().getPluginConfig().getSounds().getSkillReset());
    }

    public static void playSound(Player player, String sound) {

        player.playSound(player.getLocation(), sound, 10f, 1f);
    }
}
