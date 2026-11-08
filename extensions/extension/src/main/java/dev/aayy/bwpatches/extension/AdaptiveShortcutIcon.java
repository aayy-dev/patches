package dev.aayy.bwpatches.extension;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;

/**
 * Runtime logic for the Native Alpha "Keep shortcut icon shape" patch.
 *
 * <p>Native Alpha hands the picked image to the launcher as a plain bitmap icon, which launchers
 * treat as a legacy icon and force into their own shape (shrunk onto a backplate, or cropped).
 * This turns the image into an adaptive icon instead: the image is centred on a transparent
 * canvas and scaled so that every visible pixel stays inside the circle that all launcher icon
 * shapes contain. The launcher's mask then only ever clips transparent padding, so the icon keeps
 * the image's own shape and transparency.
 *
 * <p>Uses only framework APIs and fails safe: on any error the original bitmap is returned.
 */
@SuppressWarnings("unused")
public final class AdaptiveShortcutIcon {

    /**
     * Adaptive icon layers are 108 units square, and launchers show at most the centred 72 units.
     * Every mask shape (circle, squircle, rounded square, teardrop) contains the centred circle of
     * diameter 72, so content within radius 36 is never clipped.
     */
    private static final float UNCLIPPED_RADIUS_FRACTION = 36f / 108f;

    /** Output canvas side in pixels; the launcher scales it to its icon size. */
    private static final int CANVAS_SIZE = 432;

    /** Larger images are scaled down to this side before measuring and drawing. */
    private static final int WORKING_MAX_SIDE = 512;

    /** Pixels at or below this alpha count as transparent padding. */
    private static final int ALPHA_THRESHOLD = 16;

    private AdaptiveShortcutIcon() {
    }

    /**
     * Icon size in percent of the largest unclipped size. The patch replaces this method's body
     * with the value of its "Icon size" option.
     */
    public static int sizePercent() {
        return 100;
    }

    /**
     * @return the image placed on a transparent canvas sized for an adaptive icon, or
     * {@code source} unchanged if it is null, fully transparent, or anything goes wrong.
     */
    public static Bitmap toAdaptive(Bitmap source) {
        if (source == null) {
            return null;
        }
        try {
            Bitmap image = source;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && image.getConfig() == Bitmap.Config.HARDWARE) {
                image = image.copy(Bitmap.Config.ARGB_8888, false);
            }
            int longest = Math.max(image.getWidth(), image.getHeight());
            if (longest > WORKING_MAX_SIDE) {
                float shrink = (float) WORKING_MAX_SIDE / longest;
                image = Bitmap.createScaledBitmap(image,
                        Math.max(1, Math.round(image.getWidth() * shrink)),
                        Math.max(1, Math.round(image.getHeight() * shrink)),
                        true);
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixels(pixels, 0, width, 0, 0, width, height);

            // Bounding box of the visible pixels.
            int minX = width;
            int minY = height;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((pixels[y * width + x] >>> 24) > ALPHA_THRESHOLD) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (maxX < 0) {
                return source;
            }

            // Distance from the box centre to the farthest visible pixel corner.
            float centreX = (minX + maxX + 1) / 2f;
            float centreY = (minY + maxY + 1) / 2f;
            float farthestSquared = 0f;
            for (int y = minY; y <= maxY; y++) {
                float dy = Math.max(Math.abs(y - centreY), Math.abs(y + 1 - centreY));
                for (int x = minX; x <= maxX; x++) {
                    if ((pixels[y * width + x] >>> 24) > ALPHA_THRESHOLD) {
                        float dx = Math.max(Math.abs(x - centreX), Math.abs(x + 1 - centreX));
                        float squared = dx * dx + dy * dy;
                        if (squared > farthestSquared) farthestSquared = squared;
                    }
                }
            }
            float farthest = (float) Math.sqrt(farthestSquared);

            float targetRadius = UNCLIPPED_RADIUS_FRACTION * CANVAS_SIZE * (sizePercent() / 100f);
            float scale = targetRadius / farthest;
            float half = CANVAS_SIZE / 2f;
            RectF destination = new RectF(
                    half - centreX * scale,
                    half - centreY * scale,
                    half + (width - centreX) * scale,
                    half + (height - centreY) * scale);

            Bitmap output = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
            new Canvas(output).drawBitmap(image, null, destination,
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            return output;
        } catch (Throwable t) {
            return source;
        }
    }
}
