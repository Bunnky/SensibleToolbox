package io.github.thebusybiscuit.sensibletoolbox.api.items;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.sensibletoolbox.api.recipes.CustomRecipeManager;
import io.github.thebusybiscuit.sensibletoolbox.api.recipes.ProcessingResult;
import io.github.thebusybiscuit.sensibletoolbox.items.upgrades.ThoroughnessUpgrade;

/**
 * Represents a machine which processes items from its input slots to
 * an internal processing store, and places resulting items in its output slots.
 */
public abstract class AbstractIOMachine extends AbstractProcessingMachine {

    // a stack of items which can't be placed into output due to lack of space
    private final Deque<ItemStack> pendingItems = new ArrayDeque<>();

    protected AbstractIOMachine() {
        super();
    }

    public AbstractIOMachine(ConfigurationSection conf) {
        super(conf);
    }

    @Override
    public int getTickRate() {
        return 10;
    }

    @Override
    protected void onOutOfCharge() {
        getLocation().getWorld().playSound(getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 1.0F, 0.5F);
    }

    @Override
    public void onServerTick() {
        if (getProcessing() == null && isRedstoneActive() && pendingItems.isEmpty()) {
            // not doing any processing - anything in input to take?
            for (int slot : getInputSlots()) {
                if (getInventoryItem(slot) != null) {
                    onMachineStartup();
                    pullItemIntoProcessing(slot);
                    break;
                }
            }
        }

        if (getProgress() > 0 && getCharge() > 0) {
            // currently processing....
            double chargeNeeded = getScuPerTick() * getPowerMultiplier() * getTickRate();
            // throttle back on the progress and charge if necessary
            int mult = chargeNeeded < getCharge() ? getTickRate() : (int) (getCharge() / getPowerMultiplier());
            setProgress(getProgress() - getSpeedMultiplier() * mult);
            setCharge(getCharge() - getPowerMultiplier() * mult);
            playActiveParticleEffect();
        }

        if (!isJammed()) {
            if (!pendingItems.isEmpty()) {
                // try to move previously jammed items into output
                pushItemIntoOutput(pendingItems.pop(), false);
            } else if (getProcessing() != null && getProgress() <= 0) {
                // done processing - try to move new items into output
                ProcessingResult recipe = getCustomRecipeFor(getProcessing());
                pushItemIntoOutput(recipe.getResult(), true);
            }
        }

        handleAutoEjection();

        super.onServerTick();
    }

    @Override
    public void onBlockUnregistered(Location l) {
        for (ItemStack s : pendingItems) {
            l.getWorld().dropItemNaturally(l, s);
        }

        super.onBlockUnregistered(l);
    }

    private void pushItemIntoOutput(ItemStack result, boolean addBonus) {
        if (result == null) {
            return;
        }

        if (addBonus) {
            Random rnd = ThreadLocalRandom.current();

            if (rnd.nextInt(100) < getThoroughnessAmount() * ThoroughnessUpgrade.BONUS_OUTPUT_CHANCE) {
                // bonus item(s), yay!
                int bonus = rnd.nextInt(result.getAmount()) + 1;
                result.setAmount(Math.min(result.getMaxStackSize(), result.getAmount() + bonus));
            }
        }

        while (result.getAmount() > 0) {
            int slot = findOutputSlot(result, true);

            if (slot >= 0) {
                // good, there's space to move (at least some of) the item
                ItemStack inOutput = getInventoryItem(slot);

                if (inOutput == null) {
                    inOutput = result.clone();
                    result.setAmount(0);
                } else {
                    int toAdd = Math.min(result.getAmount(), inOutput.getMaxStackSize() - inOutput.getAmount());
                    inOutput.setAmount(inOutput.getAmount() + toAdd);
                    result.setAmount(result.getAmount() - toAdd);
                }

                setInventoryItem(slot, inOutput);
            } else {
                // no space!
                setJammed(true);
                pendingItems.push(result);
                break;
            }
        }

        if (!isJammed()) {
            setProcessing(null);
            update(false);
        }
    }

    private void pullItemIntoProcessing(int inputSlot) {
        ItemStack s = getInventoryItem(inputSlot);
        ItemStack toProcess = s.clone();
        toProcess.setAmount(1);
        ProcessingResult recipe = getCustomRecipeFor(toProcess);

        if (recipe == null) {
            // shouldn't happen but...
            getLocation().getWorld().dropItemNaturally(getLocation(), s);
            setInventoryItem(inputSlot, null);
            return;
        }

        setProcessing(toProcess);
        getProgressMeter().setMaxProgress(recipe.getProcessingTime());
        setProgress(recipe.getProcessingTime());
        s.setAmount(s.getAmount() - 1);
        setInventoryItem(inputSlot, s);

        update(false);
    }

    @Override
    public boolean acceptsItemType(ItemStack i) {
        return CustomRecipeManager.getManager().hasRecipe(this, i);
    }

    private ProcessingResult getCustomRecipeFor(ItemStack s) {
        return CustomRecipeManager.getManager().getRecipe(this, s);
    }

    @Override
    public boolean acceptsEnergy(BlockFace face) {
        return true;
    }

    @Override
    public boolean suppliesEnergy(BlockFace face) {
        return false;
    }

}
