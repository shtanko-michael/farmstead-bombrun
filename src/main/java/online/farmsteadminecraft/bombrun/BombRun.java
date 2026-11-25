package online.farmsteadminecraft.bombrun;

import net.minecraft.resources.ResourceLocation;
import online.farmsteadminecraft.bombrun.brand.FarmsteadBrand;
import online.farmsteadminecraft.bombrun.config.BombRunConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-independent entry point: identity, branding, config. Each loader's entrypoint calls {@link #init()}. */
public final class BombRun {
    public static final String MOD_ID = "farmstead_bombrun";
    public static final String NAME = "Bomb Run";
    /** Project slug on Modrinth (free as of 2026-11-25, not claimed yet): the start-up line links there and the update check asks it. */
    public static final String MODRINTH_SLUG = "bomb-run";
    /** CurseForge project id: none yet, so the update check asks only Modrinth. */
    public static final String CURSEFORGE_ID = "";
    public static final Logger LOGGER = LoggerFactory.getLogger("Bomb Run");
    public static final String VERSION = /*$ mod_version*/"0.1.0-alpha.1";
    public static final String MINECRAFT = /*$ minecraft*/"1.21.1";

    private BombRun() {
    }

    public static void init() {
        String loader = ModLoaderAccess.get().loaderName();
        FarmsteadBrand.started(LOGGER, NAME, VERSION, loader, MINECRAFT, MODRINTH_SLUG);
        BombRunConfig.load();
        if (BombRunConfig.CHECK_UPDATES.on()) checkForUpdate();
        else FarmsteadBrand.off(LOGGER, NAME);
    }

    public static void checkForUpdate() {
        FarmsteadBrand.checkForUpdate(LOGGER, new FarmsteadBrand.Project(NAME, VERSION, ModLoaderAccess.get().loaderName(),
            MINECRAFT, MODRINTH_SLUG, CURSEFORGE_ID));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
