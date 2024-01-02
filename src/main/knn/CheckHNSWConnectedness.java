package knn;

import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsReader;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.hnsw.HnswGraph;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.IntConsumer;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

public class CheckHNSWConnectedness {
    private static Set<Integer> getReachableNodes(HnswGraph graph, int level, int entryPoint) throws IOException {
        Set<Integer> visited = new HashSet<>();
        Stack<Integer> candidates = new Stack<>();
        candidates.push(entryPoint);

        while (!candidates.isEmpty()) {
            int node = candidates.pop();

            if (visited.contains(node)) {
                continue;
            }

            visited.add(node);
            graph.seek(level, node);

            int friendOrd;
            while ((friendOrd = graph.nextNeighbor()) != NO_MORE_DOCS) {
                candidates.push(friendOrd);
            }
        }
        return visited;
    }

    public static List<List<List<Integer>>> checkConnected(String index, String hnswField) throws IOException {
        List<List<List<Integer>>> segmentCounts = new ArrayList<>();
        try (FSDirectory dir = FSDirectory.open(Paths.get(index));
             IndexReader indexReader = DirectoryReader.open(dir)) {
            for (LeafReaderContext ctx : indexReader.leaves()) {
                KnnVectorsReader reader = ((PerFieldKnnVectorsFormat.FieldsReader) ((SegmentReader) ctx.reader()).getVectorReader()).getFieldReader(hnswField);
                if (reader != null) {
                    List<List<Integer>> nodeCounts = new ArrayList<>();
                    HnswGraph graph = ((Lucene99HnswVectorsReader) reader).getGraph(hnswField);
                    for (int l = 0; l < graph.numLevels(); l++) {
                        Set<Integer> reachableNodes = getReachableNodes(graph, l, graph.entryNode());

                        Set<Integer> unReacheable = findUnReacheable(graph, l, reachableNodes);
                        List<Set<Integer>> otherComponents = new ArrayList<>();
                        if (!unReacheable.isEmpty()) {
                            findConnectedComponents(graph, l, unReacheable, otherComponents);
                        }
                        int graphSize = graph.getNodesOnLevel(l).size();
                        List<Integer> compSizes = otherComponents.stream().map(Set::size).toList();
                        ArrayList<Integer> sizes = new ArrayList<>();
                        sizes.add(graphSize);
                        sizes.add(reachableNodes.size());
                        sizes.addAll(compSizes);
                        nodeCounts.add(sizes);
                    }

                    Set<Integer> overallReachableNodes = getOverallReachableNodes(graph);
                    nodeCounts.add(List.of(graph.size(), overallReachableNodes.size()));
                    segmentCounts.add(nodeCounts);
                }
            }
        }

        return segmentCounts;
    }

    private static void findConnectedComponents(HnswGraph graph, int l, Set<Integer> nodeSet, List<Set<Integer>> components) throws IOException {
        while(!nodeSet.isEmpty()) {
            Integer one = nodeSet.iterator().next();
            nodeSet.remove(one);
            Set<Integer> reachableNodes = getReachableNodes(graph, l, one);
            reachableNodes.add(one);
            components.add(reachableNodes);
            nodeSet.removeAll(reachableNodes);
        }
    }

    public static Set<Integer> findUnReacheable(HnswGraph hnswGraph, int level, Set<Integer> reachable) throws IOException {
        HnswGraph.NodesIterator nodesOnLevel = hnswGraph.getNodesOnLevel(level);
        Set<Integer> unreachable = new HashSet<>();
        nodesOnLevel.forEachRemaining((IntConsumer) node -> {
            if (!reachable.contains(node)) {
                unreachable.add(node);
            }
        });
        return unreachable;
    }

