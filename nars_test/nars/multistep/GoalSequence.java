package nars.multistep;

import nars.core.NAR;
import nars.core.build.Default;
import nars.io.TextOutput;
import nars.io.condition.OutputContainsCondition;
import nars.io.narsese.Narsese;
import nars.language.Tense;

/**
I played a lot with 1.6.0 version for long time now. I'm am pleased that it works that nicely.

However I found one thing which I think is crucial to make it more effective.

Lets say we have a sequence of actions which leads to a goal, like "(&/,a,b,c) =/> d" with the goal "d!".
We currently don't create such statements by temporal induction, which currently means it will be like that:

a =/> b
b =/> c
c =/> d

with d being the goal,
it will spawn c,
then spawn b,
then spawn a with even less priority,
now it will eventually execute a.

However, this is very ineffective, because it has to do every step "mentally", with a ton of reasoning-overhead,
which leads, altough working in principial, to failing even for relatively simple examples.

Clearly with expressions like (&/,a,b,c), the execution could be achieved with much less reasoning overhead for the system,
it may work much better then.

Any ideas of a strategy to generate statements like "(&/,a,b,c) =/> d" with temporal induction in an effective way?
*/
public class GoalSequence {
    
    final int maxCycles = 2000;
    
    public GoalSequence(int duration, int pause, int interSeqPause/*, float questionPriority, float questionDurability*/) throws Narsese.InvalidInputException {
        NAR n = new NAR(new Default());
        n.param.duration.set(duration);
    
        System.out.println("duration=" + duration + ", pause=" + pause + ", interSeqPause=" + interSeqPause );
        
        OutputContainsCondition c = new OutputContainsCondition(n, "<(&/, <a-->meta-word>, <b-->meta-word>) =/> <c-->meta-word>>.", 4);
        OutputContainsCondition c2 = new OutputContainsCondition(n, "<<b --> word> =/> <c --> word>>.", 4);
                
        while (n.time() < maxCycles) {
           
            n.believe("<a --> word>", Tense.Present, 1.0f, 0.9f);
            n.run(pause);
            n.believe("<b --> word>", Tense.Present, 1.0f, 0.9f);
            n.run(pause);
            n.believe("<c --> word>", Tense.Present, 1.0f, 0.9f);
            n.run(pause);
            
            n.ask("<?what =/> <c --> meta-word>>");
            
            n.run(interSeqPause);
        }
        
        System.out.println(c);
        System.out.println(c2);
        System.out.println();
    }
    
    public static void main(String[] args) throws Narsese.InvalidInputException {
        for (int pause = 1; pause < 10; pause++) {
            new GoalSequence(5, pause, pause * 10);
        }
    }
}
