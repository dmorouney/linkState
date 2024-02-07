/***
 * @author: Robert Morouney
 * laurierid: 069001422
 * contact: moro1422@mylaurier.ca
 * @warning: file may contain java.
 * 
 * Completed for CP372 Winter 2019 based upon assigment 3 specifications 
 * provided on mylearningspace.wlu.ca
 */
import java.io.InputStream;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Scanner;

/***
 * Implements the a partial LinkState routing algorithm using Djirkstra's Shortest
 * Path First technique. An adjacency matrix is read in through stdin and broadcast
 * to the virtual routers on separate threads. Routers (implemented as LinkRouter) 
 * use the provided adjacency matrix to build a view of the network calculating the 
 * costs and paths to every other router in the network. 
 */
public class LinkState {
    public static final int INFINITY = Integer.MAX_VALUE >>> 1; // Max val overflows 
    /**
     * Main method executes program in following order:
     *  1. read matrix size, matrix and gateway nodes from stdin
     *  2. create virtual nodes on network and give them each a reference to above matrix
     *  3. encapsulate virtual nodes in threads and run simultaneously
     *  4. for each node N, N will execute Djirkstra's algorithm to create a tree of shortest paths
     *     beginning at N to every other node.
     *  5. tree from step 4 used to create routing table of costs and first hop(s)
     *  6. Nodes not marked as gateways will print their routing tables.
     * 
     *  @param args[] Arguments given to program. Unused.
     */
    public static void main(String args[]) {
        LinkMatrix costMatrix = new LinkMatrix(System.in);
        LinkRouter routers[] = new LinkRouter[costMatrix.getNodes()];
        Thread[] threads = new Thread[routers.length];
        for (int i = 0; i < routers.length; i++) {
            routers[i] = new LinkRouter(i + 1, costMatrix);
            threads[i] = new Thread(routers[i]);
            threads[i].start();
        }
        boolean[] printable = costMatrix.getGatewayMask();
        for(int i=0; i < routers.length; i++) {
            try { threads[i].join(0); } catch (InterruptedException e) { System.exit(1); }
            if (!printable[i]) System.out.println(routers[i]);
        }
    }


    /***
     * Adjacency matrix for weights between routing nodes. Matrix holds initial view
     * of network and includes a mask to determine which nodes act as gateways. Matrix
     * is initialized with the instantiation of the object and accessor functions are 
     * provided.  This will likely be a shared resource so it does not support writing 
     * after creation.
     */
    public static class LinkMatrix {
        private int[][] matrix;
        private int nodes;
        private boolean[] gateways;

        /***
         * Initialize LinkMatrix from InputStream.  This can be a pipe or openfile
         * as long as it is valid to read from. LinkMatrix was implemented this way
         * so that it can easily take in data from a FileStream or an InputStream. 
         * 
         * @param fd : Open File Input Stream (InputStream).
         * see : reference 6.
         */
        public LinkMatrix(InputStream fd) {
            try (Scanner sc = new Scanner(fd)) {
                if (sc.hasNextInt())
                    this.nodes = sc.nextInt();
                else
                    throw new Exception("Error reading size of matrix (n).");
                if (this.nodes <= 0)
                    throw new Exception("Invalid matirx size of: " + Integer.toString(this.nodes));
                this.matrix = new int[this.nodes][this.nodes];
                for (int i = 0; i < this.nodes; i++) {
                    for (int j = 0; j < this.nodes; j++) {
                        if (sc.hasNextInt())
                            this.matrix[i][j] = sc.nextInt();
                        else
                            throw new Exception("Incorrect Data Format.\n" + "Out of Integers");
                    }
                }
                this.gateways = new boolean[this.nodes];
                while (sc.hasNextInt()) {
                    int n = sc.nextInt();
                    if (n >= this.nodes)
                        throw new Exception("Gateway " + Integer.toString(n) + "dosen't exist.");
                    this.gateways[n-1] = true;
                }
            } catch (Exception e) {
                System.err.println(e.getMessage() + ": (LinkMatrix)");
            }
        }
        /**
         * Get number of nodes in the network
         * @return the number of nodes (int)
         */
        public int getNodes() {
            return this.nodes;
        }

