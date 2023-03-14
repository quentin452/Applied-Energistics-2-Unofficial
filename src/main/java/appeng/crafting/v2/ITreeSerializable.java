package appeng.crafting.v2;

import java.io.IOException;
import java.util.List;

/**
 * Interface for elements of the crafting tree that can be serialized for network transmission.
 *
 * This forms a semi-sealed class hierarchy, make sure to register all implementers with CraftingTreeSerializer.
 */
public interface ITreeSerializable {

    /**
     * Write the contents of this node to the byte buffer. The type code is pre-serialized into the buffer already.
     *
     * @return The list of child nodes to recursively serialize (done using a custom stack to avoid
     *         StackOverflowExceptions).
     */
    List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException;

    /**
     * Ran after deserializing of the children finishes.
     *
     * @param children The deserialized children.
     */
    void loadChildren(List<ITreeSerializable> children) throws IOException;

    /**
     * Use when inlining serialization of a child class instead of going through the queue system.
     *
     * @return An override for the parent object passed to deserialized children.
     */
    default ITreeSerializable getSerializationParent() {
        return this;
    }
}
