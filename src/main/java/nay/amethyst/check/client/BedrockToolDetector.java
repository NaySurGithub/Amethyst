package nay.amethyst.check.client;

import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.powernukkitx.Player;

/**
 * Matches the identity a known tool sends: a fixed device model, an empty geometry engine version and
 * a fully black skin. Any one of them can belong to a real player; the three together do not.
 */
public final class BedrockToolDetector {

    private static final String DEVICE_MODEL = "SM-G970F";
    private static final String GEOMETRY_VERSION = "0.0.0";
    private static final int SKIN_WIDTH = 64;
    private static final int SKIN_HEIGHT = 32;

    private BedrockToolDetector() {
    }

    public static String detect(Player player) {
        var chain = player.getClientChainData();
        if (chain == null) {
            return null;
        }

        boolean modelMatch = DEVICE_MODEL.equals(chain.getDeviceModel())
                && chain.getDeviceOS() == BuildPlatform.GOOGLE;
        if (!modelMatch) {
            return null;
        }

        var wrapper = player.getSkin();
        var serialized = wrapper == null ? null : wrapper.getSkin();
        if (serialized == null || !GEOMETRY_VERSION.equals(serialized.getGeometryDataEngineVersion())) {
            return null;
        }

        ImageData image = serialized.getSkinData();
        if (image == null || !isBlankSkin(image.getWidth(), image.getHeight(), image.getImage())) {
            return null;
        }

        return "device=" + DEVICE_MODEL + " geometry=" + GEOMETRY_VERSION
                + " blankSkin=" + SKIN_WIDTH + "x" + SKIN_HEIGHT;
    }

    private static boolean isBlankSkin(int width, int height, byte[] data) {
        if (width != SKIN_WIDTH || height != SKIN_HEIGHT) {
            return false;
        }
        if (data == null || data.length != SKIN_WIDTH * SKIN_HEIGHT * 4) {
            return false;
        }

        for (int i = 0; i < data.length; i += 4) {
            if (data[i] != 0 || data[i + 1] != 0 || data[i + 2] != 0 || data[i + 3] != (byte) -1) {
                return false;
            }
        }
        return true;
    }
}
