package nars.util.graph;

import nars.core.Memory;
import nars.core.NAR;
import nars.entity.Item;
import nars.entity.Sentence;
import nars.inference.TemporalRules;
import nars.io.Symbols;
import nars.io.Symbols.NativeOperator;
import nars.language.CompoundTerm;
import nars.language.Conjunction;
import nars.language.Implication;
import nars.language.Interval;
import nars.language.Negation;
import nars.language.Term;
import nars.operator.Operation;



public class ImplicationGraph extends SentenceItemGraph {

    float minConfidence = 0.1f;
    float minFreq = 0.6f;

    public ImplicationGraph(NAR nar) {
        this(nar.memory);
    }
    public ImplicationGraph(Memory memory) {
        super(memory);
    }
    
    public static class UniqueInterval extends Interval {
        private static int _id = 0;
        
        public final Implication parent;
        private final int id;
    
        public UniqueInterval(Implication parent, Interval i) {
            super(i.magnitude, true);    
            this.parent = parent;
            this.id = _id++;
        }

        @Override
        public int hashCode() {
            return id + parent.hashCode() * 37  + super.hashCode();
        }

        @Override
        public boolean equals(Object that) {
            if (that == this) return true;
            
            if (that instanceof UniqueInterval) {
                UniqueInterval ui = (UniqueInterval)that;
                return (ui.id == id && ui.parent.equals(parent) && ui.magnitude == magnitude);
            }
            return false;
        }
        
        
    }
    
    static class PostCondition extends Negation {

        public PostCondition(Term t) {
            super("~" + t.name(), t);
        }
        
    }
    
    @Override
    public boolean add(final Sentence s, final CompoundTerm ct, final Item c) {
        if (!(ct instanceof Implication)) {
            return false;
        }
        
        final Implication st = (Implication)ct;
        
        final Term subject, predicate;
        
        if (st.operator() == NativeOperator.IMPLICATION_BEFORE) {
            //reverse temporal order
            subject = st.getPredicate();
            predicate = st.getSubject();            
        }
        else {
            subject = st.getSubject();
            predicate = st.getPredicate();            
        }            

        final Term precondition = predicate;
        final Term postcondition = new PostCondition(precondition);
        addVertex(precondition);
        addVertex(postcondition);

        if (subject instanceof Conjunction) {
            Conjunction seq = (Conjunction)subject;
            if (seq.operator() == Symbols.NativeOperator.SEQUENCE) {
                Term prev = precondition;
                for (Term a : seq.term) {


                    if (a instanceof Interval) {
                        a = new UniqueInterval(st, (Interval)a);
                    }
                    if ((a instanceof Operation) || (a instanceof Interval)) {
                        addVertex(a);
                        if (!prev.equals(a)) {
                            newImplicationEdge(prev, a, c, s);
                        }
                        prev = a;
                    }
                    else {
                        //separate the term into a disconnected pre and post condition
                        Term pre = a;
                        Term post = new PostCondition(a);
                        addVertex(pre);
                        addVertex(post);
                        newImplicationEdge(prev, a, c, s); //leading edge from previous only                            
                        prev = post;
                    }

                }

                newImplicationEdge(prev, postcondition, c, s);
                return true;
            }
            else if (seq.operator() == Symbols.NativeOperator.PARALLEL) {
                //TODO
            }
        }
        else {
            addVertex(subject);
            newImplicationEdge(precondition, subject, c, s);
            newImplicationEdge(subject, postcondition, c, s);
        }

        return true;
    }

    public Sentence newImplicationEdge(final Term source, final Term target, final Item c, final Sentence parent) {
        Implication impFinal = new Implication(source, target, TemporalRules.ORDER_FORWARD);                    
        Sentence impFinalSentence = new Sentence(impFinal, '.', parent.truth, parent.stamp);
        addEdge(source, target, impFinalSentence);
        concepts.put(impFinalSentence, c);
        
        return impFinalSentence;
    }
    
    
    @Override
    public boolean allow(final Sentence s) {        
        float conf = s.truth.getConfidence();
        float freq = s.truth.getFrequency();
        if ((conf > minConfidence) && (freq > minFreq))
            return true;
        return false;
    }


    @Override
    public boolean allow(final CompoundTerm st) {
        Symbols.NativeOperator o = st.operator();
        if ((o == Symbols.NativeOperator.IMPLICATION_WHEN) || (o == Symbols.NativeOperator.IMPLICATION_BEFORE) || (o == Symbols.NativeOperator.IMPLICATION_AFTER)) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder x = new StringBuilder();
        x.append(getClass().toString() + "\n");
        x.append("Terms:\n");
        for (Term v : vertexSet())
            x.append("  " + v.toString() + ",");
        x.append("\nImplications:\n");
        for (Sentence v : edgeSet())
            x.append("  " + v.toString() + ",");
        x.append("\n\n");
        return x.toString();
    }

    @Override
    public double getEdgeWeight(Sentence e) {
        float freq = e.truth.getFrequency();
        float conf = e.truth.getConfidence();        
        float conceptPriority = concepts.get(e).getPriority();
        //weight = cost = distance
        //return 1.0 / (freq * conf * conceptPriority);
        return 1.0 / (freq * conf * (0.5f + 0.5f * conceptPriority));
    }
    
    
    
}