package nay.amethyst.check;

import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.powernukkitx.Player;

import java.util.Arrays;

/** Matches the client identity a known tool sends. */
public final class BedrockToolDetector {

    private static final String DEVICE_MODEL = "SM-G970F";
    private static final String GEOMETRY_VERSION = "0.0.0";
    private static final int SKIN_WIDTH = 64;
    private static final int SKIN_HEIGHT = 32;
    private static final byte[] BLANK_SKIN = blankSkin();

    private BedrockToolDetector() {
    }

    public static String detect(Player player) {
        var wrapper = player.getSkin();
        var serialized = wrapper == null ? null : wrapper.getSkin();
        if (serialized == null) {
            return null;
        }

        ImageData image = serialized.getSkinData();
        if (image == null || !isBlankSkin(image.getWidth(), image.getHeight(), image.getImage())) {
            return null;
        }

        var chain = player.getClientChainData();
        boolean modelMatch = chain != null && DEVICE_MODEL.equals(chain.getDeviceModel())
                && chain.getDeviceOS() == BuildPlatform.GOOGLE;
        if (modelMatch && GEOMETRY_VERSION.equals(serialized.getGeometryDataEngineVersion())) {
            return "device=" + DEVICE_MODEL + " geometry=" + GEOMETRY_VERSION
                    + " blankSkin=" + SKIN_WIDTH + "x" + SKIN_HEIGHT;
        }

        return "blankSkin=" + SKIN_WIDTH + "x" + SKIN_HEIGHT
                + " device=" + (chain == null ? "?" : chain.getDeviceModel())
                + " geometry=" + serialized.getGeometryDataEngineVersion();
    }

    private static boolean isBlankSkin(int width, int height, byte[] data) {
        return width == SKIN_WIDTH && height == SKIN_HEIGHT && Arrays.equals(BLANK_SKIN, data);
    }

    private static byte[] blankSkin() {
        byte[] skin = new byte[SKIN_WIDTH * SKIN_HEIGHT * 4];
        for (int i = 3; i < skin.length; i += 4) {
            skin[i] = (byte) 0xff;
        }
        return skin;
    }
}
