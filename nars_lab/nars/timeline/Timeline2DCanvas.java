package nars.timeline;

import com.google.common.collect.Lists;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JFrame;
import nars.core.NAR;
import nars.core.build.DefaultNARBuilder;
import nars.entity.Item;
import nars.gui.NARSwing;
import nars.gui.NWindow;
import nars.gui.output.chart.TimeSeriesChart;
import nars.io.Output.IN;
import nars.io.Output.OUT;
import nars.io.Texts;
import nars.util.InferenceTrace;
import nars.util.InferenceTrace.InferenceEvent;
import nars.util.InferenceTrace.OutputEvent;
import nars.util.InferenceTrace.TaskEvent;
import org.jbox2d.common.MathUtils;
import processing.core.PApplet;
import processing.event.KeyEvent;

/** Timeline view of an inference trace.  Focuses on a specific window and certain features which
 can be adjusted dynamically. Can either analyze a trace while a NAR runs, or after it finishes. */
public class Timeline2DCanvas extends PApplet {


    float camScale = 1f;
    float scaleSpeed = 0.1f;
    private float lastMousePressY = Float.NaN;
    private float lastMousePressX = Float.NaN;

    private final InferenceTrace trace;
    //float dScale = 0.6f;

    boolean updating = true;

    float minLabelScale = 2f;
    float minYScale = 0.5f;
    float minTimeScale = 0.5f;
    float drawnTextScale = 0;
    
    
    //display options
    boolean showEventLabels = true;
    float textScale = 0.1f;
    float timeScale = 32f;
    float yScale = 32f;
    final Map<String, TimeSeriesChart> charts = new TreeMap();
    long cycleStart = 0;
    long cycleEnd = 45;
    
    float camX = 0f;
    float camY = 0f;
    

    
    /**
     * Modes:
     *      Line
     *      Line with vertical pole to base
     *      Stacked bar
     *      Stacked bar normalized each step
     *      Scatter
     *      Spectral
     *      Event Bubble
     * 
     */
    public static class Chart {
        private final List<String> sensors;
        private float height = 1.0f;
        boolean showVerticalLines = true;

        public Chart(String... sensors) {
            this(Lists.newArrayList(sensors), 1f);
        }
        
        public Chart(List<String> sensors, float height) {
            this.sensors = sensors;
            this.height = height;
            showVerticalLines = sensors.size() == 1;
        }

        private Chart height(float h) {
            this.height = h;
            return this;
        }
        
        
        
    }
    
    //stores the previous "representative event" for an object as the visualization is updated each time step
    public Map<Object,EventPoint> lastSubjectEvent = new HashMap();
    
    //all events mapped to their visualized feature
    public Map<Object,EventPoint> events = new HashMap();
    
    public List<Chart> chartsEnabled = new ArrayList();
    
    public static class EventPoint<X> {
        public float x, y, z;
        public final X value;
        public final List<EventPoint<X>> incoming = new ArrayList<>();
        public final Object subject;

        public EventPoint(X value, Object subject, float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.subject = subject;
            this.value = value;
        }

        private void set(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
    }
    
    
    public static void main(String[] args) {
        int cycles = 200;
        NAR nar = new DefaultNARBuilder().build();
        InferenceTrace it = new InferenceTrace(nar);
        nar.addInput("<a --> b>.");
        nar.addInput("<b --> c>.");
        nar.addInput("<(^pick,x) =\\> a>.");
        nar.addInput("<(*, b, c) <-> x>.");
        nar.finish(cycles);
        
        
        
        NWindow n = new NWindow("Timeline Test", new Timeline2DCanvas(it, 0, cycles));
        n.show(800,800);
        n.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
    
    public Timeline2DCanvas(InferenceTrace trace, int cycleStart, int cycleEnd) {
        super();
        this.trace = trace;
        this.cycleStart = cycleStart;
        this.cycleEnd = cycleEnd;

    
        
        addChart("concept.count");
        addChart("concept.priority.hist.0", "concept.priority.hist.1", "concept.priority.hist.2", "concept.priority.hist.3").height(2);
        addChart("task.derived", "task.immediate_processed").height(2);
                    
        init();
  
    }

    public Chart addChart(String... sensors) {
        Chart c = new Chart(sensors);
        chartsEnabled.add(c);
        return c;
    }
    
    @Override
    public void setup() {
        colorMode(HSB);
    }

    @Override
    protected void resizeRenderer(int newWidth, int newHeight) {
        super.resizeRenderer(newWidth, newHeight);
        updateNext();
    }

    
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        super.mouseWheelMoved(e);
        /*int wr = e.getWheelRotation();
        camScale += wr * dScale;
        if (wr != 0)
            updateNext();*/
    }

    @Override
    public void keyPressed(KeyEvent event) {
        super.keyPressed(event);
        if (event.getKey() == 'l') { 
            showEventLabels = !showEventLabels;
            updateNext();
        }
            
    }


    
    
