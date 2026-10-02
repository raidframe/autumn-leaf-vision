package image;

/**
 * Binary image produced by the analyser.
 *
 * The mask stores only two states per pixel:
 * - true  -> this pixel is considered part of a leaf region
 * - false -> this pixel is treated as background
 *
 * The data is stored in one flat array instead of boolean[height][width]
 * because that is compact, fast to scan, and convenient for Union-Find.
 */
public class BinaryMask {
    private final int width;
    private final int height;
    // A compact one-dimensional representation of a two-dimensional black/white image.
    // white[y * width + x] represents the pixel at column x and row y.
    // true means the pixel belongs to a detected leaf area; false means background.
    private final boolean[] white;

    public BinaryMask(int width, int height) {
        this.width = width;
        this.height = height;
        this.white = new boolean[width * height];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public boolean isWhite(int x, int y) {
        // Convert 2D coordinates into the 1D array index.
        return white[y * width + x];
    }

    public void setWhite(int x, int y, boolean value) {
        // Store whether this pixel is considered part of the leaf mask.
        white[y * width + x] = value;
    }

    public boolean[] data() {
        // Exposes the raw mask data in case another algorithm needs direct access.
        return white;
    }
}
