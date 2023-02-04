package appeng.client.render;

import java.util.ArrayList;
import java.util.List;

import appeng.api.util.DimensionalCoord;

// taken from McJty's McJtyLib
public class BlockPosHighlighter {

    private static final List<DimensionalCoord> highlightedBlock = new ArrayList<>();
    private static long expireHighlight;
    private static final int min = 3000;

    public static void highlightBlock(DimensionalCoord c, long expireHighlight) {
        highlightedBlock.add(c);
        BlockPosHighlighter.expireHighlight = Math.max(expireHighlight, System.currentTimeMillis() + min);
    }

    public static List<DimensionalCoord> getHighlightedBlock() {
        return highlightedBlock;
    }

    public static void clear() {
        highlightedBlock.clear();
        expireHighlight = -1;
    }

    public static long getExpireHighlight() {
        return expireHighlight;
    }
}
