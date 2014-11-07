package nars.perf;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import nars.core.EventEmitter.Observer;
import nars.core.Events;
import nars.core.NAR;
import nars.core.Param;
import nars.core.build.Default;
import nars.core.control.DefaultAttention;
import nars.entity.Concept;
import nars.gui.NWindow;
import nars.gui.output.chart.TimeSeries;
import nars.gui.output.timeline.LineChart;
import nars.io.Input;
import nars.io.TextOutput;
import nars.io.Texts;
import nars.language.Term;
import nars.storage.AdaptiveContinuousBag;
import nars.storage.Bag;
import nars.gui.output.timeline.Timeline2DCanvas;
import nars.gui.output.timeline.Chart;
import nars.gui.output.timeline.StackedPercentageChart;

/**
 *
 * @author me
 */


public class BagFairness {

    final int bins = 10;
    TimeSeries fired[] = new TimeSeries[bins];
    TimeSeries bin[] = new TimeSeries[bins];
    float fireCount[] = new float[bins];
    long total = 0;
    Concept nextConcept = null;
            
    public BagFairness(NAR n, Input input, int iterations) {

        final float maxConcepts = 1000;
        
        for (int b = 0; b < bins; b++) {
            double percentStart = ((double)b)/bins;
            double percentEnd = ((double)(b+1))/bins;            
            if (percentEnd > 1.0) percentEnd = 1.0;
            
            bin[b] = new TimeSeries("Concept: " + Texts.n2(percentStart) + ".." + Texts.n2(percentEnd), Color.getHSBColor(0.2f + 0.7f * (float)percentStart, 0.8f, 0.8f), iterations-1).setRange(0, maxConcepts);
            fired[b] = new TimeSeries("Fired: " + Texts.n2(percentStart) + ".." + Texts.n2(percentEnd), Color.getHSBColor(0.2f + 0.7f * (float)percentStart, 0.8f, 0.8f), iterations-1).setRange(0, maxConcepts);
        }

        
        n.event().on(Events.ConceptFire.class, new Observer() {

            @Override
            public void event(Class event, Object[] arguments) {
                nextConcept = (Concept)arguments[0];
            }
            
        });
        
        n.addInput(input);
        
        while (n.time() < iterations) {

            if (nextConcept!=null) {
                float p = nextConcept.getPriority();
                int b = Bag.bin(p, bins-1);
                
                fireCount[b]++;
                
                nextConcept = null;
            }

            
            int concepts = ((DefaultAttention)n.memory.concepts).concepts.size();
            double[] d = new double[bins];
            ((DefaultAttention)n.memory.concepts).concepts.getPriorityDistribution(d);
            for (int b = 0; b < bins; b++) {
                
                bin[b].push(n.time(), (float)d[b] * concepts);
                fired[b].push(n.time(), fireCount[b]);
            }
            
            
            
            ///((SequentialMemoryCycle)n.memory.conceptProcessor).processConcept();
            n.step(1);
        }
        
        List<Chart>charts = new ArrayList();
        
        /*for (b = 0; b < bins; b++) {
            charts[b] = new Timeline2DCanvas.LineChart(bin[b]);                 
        } 8*/       
        charts.add(new StackedPercentageChart(bin).height(8f));
        charts.add(new LineChart(bin).height(8f));
        
        charts.add(new StackedPercentageChart(fired).height(8f));
        
        new NWindow("_", new Timeline2DCanvas(charts)).show(800, 800, true);

        //printResults(insertProb, removeProb);
        
    }

    /*protected void removalPriority(float p) {
        int b = (int)Math.floor(p * bins);
        bin[b]++;
        total++;
    }*/
    
//    protected void printResults(double insertProb, double removeProb) {
//        System.out.print(insertProb + ", " + removeProb + ",    ");
//        for (int i = bins-1; i >=0; i--) {
//            double percentStart = ((double)i)/bins;
//            double percentEnd = ((double)(i+1))/bins;
//            double amount = ((double)bin[i]) / ((double)total);
//            //System.out.println( percentStart + ".." + percentEnd + ":\t\t" + amount);
//            System.out.print(amount + ", ");
//        }
//        System.out.println();
//    }
//    
    
    public static class RandomTermInput implements Input<String> {
        private final double inputProb;
        private final double minPriority;
        private final double maxPriority;
        private final int numTerms;
        private final double inheritanceProb;
        private final double similarityProb;
        private final double productProb;

        public RandomTermInput(int numTerms, double inputProb, double inheritanceProb, double similarityProb, double productProb, double minPriority, double maxPriority) {
            this.numTerms = numTerms;
            this.inputProb = inputProb;
            this.inheritanceProb = inheritanceProb;
            this.similarityProb = similarityProb;
            this.productProb = productProb;
            this.minPriority = minPriority;
            this.maxPriority = maxPriority;
            
        }

        @Override public String next() throws IOException {
            double p = Math.random();
            if (p < inputProb) {
                
                //uniform distribution
                double pr = Math.random() * (maxPriority-minPriority) + minPriority;
                float priority = (float)pr;                               
                
                double tp = inheritanceProb + similarityProb + productProb;                
                double s = Math.random() * tp;
                s -= inheritanceProb; if (s < 0) {
                    return "$" + Texts.n2(priority) + "$ <" + randomTerm() + " --> " + randomTerm() + ">.";
                }
                s -= similarityProb; if (s < 0) {
                    return "$" + Texts.n2(priority) + "$ <" + randomTerm() + " <-> " + randomTerm() + ">.";
                }
                s -= productProb; if (s < 0) {
                    return "$" + Texts.n2(priority) + "$ <(*," + randomTerm() + "," + randomTerm() + ") --> " + randomTerm() + ">.";
                }
                
                
            }
            return null;
        }

        private Term randomTerm() {
            int t = (int)(Math.random() * numTerms);
            return new Term("t" + t);
        }

        @Override public boolean finished(boolean stop) { return false; }
    }
    
    public static void main(String[] args) {
        NAR n = new Default() {

            @Override
            public Bag<Concept, Term> newConceptBag() {
                return new AdaptiveContinuousBag(getConceptBagSize());
            }
            
        }.build();
        //NAR n = new ContinuousBagNARBuilder(new ContinuousBag2.CubicBagCurve(), true).build();
        
        //n.param().conceptForgetDurations.set(1.0);
        

        new TextOutput(n, System.out);
        
        for (double rProb = 0.05; rProb <= 1.0; rProb += 10.10) {
            new BagFairness(n,
                    new RandomTermInput(32, rProb, 0.75, 0.5, 0.5, 0, 1.0),
                    1500
            );
        }
    }
}
