package io.github.thebusybiscuit.sensibletoolbox.commands;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.sensibletoolbox.api.SensibleToolbox;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBItem;
import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;

public class GiveCommand extends AbstractCommand {

    public GiveCommand() {
        super("stb give", 1);
        setPermissionNode("stb.commands.give");
        setUsage("/<command> give <item-name> [<amount>] [<player-name>]");
        setQuotedArgs(true);
    }

    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        Player target;
        int amount = 1;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            Validate.notNull(target, "Unknown player: " + args[2]);
        }
        else {
            if (args.length >= 2) {
                try {
                    amount = Integer.parseInt(args[1]);
                }
                catch (NumberFormatException e) {
                    throw new DHUtilsException("Invalid amount: " + args[1]);
                }
            }
            notFromConsole(sender);
            target = (Player) sender;
        }

        String id = args[0].replace(" ", "").toLowerCase();
        BaseSTBItem item = SensibleToolbox.getItemRegistry().getItemById(id);
        Validate.notNull(item, "Unknown SensibleToolbox item: " + args[0]);
        target.getInventory().addItem(item.toItemStack(amount));
        MiscUtil.statusMessage(target, "You received " + amount + " x &6" + item.getItemName() + "&-.");
        return true;
    }

    @Override
    public List<String> onTabComplete(Plugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return getItemCompletions(plugin, sender, args[0]);
        }
        else if (args.length == 2) {
            return null; // list players
        }
        else {
            return noCompletions(sender);
        }
    }

    protected List<String> getItemCompletions(Plugin plugin, CommandSender sender, String prefix) {
        List<String> res = new ArrayList<>();

        for (String item : SensibleToolbox.getItemRegistry().getItemIds()) {
            if (item.startsWith(prefix)) {
                res.add(item);
            }
        }

        return getResult(res, sender, true);
    }
}
