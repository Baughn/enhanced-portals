package uk.co.shadeddimensions.ep3.tileentity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatMessageComponent;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.wands.IWandable;
import uk.co.shadeddimensions.ep3.item.ItemLocationCard;
import uk.co.shadeddimensions.ep3.network.CommonProxy;
import uk.co.shadeddimensions.ep3.portal.EntityManager;
import uk.co.shadeddimensions.ep3.portal.GlyphIdentifier;
import uk.co.shadeddimensions.ep3.portal.PortalUtils;
import uk.co.shadeddimensions.ep3.tileentity.frame.TileModuleManipulator;
import uk.co.shadeddimensions.ep3.tileentity.frame.TilePortalController;
import uk.co.shadeddimensions.ep3.tileentity.frame.TileRedstoneInterface;
import uk.co.shadeddimensions.ep3.util.GeneralUtils;
import uk.co.shadeddimensions.ep3.util.GuiPayload;
import uk.co.shadeddimensions.ep3.util.PortalTextureManager;
import uk.co.shadeddimensions.ep3.util.WorldCoordinates;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyContainerItem;
import cofh.api.energy.IEnergyHandler;
import cofh.util.EnergyHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileStabilizerMain extends TileEnhancedPortals implements IInventory, IEnergyHandler, IAspectContainer, IWandable
{
    static final int ACTIVE_PORTALS_PER_ROW = 2;

    ArrayList<ChunkCoordinates> blockList;
    HashMap<String, String> activeConnections;
    HashMap<String, String> activeConnectionsReverse;
    public int powerState;
    ItemStack inventory;
    int rows, tickTimer;
    EnergyStorage energyStorage;
    public int instability = 0;
    Random rand = new Random();
    @SideOnly(Side.CLIENT)
    public int intActiveConnections;

    public TileStabilizerMain()
    {
        blockList = new ArrayList<ChunkCoordinates>();
        activeConnections = new HashMap<String, String>();
        activeConnectionsReverse = new HashMap<String, String>();
        energyStorage = new EnergyStorage(1000000000);
    }

    /***
     * Sets up a new connection between two portals.
     * 
     * @return True if connection was successfully established.
     */
    public boolean setupNewConnection(GlyphIdentifier portalA, GlyphIdentifier portalB, PortalTextureManager textureManager)
    {
        if (activeConnections.containsKey(portalA.getGlyphString()) || activeConnections.containsValue(portalB.getGlyphString()) || !hasEnoughPowerToStart() || !canAcceptNewConnection())
        {
            return false;
        }
        else if (!hasEnoughPowerToStart())
        {
            return false;
        }

        TilePortalController cA = CommonProxy.networkManager.getPortalController(portalA), cB = CommonProxy.networkManager.getPortalController(portalB);

        if (cA == null || cB == null)
        {
            return false;
        }
        else if (cA.isPortalActive || cB.isPortalActive) // Make sure both portals are inactive
        {
            return false;
        }
        else if (!cA.isFullyInitialized() || !cB.isFullyInitialized()) // Make sure they're set up correctly...
        {
            return false;
        }
        else if (cA.isPortalActive || cB.isPortalActive)
        {
            return false;
        }
        else if (!cA.blockManager.getDimensionalBridgeStabilizer().equals(cB.blockManager.getDimensionalBridgeStabilizer())) // And make sure they're on the same DBS
        {
            return false;
        }

        if (textureManager != null)
        {
            cA.swapTextureData(textureManager);
            cB.swapTextureData(textureManager);
        }

        if (!PortalUtils.createPortalFrom(cA))
        {
            cA.revertTextureData();
            cB.revertTextureData();
            return false;
        }
        else if (!PortalUtils.createPortalFrom(cB)) // Make sure both portals can be created
        {
            PortalUtils.removePortalFrom(cA);
            cA.revertTextureData();
            cB.revertTextureData();
            return false;
        }

        activeConnections.put(portalA.getGlyphString(), portalB.getGlyphString());
        activeConnectionsReverse.put(portalB.getGlyphString(), portalA.getGlyphString());
        return true;
    }

    /***
     * Terminates both portals and removes them from the active connection list.
     */
    public void terminateExistingConnection(GlyphIdentifier portalA, GlyphIdentifier portalB)
    {
        if (portalA == null || portalB == null)
        {
            return;
        }

        TilePortalController cA = CommonProxy.networkManager.getPortalController(portalA), cB = CommonProxy.networkManager.getPortalController(portalB);

        if (cA == null || cB == null)
        {
            if (cA != null)
            {
                PortalUtils.removePortalFrom(cA);
                cA.revertTextureData();
            }

            if (cB != null)
            {
                PortalUtils.removePortalFrom(cB);
                cB.revertTextureData();
            }

            removeExistingConnection(portalA, portalB);
        }
        else if (activeConnections.containsKey(portalA.getGlyphString()) && activeConnections.get(portalA.getGlyphString()).equals(portalB.getGlyphString()) || activeConnectionsReverse.containsKey(portalA.getGlyphString()) && activeConnectionsReverse.get(portalA.getGlyphString()).equals(portalB.getGlyphString()))
        {
            // Make sure we're terminating the correct connection, also don't mind that we're terminating it from the other side that we started it from
            PortalUtils.removePortalFrom(cA);
            PortalUtils.removePortalFrom(cB);
            cA.revertTextureData();
            cB.revertTextureData();

            removeExistingConnection(portalA, portalB);
        }
    }

    /***
     * Terminates both portals and removes them from the active connection list. Used by dialling devices when the exit location is not known by the controller.
     */
    public void terminateExistingConnection(GlyphIdentifier identifier)
    {
        if (identifier == null || identifier.isEmpty())
        {
            return;
        }

        GlyphIdentifier portalA = new GlyphIdentifier(identifier), portalB = null;

        if (activeConnections.containsKey(identifier.getGlyphString()))
        {
            portalB = new GlyphIdentifier(activeConnections.get(identifier.getGlyphString()));
        }
        else if (activeConnectionsReverse.containsKey(identifier.getGlyphString()))
        {
            portalB = new GlyphIdentifier(activeConnectionsReverse.get(identifier.getGlyphString()));
        }

        terminateExistingConnection(portalA, portalB);
    }

    /***
     * Removes a connection from the active list.
     */
    public void removeExistingConnection(GlyphIdentifier portalA, GlyphIdentifier portalB)
    {
        activeConnections.remove(portalA.getGlyphString());
        activeConnections.remove(portalB.getGlyphString());
        activeConnectionsReverse.remove(portalA.getGlyphString());
        activeConnectionsReverse.remove(portalB.getGlyphString());

        if (activeConnections.size() == 0 && powerState == 0 && instability > 0)
        {
            instability = 0;
        }
    }

    /***
     * Gets whether or not this stabilizer has enough power to keep the portal open for at least one second.
     */
    boolean hasEnoughPowerToStart()
    {
        if (CommonProxy.redstoneFluxPowerMultiplier == 0)
        {
            return true;
        }

        int powerRequirement = CommonProxy.redstoneFluxPowerMultiplier * 1 * CommonProxy.REDSTONE_FLUX_COST;
        return extractEnergy(null, (int) (powerRequirement * 0.3), true) == (int) (powerRequirement * 0.3);
    }

    /***
     * Whether or not this stabilizer can create a new connection
     */
    public boolean canAcceptNewConnection()
    {
        return activeConnections.size() * 2 + 2 <= ACTIVE_PORTALS_PER_ROW * rows;
    }

    @Override
    public void updateEntity()
    {
        if (activeConnections.size() > 0 && CommonProxy.redstoneFluxPowerMultiplier > 0 && tickTimer >= CommonProxy.REDSTONE_FLUX_TIMER)
        {
            int powerRequirement = CommonProxy.redstoneFluxPowerMultiplier * activeConnections.size() * CommonProxy.REDSTONE_FLUX_COST;

            if (powerState == 0 && extractEnergy(null, powerRequirement, true) == powerRequirement) // Simulate the full power requirement
            {
                extractEnergy(null, powerRequirement, false);
                instability = 0;
            }
            else if ((powerState == 1 || powerState == 0) && extractEnergy(null, (int) (powerRequirement * 0.8), true) == (int) (powerRequirement * 0.8)) // Otherwise, try it at 80%
            {
                extractEnergy(null, (int) (powerRequirement * 0.8), false);
                instability = 20;
            }
            else if ((powerState == 2 || powerState == 0) && extractEnergy(null, (int) (powerRequirement * 0.5), true) == (int) (powerRequirement * 0.5)) // Otherwise, try it at 50%
            {
                extractEnergy(null, (int) (powerRequirement * 0.5), false);
                instability = 50;
            }
            else if ((powerState == 3 || powerState == 0) && extractEnergy(null, (int) (powerRequirement * 0.3), true) == (int) (powerRequirement * 0.3)) // Otherwise, try it at 30%
            {
                extractEnergy(null, (int) (powerRequirement * 0.3), false);
                instability = 70;
            }
            else
                // Fail
            {
                for (int i = activeConnections.size() - 1; i > -1; i--) // Go backwards so we don't get messed up by connections getting removed from this list
                {
                    terminateExistingConnection(new GlyphIdentifier(activeConnections.values().toArray(new String[activeConnections.size()])[i]));
                }

                instability = 0;
            }

            tickTimer = -1;
        }

        if (inventory != null)
        {
            if (inventory.getItem() instanceof IEnergyContainerItem)
            {
                if (((IEnergyContainerItem) inventory.getItem()).getEnergyStored(inventory) > 0)
                {
                    energyStorage.receiveEnergy(((IEnergyContainerItem) inventory.getItem()).extractEnergy(inventory, 10000, false), false);
                }
            }
            else if (inventory.itemID == CommonProxy.itemLocationCard.itemID)
            {
                if (!ItemLocationCard.hasDBSLocation(inventory) && !worldObj.isRemote)
                {
                    ItemLocationCard.setDBSLocation(inventory, getWorldCoordinates());
                }
            }
        }

        tickTimer++;
    }

    public void onEntityEnterPortal(GlyphIdentifier uID, Entity entity, TilePortal portal)
    {
        if (EntityManager.isEntityFitForTravel(entity))
        {
            GlyphIdentifier exit = null;

            if (activeConnections.containsKey(uID.getGlyphString()))
            {
                exit = new GlyphIdentifier(activeConnections.get(uID.getGlyphString()));
            }
            else if (activeConnectionsReverse.containsKey(uID.getGlyphString()))
            {
                exit = new GlyphIdentifier(activeConnectionsReverse.get(uID.getGlyphString()));
            }

            if (exit == null)
            {
                return;
            }

            TilePortalController controller = CommonProxy.networkManager.getPortalController(exit);

            if (instability > 0)
            {
                if (rand.nextInt(100) < instability)
                {
                    if (instability == 20) // Low Instability - 3 Effects
                    {
                        int effect = rand.nextInt(3);

                        if (effect == 0) // Teleport somewhere close
                        {
                            WorldCoordinates coord = controller.getWorldCoordinates();
                            coord.posX += rand.nextBoolean() ? 1 + rand.nextInt(31) : -(1 + rand.nextInt(31));
                            coord.posY = controller.worldObj.getTopSolidOrLiquidBlock(coord.posX, coord.posZ);
                            coord.posZ += rand.nextBoolean() ? 1 + rand.nextInt(31) : -(1 + rand.nextInt(31));

                            EntityManager.transferEntityWithinDimension(entity, coord.posX, coord.posY, coord.posZ, entity.rotationYaw, 0, 0, false);
                        }
                        else if (effect == 1) // Get thrown out of the portal (before entering)
                        {
                            int portalType = portal.getBlockMetadata();

                            if (portalType == 1)
                            {
                                entity.motionX = 0;
                                entity.motionZ = entity.posZ > portal.zCoord ? 1 : -1;
                                entity.motionY = 1;
                                entity.velocityChanged = true;
                            }
                            else if (portalType == 2)
                            {
                                entity.motionX = entity.posX < portal.xCoord ? -1 : 1;
                                entity.motionZ = 0;
                                entity.motionY = 1;
                                entity.velocityChanged = true;
                            }
                            else if (portalType == 3)
                            {
                                entity.motionY = entity.posY < portal.yCoord ? -1 : 1;
                                entity.motionZ = 0;
                                entity.motionX = 0;
                                entity.velocityChanged = true;
                            }
                        }
                        else if (effect == 2) // Get thrown out of the portal (after teleporting)
                        {
                            EntityManager.transferEntity(entity, uID, exit, portal.getBlockMetadata());

                            if (controller.portalType == 1)
                            {
                                entity.motionX = 0;
                                entity.motionZ = entity.rotationYaw == 0 ? -1 : 1;
                                entity.motionY = 1;
                                entity.velocityChanged = true;
                            }
                            else if (controller.portalType == 2)
                            {
                                entity.motionX = entity.rotationYaw == 90 ? -1 : 1;
                                entity.motionZ = 0;
                                entity.motionY = 1;
                                entity.velocityChanged = true;
                            }
                            else if (controller.portalType == 3)
                            {
                                entity.motionY = 1;
                                entity.motionZ = 0;
                                entity.motionX = 0;
                                entity.velocityChanged = true;
                            }
                        }

                        addLowInstabilityEffects(entity);
                    }
                    else if (instability == 50) // Medium Instability - 2 Effects
                    {
                        WorldCoordinates coord = controller.getWorldCoordinates();
                        coord.posX += rand.nextBoolean() ? 1 + rand.nextInt(127) : -(1 + rand.nextInt(127));
                        coord.posZ += rand.nextBoolean() ? 1 + rand.nextInt(127) : -(1 + rand.nextInt(127));

                        if (rand.nextInt(2) == 0) // Teleport somewhere fairly nearby -- on the ground
                        {
                            coord.posY = controller.worldObj.getTopSolidOrLiquidBlock(coord.posX, coord.posZ);
                        }
                        else
                            // Teleport somewhere fairly nearby -- in the air
                        {
                            coord.posY = controller.worldObj.getTopSolidOrLiquidBlock(coord.posX, coord.posZ) + rand.nextInt(10);
                        }

                        EntityManager.transferEntityWithinDimension(entity, coord.posX, coord.posY, coord.posZ, entity.rotationYaw, 0, 0, false);
                        addMediumInstabilityEffects(entity);
                    }
                    else if (instability == 70) // High Instability - 1 Effect
                    {
                        EntityManager.teleportEntityToDimension(entity); // Teleport to dimension
                        addHighInstabilityEffects(entity);
                    }
                }
                else
                {
                    EntityManager.transferEntity(entity, uID, exit, portal.getBlockMetadata()); // Random missed the instability - teleport normally
                }
            }
            else
            {
                EntityManager.transferEntity(entity, uID, exit, portal.getBlockMetadata()); // No instability effects - teleport normally
            }

            TileModuleManipulator module = controller.blockManager.getModuleManipulator(controller.worldObj);

            if (module != null)
            {
                module.onEntityTeleported(entity);
            }

            for (ChunkCoordinates c : controller.blockManager.getRedstoneInterfaces())
            {
                ((TileRedstoneInterface) controller.worldObj.getBlockTileEntity(c.posX, c.posY, c.posZ)).entityTeleport(entity);
            }
        }

        EntityManager.setEntityPortalCooldown(entity);
    }

    void addLowInstabilityEffects(Entity entity)
    {
        if (entity instanceof EntityLivingBase)
        {
            PotionEffect blindness = new PotionEffect(Potion.blindness.id, 200, 1);
            PotionEffect hunger = new PotionEffect(Potion.hunger.id, 200, 1);
            PotionEffect poison = new PotionEffect(Potion.poison.id, 200, 1);

            blindness.setCurativeItems(new ArrayList<ItemStack>());
            hunger.setCurativeItems(new ArrayList<ItemStack>());
            poison.setCurativeItems(new ArrayList<ItemStack>());

            int effect = rand.nextInt(3);
            ((EntityLivingBase) entity).addPotionEffect(effect == 0 ? blindness : effect == 1 ? hunger : poison);
        }
    }

    void addMediumInstabilityEffects(Entity entity)
    {
        if (entity instanceof EntityLivingBase)
        {
            PotionEffect blindness = new PotionEffect(Potion.blindness.id, 400, 1);
            PotionEffect hunger = new PotionEffect(Potion.hunger.id, 400, 1);
            PotionEffect poison = new PotionEffect(Potion.poison.id, 400, 1);

            blindness.setCurativeItems(new ArrayList<ItemStack>());
            hunger.setCurativeItems(new ArrayList<ItemStack>());
            poison.setCurativeItems(new ArrayList<ItemStack>());

            int effect = rand.nextInt(3);

            if (effect == 0)
            {
                ((EntityLivingBase) entity).addPotionEffect(blindness);
                ((EntityLivingBase) entity).addPotionEffect(hunger);
            }
            else if (effect == 1)
            {
                ((EntityLivingBase) entity).addPotionEffect(blindness);
                ((EntityLivingBase) entity).addPotionEffect(poison);
            }
            else
            {
                ((EntityLivingBase) entity).addPotionEffect(poison);
                ((EntityLivingBase) entity).addPotionEffect(hunger);
            }
        }
    }

    void addHighInstabilityEffects(Entity entity)
    {
        if (entity instanceof EntityLivingBase)
        {
            PotionEffect blindness = new PotionEffect(Potion.blindness.id, 600, 1);
            PotionEffect hunger = new PotionEffect(Potion.hunger.id, 600, 1);
            PotionEffect poison = new PotionEffect(Potion.poison.id, 600, 1);

            blindness.setCurativeItems(new ArrayList<ItemStack>());
            hunger.setCurativeItems(new ArrayList<ItemStack>());
            poison.setCurativeItems(new ArrayList<ItemStack>());

            ((EntityLivingBase) entity).addPotionEffect(blindness);
            ((EntityLivingBase) entity).addPotionEffect(hunger);
            ((EntityLivingBase) entity).addPotionEffect(poison);
        }
    }

    public GlyphIdentifier getConnectedPortal(GlyphIdentifier uniqueIdentifier)
    {
        if (activeConnections.containsKey(uniqueIdentifier.getGlyphString()))
        {
            return new GlyphIdentifier(activeConnections.get(uniqueIdentifier.getGlyphString()));
        }
        else if (activeConnectionsReverse.containsKey(uniqueIdentifier.getGlyphString()))
        {
            return new GlyphIdentifier(activeConnectionsReverse.get(uniqueIdentifier.getGlyphString()));
        }

        return null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);

        energyStorage.writeToNBT(tag);
        tag.setInteger("powerState", powerState);
        tag.setInteger("rows", rows);
        GeneralUtils.saveChunkCoordList(tag, blockList, "blockList");

        if (!activeConnections.isEmpty())
        {
            NBTTagList c = new NBTTagList();

            for (Entry<String, String> entry : activeConnections.entrySet())
            {
                NBTTagCompound t = new NBTTagCompound();
                t.setString("Key", entry.getKey());
                t.setString("Value", entry.getValue());
                c.appendTag(t);
            }

            tag.setTag("activeConnections", c);
        }

        if (inventory != null)
        {
            NBTTagCompound t = new NBTTagCompound();
            inventory.writeToNBT(t);
            tag.setTag("inventory", t);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);

        powerState = tag.getInteger("powerState");
        rows = tag.getInteger("rows");
        energyStorage = new EnergyStorage(rows * CommonProxy.REDSTONE_FLUX_COST);
        blockList = GeneralUtils.loadChunkCoordList(tag, "blockList");
        energyStorage.readFromNBT(tag);

        if (tag.hasKey("activeConnections"))
        {
            NBTTagList c = tag.getTagList("activeConnections");

            for (int i = 0; i < c.tagCount(); i++)
            {
                NBTTagCompound t = (NBTTagCompound) c.tagAt(i);

                String A = t.getString("Key"), B = t.getString("Value");

                activeConnections.put(A, B);
                activeConnectionsReverse.put(B, A);
            }
        }

        if (tag.hasKey("inventory"))
        {
            inventory = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("inventory"));
        }
    }

    @Override
    public void fillPacket(DataOutputStream stream) throws IOException
    {
        stream.writeInt(activeConnections.size());
        stream.writeInt(powerState);
        stream.writeInt(instability);
        stream.writeInt(energyStorage.getMaxEnergyStored());
        stream.writeInt(energyStorage.getEnergyStored());
    }

    @Override
    public void usePacket(DataInputStream stream) throws IOException
    {
        intActiveConnections = stream.readInt();
        powerState = stream.readInt();
        instability = stream.readInt();
        energyStorage.setCapacity(stream.readInt());
        energyStorage.setEnergyStored(stream.readInt());
    }

    @Override
    public void validate()
    {
        // Don't call super - we don't need to send any packets here
        tileEntityInvalid = false;
    }

    public void deconstruct()
    {
        for (int i = activeConnections.size() - 1; i > -1; i--) // Go backwards so we don't get messed up by connections getting removed from this list
        {
            terminateExistingConnection(new GlyphIdentifier(activeConnections.values().toArray(new String[activeConnections.size()])[i]));
        }

        for (ChunkCoordinates c : blockList)
        {
            TileEntity tile = worldObj.getBlockTileEntity(c.posX, c.posY, c.posZ);

            if (tile instanceof TileStabilizer)
            {
                TileStabilizer t = (TileStabilizer) tile;
                t.mainBlock = null;
            }
        }

        worldObj.setBlock(xCoord, yCoord, zCoord, CommonProxy.blockStabilizer.blockID, 0, 3);
    }

    public EnergyStorage getEnergyStorage()
    {
        return energyStorage;
    }

    @Override
    public void guiActionPerformed(GuiPayload payload, EntityPlayer player)
    {
        if (payload.data.hasKey("button"))
        {
            powerState++;

            if (powerState > 3)
            {
                powerState = 0;
            }
        }
        if (payload.data.hasKey("energy"))
        {
            energyStorage.setEnergyStored(payload.data.getInteger("energy"));
        }
    }

    /* IEnergyHandler */
    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate)
    {
        return energyStorage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate)
    {
        return energyStorage.extractEnergy(maxExtract, simulate);
    }

    @Override
    public boolean canInterface(ForgeDirection from)
    {
        return true;
    }

    @Override
    public int getEnergyStored(ForgeDirection from)
    {
        return energyStorage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from)
    {
        return energyStorage.getMaxEnergyStored();
    }

    /* IInventory */
    @Override
    public int getSizeInventory()
    {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int i)
    {
        return inventory;
    }

    @Override
    public ItemStack decrStackSize(int i, int j)
    {
        ItemStack stack = getStackInSlot(i);

        if (stack != null)
        {
            if (stack.stackSize <= j)
            {
                setInventorySlotContents(i, null);
            }
            else
            {
                stack = stack.splitStack(j);

                if (stack.stackSize == 0)
                {
                    setInventorySlotContents(i, null);
                }
            }
        }

        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i)
    {
        return inventory;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack)
    {
        inventory = itemstack;
    }

    @Override
    public String getInvName()
    {
        return "tile.ep3.stabilizer.name";
    }

    @Override
    public boolean isInvNameLocalized()
    {
        return false;
    }

    @Override
    public int getInventoryStackLimit()
    {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer entityplayer)
    {
        return true;
    }

    @Override
    public void openChest()
    {
    }

    @Override
    public void closeChest()
    {
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack)
    {
        return EnergyHelper.isEnergyContainerItem(itemstack);
    }

    public int getActiveConnections()
    {
        return activeConnections != null ? activeConnections.size() : -1;
    }

    public void setData(ArrayList<ChunkCoordinates> blocks, int rows2)
    {
        rows = rows2;
        blockList = blocks;
        energyStorage = new EnergyStorage(rows * CommonProxy.REDSTONE_FLUX_COST);
        CommonProxy.sendUpdatePacketToAllAround(this);
    }

    /* IAspectContainer */
    int storedAspect = 0;
    final int MAX_ASPECT = 1000;
    
    @Override
    public AspectList getAspects()
    {
        AspectList list = new AspectList();
        list.add(Aspect.TRAVEL, storedAspect);
        return list;
    }

    @Override
    public void setAspects(AspectList aspects)
    {
        
    }

    @Override
    public boolean doesContainerAccept(Aspect tag)
    {
        return tag.equals(Aspect.TRAVEL);
    }

    @Override
    public int addToContainer(Aspect tag, int amount)
    {
        if (storedAspect + amount <= MAX_ASPECT)
        {
            storedAspect += amount;
            return 0;
        }
        else
        {
            int leftOver = (storedAspect + amount) - MAX_ASPECT;
            storedAspect = MAX_ASPECT;
            
            return leftOver;
        }
    }

    @Override
    public boolean takeFromContainer(Aspect tag, int amount)
    {
        if (tag.equals(Aspect.TRAVEL) && amount >= storedAspect)
        {
            storedAspect -= amount;
            return true;
        }
        
        return false;
    }

    @Override
    public boolean takeFromContainer(AspectList ot)
    {    
        return false;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect tag, int amount)
    {
        return tag.equals(Aspect.TRAVEL) && amount >= storedAspect;
    }

    @Override
    public boolean doesContainerContain(AspectList ot)
    {
        return false;
    }

    @Override
    public int containerContains(Aspect tag)
    {
        return tag.equals(Aspect.TRAVEL) ? storedAspect : 0;
    }

    /* IWandable */
    @Override
    public int onWandRightClick(World world, ItemStack wandstack, EntityPlayer player, int x, int y, int z, int side, int md)
    {
        return 0;
    }

    @Override
    public ItemStack onWandRightClick(World world, ItemStack wandstack, EntityPlayer player)
    {
        player.sendChatToPlayer(ChatMessageComponent.createFromText("Storing: " + storedAspect + " / " + MAX_ASPECT + " (" + ((float) storedAspect / MAX_ASPECT) * 100 + ") essentia.")); 
        return wandstack;
    }

    @Override
    public void onUsingWandTick(ItemStack wandstack, EntityPlayer player, int count)
    {
        
    }

    @Override
    public void onWandStoppedUsing(ItemStack wandstack, World world, EntityPlayer player, int count)
    {
        
    }
}
