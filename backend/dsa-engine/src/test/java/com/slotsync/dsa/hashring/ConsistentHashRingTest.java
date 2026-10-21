package com.slotsync.dsa.hashring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit & Distribution Tests for ConsistentHashRing.
 */
public class ConsistentHashRingTest {

    public static void runAllTests() {
        System.out.println("--> Running ConsistentHashRingTest...");
        testSingleNodeMapping();
        testDeterministicRouting();
        testUniformDistributionBalance();
        testNodeAdditionAndRemoval();
        System.out.println("  [PASS] All ConsistentHashRing tests passed successfully!\n");
    }

    public static void testSingleNodeMapping() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(50);
        ring.addNode("worker-1");

        for (int i = 0; i < 20; i++) {
            String target = ring.getNode("user_" + i);
            if (!"worker-1".equals(target)) {
                throw new AssertionError("All keys must route to the only active node worker-1");
            }
        }
    }

    public static void testDeterministicRouting() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100, List.of("node-A", "node-B", "node-C"));
        String node1 = ring.getNode("ticket_4588");
        String node2 = ring.getNode("ticket_4588");

        if (!node1.equals(node2)) {
            throw new AssertionError("Consistent hash must be strictly deterministic for identical keys!");
        }
    }

    public static void testUniformDistributionBalance() {
        // 4 worker nodes with 150 virtual replicas each
        List<String> nodes = List.of("node-1", "node-2", "node-3", "node-4");
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(150, nodes);

        Map<String, Integer> distribution = new HashMap<>();
        for (String node : nodes) {
            distribution.put(node, 0);
        }

        final int totalKeys = 10_000;
        for (int i = 0; i < totalKeys; i++) {
            String selectedNode = ring.getNode("request_id_" + i);
            distribution.put(selectedNode, distribution.get(selectedNode) + 1);
        }

        // Expected share per node is 2,500 keys (25%)
        // Verify each node gets between 18% and 32% (healthy uniform distribution)
        int minExpected = (int) (totalKeys * 0.18);
        int maxExpected = (int) (totalKeys * 0.32);

        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            int count = entry.getValue();
            if (count < minExpected || count > maxExpected) {
                throw new AssertionError(String.format(
                    "Uneven distribution! Node %s received %d keys (Expected between %d and %d)",
                    entry.getKey(), count, minExpected, maxExpected));
            }
        }
    }

    public static void testNodeAdditionAndRemoval() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        ring.addNode("node-alpha");
        ring.addNode("node-beta");

        if (ring.getPhysicalNodes().size() != 2) {
            throw new AssertionError("Expected 2 physical nodes");
        }

        ring.removeNode("node-beta");
        if (ring.getPhysicalNodes().size() != 1) {
            throw new AssertionError("Expected 1 physical node after removal");
        }

        // All keys must now map to node-alpha
        for (int i = 0; i < 10; i++) {
            if (!"node-alpha".equals(ring.getNode("session_" + i))) {
                throw new AssertionError("Expected all keys to route to remaining node-alpha");
            }
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
