package appeng.core.features.registries;

import java.util.HashSet;
import java.util.Set;

import appeng.api.features.IIfaceTermRegistry;
import appeng.api.util.IIfaceTermViewable;
import appeng.parts.misc.PartInterface;
import appeng.parts.p2p.PartP2PInterface;
import appeng.tile.misc.TileInterface;

/**
 * Interface Terminal Registry impl for registering viewable instances.
 */
public class IfaceTermRegistry implements IIfaceTermRegistry {

    private final Set<Class<? extends IIfaceTermViewable>> supportedClasses = new HashSet<>();
    private static IfaceTermRegistry INSTANCE;

    /**
     * Singleton, do not instantiate more than once.
     */
    public IfaceTermRegistry() {
        supportedClasses.add(TileInterface.class);
        supportedClasses.add(PartInterface.class);
        supportedClasses.add(PartP2PInterface.class);
        INSTANCE = this;
    }

    /**
     * Get all supported classes that were registered during startup
     */
    public Set<Class<? extends IIfaceTermViewable>> getSupportedClasses() {
        return supportedClasses;
    }

    @Override
    public void register(Class<? extends IIfaceTermViewable> clazz) {
        supportedClasses.add(clazz);
    }

    public static IfaceTermRegistry instance() {
        return INSTANCE;
    }
}
