package io.github.thebusybiscuit.sensibletoolbox.api.recipes;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.desht.dhutils.Debugger;

/**
 * Represents items that may be converted to SCU by some machine. A machine
 * which does this conversion can create a static instance of this object, to
 * effectively use as a fuel dictionary.
 * 
 * @author desht
 */
public class FuelItems {

    private final Map<ItemStack, FuelValues> fuels = new HashMap<>();
    private final Map<Material, FuelValues> fuelMaterials = new EnumMap<>(Material.class);
    private final Set<ItemStack> fuelInfo = new HashSet<>();

    /**
     * Register an item as fuel.
     *
     * @param stack
     *            the item to register
     * @param ignoreData
     *            true if the item's data value should be ignore; false otherwise
     * @param chargePerTick
     *            the amount of SCU generated per tick
     * @param burnTime
     *            the time in server ticks to convert the item into SCU
     */
    public void addFuel(ItemStack stack, boolean ignoreData, double chargePerTick, int burnTime) {
        if (ignoreData) {
            fuelMaterials.put(stack.getType(), new FuelValues(chargePerTick, burnTime));
        } else {
            fuels.put(getSingle(stack), new FuelValues(chargePerTick, burnTime));
        }

        ItemStack info = stack.clone();
        ItemMeta im = info.getItemMeta();
        im.setLore(Arrays.asList(ChatColor.GRAY + "" + ChatColor.ITALIC + get(stack).toString()));
        info.setItemMeta(im);
        fuelInfo.add(info);
        Debugger.getInstance().debug("register burnable fuel: " + stack + " -> " + get(stack).toString());
    }

    public Collection<ItemStack> getFuelInfos() {
        return fuelInfo;
    }

    /**
     * Get the fuel values for the given item.
     *
     * @param stack
     *            the item to check
     * @return the fuel values for the item, or null if this item is not known
     */
    public FuelValues get(ItemStack stack) {
        FuelValues res = fuels.get(getSingle(stack));
        return res == null ? fuelMaterials.get(stack.getType()) : res;
    }

    /**
     * Check if the given can be used as a fuel.
     *
     * @param stack
     *            the item to check
     * @return true if the item is a fuel, false otherwise
     */
    public boolean has(ItemStack stack) {
        return fuels.containsKey(getSingle(stack)) || fuelMaterials.containsKey(stack.getType());
    }

    private ItemStack getSingle(ItemStack stack) {
        if (stack.getAmount() == 1) {
            return stack;
        } else {
            ItemStack stack2 = stack.clone();
            stack2.setAmount(1);
            return stack2;
        }
    }
}
