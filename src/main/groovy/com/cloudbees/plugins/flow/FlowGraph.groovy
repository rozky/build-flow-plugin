package com.cloudbees.plugins.flow

import org.jgrapht.DirectedGraph
import org.jgrapht.alg.DijkstraShortestPath
import org.jgrapht.graph.SimpleDirectedGraph

class FlowGraph {
    private DirectedGraph<String, GraphEdge> underlying
    private List<String> startJobs = new ArrayList<>()
    private String buildMode = "SELECTED"
    private Map params = new HashMap()
    private List<Closure> successListeners = new ArrayList<Closure>()

    /**
     * Creates a graph from a java property file located at the given URL.
     * A key is a source vertex and value is comma separated list of target vertices
     *
     * @param graphDefinition the a java property file containing graph definition
     *
     * @return the graph
     */
    static def FlowGraph createFromPropertyFile(InputStream graphDefinition) {
        createFromPropertyFile(graphDefinition, false)
    }

    /**
     * Creates a graph from a java property file located at the given URL.
     * If inverse is true then keys are source vertices and values are comma separated list of target vertices
     * If inverse is false then keys are target vertices and values are comma separated list of sources vertices
     *
     * @param graphDefinition the a java property file containing graph definition
     *
     * @return the graph
     */
    static def FlowGraph createFromPropertyFile(InputStream graphDefinition, boolean inverse) {
        def properties = new Properties()
        properties.load(graphDefinition)

        def graph = new FlowGraph()

        properties.stringPropertyNames().each {key ->
            def String value = properties.getProperty(key)
            value.split(",").each {valueItem ->
                if (inverse) {
                    graph.addEdge(key, valueItem)
                } else {
                    graph.addEdge(valueItem, key)
                }
            }
        }

        graph
    }

    static def FlowGraph createFromPropertyFileURL(URL graphDefinition) {
        graphDefinition.withInputStream {stream ->
            createFromPropertyFile(stream, false)
        }
    }

    static def FlowGraph createFromPropertyFileURL(String graphDefinitionURL) {
        new URL(graphDefinitionURL).withInputStream {stream ->
            createFromPropertyFile(stream, false)
        }
    }

    FlowGraph() {
        underlying = new SimpleDirectedGraph<String, GraphEdge>(GraphEdge.class);
    }

    def FlowGraph addVertex(String jobName) {
        underlying.addVertex(jobName)
        return this;
    }

    def FlowGraph addVertices(List<String> jobNames) {
        jobNames.each { underlying.addVertex(it) }
        return this;
    }

    def FlowGraph addEdge(String sourceJobName, String targetJobName) {
        underlying.addVertex(sourceJobName)
        underlying.addVertex(targetJobName)
        underlying.addEdge(sourceJobName, targetJobName,  new GraphEdge(sourceJobName, targetJobName))
        return this;
    }

    def FlowGraph withEdges(List<String>... edges) {
        edges.each {edge -> addEdge(edge[0], edge[1])}
        return this;
    }

    def boolean hasIncomingEdges(JobInvocation job) {
        def edges = underlying.incomingEdgesOf(job.name)
        edges != null && !edges.isEmpty()
    }

    def Set<GraphEdge> getIncomingEdgesOf(JobInvocation job) {
        underlying.incomingEdgesOf(job.name)
    }

    def Set<GraphEdge> getOutgoingEdgesOf(JobInvocation job) {
        underlying.outgoingEdgesOf(job.name)
    }

    def findPath(String source, String target) {
        if (underlying.containsVertex(source) && underlying.containsVertex(target)) {
            return DijkstraShortestPath.findPathBetween(underlying, source, target)
        }

        return null
    }

    def pathExists(String source, String target) {
        def paths = findPath(source, target)
        paths != null && !paths.isEmpty()
    }

    def isNotChildOfAny(String childJob, Iterable<String> jobs) {
        def parent = jobs.find { job -> job != childJob && pathExists(job, childJob) }
        parent == null
    }

    def isChildOfAny(String childJob, Iterable<String> jobs) {
        def parent = jobs.find { job -> job != childJob && pathExists(job, childJob) }
        parent != null
    }

    def isParentOfAny(String parentJob, Iterable<String> jobs) {
        def parent = jobs.find { job -> job != parentJob && pathExists(parentJob, job) }
        parent != null
    }

    def boolean containsEdge(String source, String target) {
        underlying.containsEdge(source, target)
    }

    def boolean containsVertex(String vertex) {
        underlying.containsVertex(vertex)
    }

    def Set<String> getVertices() {
        underlying.vertexSet()
    }

    def FlowGraph onBuildSuccess(Closure listener) {
        this.successListeners.add(listener);
        return this
    }

    def FlowGraph withBuildMode(String buildMode) {
        if ("SELECTED".equals(buildMode) || "EVERYTHING".equals(buildMode) || "CHANGED".equals(buildMode)) {
            this.buildMode = buildMode
        }
        return this
    }

    def FlowGraph withStartJobs(Collection<String> startJobs) {
        this.startJobs = startJobs;
        return this
    }

    def FlowGraph withMoreStartJobs(Collection<String> startJobs) {
        this.startJobs.addAll(startJobs)
        return this
    }

    def FlowGraph withParams(Map params) {
        this.params = params
        return this
    }

    List<Closure> getSuccessListeners() {
        return successListeners
    }

    Collection<String> getStartJobs() {
        return startJobs
    }

    String getBuildMode() {
        return buildMode
    }

    Map getParams() {
        return params
    }

    @Override
    def String toString() {
        return "vertices[${underlying.vertexSet().size()}]: ${underlying.vertexSet()}" +
                ", edges[${underlying.edgeSet().size()}]: ${underlying.edgeSet()}"
    }

    @Override
    def boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        FlowGraph flowGraph = (FlowGraph) o

        if (!underlying.edgeSet().equals(flowGraph.underlying.edgeSet())) {
            return false
        }

        if (!underlying.vertexSet().equals(flowGraph.underlying.vertexSet())) {
            return false
        }

        return true
    }

    int hashCode() {
        return (underlying != null ? underlying.hashCode() : 0)
    }
}


class GraphEdge {
    String source;
    String target;

    GraphEdge(String source, String target) {
        this.source = source
        this.target = target
    }

    @Override
    def String toString() {
        return "(" + source + ' -> ' + target + ")";
    }

    @Override
    def boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        GraphEdge graphEdge = (GraphEdge) o

        if (source != graphEdge.source) return false
        if (target != graphEdge.target) return false

        return true
    }

    @Override
    def int hashCode() {
        int result
        result = (source != null ? source.hashCode() : 0)
        result = 31 * result + (target != null ? target.hashCode() : 0)
        return result
    }
}
