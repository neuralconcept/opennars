/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package nars.gui.output.graph;

/**
 *
 * @author me
 */

import automenta.vivisect.dimensionalize.FastOrganicLayout;
import automenta.vivisect.graph.AnimatingGraphVis;
import java.util.concurrent.atomic.AtomicReference;
import nars.core.EventEmitter.EventObserver;
import nars.core.Events.FrameEnd;
import nars.core.Events.ResetEnd;
import nars.core.NAR;
import nars.util.DefaultGraphizer;
import nars.util.NARGraph;
import org.jgrapht.Graph;

/**
 *
 */
public class NARGraphVis extends AnimatingGraphVis<Object,Object> implements EventObserver {
        
    
    final AtomicReference<Graph> displayedGraph = new AtomicReference();
    private final NAR nar;
    boolean showTaskLinks = false;
    boolean showTermLinks = true;
    float minPriority = 0;
    private boolean showBeliefs = false;
    private boolean showDerivations = false;
    private boolean showQuestions = false;
    private boolean showTermContent = false;
            
    public NARGraphVis(NAR n) {
        super(null, new NARGraphDisplay(), new FastOrganicLayout());
        this.nar = n;
        
        
    }

    @Override
    public void onVisible(boolean showing) {  
        nar.memory.event.set(this, showing, FrameEnd.class, ResetEnd.class);        
    }

    @Override
    public void event(Class event, Object[] args) {
        if (event == FrameEnd.class) {
            displayedGraph.set(nextGraph());
        }
        else if (event == ResetEnd.class) {
            displayedGraph.set(null);
        }
    }
    
    
    protected Graph nextGraph() {
        return new NARGraph().add(nar, new NARGraph.ExcludeBelowPriority(minPriority), new DefaultGraphizer(showBeliefs, showDerivations, showQuestions, showTermContent, 0, showTermLinks, showTaskLinks));
    }

    @Override
    public Graph<Object, Object> getGraph() {        
        if (displayedGraph == null)
            return null;
        return displayedGraph.get();
    }
    

    public void setTaskLinks(boolean taskLinks) {
        this.showTaskLinks = taskLinks;
    }

    
    
    
    
}