package me.desht.sensibletoolbox.api.items;

import me.desht.dhutils.Debugger;
import me.desht.dhutils.LogUtils;
import me.desht.sensibletoolbox.api.*;
import me.desht.sensibletoolbox.api.gui.*;
import me.desht.sensibletoolbox.api.recipes.CustomRecipeManager;
import me.desht.sensibletoolbox.api.util.STBUtil;
import me.desht.sensibletoolbox.core.energy.EnergyNet;
import me.desht.sensibletoolbox.core.energy.EnergyNetManager;
import me.desht.sensibletoolbox.core.gui.STBInventoryGUI;
import me.desht.sensibletoolbox.items.energycells.EnergyCell;
import me.desht.sensibletoolbox.items.machineupgrades.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public abstract class BaseSTBMachine extends BaseSTBBlock implements ChargeableBlock, STBInventoryHolder {
    private double charge;
    private ChargeDirection chargeDirection;
    private boolean jammed;  // true if no space in output slots for processing result
    private EnergyCell installedCell;
    private double speedMultiplier;
    private double powerMultiplier;
    private BlockFace autoEjectDirection;
    private boolean needToProcessUpgrades;
    private int chargeMeterId;
    private final String frozenInput;
    private final String frozenOutput;
    private final List<MachineUpgrade> upgrades = new ArrayList<MachineUpgrade>();
    private final Map<BlockFace, EnergyNet> energyNets = new HashMap<BlockFace, EnergyNet>();
    private int regulatorAmount;
    private int thoroughnessAmount;
    private String chargeLabel;
    private int charge8; // a 0..7 value representing charge boundaries

    protected BaseSTBMachine() {
        super();
        charge = 0;
        chargeDirection = ChargeDirection.MACHINE;
        jammed = false;
        autoEjectDirection = null;
        needToProcessUpgrades = false;
        frozenInput = frozenOutput = null;
    }

    public BaseSTBMachine(ConfigurationSection conf) {
        super(conf);
        charge = conf.getInt("charge");
        charge8 = (int) ((charge * 8) / getMaxCharge());
        chargeDirection = ChargeDirection.valueOf(conf.getString("chargeDirection", "MACHINE"));
        jammed = false;
        if (conf.contains("energyCell") && getEnergyCellSlot() >= 0) {
            EnergyCell cell = (EnergyCell) SensibleToolbox.getItemRegistry().getItemById(conf.getString("energyCell"));
            cell.setCharge(conf.getDouble("energyCellCharge", 0.0));
            installEnergyCell(cell);
        }
        if (conf.contains("upgrades")) {
            for (String l : conf.getStringList("upgrades")) {
                String[] f = l.split("::", 3);
                try {
                    YamlConfiguration upgConf = new YamlConfiguration();
                    int amount = Integer.parseInt(f[1]);
                    if (f.length > 2) {
                        upgConf.loadFromString(f[2]);
                    }
                    MachineUpgrade upgrade = (MachineUpgrade) SensibleToolbox.getItemRegistry().getItemById(f[0], upgConf);
                    upgrade.setAmount(amount);
                    upgrades.add(upgrade);
                } catch (Exception e) {
                    LogUtils.warning("can't restore saved module " + f[0] + " for " + this + ": " + e.getMessage());
                }
            }
        }
        needToProcessUpgrades = true;
        frozenInput = conf.getString("inputSlots");
        frozenOutput = conf.getString("outputSlots");
    }

    @Override
    public YamlConfiguration freeze() {
        YamlConfiguration conf = super.freeze();
        conf.set("charge", charge);
        conf.set("accessControl", getAccessControl().toString());
        conf.set("redstoneBehaviour", getRedstoneBehaviour().toString());
        conf.set("chargeDirection", getChargeDirection().toString());
        List<String> upg = new ArrayList<String>();
        for (MachineUpgrade upgrade : upgrades) {
            upg.add(upgrade.getItemTypeID() + "::" + upgrade.getAmount() + "::" + upgrade.freeze().saveToString());
        }
        conf.set("upgrades", upg);

        if (getGUI() != null) {
            conf.set("inputSlots", getGUI().freezeSlots(getInputSlots()));
            conf.set("outputSlots", getGUI().freezeSlots(getOutputSlots()));
        }
        if (installedCell != null) {
            conf.set("energyCell", installedCell.getItemTypeID());
            conf.set("energyCellCharge", installedCell.getCharge());
        }
        return conf;
    }

    /**
     * Get the inventory slots which may be used for placing items into this
     * machine.
     *
     * @return an array of inventory slot numbers; may be empty
     */
    public abstract int[] getInputSlots();

    /**
     * Get the inventory slots which may be used for taking items out of this
     * machine.
     *
     * @return an array of inventory slot numbers; may be empty
     */
    public abstract int[] getOutputSlots();

    /**
     * Get the inventory slots which may be used for storing machine upgrade
     * items.
     *
     * @return an array of inventory slot numbers; may be empty
     */
    public abstract int[] getUpgradeSlots();

    /**
     * Get the inventory slot to use to draw a label for the upgrade slots.
     *
     * @return an inventory slot number; may be -1 to indicate no label
     */
    public abstract int getUpgradeLabelSlot();

    protected void playActiveParticleEffect() {
    }

    /**
     * Check whether this machine requires shaped recipes.  By default,
     * machines use shapeless recipes (of possibly only one item), but this
     * can be overridden by machines if shaped recipes will be used.
     *
     * @return true if shaped recipes will be used; false otherwise
     */
    public boolean hasShapedRecipes() {
        return false;
    }

    /**
     * Add any custom recipes for this machine.  By default this method does
     * nothing; override if necessary to add any custom recipes.
     *
     * @param crm the recipe manager object
     */
    public void addCustomRecipes(CustomRecipeManager crm) {
    }

    /**
     * Check if the machine is jammed; if it has work to do, but no space in
     * its output slot(s).
     *
     * @return true if the machine is jammed
     */
    public final boolean isJammed() {
        return jammed;
    }

    /**
     * Set the jammed status of the machine; if it has work to do, but no
     * space in its output slot(s).
     *
     *  @param jammed true if jammed; false otherwise
     */
    public final void setJammed(boolean jammed) {
        this.jammed = jammed;
    }

    /**
     * Get the speed multiplier currently in effect for this machine, due to
     * any installed upgrades.
     *
     * @return the current speed multiplier
     */
    public final double getSpeedMultiplier() {
        return speedMultiplier;
    }

    private void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    /**
     * Get the power usage multiplier currently in effect for this machine,
     * due to any installed upgrades.
     *
     * @return the current power usage multiplier
     */
    public final double getPowerMultiplier() {
        return powerMultiplier;
    }

    private void setPowerMultiplier(double powerMultiplier) {
        this.powerMultiplier = powerMultiplier;
    }

    /**
     * Get the direction that the machine should attempt to eject any items in
     * the output slot(s).
     *
     * @return the auto-eject direction
     */
    public final BlockFace getAutoEjectDirection() {
        return autoEjectDirection;
    }

    private void setAutoEjectDirection(BlockFace direction) {
        autoEjectDirection = direction;
    }

    /**
     * Get the charge direction for any energy cell that may be installed in
     * this machine.
     *
     * @return the current charge direction
     */
    public final ChargeDirection getChargeDirection() {
        return chargeDirection;
    }

    /**
     * Set the charge direction for any energy cell that may be installed in
     * this machine.
     *
     * @param chargeDirection the new charge direction
     */
    public final void setChargeDirection(ChargeDirection chargeDirection) {
        this.chargeDirection = chargeDirection;
        update(false);
    }

    @Override
    public double getCharge() {
        return charge;
    }

    @Override
    public void setCharge(double charge) {
        if (charge == this.charge) {
            return;
        }
        if (charge <= 0 && this.charge > 0 && getLocation() != null) {
            playOutOfChargeSound();
        }
        this.charge = Math.min(getMaxCharge(), Math.max(0, charge));
        if (getGUI() != null && chargeMeterId >= 0) {
            getGUI().getMonitor(chargeMeterId).repaintNeeded();
        }

        // does the charge indicator label need updating?
        int c8 = (int) ((getCharge() * 8) / getMaxCharge());
        if (c8 != charge8) {
            charge8 = c8;
            buildChargeLabel();
            updateAttachedLabelSigns();
        }
        update(false);
    }

    private String getChargeLabel() {
        if (chargeLabel == null) {
            buildChargeLabel();
        }
        return chargeLabel;
    }

    @Override
    protected String[] getSignLabel(BlockFace face) {
        String[] label = super.getSignLabel(face);
        if (label[3].isEmpty()) {
            label[3] = getChargeLabel();
        }
        return label;
    }

    private void buildChargeLabel() {
        StringBuilder s = new StringBuilder("⌁").append(ChatColor.DARK_RED.toString()).append("◼");
        for (int i = 0; i < charge8; i++) {
            s.append("◼");
            if (i == 0) {
                s.append(ChatColor.GOLD.toString());
            } else if (i == 2) {
                s.append(ChatColor.GREEN.toString());
            }
        }
        s.append(StringUtils.repeat(" ", 15 - s.length()));
        chargeLabel = s.toString();
    }

    @Override
    public String[] getExtraLore() {
        return getMaxCharge() > 0 ? new String[]{STBUtil.getChargeString(this)} : new String[0];
    }

    /**
     * Called when a machine starts processing an item.
     */
    protected void onMachineStartup() {
        // override in subclasses
    }

    protected void playOutOfChargeSound() {
        // override in subclasses
    }

    @Override
    public Inventory getInventory() {
        return getGUI().getInventory();
    }

    protected ItemStack getInventoryItem(int slot) {
        return getInventory().getItem(slot);
    }

    protected void setInventoryItem(int slot, ItemStack item) {
        Validate.isTrue(getGUI().getSlotType(slot) == InventoryGUI.SlotType.ITEM, "Attempt to insert item into non-item slot");
        getInventory().setItem(slot, item != null && item.getAmount() > 0 ? item : null);
        update(false);
    }

    @Override
    protected InventoryGUI createGUI() {
        InventoryGUI gui = GUIUtil.createGUI(this, getInventoryGUISize(), ChatColor.DARK_BLUE + getItemName());

        if (shouldPaintSlotSurrounds()) {
            gui.paintSlotSurround(getInputSlots(), STBInventoryGUI.INPUT_TEXTURE);
            gui.paintSlotSurround(getOutputSlots(), STBInventoryGUI.OUTPUT_TEXTURE);
        }
        for (int slot : getInputSlots()) {
            gui.setSlotType(slot, InventoryGUI.SlotType.ITEM);
        }
        gui.thawSlots(frozenInput, getInputSlots());
        for (int slot : getOutputSlots()) {
            gui.setSlotType(slot, InventoryGUI.SlotType.ITEM);
        }
        gui.thawSlots(frozenOutput, getOutputSlots());

        int[] upgradeSlots = getUpgradeSlots();
        for (int slot : upgradeSlots) {
            gui.setSlotType(slot, InventoryGUI.SlotType.ITEM);
        }
        if (getUpgradeLabelSlot() >= 0) {
            gui.addLabel("Upgrades", getUpgradeLabelSlot(), null);
        }
        for (int i = 0; i < upgrades.size() && i < upgradeSlots.length; i++) {
            gui.getInventory().setItem(upgradeSlots[i], upgrades.get(i).toItemStack(upgrades.get(i).getAmount()));
        }

        gui.addGadget(new RedstoneBehaviourGadget(gui, getRedstoneBehaviourSlot()));
        gui.addGadget(new AccessControlGadget(gui, getAccessControlSlot()));

        if (getEnergyCellSlot() != -1) {
            gui.setSlotType(getEnergyCellSlot(), STBInventoryGUI.SlotType.ITEM);
        }
        gui.addGadget(new ChargeDirectionGadget(gui, getChargeDirectionSlot()));
        chargeMeterId = getMaxCharge() > 0 ? gui.addMonitor(new ChargeMeter(gui)) : -1;
        if (installedCell != null) {
            gui.paintSlot(getEnergyCellSlot(), installedCell.toItemStack(), true);
        }
        return gui;
    }

    protected boolean shouldPaintSlotSurrounds() {
        return true;
    }

    /**
     * Get the inventory slot where a redstone behaviour gadget will be
     * displayed.  The default is slot 8; right-hand edge of the top row.
     *
     * @return an inventory slot number, or -1 for no gadget
     */
    public int getRedstoneBehaviourSlot() {
        return 8;  // top right
    }

    /**
     * Get the inventory slot where an access control gadget will be
     * displayed.  The default is slot 17; right-hand edge of the second row.
     *
     * @return an inventory slot number, or -1 for no gadget
     */
    public int getAccessControlSlot() {
        return 17;  // just below top right
    }

    /**
     * Get the inventory slot where an charge meter gadget will be
     * displayed.  The default is slot 26; right-hand edge of the third row.
     *
     * @return an inventory slot number, or -1 for no gadget
     */
    public int getChargeMeterSlot() {
        return 26;  // just below access control slot
    }

    /**
     * Get the inventory slot for an energy cell slot, where an energy cell
     * can be inserted.  The default is -1; no energy cell slot.
     *
     * @return an inventory slot number, or -1 for no energy cell
     */
    public int getEnergyCellSlot() {
        return -1;
    }

    /**
     * Get the inventory slot for an charge direction slot; where the energy
     * flow between machine and energy cell can be changed.  The default is
     * -1; no gadget.  If a gadget is added, it is recommended to place it
     * adjacent to the energy cell slot defined with
     * {@link #getEnergyCellSlot()}
     *
     * @return an inventory slot number, or -1 for no energy cell
     */
    public int getChargeDirectionSlot() {
        return -1;
    }

    /**
     * Get the size for this machine's GUI, in slots.  The size must be a
     * multiple of 9.  The default size is 54 slots (6 rows).
     *
     * @return the number of inventory slots in this machine's GUI
     */
    public int getInventoryGUISize() {
        return 54;
    }

    protected int getRegulatorAmount() {
        return regulatorAmount;
    }

    private void setRegulatorAmount(int regulatorAmount) {
        this.regulatorAmount = regulatorAmount;
    }

    protected int getThoroughnessAmount() {
        return thoroughnessAmount;
    }

    private void setThoroughnessAmount(int thoroughnessAmount) {
        this.thoroughnessAmount = thoroughnessAmount;
    }

    @Override
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !event.getPlayer().isSneaking()) {
            getGUI().show(event.getPlayer());
            event.setCancelled(true);
        }
        super.onInteractBlock(event);
    }

    @Override
    public void setLocation(Location loc) {
        if (loc == null) {
            getGUI().ejectItems(getInputSlots());
            getGUI().ejectItems(getOutputSlots());
            getGUI().ejectItems(getUpgradeSlots());
            if (installedCell != null) {
                getGUI().ejectItems(getEnergyCellSlot());
                installedCell = null;
            }
            upgrades.clear();
            setGUI(null);
            EnergyNetManager.onMachineRemoved(this);
        }
        super.setLocation(loc);
        if (loc != null) {
            EnergyNetManager.onMachinePlaced(this);
        }
    }


    /**
     * Find a candidate slot for item insertion; this will look for an empty slot, or a slot containing the
     * same kind of item as the candidate item.  It will NOT check item amounts (see #insertItem() for that)
     *
     * @param item the candidate item to insert
     * @param side the side being inserted from (SELF is a valid option here too)
     * @return the slot number if a slot is available, or -1 otherwise
     */
    protected int findAvailableInputSlot(ItemStack item, BlockFace side) {
        for (int slot : getInputSlots()) {
            ItemStack inSlot = getInventoryItem(slot);
            if (inSlot == null || inSlot.isSimilar(item)) {
                return slot;
            } else if (inSlot.isSimilar(item)) {
                return slot;
            }
        }
        return -1;
    }

    protected int findOutputSlot(ItemStack item) {
        for (int slot : getOutputSlots()) {
            ItemStack outSlot = getInventoryItem(slot);
            if (outSlot == null) {
                return slot;
            } else if (outSlot.isSimilar(item) && outSlot.getAmount() + item.getAmount() <= item.getType().getMaxStackSize()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Check if the given item would be accepted as input to this machine.  By default, every item type
     * is accepted, but this can be overridden in subclasses.
     *
     * @param item the item to check
     * @return true if the item is accepted, false otherwise
     */
    public boolean acceptsItemType(ItemStack item) {
        return true;
    }

    /**
     * Check if the given slot accepts items as input.
     *
     * @param slot the slot to check
     * @return true if this is an input slot, false otherwise
     */
    public boolean isInputSlot(int slot) {
        return isSlotIn(slot, getInputSlots());
    }

    /**
     * Check if the given slot can be used to extract items.
     *
     * @param slot the slot to check
     * @return true if this is an output slot, false otherwise
     */
    public boolean isOutputSlot(int slot) {
        return isSlotIn(slot, getOutputSlots());
    }

    /**
     * Check if the given slot can be used to install upgrades, i.e. STB items
     * which subclass {@link me.desht.sensibletoolbox.items.machineupgrades.MachineUpgrade}
     *
     * @param slot the slot to check
     * @return true if this is an upgrade slot, false otherwise
     */
    public boolean isUpgradeSlot(int slot) {
        return isSlotIn(slot, getUpgradeSlots());
    }

    private boolean isSlotIn(int slot, int[] slots) {
        for (int s1 : slots) {
            if (s1 == slot) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int insertItems(ItemStack toInsert, BlockFace side, boolean sorting, UUID uuid) {
        if (!hasAccessRights(uuid)) {
            return 0;
        }
        if (sorting) {
            return 0; // machines don't take items from sorters
        }
        int slot = findAvailableInputSlot(toInsert, side);
        int nInserted = 0;
        if (slot >= 0 && acceptsItemType(toInsert)) {
            ItemStack inMachine = getInventoryItem(slot);
            if (inMachine == null) {
                nInserted = toInsert.getAmount();
                setInventoryItem(slot, toInsert);
            } else {
                nInserted = Math.min(toInsert.getAmount(), inMachine.getType().getMaxStackSize() - inMachine.getAmount());
                if (nInserted > 0) {
                    inMachine.setAmount(inMachine.getAmount() + nInserted);
                    setInventoryItem(slot, inMachine);
                }
            }
            if (Debugger.getInstance().getLevel() > 1) {
                Debugger.getInstance().debug(2, "inserted " + nInserted + " out of " +
                        STBUtil.describeItemStack(toInsert) + " into " + this);
            }
        }
        return nInserted;
    }

    @Override
    public ItemStack extractItems(BlockFace face, ItemStack receiver, int amount, UUID uuid) {
        if (!hasAccessRights(uuid)) {
            return null;
        }
        int[] slots = getOutputSlots();
        int max = slots == null ? getInventory().getSize() : slots.length;
        for (int i = 0; i < max; i++) {
            int slot = slots == null ? i : slots[i];
            ItemStack stack = getInventoryItem(slot);
            if (stack != null) {
                if (receiver == null || stack.isSimilar(receiver)) {
                    int toTake = Math.min(amount, stack.getAmount());
                    if (receiver != null) {
                        toTake = Math.min(toTake, receiver.getType().getMaxStackSize() - receiver.getAmount());
                    }
                    if (toTake > 0) {
                        ItemStack result = stack.clone();
                        result.setAmount(toTake);
                        if (receiver != null) {
                            receiver.setAmount(receiver.getAmount() + toTake);
                        }
                        stack.setAmount(stack.getAmount() - toTake);
                        setInventoryItem(slot, stack);
                        setJammed(false);
                        update(false);
                        if (Debugger.getInstance().getLevel() > 1) {
                            Debugger.getInstance().debug(2, "extracted " + STBUtil.describeItemStack(result) + " from " + this);
                        }
                        return result;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Inventory showOutputItems(UUID uuid) {
        if (hasAccessRights(uuid) && getOutputSlots() != null) {
            Inventory inv = Bukkit.createInventory(this, STBUtil.roundUp(getOutputSlots().length, 9));
            int i = 0;
            for (int slot : getOutputSlots()) {
                inv.setItem(i++, getInventoryItem(slot));
            }
            return inv;
        } else {
            return null;
        }
    }

    @Override
    public void updateOutputItems(UUID uuid, Inventory inventory) {
        if (hasAccessRights(uuid) && getOutputSlots() != null) {
            int i = 0;
            for (int slot : getOutputSlots()) {
                setInventoryItem(slot, inventory.getItem(i++));
            }
        }
    }

    @Override
    public boolean onSlotClick(HumanEntity player, int slot, ClickType click, ItemStack inSlot, ItemStack onCursor) {
        if (isInputSlot(slot)) {
            if (onCursor.getType() != Material.AIR && !acceptsItemType(onCursor)) {
                return false;
            }
            if (inSlot != null) {
                update(false);
            }
        } else if (isOutputSlot(slot)) {
            if (onCursor.getType() != Material.AIR) {
                return false;
            } else if (inSlot != null) {
                setJammed(false);
                update(false);
            }
        } else if (isUpgradeSlot(slot)) {
            if (onCursor.getType() != Material.AIR) {
                if (!isValidUpgrade(player, SensibleToolbox.getItemRegistry().fromItemStack(onCursor))) {
                    return false;
                }
            }
            needToProcessUpgrades = true;
        } else if (slot == getEnergyCellSlot()) {
            if (onCursor.getType() != Material.AIR) {
                EnergyCell cell = SensibleToolbox.getItemRegistry().fromItemStack(onCursor, EnergyCell.class);
                if (cell != null) {
                    installEnergyCell(cell);
                } else {
                    return false;
                }
            } else if (inSlot != null) {
                installEnergyCell(null);
            }
        }
        return true;
    }

    @Override
    public int onShiftClickInsert(HumanEntity player, int slot, ItemStack toInsert) {
        BaseSTBItem item = SensibleToolbox.getItemRegistry().fromItemStack(toInsert);

        if (getUpgradeSlots().length > 0 && isValidUpgrade(player, item)) {
            int upgradeSlot = findAvailableUpgradeSlot(toInsert);
            if (upgradeSlot >= 0) {
                if (getInventoryItem(upgradeSlot) != null) {
                    toInsert.setAmount(toInsert.getAmount() + getInventoryItem(upgradeSlot).getAmount());
                }
                setInventoryItem(upgradeSlot, toInsert);
                needToProcessUpgrades = true;
                return toInsert.getAmount();
            }
        }

        if (item instanceof EnergyCell && getEnergyCellSlot() >= 0 && installedCell == null) {
            installEnergyCell((EnergyCell) item);
            setInventoryItem(getEnergyCellSlot(), installedCell.toItemStack());
            return 1;
        }

        int insertionSlot = findAvailableInputSlot(toInsert, BlockFace.SELF);
        if (insertionSlot >= 0 && acceptsItemType(toInsert)) {
            ItemStack inMachine = getInventoryItem(insertionSlot);
            if (inMachine == null) {
                // insert the whole stack
                setInventoryItem(insertionSlot, toInsert);
                update(false);
                return toInsert.getAmount();
            } else {
                // insert as much as possible
                int nToInsert = Math.min(inMachine.getMaxStackSize() - inMachine.getAmount(), toInsert.getAmount());
                if (nToInsert > 0) {
                    inMachine.setAmount(inMachine.getAmount() + nToInsert);
                    setInventoryItem(insertionSlot, inMachine);
                    update(false);
                }
                return nToInsert;
            }
        }

        return 0;
    }

    protected boolean isValidUpgrade(HumanEntity player, BaseSTBItem item) {
        if (!(item instanceof MachineUpgrade)) {
            return false;
        }
        if (item instanceof EjectorUpgrade && ((EjectorUpgrade) item).getDirection() == BlockFace.SELF) {
            STBUtil.complain(player, "Ejector upgrade must have a direction configured.");
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean onShiftClickExtract(HumanEntity player, int slot, ItemStack toExtract) {
        // allow extraction to continue in all cases
        if (slot == getEnergyCellSlot() && toExtract != null) {
            installEnergyCell(null);
        } else if (isUpgradeSlot(slot)) {
            needToProcessUpgrades = true;
        } else if (isOutputSlot(slot) && getInventoryItem(slot) != null) {
            setJammed(false);
            update(false);
        } else if (isInputSlot(slot) && getInventoryItem(slot) != null) {
            update(false);
        }
        return true;
    }

    @Override
    public boolean onClickOutside(HumanEntity player) {
        return false;
    }

    private int findAvailableUpgradeSlot(ItemStack upgrade) {
        for (int slot : getUpgradeSlots()) {
            ItemStack inSlot = getInventoryItem(slot);
            if (inSlot == null || inSlot.isSimilar(upgrade) && inSlot.getAmount() + upgrade.getAmount() <= upgrade.getType().getMaxStackSize()) {
                return slot;
            }
        }
        return -1;
    }

    private void scanUpgradeSlots() {
        upgrades.clear();
        for (int slot : getUpgradeSlots()) {
            ItemStack stack = getInventoryItem(slot);
            if (stack != null) {
                MachineUpgrade upgrade = SensibleToolbox.getItemRegistry().fromItemStack(stack, MachineUpgrade.class);
                if (upgrade == null) {
                    setInventoryItem(slot, null);
                    if (getLocation() != null) {
                        getLocation().getWorld().dropItemNaturally(getLocation(), stack);
                    }
                } else {
                    upgrade.setAmount(stack.getAmount());
                    upgrades.add(upgrade);
                    if (getTicksLived() > 20) {
                        // if the machine has only just been placed, no need to do a DB save
                        update(false);
                    }
                }
            }
        }
    }

    private void processUpgrades() {
        int nSpeed = 0;
        BlockFace ejectDirection = null;
        int nRegulator = 0;
        int nThorough = 0;
        for (MachineUpgrade upgrade : upgrades) {
            if (upgrade instanceof SpeedUpgrade) {
                nSpeed += upgrade.getAmount();
            } else if (upgrade instanceof EjectorUpgrade) {
                ejectDirection = ((EjectorUpgrade) upgrade).getDirection();
            } else if (upgrade instanceof RegulatorUpgrade) {
                nRegulator += upgrade.getAmount();
            } else if (upgrade instanceof ThoroughnessUpgrade) {
                nThorough += upgrade.getAmount();
            }
        }
        setRegulatorAmount(nRegulator);
        setThoroughnessAmount(nThorough);
        setSpeedMultiplier(Math.pow(1.4, nSpeed - nThorough));
        setPowerMultiplier(Math.pow(1.6, nSpeed + nThorough));
        setPowerMultiplier(Math.max(getPowerMultiplier() - nRegulator * 0.1, 1.0));
        setAutoEjectDirection(ejectDirection);
        Debugger.getInstance().debug("upgrades for " + this + " speed=" + getSpeedMultiplier() +
                " power=" + getPowerMultiplier() + " eject=" + getAutoEjectDirection());
    }

    public void installEnergyCell(EnergyCell cell) {
        installedCell = cell;
        Debugger.getInstance().debug("installed energy cell " + cell + " in " + this);
        update(false);
    }

    @Override
    public int getTickRate() {
        return 1;
    }

    @Override
    public void onServerTick() {
        if (getTicksLived() % EnergyNetManager.getTickRate() == 0) {
            double transferred = 0.0;
            if (installedCell != null) {
                switch (chargeDirection) {
                    case MACHINE:
                        transferred = transferCharge(installedCell, this);
                        break;
                    case CELL:
                        transferred = transferCharge(this, installedCell);
                        break;
                }
            }
            if (!getInventory().getViewers().isEmpty()) {
                if (transferred > 0.0) {
                    setInventoryItem(getEnergyCellSlot(), installedCell.toItemStack());
                }
                if (chargeMeterId >= 0) {
                    getGUI().getMonitor(chargeMeterId).doRepaint();
                }
            }
        }
        if (needToProcessUpgrades) {
            if (getGUI() != null) {
                scanUpgradeSlots();
            }
            processUpgrades();
            needToProcessUpgrades = false;
        }
        super.onServerTick();
    }

    private double transferCharge(Chargeable from, Chargeable to) {
        if (to.getCharge() >= to.getMaxCharge() || from.getCharge() == 0) {
            return 0;
        }
        double toTransfer = Math.min(from.getChargeRate() * EnergyNetManager.getTickRate(), to.getMaxCharge() - to.getCharge());
        toTransfer = Math.min(to.getChargeRate() * EnergyNetManager.getTickRate(), toTransfer);
        toTransfer = Math.min(from.getCharge(), toTransfer);
        to.setCharge(to.getCharge() + toTransfer);
        from.setCharge(from.getCharge() - toTransfer);
//		System.out.println("transfer " + toTransfer + " charge from " + from + " to " + to + " from now=" + from.getCharge() + " to now=" + to.getCharge());
        return toTransfer;
    }

    @Override
    public void attachToEnergyNet(EnergyNet net, BlockFace face) {
        Debugger.getInstance().debug(this + ": attach to Energy net #" + net.getNetID());
        energyNets.put(face, net);
    }

    @Override
    public void detachFromEnergyNet(EnergyNet net) {
        Iterator<Map.Entry<BlockFace, EnergyNet>> iter = energyNets.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<BlockFace, EnergyNet> entry = iter.next();
            if (entry.getValue().getNetID() == net.getNetID()) {
                iter.remove();
                Debugger.getInstance().debug(this + ": detached from Energy net #" + net.getNetID());
            }
        }
    }

    @Override
    public EnergyNet[] getAttachedEnergyNets() {
        Set<EnergyNet> nets = new HashSet<EnergyNet>();
        nets.addAll(energyNets.values());
        return nets.toArray(new EnergyNet[nets.size()]);
    }

    @Override
    public List<BlockFace> getFacesForNet(EnergyNet net) {
        List<BlockFace> res = new ArrayList<BlockFace>();
        for (Map.Entry<BlockFace, EnergyNet> entry : energyNets.entrySet()) {
            if (entry.getValue().getNetID() == net.getNetID()) {
                res.add(entry.getKey());
            }
        }
        return res;
    }

}