    protected void updateCamera() {
        
        //scale limits
        
        if (mouseButton > 0) {
            if (Float.isFinite(lastMousePressX)) {
                float dx = (mouseX - lastMousePressX);
                float dy = (mouseY - lastMousePressY);
                
                if (mouseButton == 37) {
                    //left mouse button
                    if ((dx!=0) || (dy!=0)) {
                        camX -= dx;
                        camY -= dy;
                        updateNext();
                    }
                }
                else if (mouseButton == 39) {
                    //right mouse button
                    yScale += dy * scaleSpeed;
                    timeScale += dx * scaleSpeed;
                    updateNext();
                }
//                else if (mouseButton == 3) {
//                    //middle mouse button (wheel)
//                    rotZ -= dx * rotSpeed;
//                }
            }
            
            lastMousePressX = mouseX;
            lastMousePressY = mouseY;
            
        }
        else {
            lastMousePressX = Float.NaN;
        }

        if (yScale < minYScale) yScale = minYScale;
        if (timeScale < minTimeScale) timeScale = minTimeScale;


        translate(-camX + width/2, -camY + height/2);
        

        if (camScale!=1.0) {
            if (camScale > 100f) camScale = 100f;
            if (camScale < 0.1f) camScale = 0.1f;
            scale(camScale);
        }
        

        cycleStart = (int)(Math.floor((camX - width/2)/timeScale)-1);
        cycleStart = Math.max(0, cycleStart);
        cycleEnd = (int)(Math.ceil((camX + width/2)/timeScale)+1);
        

        
        drawnTextScale = Math.min(yScale,timeScale) * textScale;

    }
    

    public void updateNext() { updating = true; }
    
    public void draw() {            
        
        updateCamera();
        
        if (!updating) {
            return;
        }
        
        updating = false;
        
        background(0);
        


        lastSubjectEvent.clear();
        events.clear();            

        drawEvents();
        
        
        float y = -yScale;        
        float yMargin = yScale*0.1f;
        for (Chart c : chartsEnabled) {
            float h = c.height * yScale;
            drawChart(c, y, timeScale, yScale);
            y -=(h+yMargin);
        }
  
    }

    protected void drawChart(Chart c, float y, float timeScale, float yScale) {
                
        yScale = yScale * c.height;
        


        double min = Double.NaN, max = 0;
        for (String cc : c.sensors) {
            TimeSeriesChart chart = trace.charts.get(cc);

            if (Double.isNaN(min)) {
                min = (chart.getMin());
                max = (chart.getMax());
            }
            else {
                min = Math.min(min, chart.getMin());
                max = Math.max(max, chart.getMax());
            }                                
        }

        stroke(127);
        strokeWeight(0.5f);

        //bottom line
        line(cycleStart * timeScale, y, cycleEnd * timeScale, y);

        //top line
        line(cycleStart * timeScale, y-yScale, cycleEnd * timeScale, y-yScale);


        float screenyHi = screenY(cycleStart*timeScale, y-yScale);
        float screenyLo = screenY(cycleStart*timeScale, y);

        int ccolor = 0;

        for (String cc : c.sensors) {
            TimeSeriesChart chart = trace.charts.get(cc);

            ccolor = chart.getColor().getRGB();

            float lx=0, ly=0;

            strokeWeight(1f);            


            fill(255f);
           
            for (long t = cycleStart; t < cycleEnd; t++) {
                float x = t * timeScale;


                float v = chart.getValue(t);
                if (Float.isNaN(v)) continue;

                float p = (max == min) ? 0 : (float)((v - min) / (max - min));


                float px = x;
                float h = p*yScale;
                float py = y - h;

                if (c.showVerticalLines) {
                    stroke(ccolor, 127f);
                    line(px, py, px, py+h);
                }

                stroke(ccolor);
                if (t!=cycleStart) {
                    line(lx, ly, px, py);
                }

                lx = px;
                ly = py;

            }
        }                   

        //draw overlay
        pushMatrix();
        resetMatrix();
        textSize(15f);

        int dsy = (int) Math.abs(screenyLo - screenyHi);

        float dsyt = screenyHi + 0.15f * dsy;
        float ytspace = dsy * 0.75f / c.sensors.size() / 2;
        for (String cs : c.sensors) {
            fill(trace.charts.get(cs).getColor().getRGB());
            dsyt += ytspace;
            text(cs, 0, dsyt);
            dsyt += ytspace;
        }

        textSize(11f);
        fill(200, 195f);
        text(Texts.n4((float)min), 0, screenyLo-dsy/10f);
        text(Texts.n4((float)max), 0, screenyHi+dsy/10f);

        popMatrix();



        
    }

    protected void drawEvents() {
        noStroke();
        TreeMap<Long, List<InferenceEvent>> time = trace.time;
        
        for (Map.Entry<Long, List<InferenceEvent>> e : time.subMap(cycleStart, cycleEnd).entrySet()) {
            long t = e.getKey();                        
            List<InferenceEvent> v = e.getValue();            
            drawEvent(t, v, 0);
        }
        
        
        strokeCap(SQUARE);        
        strokeWeight(0.75f);
        for (EventPoint<Object> to : events.values()) {
            for (EventPoint<Object> from : to.incoming) {                
                stroke(256f * NARSwing.hashFloat(to.subject.hashCode()), 100f, 200f, 127);
                line(timeScale * from.x, yScale * from.y, timeScale * to.x, yScale * to.y);
            }                
        }
        
    }
    
