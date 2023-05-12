package tterrag.wailaplugins.plugins;

import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.IntStream;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.util.ItemUtil;

import mcp.mobius.waila.api.*;
import mcp.mobius.waila.api.impl.ModuleRegistrar;
import mods.railcraft.api.electricity.IElectricGrid;
import mods.railcraft.api.tracks.ITrackInstance;
import mods.railcraft.common.blocks.machine.TileMachineBase;
import mods.railcraft.common.blocks.machine.TileMultiBlock;
import mods.railcraft.common.blocks.machine.alpha.TileTankWater;
import mods.railcraft.common.blocks.machine.beta.*;
import mods.railcraft.common.blocks.tracks.TileTrack;
import mods.railcraft.common.blocks.tracks.TrackElectric;
import mods.railcraft.common.carts.EntityLocomotive;
import mods.railcraft.common.carts.EntityLocomotiveElectric;
import mods.railcraft.common.carts.EntityLocomotiveSteam;
import mods.railcraft.common.fluids.tanks.StandardTank;
import mods.railcraft.common.items.ItemElectricMeter;
import mods.railcraft.common.plugins.buildcraft.triggers.ITemperature;
import tterrag.wailaplugins.api.Plugin;
import tterrag.wailaplugins.config.WPConfigHandler;

final class WaterTankRateCalculator {

    private static final float RATE_TICK_RATIO = 0.125F;
    private final float ONE = 1.0F;
    private final float BASE_HUMIDITY_RATE = 10F;
    private final float INSIDE_RATE = 0.5F;
    private final float SNOW_RATE = 0.5F;
    private final float RAIN_RATE = 3.0F;
    private final World world;
    private final int x;
    private final int y;
    private final int z;

    private float rate;

    private float humidityRate;

    private float insideRate;

    private float snowingOrRainingRate;

    WaterTankRateCalculator(TileMultiBlock multiBlock) {
        world = multiBlock.getWorld();
        x = multiBlock.getMasterBlock().xCoord;
        y = multiBlock.getMasterBlock().yCoord;
        z = multiBlock.getMasterBlock().zCoord;
    }

    public static float convertRateToLitersPerSecond(float rate) {
        return rate * RATE_TICK_RATIO * 20;
    }

    public WaterTankRateCalculator init() {
        humidityRate = calculateHumidityRate();
        insideRate = calculateInsideRate();
        snowingOrRainingRate = calculateSnowingOrRainingRate();

        rate = Math.max(MathHelper.floor_float(humidityRate * insideRate * snowingOrRainingRate), ONE);
        return this;
    }

    private float calculateHumidityRate() {
        return BASE_HUMIDITY_RATE * world.getBiomeGenForCoords(x, z).rainfall;
    }

    private float calculateInsideRate() {
        IntStream streamX = IntStream.rangeClosed(x - 1, x + 1);
        return streamX.anyMatch(eachX -> {
            IntStream streamZ = IntStream.rangeClosed(z - 1, z + 1);
            return streamZ.anyMatch(eachZ -> world.canBlockSeeTheSky(eachX, y + 3, eachZ));
        }) ? ONE : INSIDE_RATE;
    }

    private float calculateSnowingOrRainingRate() {
        if (!world.isRaining() || getIsInside()) return ONE;
        return world.getBiomeGenForCoords(x, z).getEnableSnow() ? SNOW_RATE : RAIN_RATE;
    }

    public float getRate() {
        return rate;
    }

    public float getHumidityRate() {
        return humidityRate;
    }

    public float getInsideRate() {
        return insideRate;
    }

    public float getSnowingOrRainingRate() {
        return snowingOrRainingRate;
    }

    public boolean getIsSnowing() {
        return snowingOrRainingRate == SNOW_RATE;
    }

    public boolean getIsRaining() {
        return snowingOrRainingRate == RAIN_RATE;
    }

    public boolean getIsInside() {
        return insideRate == INSIDE_RATE;
    }

}

@Plugin(deps = "Railcraft")
public class PluginRailcraft extends PluginBase implements IWailaEntityProvider {

