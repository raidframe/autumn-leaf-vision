package models;

/**
 * Bounding box and metadata for one connected component.
 *
 * A ClusterBox represents one detected group of white pixels in the mask.
 * It stores:
 * - the smallest rectangle that contains the whole group
 * - the number of pixels in that group
 * - a rank used by the UI when clusters are sorted by size
 *
 * The box is updated incrementally while pixels of the same component are scanned.
 */

public class ClusterBox {
    private int minX;
    private int minY;
    private int maxX;
    private int maxY;
    private int pixels;
    private int rank;

    public ClusterBox(int x, int y) {
        // A new cluster starts with one pixel, so min and max coordinates are identical.
        this.minX = x;
        this.minY = y;
        this.maxX = x;
        this.maxY = y;
        this.pixels = 1;
    }

    public void include(int x, int y) {
        // Expand the rectangle so it contains the new pixel.
        // The pixel counter is also increased so the cluster can later be filtered by size.
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        pixels++;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getPixels() { return pixels; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    // Centre point of the rectangle, used for route drawing and distance calculations.
    public double centreX() { return (minX + maxX) / 2.0; }
    public double centreY() { return (minY + maxY) / 2.0; }

    // Rectangle dimensions in mask pixels.
    public int width() { return maxX - minX + 1; }
    public int height() { return maxY - minY + 1; }

    public boolean contains(int x, int y) {
        // Checks whether a mask coordinate lies inside this cluster's bounding rectangle.
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