    private void drawEvent(long t, List<InferenceEvent> v, float y) {
        
        
        float itemScale = Math.min(timeScale,yScale)*0.5f;
        
        
        
        float x = t;
        for (InferenceEvent i : v) {            
            
            //box(2);
            //quad(-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0);
            
            if (i instanceof TaskEvent) {
                TaskEvent te = (TaskEvent)i;
                float p = te.priority;                
                
                {
                    fill(256f * NARSwing.hashFloat(i.getClass().hashCode()), 200f, 200f);
                    float z = p*10f;
                    
                    switch (te.type) {
                        case Added:
                            //forward
                            triangleHorizontal(i, te.task, p*itemScale, x, y, z, 1.0f);
                            break;
                        case Removed:
                            //backwards
                            triangleHorizontal(i, te.task, p*itemScale, x, y, z, -1.0f);
                            break;
                                
                    }
                    
                }                
            }
            else if (i instanceof OutputEvent) {
                OutputEvent te = (OutputEvent)i;
                
                float p = 0.5f;
                if (te.signal instanceof Item) {
                    p = ((Item)te.signal).getPriority();
                }
                float ph = 0.5f + 0.5f * p; //so that priority 0 will still be visible

                fill(256f * NARSwing.hashFloat(te.channel.hashCode()), 100f + 100f * ph, 255f * ph);

                
                if (te.channel.equals(IN.class)) {
                    /*pushMatrix();
                    translate(x*timeScale, y*yScale);
                    rotate(0.65f); //angled diagonally down and to the right                    */
                    triangleHorizontal(i, te.signal, ph*itemScale, x, y, 0, 1.0f);                    
                    //popMatrix();
                }
                else if (te.channel.equals(OUT.class)) {
                    //TODO use faster triangleVertical function instead of push and rotate
                    /*pushMatrix();
                    translate(x*timeScale, y*yScale);
                    rotate(MathUtils.HALF_PI); //angled diagonally down and to the right                   */
                    triangleHorizontal(i, te.signal, ph*itemScale, x, y, 0, 1.0f);                    
                    //popMatrix();
                }
                /*else if exe... {
                    
                }*/
                else {                    
                    rect(i, te.signal, ph*itemScale, x, y);
                }
            }
            else {
                fill(256f * NARSwing.hashFloat(i.toString().hashCode()), 200f, 200f);
                rect(i, null, 0.75f * itemScale, x, y);
            }

            x += 1.0 / v.size();
            y += 1;
        }
        
            
    }

    protected void rect(Object event, Object subject, float r, float x, float y/*, float z*/) {
        float px = x*timeScale;
        float py = y*yScale;
        rect(
                px + -r/2f,  py + -r/2f, 
                r,   r
        );
        
        label(event, subject, r, x, y);
    }
    
    protected void label(Object event, Object subject, float r, float x, float y) {
        if ((showEventLabels) && (yScale > minLabelScale) && (timeScale > minLabelScale)) {
            fill(255f);            
            textSize(drawnTextScale);
            text(event.toString(), timeScale*x-r/2, yScale*y);
        }
        
        setEventPoint(event, subject, x, y, 0);
    }
    
    
    protected void triangleHorizontal(Object event, Object subject, float r, float x, float y, float z, float direction) {        
        float px = x*timeScale;
        float py = y*yScale;
        
        triangle(
                px + direction * -r/2,   py + direction * -r/2, 
                px + direction * r/2,    py + 0, 
                px + direction * -r/2,   py + direction * r/2
        );
        label(event, subject, r, x, y);
    }
    
    protected void setEventPoint(Object event, Object subject, float x, float y, float z) {
        EventPoint f = new EventPoint(event, subject, x, y, z);        
        events.put(event, f);
        
        if (subject!=null) {
            EventPoint e = lastSubjectEvent.put(subject, f);
            if (e != null) {
                f.incoming.add(e);
            }
        }
    }
    
    
    /*
    
    
import picking.*;

Picker picker;
float a = 0.0;

void setup() {
  size(200, 150, P3D);
  picker = new Picker(this);
}

void draw() {
  a += 0.01;

  background(255);

  picker.start(0);
  drawBox(80, 75, 50, #ff8800);

  picker.start(1);
  drawBox(140, 75, 20, #eeee00);

  picker.stop();

  color c = 0;
  int id = picker.get(mouseX, mouseY);
  switch (id) {
    case 0:
      c = #ff8800;
      break;
    case 1:
      c = #eeee00;
      break;
  }
  drawBorder(10, c);
}

void drawBox(int x, int y, int w, color c) {
  stroke(0);
  fill(c);
  pushMatrix();
    translate(x, y);
    rotateX(a); rotateY(a);
    box(w);
  popMatrix();
}

void drawBorder(int w, color c) {
  noStroke();
  fill(c);
  rect(0,   0, width, w);
  rect(0, height - w, width, w);
  rect(0,   0, w, height);
  rect(width - w, 0, w, height);
}
    */


}