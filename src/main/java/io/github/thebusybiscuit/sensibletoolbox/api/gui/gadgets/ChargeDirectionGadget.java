package io.github.thebusybiscuit.sensibletoolbox.api.gui.gadgets;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import io.github.thebusybiscuit.sensibletoolbox.api.energy.ChargeDirection;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.InventoryGUI;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBItem;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBMachine;

/**
 * A GUI gadget which can display and change the charge direction
 * for a STB device which can hold an energy cell.
 * 
 * @author desht
 */
public class ChargeDirectionGadget extends CyclerGadget<ChargeDirection> {

    /**
     * Constructs a charge direction gadget.
     *
     * @param gui
     *            the GUI that the gadget belongs to
     * @param slot
     *            the GUI slot that the gadget occupies
     */
    public ChargeDirectionGadget(InventoryGUI gui, int slot) {
        super(gui, slot, "Charge");

        add(ChargeDirection.MACHINE, ChatColor.GOLD, Material.MAGMA_CREAM, "Energy will transfer from", "an installed energy cell", "to this machine");
        add(ChargeDirection.CELL, ChatColor.GREEN, Material.SLIME_BALL, "Energy will transfer", "from this machine to", "an installed energy cell");
        setInitialValue(((BaseSTBMachine) gui.getOwningItem()).getChargeDirection());
    }

    @Override
    protected boolean ownerOnly() {
        return false;
    }

    @Override
    protected void apply(BaseSTBItem stbItem, ChargeDirection newValue) {
        ((BaseSTBMachine) stbItem).setChargeDirection(newValue);
    }
}
