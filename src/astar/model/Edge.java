package astar.model;

public class Edge {
    public Node n;
    public double c;

    public Edge(Node n, double c) {
        this.n = n;
        this.c = c;
    }
}