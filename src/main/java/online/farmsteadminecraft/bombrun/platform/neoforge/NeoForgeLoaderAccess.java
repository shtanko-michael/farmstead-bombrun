package online.farmsteadminecraft.bombrun.platform.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import online.farmsteadminecraft.bombrun.ModLoaderAccess;

final class NeoForgeLoaderAccess implements ModLoaderAccess {
    private static Dist dist() {
        return FMLEnvironment.dist;
    }

    @Override
    public boolean isClient() {
        return dist().isClient();
    }

    @Override
    public boolean isServer() {
        return dist().isDedicatedServer();
    }

    @Override
    public boolean isModLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    @Override
    public java.nio.file.Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String loaderName() {
        return "neoforge";
    }
}
