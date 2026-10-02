package models;

import image.BinaryMask;

/**
 * Immutable analysis output.
 *
 * Contains:
 * - mask: the black/white classification of every analysed pixel
 * - clusters: the detected connected regions that survived size filtering
 * - totalLeafPixels: total number of white pixels across all kept clusters
 * - estimatedLeafCount: heuristic estimate of how many individual leaves are present
 *
 * Packing both values into one record makes it easy to pass the full
 * analysis result from ImageAnalyzer to the UI in a single object.
 */
public record AnalysisResult(
        BinaryMask mask,
        java.util.List<ClusterBox> clusters,
        int totalLeafPixels,
        int estimatedLeafCount
) {
}
