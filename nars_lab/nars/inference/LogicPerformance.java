/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package nars.inference;

import automenta.vivisect.swing.NWindow;
import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nars.core.Events.ConceptFire;
import nars.core.Events.TaskImmediateProcess;
import nars.core.NAR;
import nars.core.Parameters;
import nars.core.build.Default;
import nars.core.control.FireConcept;
import nars.core.control.NAL;
import nars.entity.Concept;
import nars.entity.Sentence;
import nars.entity.Task;
import nars.entity.TaskLink;
import nars.gui.output.JGraphXGraphPanel;
import nars.io.ExampleFileInput;
import nars.io.condition.OutputCondition;
import nars.io.condition.OutputContainsCondition;
import nars.language.Term;
import nars.operator.Operation;
import nars.util.NARGraph;
import nars.util.NARGraph.TimeNode;
import nars.util.NARGraph.UniqueEdge;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.DirectedSubgraph;

/**
 * Graph analysis of reasoning processes to determine essential and non-essential
 * activity
 * 
 * @see http://jgrapht.org/javadoc/org/jgrapht/alg/package-summary.html
 */
public class LogicPerformance {
    private final TaskReasonGraph essential;
    private final Collection<Task> solutionTask;
    
    /**
     * contains tasks/sentences, time cycles, etc to explain a task's formation */     
    public static class TaskReasonGraph extends NARGraph {

        public TaskReasonGraph() {
            super();
        }
        
        public TaskReasonGraph(Task[] results, int maxLevels) {
            this();

            for (Task result : results)
                explain(result, maxLevels);
        }
        
        public void explain(Task t, int maxLevels) {
            explain(t, maxLevels, Collections.EMPTY_LIST);
        }

        public void explain(Task t, int maxLevels, List<Task> generated) {
            //String x = toString() + "\n";
            Term cause = t.getCause();
            Sentence bestSolution = t.getBestSolution();            
            Sentence parentBelief = t.getParentBelief();
            Task parentTask = t.getParentTask();
            
            addVertex(t);
            at(t, t.getCreationTime());
            
            Sentence s = t.sentence;
            if (!s.isEternal()) {
                at(t, s.getOccurenceTime(), "o");
            }
            for (Term term : t.sentence.stamp.getChain()) {
                addVertex(term);
                addEdge(term, t, new UniqueEdge("s"));
            }
            
            if ((cause!=null) && (cause instanceof Operation)) {
                //x += "  cause=" + cause + "\n";
                Task causeTask = ((Operation)cause).getTask();
                addVertex(causeTask);
                addEdge(causeTask, t,new UniqueEdge("cause"));
                explain(causeTask, maxLevels-1);
            }
            if (bestSolution!=null) {
                if (!t.getTerm().equals(bestSolution.term)) {
                    
                    addVertex(bestSolution.term);
                    addEdge(bestSolution.term, t, new UniqueEdge("t"));
                    
                    addVertex(bestSolution);
                    addEdge(t, bestSolution,new UniqueEdge("bestSolution"));
                    
                    at(bestSolution, bestSolution.getCreationTime());
                }
            }
            if (parentBelief!=null) {
                addVertex(parentBelief.term);
                addEdge(parentBelief.term, t, new UniqueEdge("t"));
                
                addVertex(parentBelief);
                addEdge(parentBelief, t, new UniqueEdge("belief"));
                at(parentBelief, parentBelief.getCreationTime());
            }
            if (parentTask!=null) {
                //x += "  parentTask=" + parentTask + " @ " + parentTask.getCreationTime() + "\n";
                if (t.equals(parentTask)) {
                    //System.err.println(t + " equals parentTask: " + parentTask);
                    //System.err.println(t.getExplanation());
                }
                else {
                    addVertex(parentTask);
                    addEdge(parentTask, t, new UniqueEdge("parent"));

                    if (maxLevels > 0)
                        explain(parentTask, maxLevels-1);
                }
                            
            }
            else {
                addVertex("INPUT");
                addEdge("INPUT", t, new UniqueEdge(""));
            }
        

            explainGeneration(t, generated);
            
                    
        }

        protected void explainGeneration(Object source, List generated) {
            for (Object g : generated) {
                if (g.equals(source)) continue;
                addVertex(g);
                addEdge(source, g, new UniqueEdge("g"));
            }
        }
        
        public void explain(long t, FireConcept f) {
            //Concept conceptFired, TaskLink link, List<Task> generated) {
            Term term = f.getCurrentConcept().term;
            TaskLink link = f.getCurrentTaskLink();
            
            addVertex(f);
            addVertex(term);
            addVertex(link);
            
            addEdge(term, link, new UniqueEdge("tasklink"));
            addEdge(link, f, new UniqueEdge("fire"));
            at(f, t, "fire");
            
            explainGeneration(f, f.tasksAdded);
        }

        
    }