    public static final String TANK_FLUID = "tankFluid";
    public static final String HEAT = "heat";
    public static final String MAX_HEAT = "maxHeat";
    public static final String CURRENT_OUTPUT = "currentOutput";
    public static final String ENERGY_STORED = "energyStored";
    public static final String CHARGE = "charge";
    private static final DecimalFormat fmtCharge = new DecimalFormat("#.##");

    private static void addChargeTooltip(List<String> currenttip, NBTTagCompound tag, EntityPlayer player) {
        ItemStack current = player.getCurrentEquippedItem();
        boolean hasMeter = !WPConfigHandler.meterInHand
                || (current != null && ItemUtil.stacksEqual(current, ItemElectricMeter.getItem()));

        double charge = tag.getDouble(CHARGE);
        String chargeFmt = fmtCharge.format(charge) + "c";

        currenttip.add(
                EnumChatFormatting.RESET + String.format(
                        lang.localize("charge"),
                        hasMeter ? chargeFmt : (EnumChatFormatting.ITALIC + lang.localize("needMeter"))));
    }

    private static void addHeatTooltip(List<String> currenttip, NBTTagCompound tag) {
        int heat = Math.round(tag.getFloat(HEAT));
        int max = Math.round(tag.getFloat(MAX_HEAT));

        currenttip.add(String.format(lang.localize("engineTemp"), heat, max));
    }

    @Override
    public void load(IWailaRegistrar registrar) {
        super.load(registrar);

        registerBody(TileMachineBase.class, TileTrack.class);
        registerNBT(TileEngineSteam.class, IElectricGrid.class, TileTrack.class, TileMultiBlock.class);

        registerEntityBody(this, EntityLocomotive.class);
        registerEntityNBT(this, EntityLocomotive.class);

        addConfig("multiblocks");
        addConfig("heat");
        addConfig("tanks");
        addConfig("energy");
        addConfig("engines");
        addConfig("charge");
        addConfig("locomotives");
        addConfig("waterTankRate");
    }

    @Override
    public void postLoad() {
        // Remove WAILA's RC plugin
        ModuleRegistrar.instance().bodyBlockProviders.remove(TileTankBase.class);
    }

    @Override
    protected void getBody(ItemStack stack, List<String> currenttip, IWailaDataAccessor accessor) {
        TileEntity tile = accessor.getTileEntity();
        NBTTagCompound tag = accessor.getNBTData();

        if (tile instanceof TileMultiBlock && getConfig("multiblocks")) {
            currenttip.add(
                    String.format(
                            lang.localize("formed"),
                            lang.localize(((TileMultiBlock) tile).isStructureValid() ? "yes" : "no")));
        }

        if ((tile instanceof TileEngineSteamHobby || tile instanceof TileBoilerFirebox
                || tile instanceof TileBoilerTank) && getConfig("heat")) {
            addHeatTooltip(currenttip, tag);
        }

        if (tile instanceof TileEngine && getConfig("engines")) {
            int energy = tag.getInteger(ENERGY_STORED);
            int gen = Math.round(tag.getFloat(CURRENT_OUTPUT));

            currenttip.add(lang.localize("energyStored", energy + " / " + ((TileEngine) tile).maxEnergy() + " RF"));
            currenttip.add(lang.localize("generating", gen));
        }

        if (tag.hasKey(TANK_FLUID)) {
            FluidTankInfo fluidInfo = FluidHandlerHelper.getFluidInfo(tag.getCompoundTag(TANK_FLUID));

            FluidHandlerHelper.addFluidInfo((ITaggedList<String, String>) currenttip, fluidInfo);
        }

        if (tile instanceof TileTankWater && ((TileTankWater) tile).isStructureValid() && getConfig("waterTankRate")) {
            WaterTankRateCalculator waterTankRateCalculator = new WaterTankRateCalculator((TileMultiBlock) tile).init();
            float rate = waterTankRateCalculator.getRate();
            currenttip.add(
                    lang.localize("currentRate", rate, WaterTankRateCalculator.convertRateToLitersPerSecond(rate)));
            currenttip.add(lang.localize("biomeHumidityRate", waterTankRateCalculator.getHumidityRate()));

            float snowingOrRainingRate = waterTankRateCalculator.getSnowingOrRainingRate();
            if (waterTankRateCalculator.getIsRaining()) {
                currenttip.add(lang.localize("rainingRate", snowingOrRainingRate));
            } else if (waterTankRateCalculator.getIsSnowing()) {
                currenttip.add(lang.localize("snowingRate", snowingOrRainingRate));
            }

            if (waterTankRateCalculator.getIsInside()) {
                currenttip.add(lang.localize("cantSeeTheSkyRate", waterTankRateCalculator.getInsideRate()));
            }
        }

        if (getConfig("charge") && tag.hasKey(CHARGE)) {
            addChargeTooltip(currenttip, tag, accessor.getPlayer());
        }
    }

