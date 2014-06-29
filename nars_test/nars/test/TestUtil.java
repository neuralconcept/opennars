/*
 * Copyright (C) 2014 me
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nars.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import nars.core.NAR;
import nars.entity.Sentence;
import nars.io.TextInput;
import nars.io.TextOutput;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author me
 */
public class TestUtil {
    private final int performanceIterations = 4;

    protected ScriptEngineManager engineManager = new ScriptEngineManager();
    protected ScriptEngine js = engineManager.getEngineByName("nashorn");

    protected Map<String, String> exCache = new HashMap(); //path -> script data
    private final boolean testPerformance;
    
    public TestUtil(boolean testPerformance) {
        this.testPerformance = testPerformance;        
    }
    
    
    public String getExample(String path)  {
        try {
            String existing = exCache.get(path);
            if (existing!=null)
                return existing;
            
            StringBuffer  sb  = new StringBuffer();
            String line;
            File fp = new File(getClass().getResource("nal/" + path).toURI());
            BufferedReader br = new BufferedReader(new FileReader(fp));
            while ((line = br.readLine())!=null) {
                sb.append(line + "\n");
            }
            existing = sb.toString();
            exCache.put(path, existing);
            return existing;
        } catch (Exception ex) {
            System.err.println(ex);
            ex.printStackTrace();
        }
        return "";
    }
        
    public boolean match(String x, String y) {
        if (x.equals(y))
            return true;
        return false;
    }
    
    
    final LinkedList<String> out = new LinkedList();

    public boolean outputContains(String s) {        
        for (String o : out) {
            if (o.indexOf(s)!=-1)
                return true;
        }
        return false;
    }
    

    public Sentence parseOutput(String o) {
        //getTruthString doesnt work yet because it gets confused when Stamp is at the end of the string. either remove that first or make getTruthString aware of that
        return TextInput.parseOutput(o);
    }
    
    protected void testNAL(String path) {
        int extraCycles = 0;
        final NAR n = new NAR();        
        
        
        final LinkedList<String> expressions = new LinkedList();
        out.clear();
        
        new TextInput(n, getExample(path));
        //new TextOutput(n, new PrintWriter(System.out));
        new TextOutput(n) {
            @Override
            public void output(Class c, Object line) {                
                String s = line.toString();
                s = s.trim();

                if (c == ECHO.class) {
                    if (s.startsWith("\"\'")) {      
                        //remove begining "' and trailing "
                        String expression = s.substring(2, s.length()-1);
                        expressions.add(expression);
                        return;
                    }                        
                }

                if (c == OUT.class) {
                    out.add(s);
                }
            }            
        };            

        n.run(extraCycles, false);

        js.put("test", this);
        js.put("out", out);

        //System.err.println("'" + path + "' output=" + Arrays.asList(out));

        for (String e : expressions) {
            try {
                Object result = js.eval(e);
                if (result instanceof Boolean) {
                    assertTrue(path + ": " + e, (Boolean)result);
                }
            }
            catch (Exception x) {
                assertTrue(x.toString() + "<-" + path + ": " + e, false);
                System.err.println(e);
                System.err.println(x);
                x.printStackTrace();
            }            
        }
        
        if (testPerformance) {
            perfNAL(path, 0, performanceIterations, 1);            
        }
    }
    
    protected void perfNAL(String path, int extraCycles, int repeats, int warmups) {
        
        final NAR n = new NAR();        
        
        long totalTime = 0;
        long totalMemory = 0;

        for (int r = 0; r < repeats; r++) {
            long start = System.nanoTime();

            System.gc();

            long freeMemStart = Runtime.getRuntime().freeMemory();

            n.reset();
            new TextInput(n, getExample(path));
            n.run(extraCycles, false);

            if (warmups == 0) {
                totalTime += System.nanoTime() - start;
                totalMemory += freeMemStart - Runtime.getRuntime().freeMemory();
            }
            else
                warmups--;
        }
        System.out.print(path + ": " + ((double)totalTime)/((double)repeats)/1000000.0 + "ms per iteration, ");
        System.out.println(((double)totalMemory)/((double)repeats)/1024.0 + " kb per iteration");
        
    }
    
}