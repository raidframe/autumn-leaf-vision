package algorithms;

import models.ClusterBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Route builder for detected leaf clusters.
 *
 * This class does not try to solve the full travelling-salesman problem.
 * Instead, it builds a simple demonstration route:
 *
 * - start from one chosen cluster
 * - repeatedly move to the nearest unvisited cluster
 * - return the visiting order as a list
 *
 * The approach is greedy, fast, and easy to explain in a coursework project.
 */
public class RoutePlanner {
    public List<ClusterBox> buildRoute(List<ClusterBox> clusters, ClusterBox start) {
        // Build an ordered list of clusters to visit.
        // The method uses a simple greedy nearest-neighbour strategy:
        // from the current cluster, always choose the closest cluster that
        // has not been visited yet.
        if (clusters.isEmpty()) return List.of();

        // Work on a copy so the original cluster list is not modified.
        List<ClusterBox> remaining = new ArrayList<>(clusters);

        // If the UI selected a cluster, start from it; otherwise start from
        // the first detected cluster.
        ClusterBox current = start != null ? start : remaining.getFirst();
        remaining.remove(current);

        List<ClusterBox> route = new ArrayList<>();
        route.add(current);

        // Repeatedly search all remaining clusters and pick the closest one.
        // This does not guarantee the globally shortest route, but it is fast.
        while (!remaining.isEmpty()) {
            ClusterBox next = remaining.getFirst();
            double best = distance(current, next);

            for (ClusterBox candidate : remaining) {
                double d = distance(current, candidate);
                if (d < best) {
                    best = d;
                    next = candidate;
                }
            }

            route.add(next);
            remaining.remove(next);
            current = next;
        }

        return route;
    }

    private double distance(ClusterBox a, ClusterBox b) {
        // Euclidean distance between the centres of two cluster rectangles.
        double dx = a.centreX() - b.centreX();
        double dy = a.centreY() - b.centreY();
        return squareRoot(dx * dx + dy * dy);
    }

    private double squareRoot(double value) {
        if (value <= 0) {
            return 0;
        }

        double guess = value;
        if (guess < 1.0) {
            guess = 1.0;
        }

        for (int i = 0; i < 12; i++) {
            guess = (guess + value / guess) / 2.0;
        }
        return guess;
    }
}