    @Override
    protected void getNBTData(TileEntity te, NBTTagCompound tag, World world, BlockCoord pos) {
        if (te instanceof TileMultiBlock && ((TileMultiBlock) te).getMasterBlock() != null) {
            if (te instanceof TileTankBase && !(te instanceof IFluidHandler)) {
                te = ((TileMultiBlock) te).getMasterBlock();
                StandardTank tank = ((TileTankBase) te).getTank();
                NBTTagCompound fluidTag = new NBTTagCompound();
                FluidHandlerHelper.setFluidInfo(fluidTag, tank.getInfo());
                tag.setTag(TANK_FLUID, fluidTag);
            }
            te = ((TileMultiBlock) te).getMasterBlock();
        }
        if (te instanceof ITemperature) {
            tag.setFloat(HEAT, ((ITemperature) te).getTemperature());
        }
        if (te instanceof TileEngine) {
            tag.setFloat(CURRENT_OUTPUT, ((TileEngine) te).currentOutput);
            tag.setInteger(ENERGY_STORED, ((TileEngine) te).getEnergy());
            if (te instanceof TileEngineSteamHobby) {
                tag.setDouble(MAX_HEAT, ((TileEngineSteamHobby) te).boiler.getMaxHeat());
            }
        }
        if (te instanceof IElectricGrid) {
            tag.setDouble(CHARGE, ((IElectricGrid) te).getChargeHandler().getCharge());
        }
        if (te instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) te).getTrackInstance();
            if (track instanceof IElectricGrid) {
                tag.setDouble(CHARGE, ((TrackElectric) track).getChargeHandler().getCharge());
            }
        }
    }

    @Override
    public Entity getWailaOverride(IWailaEntityAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(Entity entity, List<String> currenttip, IWailaEntityAccessor accessor,
            IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaBody(Entity entity, List<String> currenttip, IWailaEntityAccessor accessor,
            IWailaConfigHandler config) {
        NBTTagCompound tag = accessor.getNBTData();

        if (!getConfig("locomotives")) {
            return currenttip;
        }

        if (entity instanceof EntityLocomotiveElectric) {
            addChargeTooltip(currenttip, tag, accessor.getPlayer());
        }

        if (entity instanceof EntityLocomotiveSteam) {
            addHeatTooltip(currenttip, tag);
        }

        return currenttip;
    }

    @Override
    public List<String> getWailaTail(Entity entity, List<String> currenttip, IWailaEntityAccessor accessor,
            IWailaConfigHandler config) {
        return null;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, Entity ent, NBTTagCompound tag, World world) {
        if (ent instanceof EntityLocomotiveElectric) {
            tag.setDouble(CHARGE, ((EntityLocomotiveElectric) ent).getChargeHandler().getCharge());
        }

        if (ent instanceof EntityLocomotiveSteam) {
            tag.setDouble(HEAT, ((EntityLocomotiveSteam) ent).boiler.getHeat());
            tag.setDouble(MAX_HEAT, ((EntityLocomotiveSteam) ent).boiler.getMaxHeat());
        }

        return tag;
    }
}