    /**
     *
     * @param hnswGraph hnsw-graph that needs to be dumped
     * @param listedNodes Set of nodes to dump information about. If null then dump for all nodes in graph.
     * @param fw Writer to which all information is written
     * @param shouldDumpNeighbours dumps the actual neighbour nodes
     * @throws IOException
     */
    public static void dumpGraph(HnswGraph hnswGraph, Map<Integer, Set<Integer>> listedNodes, Writer fw, boolean shouldDumpNeighbours) throws IOException {
        for (int level = 0; level < hnswGraph.numLevels(); level++) {
            int l = level;
            HnswGraph.NodesIterator iterator = hnswGraph.getNodesOnLevel(level);
                List<String> friends = new ArrayList<>();
            iterator.forEachRemaining((IntConsumer) node -> {
                if (null == listedNodes || (listedNodes.get(l) != null &&
                        listedNodes.get(l).contains(node))) {
                    try {
                        int friendOrd;
                        hnswGraph.seek(l, node);
                        while ((friendOrd = hnswGraph.nextNeighbor()) != NO_MORE_DOCS) {
                            friends.add(friendOrd + "");
                        }
                        fw.write(getNeighbourString(l, node, friends, shouldDumpNeighbours) + "\n");
                        friends.clear();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    private static String getNeighbourString(int level, int node, List<String> friends, boolean shouldDumpNeighbours) {
        return "level=" + level + ",node=" + node + ",count=" + friends.size() + (shouldDumpNeighbours? ",friends=" + String.join(",", friends) : "");
    }

    public static Set<Integer> getOverallReachableNodes(HnswGraph graph) throws IOException {
        Set<Integer> visited = new HashSet<>();
        Stack<Integer> candidates = new Stack<>();
        candidates.push(graph.entryNode());

        while (!candidates.isEmpty()) {
            int node = candidates.pop();

            if (visited.contains(node)) {
                continue;
            }

            visited.add(node);
            // emulate collapsing all the edges
            for (int level = 0; level < graph.numLevels(); level++) {
                try {
                    graph.seek(level, node); // run with -ea (Assertions enabled)
                    int friendOrd;
                    while ((friendOrd = graph.nextNeighbor()) != NO_MORE_DOCS) {
                        candidates.push(friendOrd);
                    }
                } catch (AssertionError ae) { // TODO: seriously? Bad but have to do it for this test, as the API does not
// throw error. You will get this error when the graph.seek() tries to seek a node which is not present in that level.
                }
            }
        }
        return visited;
    }

    public static Set<Integer> getOverAllDisconnectedNodes(HnswGraph hnswGraph) throws IOException {
        Set<Integer> reachable = getOverallReachableNodes(hnswGraph);
        Set<Integer> unrechableNodes = new HashSet<>();
        for (int i = 0; i < hnswGraph.size(); i++) {
            if (!reachable.contains(i)) {
                unrechableNodes.add(i);
            }
        }
        return unrechableNodes;
    }

    private static void printSegmentConnectedCounts(List<List<List<Integer>>> segmentCounts) {
        for (int s = 0; s < segmentCounts.size(); s++) {
            List<List<Integer>> nodeCounts = segmentCounts.get(s);
            for (int i = 0; i < nodeCounts.size(); i++) {
                List<Integer> counts = nodeCounts.get(i);
                int disconnectedNodeCount = counts.get(0) - counts.get(1);
                if (i < nodeCounts.size() - 1) {
                    Iterator<Integer> iterator = counts.iterator();
                    System.out.print("Segment=" + formatInt(s) + "\tLevel = " + formatInt(i) + "\tTotal Nodes = " + formatInt(iterator.next()) + "\tReachable Nodes = " + formatInt(iterator.next()) +
                            "\tUnreachable Nodes = " + formatInt(disconnectedNodeCount) + "\t%Disconnectedness = " + formatFloat(disconnectedNodeCount * 100f / counts.get(0)) +
                            "\tOther connected component sizes = ");
                    while (iterator.hasNext()) {
                        System.out.print(iterator.next() + ",");
                    }
                    System.out.print("\n");
                } else {
                    // this is the last entry so it has overall connectivity data
                    System.out.println("Overall" + "\tTotal Nodes = " + formatInt(counts.get(0)) + "\tReachable Nodes = " + formatInt(counts.get(1)) +
                            "\tUnreachable Nodes = " + formatInt(disconnectedNodeCount) + "\t%Disconnectedness = " + formatFloat(disconnectedNodeCount * 100f / counts.get(0)));
                }
            }
            System.out.println("-----------------------------------------------------------------------------------");
        }
    }

    private static String formatFloat(float f) {
        return String.format("%4.4f", f);
    }

    private static String formatInt(int num) {
        return String.format("%-8d", num);
    }

    private static void dumpGraph(String index, String hnswField, Writer fileWriter) throws IOException {
        try (FSDirectory dir = FSDirectory.open(Paths.get(index));
             IndexReader indexReader = DirectoryReader.open(dir)) {
            for (LeafReaderContext ctx : indexReader.leaves()) {
                KnnVectorsReader reader = ((PerFieldKnnVectorsFormat.FieldsReader) ((SegmentReader) ctx.reader()).getVectorReader()).getFieldReader(hnswField);
                if (reader != null) {
                    HnswGraph graph = ((Lucene99HnswVectorsReader) reader).getGraph(hnswField);
                    dumpGraph(graph, null, fileWriter, true);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, NoSuchFieldException, IllegalAccessException {
        boolean assertOn = false;
        // *assigns* true if assertions are on.
        assert assertOn = true;

        if (!assertOn) {
            throw new IllegalArgumentException("You must enable assertions by setting -ea option to jvm arg for this program to work correctly.");
        }

        String index = args[0];
        String field = args[1];
        System.out.println("For index " + index + " field : " + field);
        List<List<List<Integer>>> segmentCounts = checkConnected(index, field);

        printSegmentConnectedCounts(segmentCounts);

        if (args.length == 3) {
            String fileForGraphDump = args[2];
            try (Writer fileWriter = new FileWriter(fileForGraphDump)) {
                dumpGraph(index, field, fileWriter);
            }
        }
    }
}
