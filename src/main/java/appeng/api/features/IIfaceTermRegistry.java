package appeng.api.features;

import appeng.api.util.IIfaceTermViewable;

/**
 * Registry for interface terminal support.
 */
public interface IIfaceTermRegistry {

    /**
     * Registers a class to be considered supported in interface terminals.
     */
    void register(Class<? extends IIfaceTermViewable> clazz);
}