        /** 
         * Gets the value weight between 2 nodes in the adjacency matrix
         * @param node1 : index of first node (1 .. N) (int)
         * @param node2 : index of second node (1 .. N) (int)
         * @return : the weight from node1 to node2 (int)
         */
        public int getAdjacentWeight(int node1, int node2) {
            int n1 = node1 - 1;
            int n2 = node2 - 1;
            if (n1 > this.nodes || n2 > this.nodes)
                System.err.println("Invalid node nummber (Nodes numbered 1 - N)");
            int ret = (this.matrix[n1][n2]);
            return ret < 0 ? INFINITY : ret;

        }

        /**
         * Given a node id return that nodes status as a gateway router. 
         * @param node1 node id to check (int)
         * @return true if node1 is gateway false otherwise
         * @throws Exception if node id is not in matrix
         */
        public boolean isGateway(int node1) throws Exception {
            int n1 = node1 - 1;
            if (n1 > this.nodes)
                throw new Exception("Invalid node nummber (Nodes numbered 1 - N)");
            return this.gateways[n1];
        }

        /**
         * Used to get the entire view of gateways on network rather than 
         * testing a single node.
         * @return list of true or false values relating node id's to gateways
         */
        public boolean[] getGatewayMask() { return this.gateways; }
    }

    /**
     * Virtual router used to implement a partial link state algorithm. 
     */
    public static class LinkRouter implements Runnable {
        private int id, n;
        private Node[] nodes;
        private LinkMatrix costs; // shared resourse (only read without lock)
        private boolean isGateway, hasPTable;
        private String print_table;

        /**
         * Initialize router (node) on network and pass a reference to an adjacency
         * matrix which has the weights (costs) of travesing adjacent nodes. LinkRouter
         * will initialize references to all the nodes (Node) on the network and their current 
         * known weights. No calculation is done in the initializer.
         * @param router_id The id of this virtual router
         * @param costs a reference to the adjacency cost matrix
         */
        public LinkRouter(int router_id, LinkMatrix costs) {
            super();
            this.id = router_id;
            this.costs = costs;
            this.n = costs.getNodes();
            try {
                this.isGateway = costs.isGateway(router_id);
            } catch (Exception e) {
                System.err.println(e.getMessage() + ": (LinkRouter : " + router_id + ")");
            }
            nodes = new Node[this.n + 1];
            this.print_table = "";
        }

