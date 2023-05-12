package tterrag.wailaplugins.plugins;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.enderio.core.common.util.BlockCoord;

import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;
import mekanism.api.gas.GasTank;
import mekanism.common.tile.TileEntityElectricBlock;
import mekanism.common.tile.TileEntityGasTank;
import mekanism.common.util.MekanismUtils;
import tterrag.wailaplugins.api.Plugin;

@Plugin(name = "Mekanism", deps = { "Mekanism" })
public class PluginMekanism extends PluginBase {

    @Override
    public void load(IWailaRegistrar registrar) {
        super.load(registrar);

        registerBody(TileEntityGasTank.class, TileEntityElectricBlock.class);

        registerNBT(TileEntityGasTank.class, TileEntityElectricBlock.class);

        addConfig("gas");
        addConfig("energy");
    }

    @Override
    protected void getBody(ItemStack stack, List<String> currenttip, IWailaDataAccessor accessor) {
        TileEntity te = accessor.getTileEntity();
        NBTTagCompound tag = accessor.getNBTData();

        // Show mekanism gas <amount> / <tank-capacity> m³ <gas-type>
        if (te instanceof TileEntityGasTank && getConfig("gas")) {
            GasTank gasTank = GasTank.readFromNBT(tag);

            if (gasTank != null) {
                if (gasTank.getGas() != null) {
                    currenttip.add(
                            String.format(
                                    "%d / %d m³ %s",
                                    gasTank.getStored(),
                                    gasTank.getMaxGas(),
                                    gasTank.getGasType().getLocalizedName()));
                } else {
                    currenttip.add(String.format("0 / %d m³", gasTank.getMaxGas()));
                }
            }
        }

        // Show mekanism electricity <stored> / <max> on electric blocks
        if (te instanceof TileEntityElectricBlock && getConfig("energy")) {
            if (tag.hasKey("energyInfo")) {
                NBTTagCompound energyInfoTag = tag.getCompoundTag("energyInfo");

                currenttip.add(
                        String.format(
                                "%s / %s",
                                MekanismUtils.getEnergyDisplay(energyInfoTag.getDouble("energyStored")),
                                MekanismUtils.getEnergyDisplay(energyInfoTag.getDouble("energyMax"))));

            }
        }
    }

    @Override
    protected void getNBTData(TileEntity te, NBTTagCompound tag, World world, BlockCoord pos) {
        if (te instanceof TileEntityGasTank && getConfig("gas")) {
            ((TileEntityGasTank) te).gasTank.write(tag);
        }

        if (te instanceof TileEntityElectricBlock && getConfig("energy")) {
            NBTTagCompound energyInfoTag = new NBTTagCompound();

            energyInfoTag.setDouble("energyMax", ((TileEntityElectricBlock) te).getMaxEnergy());
            energyInfoTag.setDouble("energyStored", ((TileEntityElectricBlock) te).getEnergy());

            tag.setTag("energyInfo", energyInfoTag);
        }

        te.writeToNBT(tag);
    }
}
