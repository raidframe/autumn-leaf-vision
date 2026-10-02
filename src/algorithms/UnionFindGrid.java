package algorithms;

/**
 * Disjoint-set / Union-Find structure for pixels in the binary mask.
 *
 * Owns:
 * - parent[i]: the current parent of node i
 * - size[i]: the size of the tree when i is a root
 *
 * Purpose:
 * - connect neighbouring white pixels
 * - quickly answer whether two pixels belong to the same component
 * - support connected-component detection in near-constant amortized time
 */
public class UnionFindGrid {
    private final int[] parent;
    private final int[] size;

    public UnionFindGrid(int n) {
        // Each grid cell starts as its own separate component.
        // parent[i] stores the current parent of item i, and size[i] stores
        // the number of items in the tree when i is a root.
        parent = new int[n];
        size = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
    }

    public int find(int x) {
        // Find the representative root of the component that contains x.
        // The root is the item whose parent points to itself.
        int root = x;
        while (root != parent[root]) {
            root = parent[root];
        }

        // Path compression optimization:
        // after the root is known, every visited node is reattached directly
        // to the root, making future find operations much faster.
        while (x != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    public void union(int a, int b) {
        // Merge the two components containing a and b.
        // If they already have the same root, they are already connected.
        int rootA = find(a);
        int rootB = find(b);
        if (rootA == rootB) return;

        // Union by size optimization:
        // attach the smaller tree under the larger tree so the structure
        // stays shallow and repeated searches remain efficient.
        if (size[rootA] < size[rootB]) {
            parent[rootA] = rootB;
            size[rootB] += size[rootA];
        } else {
            parent[rootB] = rootA;
            size[rootA] += size[rootB];
        }
    }

    public int sizeOf(int x) {
        // Return the number of items in the component that contains x.
        return size[find(x)];
    }
}