        /**
         * This overrides the run function which is added to the class since it expands "Runnable".
         * This funtion is built to work in parallel and will find the minimum distances from this
         * virtual router to all other nodes in the network.  To achieve this a special type Node is 
         * used to keep track of the distances and paths from the LinkRouter instance.
         */
        @Override
        public void run() {
            PriorityQueue<Node> pQNodes = new PriorityQueue<Node>();
            for (int i = 1; i <= costs.getNodes(); i++) {
                    int cost = costs.getAdjacentWeight(id, i);
                    cost = cost < 0  ? INFINITY : cost;
                    this.nodes[i] = new Node(i, cost);
                    pQNodes.add(this.nodes[i]);
            }

            while (pQNodes.size() > 0) {
                /* 
                * An extremely useful property of java's priority queues is that 
                * they auto bubble. Meaning that if a reference is changed
                * outside of the priority queue it reorganizes automatically.
                * see reference 7.
                */
                Node w = pQNodes.poll();
                if (w == null) break; 
                for (int i = 1; i <= this.n; i++) { // check all neighbors
                    if(i==this.id) continue;
                    try {
                        int newCost = w.cost + costs.getAdjacentWeight(w.id, i); 
                        if (newCost < nodes[i].cost) { // if neighbor exposes new path
                            nodes[i].cost = newCost; // add new cost to table
                            nodes[i].prev = w; // add this as a previous node in chain
                            nodes[i].alt.clear(); // clear old set of multiple same cost paths
                        } else if ( newCost == nodes[i].cost && w.id != this.id ) {
                            nodes[i].alt.add(w); // 
                        }
                    } catch (NullPointerException e) {
                        System.err.println("Trying to add a null pointer to Set");
                        System.exit(1);
                    } catch (Exception e) {
                        System.err.println(e.getMessage() + ": (LinkRouter.run : " + i + " : "
                                + Integer.toString(w.id) + ")");
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            }
            this.print_table = this.make_printTable();
            this.hasPTable = true;
        }

        /** 
         * creates a string with the routers forwarding table.  This is implemented as a method so it can be called
         * from run and created in parallel then stored for later printing. 
         * @return forwarding table from this instance of LinkRouter to all nodes which are not gateways.
         */
        public String make_printTable() {
            String output = "Forwarding Table for " + this.id + "\n";
            output += "\tTo\tCost\tNext Hop\n";
            for (int i = 1; i <= this.n; i++) {
                try {
                    if (nodes[i].id != this.id && this.costs.isGateway(i)) {
                        output += "\t" + nodes[i].id + "\t" + nodes[i].cost;
                        if(nodes[i].alt.size() > 0) {
                            output += "\t";
                            Iterator<Node> in = nodes[i].alt.iterator();
                            while(in.hasNext()) {
                                output += in.next().getFirstHop();
                                output += in.hasNext() ? " , " : "\n";
                            } 
                        } else
                            output += "\t" + nodes[i].prev.getFirstHop() + "\n";
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage() + ": (LinkRouter.toString : " + i + ")");
                }
            }
            return output;
        }

        /**
         * Overrides the toString method to print the LinkRouter's routing table
         * This is simply for convienece.
         */
        @Override
        public String toString() {
            if (!this.hasPTable) return make_printTable();
            return this.print_table;
        }

        /**
         * Class used to store forwarding information for each node connected to the parent
         * LinkRouter.  Node class is comparable so that it can easily be integrated into a priority
         * heap (as mentioned in the text). Node class also stores reverse paths from itself to the 
         * parent LinkRouter node.  This is acheived with an array of link lists that hold only a reference
         * to the previous node.  By following these paths the first hop from the parent LinkRouter can
         * easily be extracted
         */
        private class Node implements Comparable<Node> {    
            protected int id;
            protected int cost;
            protected Node prev;
            protected Set<Node> alt;

            /**
             * Initializes node and sets cost and ID.
             * @param nodeID (int)
             * @param cost (int)
             */
            public Node(int nodeID, int cost) {
                super();
                this.id = nodeID;
                this.cost = cost;
                this.prev = null;
                this.alt = new HashSet<Node>();
            }   
            /**
             * Compare nodes by cost so that the least cost path can be obtained
             * by popping nodes off of a priority heap ordered by their known costs.
             */
            @Override
            public int compareTo(Node other) {
                return Integer.compare(this.cost , other.cost);
            }
            /** 
             * Used for debugging can print out which node is giving an issue.
             * @return string representation of this Node instance.
             */
            @Override
            public String toString() {
                return "Node : " + id + " : cost : " + cost + " : chain : " + getChain(this);
            }
            /** 
             * This function is not used.  The purpose is to return a string representation of the 
             * path from the parent LinkRouter to this Node instance.  This will show all the hops
             * nessicary to get from the parent to this node.
             * @return chain of node ids which make up the shortest path to this node.
             */
            public String getChain(Node n) {
                if (n == null) return ""; 
                return getChain(n.prev) + " " + n.id;
            }
            /**
             * retrieves the node id of the first node that must be taken from the parent LinkRouter to 
             * get to this node along the shortest path. 
             * @return id of first hop on shortest path.
             */
            public int getFirstHop() {
                Node p = this;
                while (p.prev != null) 
                    p = p.prev;
                return p.id;
            }
        }
    }   
}
/***
 * REFERENCES VIEWED DURING RESEARCH FOR ABOVE CODE: 
 *  [1] James F. Kurose and Keith W. Ross. 2012. Computer Networking: A Top-Down Approach (6th Edition) (6th ed.). Pearson.
 *  [2] Bauer, Delling, Sanders, Schultes, and Wagner. Combining Hierarchical and Goal-Directed Speed-Up Techniques for Dijkstra’s Algorithm. (2008)
 *      http://algo2.iti.kit.edu/download/bdsssw-chgds-10.pdf
 *  [3] Chen, Chowdhury, Roche, Ramachandran, Tong. Priority Queues and Dijkstra’s Algorithm.
 *      http://www.cs.utexas.edu/users/shaikat/papers/TR-07-54.pdf
 *  [4] https://docs.oracle.com/javase/tutorial/essential/concurrency/
 *  [5] https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html#boolean 
 *  [6] https://docs.oracle.com/javase/7/docs/api/java/util/Scanner.html 
 *  [7] https://docs.oracle.com/javase/7/docs/api/java/util/PriorityQueue.html 
 */