    /** 'solution' is the subgraph of 'process' which contains the derivation
     *  of the sought results
     */
    public LogicPerformance(TaskReasonGraph process, Collection<Task> solutionTasks) {
        Set<TimeNode> allCycles = process.vertices(TimeNode.class);        
     
        System.out.println("\n--------------- ANALYSIS ----------\n");
        
        System.out.println("Active at Time Cycles: " + allCycles);
        
        solutionTask = solutionTasks;
        
        //remove all tasks which have no path to a solution; result is in 'essential'

        essential = (TaskReasonGraph) process.clone();

        Set<Object> nonEssentialVertices = new HashSet();
        Set<Object> nonEssentialVertexRoots = new HashSet();
        
        for (Object o : process.vertexSet()) {
            if (o instanceof String) continue;
            
            if (o instanceof Task) {
                Task t = (Task)o;
                
                if (solutionTasks.contains(t))
                    continue;
            }
            if (!connectsAny(o, solutionTasks))
                nonEssentialVertices.add(o);
            
        }
        
        Predicate nonTimeNode = new Predicate() {
            @Override public boolean apply(Object t) {
                return !(t instanceof TimeNode);
            }
        };
        
        DirectedSubgraph nonEssentialComponents = new DirectedSubgraph(process, Sets.filter(nonEssentialVertices, nonTimeNode), null);
        
        //calculate the root non-essentials which would remove all other non-essentials; except for time nodes
        for (Object o : nonEssentialComponents.vertexSet()) {
            if (nonEssentialComponents.incomingEdgesOf(o).isEmpty()) {
                nonEssentialVertexRoots.add(o);
            }
        }
        
        System.out.println("\nNon-essential vertices: ");
        for (Object o : nonEssentialVertices) {
            essential.removeVertex(o);
            char c = nonEssentialVertexRoots.contains(o) ? '*' : ' ';
            System.out.println("  " + c + ' ' + o + " (" + o.getClass().getSimpleName() +")");
        }
        

        
        new NWindow("Essential", new JGraphXGraphPanel(essential)).show(800,800, true);
        
        System.out.println("\nEssential vertices:");
        for (Object o : essential.vertexSet()) {
            if (o instanceof String) continue;
            System.out.println("   " + o + " (" + o.getClass().getSimpleName() +")");
        }        
        
        System.out.print("\nTotal Process vs. Actually Essential: ");
        System.out.println(process.vertexSet().size() + " to " + essential.vertexSet().size());        
        
    }
    
    public boolean connectsAny(Object source, Iterable<? extends Object> targets) { 
        
        for (Object o : targets) {
            if (source.equals(o))
                return true;
            
            DijkstraShortestPath d = new DijkstraShortestPath<>(essential, source, o);
            
            if (d.getPath()!=null)
                return true;            
        }
        return false;
    }

    
    
    public static void main(String[] args) throws Exception {
        
        Parameters.DEBUG = true;
        
        int analysisDepth = 5;
        NAR n = new NAR(new Default());
        
        ExampleFileInput example = ExampleFileInput.get("test/nal7.5");
                
        List<OutputCondition> conditions = example.enableConditions(n, 5);

        n.addInput(example);
                
        System.out.println(example.getSource());
        
        TaskReasonGraph process = new TaskReasonGraph();
        
        
        n.on(TaskImmediateProcess.class, new TaskImmediateProcess() {
            @Override public void onProcessed(Task t, NAL nal) {
                process.explain(t, analysisDepth, nal.tasksAdded);
            }            
        });
         n.on(ConceptFire.class, new ConceptFire() {

            @Override
            public void onFire(FireConcept f) {                
                
                process.explain(n.time(), f);
            }            
        });
        
        n.run(1);
        
        Set<Task> solutionTasks = new HashSet();
        
        for (OutputCondition o : conditions) {
            if (o instanceof OutputContainsCondition) {
                OutputContainsCondition c = (OutputContainsCondition)o;
                if (c.isTrue()) {
                    List<Task> t = c.getTrueReasons();                

                    process.addVertex("SOLUTION");
                    
                    for (Task task : t) {      
                        solutionTasks.add(task);
                        
                        process.addEdge(task, "SOLUTION", new UniqueEdge(c.toString()));
                    }
                }
                else {
                    System.err.println("FAIL: " + o.getFalseReason());                
                }
            }
                
            
        }
        
        //System.out.println(result);
        
        new NWindow("Process", new JGraphXGraphPanel(process)).show(800,800, true);
        
        //allTasks.graphMLWrite("/tmp/logicperf.graphml");
        
        new LogicPerformance(process, solutionTasks);
                
    }
}
