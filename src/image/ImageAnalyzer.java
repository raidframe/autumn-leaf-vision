package image;

import algorithms.UnionFindGrid;
import models.AnalysisResult;
import models.ClusterBox;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Main image-processing pipeline.
 *
 * Responsibility:
 * - resize the input image to a predictable working size
 * - classify pixels as leaf/background
 * - group neighbouring leaf pixels into connected components
 * - convert those components into ClusterBox objects
 *
 * The UI should treat this class as the main analysis service and should not
 * implement image-processing logic on its own.
 */
public class ImageAnalyzer {

    public AnalysisResult analyse(Image image,
                                  List<Color> sampleColours,
                                  int targetWidth,
                                  double hueTolerance,
                                  double minSaturation,
                                  double minBrightness,
                                  int minPixels,
                                  int maxPixels) {
        // Main analysis pipeline:
        // 1. Resize the image to a fixed analysis width.
        // 2. Convert the image into a binary mask where white pixels are possible leaves.
        // 3. Group neighbouring white pixels into connected clusters.
        // 4. Remove clusters that are too small or too large.
        //
        // Resizing keeps the algorithm fast and makes pixel thresholds more predictable.
        int width = Math.max(1, targetWidth);
        double ratio = image.getHeight() / image.getWidth();
        int height = Math.max(1, roundToInt(width * ratio));

        Image scaled = new Image(image.getUrl(), width, height, true, false);
        PixelReader reader = scaled.getPixelReader();
        image.BinaryMask mask = buildMask(reader, width, height, sampleColours, hueTolerance, minSaturation, minBrightness);
        List<ClusterBox> clusters = collectClusters(mask, minPixels, maxPixels);
        int totalLeafPixels = clusters.stream().mapToInt(ClusterBox::getPixels).sum();
        int estimatedLeafCount = estimateLeafCount(clusters, minPixels);
        return new AnalysisResult(mask, clusters, totalLeafPixels, estimatedLeafCount);
    }

    private image.BinaryMask buildMask(PixelReader reader,
                                       int width,
                                       int height,
                                       List<Color> sampleColours,
                                       double hueTolerance,
                                       double minSaturation,
                                       double minBrightness) {
        image.BinaryMask mask = new image.BinaryMask(width, height);
        boolean useSamples = !sampleColours.isEmpty();

        // Convert every pixel into a boolean value.
        // If the user picked sample colours, pixels are compared against those samples.
        // If no samples exist, the program uses a general autumn-colour preset.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = reader.getColor(x, y);
                boolean white = useSamples
                        ? matchesAnySample(c, sampleColours, hueTolerance, minSaturation, minBrightness)
                        : matchesAutumnPreset(c, minSaturation, minBrightness);
                mask.setWhite(x, y, white);
            }
        }
        return mask;
    }

    private boolean matchesAnySample(Color pixel,
                                     List<Color> samples,
                                     double hueTolerance,
                                     double minSaturation,
                                     double minBrightness) {
        // Very dark or grey pixels are ignored before hue comparison.
        // This avoids detecting shadows, white background, or low-colour noise.
        if (pixel.getSaturation() < minSaturation || pixel.getBrightness() < minBrightness) {
            return false;
        }

        // Compare the pixel hue with each user-selected colour sample.
        // A pixel is accepted if it is close enough to at least one sample.
        for (Color sample : samples) {
            double diff = hueDistance(pixel.getHue(), sample.getHue());
            if (diff <= hueTolerance) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAutumnPreset(Color pixel, double minSaturation, double minBrightness) {
        double h = pixel.getHue();
        // Default preset for orange/yellow/brown autumn colours.
        // Hue values around 5..80 degrees usually cover reds, oranges, yellows,
        // and light browns. Saturation and brightness filters reduce background noise.
        return pixel.getSaturation() >= minSaturation
                && pixel.getBrightness() >= minBrightness
                && (h >= 5 && h <= 80);
    }

    private double hueDistance(double a, double b) {
        // Hue is circular: 0 degrees and 360 degrees represent the same colour.
        // Therefore the distance must wrap around the edge of the colour wheel.
        double diff = absolute(a - b);
        return Math.min(diff, 360 - diff);
    }

    private List<ClusterBox> collectClusters(image.BinaryMask mask, int minPixels, int maxPixels) {
        int width = mask.getWidth();
        int height = mask.getHeight();
        UnionFindGrid uf = new UnionFindGrid(width * height);

        // First pass: merge neighbouring white pixels into connected components.
        // Only left and upper neighbours are checked because the scan goes
        // from top-left to bottom-right; right and lower neighbours will be
        // handled later when those pixels are reached.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask.isWhite(x, y)) continue;
                int id = y * width + x;
                if (x > 0 && mask.isWhite(x - 1, y)) uf.union(id, id - 1);
                if (y > 0 && mask.isWhite(x, y - 1)) uf.union(id, id - width);
            }
        }

        // Second pass: convert each connected component into a bounding box.
        // The root returned by UnionFindGrid identifies which component a pixel belongs to.
        Map<Integer, ClusterBox> boxes = new HashMap<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask.isWhite(x, y)) continue;
                int root = uf.find(y * width + x);
                ClusterBox box = boxes.get(root);
                if (box == null) {
                    boxes.put(root, new ClusterBox(x, y));
                } else {
                    box.include(x, y);
                }
            }
        }

        // Keep only clusters within the configured size limits.
        // This removes tiny noise spots and very large regions that are unlikely
        // to represent individual leaves or useful leaf groups.
        List<ClusterBox> result = new ArrayList<>();
        for (ClusterBox box : boxes.values()) {
            if (box.getPixels() >= minPixels && box.getPixels() <= maxPixels) {
                result.add(box);
            }
        }

        // Larger clusters are shown first and receive smaller rank numbers.
        result.sort(Comparator.comparingInt(ClusterBox::getPixels).reversed());
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setRank(i + 1);
        }
        return result;
    }

    private int estimateLeafCount(List<ClusterBox> clusters, int minPixels) {
        // Connected components are not always equal to individual leaves because
        // touching leaves merge into one region. Estimate the total leaf count by
        // comparing each cluster size with a "typical single leaf" size derived
        // from the smaller detected clusters in this image.
        if (clusters.isEmpty()) return 0;
        if (clusters.size() == 1) return 1;

        List<Integer> pixelCounts = new ArrayList<>();
        for (ClusterBox box : clusters) {
            pixelCounts.add(box.getPixels());
        }
        Collections.sort(pixelCounts);

        int sampleSize = Math.max(1, pixelCounts.size() / 2);
        List<Integer> smallerHalf = pixelCounts.subList(0, sampleSize);
        int typicalSingleLeafPixels = median(smallerHalf);
        typicalSingleLeafPixels = Math.max(typicalSingleLeafPixels, Math.max(1, minPixels));

        int estimatedLeaves = 0;
        for (ClusterBox box : clusters) {
            estimatedLeaves += Math.max(1, roundToInt((double) box.getPixels() / typicalSingleLeafPixels));
        }
        return estimatedLeaves;
    }

    private double absolute(double value) {
        if (value < 0) {
            return -value;
        }
        return value;
    }

    private int roundToInt(double value) {
        if (value >= 0) {
            return (int) (value + 0.5);
        }
        return (int) (value - 0.5);
    }

    private int median(List<Integer> values) {
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) {
            return values.get(middle);
        }
        return (values.get(middle - 1) + values.get(middle)) / 2;
    }
}
