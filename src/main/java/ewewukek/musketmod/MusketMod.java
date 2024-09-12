package ewewukek.musketmod;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

@Mod(MusketMod.MOD_ID)
public class MusketMod
{
	public static final String MOD_ID = "musketmod";
	public static ResourceLocation id(String path)
	{
		return new ResourceLocation(MOD_ID, path);
	}

	public static ResourceLocation resource(String path) {
		return new ResourceLocation(MOD_ID, path);
	}


}
