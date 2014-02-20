package org.rrd4j.graph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Paint;
import java.awt.Stroke;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;

import org.rrd4j.core.Util;
import org.rrd4j.data.DataProcessor;

/**
 * Class which actually creates Rrd4j graphs (does the hard work).
 */
public class RrdGraph implements RrdGraphConstants {
    private static final double[] SENSIBLE_VALUES = {
        1000.0, 900.0, 800.0, 750.0, 700.0, 600.0, 500.0, 400.0, 300.0, 250.0, 200.0, 125.0, 100.0,
        90.0, 80.0, 75.0, 70.0, 60.0, 50.0, 40.0, 30.0, 25.0, 20.0, 10.0,
        9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.5, 3.0, 2.5, 2.0, 1.8, 1.5, 1.2, 1.0,
        0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.0, -1
    };

    private static final char[] SYMBOLS = {'a', 'f', 'p', 'n', 'µ', 'm', ' ', 'k', 'M', 'G', 'T', 'P', 'E'};

    final RrdGraphDef gdef;
    ImageParameters im = new ImageParameters();
    DataProcessor dproc;
    ImageWorker worker;
    Mapper mapper;
    RrdGraphInfo info = new RrdGraphInfo();
    private final String signature;

    /**
     * Creates graph from the corresponding {@link org.rrd4j.graph.RrdGraphDef} object.
     *
     * @param gdef Graph definition
     * @throws java.io.IOException Thrown in case of I/O error
     */
    public RrdGraph(RrdGraphDef gdef) throws IOException {
        this.gdef = gdef;
        signature = gdef.getSignature();
        worker = new ImageWorker(1, 1); // Dummy worker, just to start with something
        try {
            createGraph();
        }
        finally {
            worker.dispose();
            worker = null;
            dproc = null;
        }
    }

    /**
     * Returns complete graph information in a single object.
     *
     * @return Graph information (width, height, filename, image bytes, etc...)
     */
    public RrdGraphInfo getRrdGraphInfo() {
        return info;
    }

    private void createGraph() throws IOException {
        boolean lazy = lazyCheck();
        if (!lazy || gdef.printStatementCount() != 0) {
            fetchData();
            resolveTextElements();
            if (gdef.shouldPlot() && !lazy) {
                calculatePlotValues();
                initializeAxesImageParameters();
                findMinMaxValues();
                identifySiUnit();
                expandValueRange();
                removeOutOfRangeRules();
                removeOutOfRangeSpans();
                initializeLimits();
                placeLegends();
                createImageWorker();
                drawBackground();
                drawData();
                drawGrid();
                drawAxisLines();
                drawText();
                drawLegend();
                drawRules();
                drawSpans();
                gator();
                drawOverlay();
                saveImage();
            }
        }
        collectInfo();
    }

    private void collectInfo() {
        info.filename = gdef.filename;
        info.width = im.xgif;
        info.height = im.ygif;
        for (CommentText comment : gdef.comments) {
            if (comment instanceof PrintText) {
                PrintText pt = (PrintText) comment;
                if (pt.isPrint()) {
                    info.addPrintLine(pt.resolvedText);
                }
            }
        }
        if (gdef.imageInfo != null) {
            info.imgInfo = Util.sprintf(gdef.locale, gdef.imageInfo, gdef.filename, im.xgif, im.ygif);
        }
    }

    private void saveImage() throws IOException {
        if (!gdef.filename.equals("-")) {
            info.bytes = worker.saveImage(gdef.filename, gdef.imageFormat, gdef.imageQuality, gdef.interlaced);
        }
        else {
            info.bytes = worker.getImageBytes(gdef.imageFormat, gdef.imageQuality, gdef.interlaced);
        }
    }

    private void drawOverlay() throws IOException {
        if (gdef.overlayImage != null) {
            worker.loadImage(gdef.overlayImage);
        }
    }

    private void gator() {
        if (!gdef.onlyGraph && gdef.showSignature) {
            worker.setTextAntiAliasing(gdef.textAntiAliasing);
            Font font = gdef.getFont(FONTTAG_WATERMARK);
            int x = (int) (im.xgif - 2 - worker.getFontAscent(font));
            int y = 4;
            worker.transform(x, y, Math.PI / 2);
            worker.drawString(signature, 0, 0, font, Color.LIGHT_GRAY);
            worker.reset();
            worker.setTextAntiAliasing(false);
        }
    }

    private void drawRules() {
        worker.clip(im.xorigin + 1, im.yorigin - gdef.height - 1, gdef.width - 1, gdef.height + 2);
        for (Map.Entry<PlotElement, Integer> entry : gdef.plotElements.entrySet())
        {
            PlotElement plotElement = entry.getKey();
            int axis = entry.getValue();
            drawRule(axis, plotElement);
        }
        worker.reset();
    }
    private void drawRule(int axis, PlotElement pe) {

        if (pe instanceof HRule) {
            HRule hr = (HRule) pe;
            double minAxisValue = im.axisImageParams[axis].yminval;
            double maxAxisValue = im.axisImageParams[axis].ymaxval;
            if (hr.value >= minAxisValue && hr.value <= maxAxisValue) {
                int y = mapper.ytr(axis, hr.value);
                worker.drawLine(im.xorigin, y, im.xorigin + im.xsize, y, hr.color, new BasicStroke(hr.width));
            }
        }
        else if (pe instanceof VRule) {
            VRule vr = (VRule) pe;
            if (vr.timestamp >= im.start && vr.timestamp <= im.end) {
                int x = mapper.xtr(vr.timestamp);
                worker.drawLine(x, im.yorigin, x, im.yorigin - im.ysize, vr.color, new BasicStroke(vr.width));
            }
        }
    }

    private void drawSpans() {
        worker.clip(im.xorigin + 1, im.yorigin - gdef.height - 1, gdef.width - 1, gdef.height + 2);
        for (Map.Entry<PlotElement, Integer> entry : gdef.plotElements.entrySet())
        {
            PlotElement plotElement = entry.getKey();
            int axis = entry.getValue();
            drawSpan(axis, plotElement);
        }
        worker.reset();
    }
    private void drawSpan(int valueAxis, PlotElement pe) {

            if (pe instanceof HSpan) {
                HSpan hr = (HSpan) pe;
                int ys = mapper.ytr(valueAxis, hr.start);
                int ye = mapper.ytr(valueAxis, hr.end);
                int height = ys - ye;
                worker.fillRect(im.xorigin, ys - height, im.xsize, height, hr.color);
            }
            else if (pe instanceof VSpan) {
                VSpan vr = (VSpan) pe;
                int xs = mapper.xtr(vr.start);
                int xe = mapper.xtr(vr.end);
                worker.fillRect(xs, im.yorigin - im.ysize, xe - xs, im.ysize, vr.color);
            }
    }

    private void drawText() {
        if (!gdef.onlyGraph) {
            worker.setTextAntiAliasing(gdef.textAntiAliasing);
            if (gdef.title != null) {
                int x = im.xgif / 2 - (int) (worker.getStringWidth(gdef.title, gdef.getFont(FONTTAG_TITLE)) / 2);
                int y = PADDING_TOP + (int) worker.getFontAscent(gdef.getFont(FONTTAG_TITLE));
                worker.drawString(gdef.title, x, y, gdef.getFont(FONTTAG_TITLE), gdef.colors[COLOR_FONT]);
            }

            for (Map.Entry<Integer, RrdAxisDef> entry : gdef.valueAxisDefs.entrySet())
            {
                int axis = entry.getKey();
                RrdAxisDef axisDef = entry.getValue();
                String verticalLabel = axisDef.verticalLabel;
                if (verticalLabel != null) {
                    int x = im.axisImageParams[axis].xoriginVerticalLabel;
                    int y = im.yorigin - im.ysize / 2;
                    int yoffset =  (int) worker.getStringWidth(verticalLabel, gdef.smallFont) / 2;
                    if (axisDef.opposite_side) {
                        y -= yoffset;
                        worker.transform(x, y, Math.PI / 2);
                    } else {
                        y += yoffset;
                        worker.transform(x, y, -Math.PI / 2);
                    }
                    int ascent = (int) worker.getFontAscent(gdef.smallFont);
                    worker.drawString(verticalLabel, 0, ascent, gdef.smallFont, axisDef.color);
                    worker.reset();
                }
            }
            worker.setTextAntiAliasing(false);
        }
    }


    /*
     * draws the horizontal grid lines based on the primary (default) y axis
     * and draws the labels of each of the y axis based on the scale of the
     * particular axis
     */

    private void drawGrid() {
        if (!gdef.onlyGraph) {
            worker.setTextAntiAliasing(gdef.textAntiAliasing);
            //draw rectangular border around the entire image including the graph, axes,  and legends
            Paint shade1 = gdef.colors[COLOR_SHADEA];
            Paint shade2 = gdef.colors[COLOR_SHADEB];
            Stroke borderStroke = new BasicStroke(1);
            worker.drawLine(0, 0, im.xgif - 1, 0, shade1, borderStroke);
            worker.drawLine(1, 1, im.xgif - 2, 1, shade1, borderStroke);
            worker.drawLine(0, 0, 0, im.ygif - 1, shade1, borderStroke);
            worker.drawLine(1, 1, 1, im.ygif - 2, shade1, borderStroke);
            worker.drawLine(im.xgif - 1, 0, im.xgif - 1, im.ygif - 1, shade2, borderStroke);
            worker.drawLine(0, im.ygif - 1, im.xgif - 1, im.ygif - 1, shade2, borderStroke);
            worker.drawLine(im.xgif - 2, 1, im.xgif - 2, im.ygif - 2, shade2, borderStroke);
            worker.drawLine(1, im.ygif - 2, im.xgif - 2, im.ygif - 2, shade2, borderStroke);

            if (gdef.drawXGrid) {
                //draw vertical grid lines and time axis labels
                new TimeAxis(this).draw();
            }

            if (gdef.drawYGrid) {
                //draw major and minor horizontal grid lines for the primary axis
                if (!drawGrid(DEFAULT_Y_AXIS, im.axisImageParams[DEFAULT_Y_AXIS])) {
                    drawGridError("Error drawing primary axis: ");
                }

                //draw horizontal grid tics and value labels for each remaining value axis (y-axis)
                for (int i=1; i<im.axisImageParams.length; i++) {
                    if (!drawTickMarksAndLabels(i, im.axisImageParams[i])) {
                        drawGridError("Error drawing axis " + i + ": ");
                    }
                }
            }
            worker.setTextAntiAliasing(false);
        }
    }

    private boolean drawGrid(int axis, AxisImageParameters aim) {
        if (gdef.altYMrtg) {
            return new ValueAxisMrtg(this).drawGrid(axis, aim);
        }
        else if (aim.logarithmic) {
            return new ValueAxisLogarithmic(this).drawGrid(axis, aim);
        }
        else {
            return new ValueAxis(this).drawGrid(axis, aim);
        }
    }

    private boolean drawTickMarksAndLabels(int axis, AxisImageParameters aim) {
        if (gdef.altYMrtg) {
            return new ValueAxisMrtg(this).drawTickMarksAndLabels(axis, aim);
        }
        else if (aim.logarithmic) {
            return new ValueAxisLogarithmic(this).drawTickMarksAndLabels(axis, aim);
        }
        else {
            return new ValueAxis(this).drawTickMarksAndLabels(axis, aim);
        }
    }

    private void drawGridError(String err) {
        String msg = err + "No Data Found";
        worker.drawString(msg,
                im.xgif / 2 - (int) worker.getStringWidth(msg, gdef.largeFont) / 2,
                (2 * im.yorigin - im.ysize) / 2,
                gdef.largeFont, gdef.colors[COLOR_FONT]);
    }


    private void initializeAxesImageParameters() {
        im.axisImageParams = new AxisImageParameters[gdef.valueAxisDefs.size()];
        for (int i=0; i<im.axisImageParams.length; i++) {
            AxisImageParameters aim  = new AxisImageParameters();
            RrdAxisDef axisDef = gdef.getAxisDef(i);
            if (axisDef.color == null) {
                axisDef.color = gdef.colors[COLOR_FONT];
            }
            aim.logarithmic = axisDef.logarithmic;
            aim.ymaxval = axisDef.max;
            aim.yminval = axisDef.min;
            im.axisImageParams[i] = aim;
        }
    }

    private void drawData() {
        worker.setAntiAliasing(gdef.antiAliasing);
        worker.clip(im.xorigin, im.yorigin - gdef.height - 1, gdef.width, gdef.height + 2);
        double[] x = xtr(dproc.getTimestamps());
        double[] lastY = null;
        double[] bottomY = null;

        for (Map.Entry<PlotElement, Integer> entry : gdef.plotElements.entrySet())
        {
            PlotElement pe = entry.getKey();
            int axis = entry.getValue();
            AxisImageParameters aim = im.axisImageParams[axis];
            double areazero = mapper.ytr(axis, (aim.yminval > 0.0) ? aim.yminval : (aim.ymaxval < 0.0) ? aim.ymaxval : 0.0);
            // draw line, area and stack
            if (pe instanceof SourcedPlotElement) {
                SourcedPlotElement source = (SourcedPlotElement) pe;
                double[] y = ytr(axis, source.getValues());
                if (source instanceof Line) {
                    worker.drawPolyline(x, y, source.color, new BasicStroke(((Line) source).width));
                }
                else if (Area.class.isAssignableFrom(source.getClass())) {
                    if(source.parent == null) {
                        worker.fillPolygon(x, areazero, y, source.color);                        
                    }
                    else {
                        worker.fillPolygon(x, lastY, y, source.color);
                        worker.drawPolyline(x, lastY, source.getParentColor(), new BasicStroke(0));                        
                    }
                }
                else if (source instanceof Stack) {
                    Stack stack = (Stack) source;
                    float width = stack.getParentLineWidth();
                    if (width >= 0F) {
                        // line
                        worker.drawPolyline(x, y, stack.color, new BasicStroke(width));
                    }
                    else {
                        // area
                        bottomY = floor(areazero, lastY);
                        worker.fillPolygon(x, bottomY, y, stack.color);
                        worker.drawPolyline(x, lastY, stack.getParentColor(), new BasicStroke(0));
                    }
                }
                else {
                    // should not be here
                    throw new IllegalStateException("Unknown plot source: " + source.getClass().getName());
                }
                lastY = y;
            }
        }
        worker.reset();
        worker.setAntiAliasing(false);
    }

    //draws the axis grid lines on the left, right, and bottom
    private void drawAxisLines() {
        if (!gdef.onlyGraph) {
            Paint gridColor = gdef.colors[COLOR_GRID];
            Paint xaxisColor = gdef.colors[COLOR_XAXIS];
            Paint yaxisColor = gdef.colors[COLOR_YAXIS];
            Paint arrowColor = gdef.colors[COLOR_ARROW];
            Stroke stroke = new BasicStroke(1);

            //vertical line along right edge of the grid
            worker.drawLine(im.xorigin + im.xsize, im.yorigin, im.xorigin + im.xsize, im.yorigin - im.ysize,
                    gridColor, stroke);
            //horizontal line along top edge of the grid
            worker.drawLine(im.xorigin, im.yorigin - im.ysize, im.xorigin + im.xsize, im.yorigin - im.ysize,
                    gridColor, stroke);

            //draw value axis as a horizontal line along the bottom edge of the grid
            worker.drawLine(im.xorigin - 4, im.yorigin, im.xorigin + im.xsize + 4, im.yorigin,
                    xaxisColor, stroke);

            //draw vertical line for each y axis
            for (int i=0; i<im.axisImageParams.length; i++) {
                int axisxorigin = im.axisImageParams[i].axisxorigin;
                worker.drawLine(axisxorigin, im.yorigin, axisxorigin, im.yorigin - im.ysize, yaxisColor, stroke);
            }

            //draw value axis arrow
            if ((gdef.valueAxisDefs.size() == 1) && (!gdef.valueAxisDefs.get(DEFAULT_Y_AXIS).opposite_side)) {
                worker.drawLine(im.xorigin + im.xsize + 4, im.yorigin - 3, im.xorigin + im.xsize + 4, im.yorigin + 3,
                        arrowColor, stroke);
                worker.drawLine(im.xorigin + im.xsize + 4, im.yorigin - 3, im.xorigin + im.xsize + 9, im.yorigin,
                        arrowColor, stroke);
                worker.drawLine(im.xorigin + im.xsize + 4, im.yorigin + 3, im.xorigin + im.xsize + 9, im.yorigin,
                        arrowColor, stroke);
            }
        }
    }

    private void drawBackground() throws IOException {
        worker.fillRect(0, 0, im.xgif, im.ygif, gdef.colors[COLOR_BACK]);
        if (gdef.backgroundImage != null) {
            worker.loadImage(gdef.backgroundImage);
        }
        worker.fillRect(im.xorigin, im.yorigin - im.ysize, im.xsize, im.ysize, gdef.colors[COLOR_CANVAS]);
    }

    private void createImageWorker() {
        worker.resize(im.xgif, im.ygif);
    }

    private void placeLegends() {
        if (!gdef.noLegend && !gdef.onlyGraph) {
            int border = (int) (getFontCharWidth(FontTag.LEGEND) * PADDING_LEGEND);
            LegendComposer lc = new LegendComposer(this, border, im.ygif, im.xgif - 2 * border);
            im.ygif = lc.placeComments() + PADDING_BOTTOM;
        }
    }


    private void initializeLimits() {
        im.xsize = gdef.width;
        im.ysize = gdef.height;


        im.unitslength = gdef.unitsLength;
        im.yAxisLabelWidth =  (int) (im.unitslength * getSmallFontCharWidth());
        int yAxisVerticalLabelHeight = (int)getSmallFontHeight();
        int xAxisLabelHeight = (int)getSmallFontHeight();

        im.nbrLeftAxis = 0;
        im.nbrRightAxis = 0;

        if (gdef.onlyGraph) {

            im.yorigin = im.ysize;
            im.ygif = im.yorigin;

            im.xorigin = 0;
            im.xgif = im.xsize;
        }
        else {
            im.yorigin = im.ysize + PADDING_TOP;
            if (gdef.title != null) {
                im.yorigin += getLargeFontHeight() + PADDING_TITLE;
            }
            im.ygif = im.yorigin + (int) (PADDING_PLOT * getSmallFontHeight());

            if (gdef.valueAxisDefs.size() == 1) {
                im.xorigin = PADDING_LEFT ;
                im.axisImageParams[DEFAULT_Y_AXIS].xoriginVerticalLabel = PADDING_LEFT;
                if (gdef.valueAxisDefs.get(DEFAULT_Y_AXIS).getVerticalLabel() != null) {
                    im.xorigin += yAxisVerticalLabelHeight;
                }
                im.xorigin += + im.yAxisLabelWidth;
                im.axisImageParams[DEFAULT_Y_AXIS].axisxorigin = im.xorigin;

                im.xgif = im.xorigin + im.xsize + PADDING_GRID_TRIANGLE_WIDTH;

            }  else {

                 Map<Integer, RrdAxisDef> leftAxisDefs = new HashMap<Integer, RrdAxisDef>();
                 Map<Integer, RrdAxisDef> rightAxisDefs = new HashMap<Integer, RrdAxisDef>();

                for (Map.Entry<Integer, RrdAxisDef> entry : gdef.valueAxisDefs.entrySet()) {
                    RrdAxisDef axisDef = entry.getValue();
                    if (axisDef.opposite()) {
                        rightAxisDefs.put(entry.getKey(), entry.getValue());
                    } else {
                        leftAxisDefs.put(entry.getKey(), entry.getValue());
                    }
                }
                im.xorigin = calculateYAxisXLeft(leftAxisDefs, 0, im.yAxisLabelWidth, yAxisVerticalLabelHeight);
                int xright = calculateYAxisXRight(rightAxisDefs, im.xorigin + im.xsize, im.yAxisLabelWidth, yAxisVerticalLabelHeight);

                im.xgif = xright;// + PADDING_RIGHT/2;
            }
        }
        mapper = new Mapper(this);
    }
    private int  calculateYAxisXLeft(Map<Integer, RrdAxisDef> axisDefs, int xYaxis, int yAxisLabelWidth, int verticalLabelHeight) {
        int x = xYaxis;

        for (int axis = im.axisImageParams.length - 1; axis >= 0; axis--) {
            if (axisDefs.containsKey(axis)) {
                RrdAxisDef axisDef = axisDefs.get(axis);

                x += PADDING_LEFT;
                if (axisDef.getVerticalLabel() != null) {
                    im.axisImageParams[axis].xoriginVerticalLabel = x;
                    x += verticalLabelHeight;// + PADDING_RIGHT/2 ;
                }
                x += im.yAxisLabelWidth;
                im.axisImageParams[axis].axisxorigin = x;
            }
        }
        return x;  //x coordinate for inner-most axis on the left side
    }
    private int  calculateYAxisXRight(Map<Integer, RrdAxisDef> axisDefs, int xYaxis, int yAxisLabelWidth, int verticalLabelHeight) {
        int x = xYaxis;

        for (int axis = 0; axis <= im.axisImageParams.length - 1; axis++) {
            if (axisDefs.containsKey(axis)) {
                RrdAxisDef axisDef = axisDefs.get(axis);

                im.axisImageParams[axis].axisxorigin = x;
                x += PADDING_VLABEL + yAxisLabelWidth;
                if (axisDef.getVerticalLabel() != null) {
                    im.axisImageParams[axis].xoriginVerticalLabel = x + PADDING_RIGHT/2;
                    x += verticalLabelHeight + PADDING_RIGHT ;
                }
            }
        }
        return x;  //x coordinate for next right axis
    }

    private void removeOutOfRangeRules() {
        for (Map.Entry<PlotElement, Integer> entry : gdef.plotElements.entrySet())
        {
            PlotElement plotElement = entry.getKey();
            int axis = entry.getValue();

            if (plotElement instanceof HRule) {
                AxisImageParameters aim = im.axisImageParams[axis];
                ((HRule) plotElement).setLegendVisibility(aim.yminval, aim.ymaxval, gdef.forceRulesLegend);
            }
            else if (plotElement instanceof VRule) {
                ((VRule) plotElement).setLegendVisibility(im.start, im.end, gdef.forceRulesLegend);
            }
        }
    }

    private void removeOutOfRangeSpans() {
        for (Map.Entry<PlotElement, Integer> entry : gdef.plotElements.entrySet())
        {
            PlotElement plotElement = entry.getKey();
            int axis = entry.getValue();

            if (plotElement instanceof HSpan) {
                AxisImageParameters aim = im.axisImageParams[axis];
                ((HSpan) plotElement).setLegendVisibility(aim.yminval, aim.ymaxval, gdef.forceRulesLegend);
            }
            else if (plotElement instanceof VSpan) {
                ((VSpan) plotElement).setLegendVisibility(im.start, im.end, gdef.forceRulesLegend);
            }
        }
    }

    private void expandValueRange() {
        for (int i=0; i<im.axisImageParams.length; i++) {
            expandValueRange(im.axisImageParams[i]);
        }
    }

    private void expandValueRange(AxisImageParameters aim) {
        aim.ygridstep = (gdef.valueAxisSetting != null) ? gdef.valueAxisSetting.gridStep : Double.NaN;
        aim.ylabfact = (gdef.valueAxisSetting != null) ? gdef.valueAxisSetting.labelFactor : 0;
        if (!gdef.rigid && !aim.logarithmic) {
            double scaled_min, scaled_max, adj;
            if (Double.isNaN(aim.ygridstep)) {
                if (gdef.altYMrtg) { /* mrtg */
                    aim.decimals = Math.ceil(Math.log10(Math.max(Math.abs(aim.ymaxval), Math.abs(aim.yminval))));
                    aim.quadrant = 0;
                    if (aim.yminval < 0) {
                        aim.quadrant = 2;
                        if (aim.ymaxval <= 0) {
                            aim.quadrant = 4;
                        }
                    }
                    switch (aim.quadrant) {
                    case 2:
                        aim.scaledstep = Math.ceil(50 * Math.pow(10, -(aim.decimals)) * Math.max(Math.abs(aim.ymaxval),
                                Math.abs(aim.yminval))) * Math.pow(10, aim.decimals - 2);
                        scaled_min = -2 * aim.scaledstep;
                        scaled_max = 2 * aim.scaledstep;
                        break;
                    case 4:
                        aim.scaledstep = Math.ceil(25 * Math.pow(10,
                                -(aim.decimals)) * Math.abs(aim.yminval)) * Math.pow(10, aim.decimals - 2);
                        scaled_min = -4 * aim.scaledstep;
                        scaled_max = 0;
                        break;
                    default: /* quadrant 0 */
                        aim.scaledstep = Math.ceil(25 * Math.pow(10, -(aim.decimals)) * aim.ymaxval) *
                        Math.pow(10, aim.decimals - 2);
                        scaled_min = 0;
                        scaled_max = 4 * aim.scaledstep;
                        break;
                    }
                    aim.yminval = scaled_min;
                    aim.ymaxval = scaled_max;
                }
                else if (gdef.altAutoscale || (gdef.altAutoscaleMin && gdef.altAutoscaleMax)) {
                    /* measure the amplitude of the function. Make sure that
                            graph boundaries are slightly higher then max/min vals
                            so we can see amplitude on the graph */
                    double delt, fact;

                    delt = aim.ymaxval - aim.yminval;
                    adj = delt * 0.1;
                    fact = 2.0 * Math.pow(10.0,
                            Math.floor(Math.log10(Math.max(Math.abs(aim.yminval), Math.abs(aim.ymaxval)))) - 2);
                    if (delt < fact) {
                        adj = (fact - delt) * 0.55;
                    }
                    aim.yminval -= adj;
                    aim.ymaxval += adj;
                }
                else if (gdef.altAutoscaleMin) {
                    /* measure the amplitude of the function. Make sure that
                            graph boundaries are slightly lower than min vals
                            so we can see amplitude on the graph */
                    adj = (im.maxval - im.minval) * 0.1;
                    im.minval -= adj;
                }
                else if (gdef.altAutoscaleMax) {
                    /* measure the amplitude of the function. Make sure that
                            graph boundaries are slightly higher than max vals
                            so we can see amplitude on the graph */
                    adj = (aim.ymaxval - aim.yminval) * 0.1;
                    aim.ymaxval += adj;
                }
                else {
                    scaled_min = aim.yminval / aim.magfact;
                    scaled_max = aim.ymaxval / aim.magfact;
                    for (int i = 1; SENSIBLE_VALUES[i] > 0; i++) {
                        if (SENSIBLE_VALUES[i - 1] >= scaled_min && SENSIBLE_VALUES[i] <= scaled_min) {
                            aim.yminval = SENSIBLE_VALUES[i] * aim.magfact;
                        }
                        if (-SENSIBLE_VALUES[i - 1] <= scaled_min && -SENSIBLE_VALUES[i] >= scaled_min) {
                            aim.yminval = -SENSIBLE_VALUES[i - 1] * aim.magfact;
                        }
                        if (SENSIBLE_VALUES[i - 1] >= scaled_max && SENSIBLE_VALUES[i] <= scaled_max) {
                            aim.ymaxval = SENSIBLE_VALUES[i - 1] * aim.magfact;
                        }
                        if (-SENSIBLE_VALUES[i - 1] <= scaled_max && -SENSIBLE_VALUES[i] >= scaled_max) {
                            aim.ymaxval = -SENSIBLE_VALUES[i] * aim.magfact;
                        }
                    }
                }
            }
            else {
                aim.yminval = (double) aim.ylabfact * aim.ygridstep *
                        Math.floor(aim.yminval / ((double) aim.ylabfact * aim.ygridstep));
                aim.ymaxval = (double) aim.ylabfact * aim.ygridstep *
                        Math.ceil(aim.ymaxval / ((double) aim.ylabfact * aim.ygridstep));
            }

        }
    }

    private void identifySiUnit() {
        for (int i=0; i<im.axisImageParams.length; i++) {
            identifySiUnit(im.axisImageParams[i]);
        }
    }
    private void identifySiUnit(AxisImageParameters aim) {
        aim.unitsexponent = gdef.unitsExponent;
        aim.base = gdef.base;
        if (!aim.logarithmic) {
            int symbcenter = 6;
            double digits;
            if (aim.unitsexponent != Integer.MAX_VALUE) {
                digits = Math.floor(aim.unitsexponent / 3);
            }
            else {
                digits = Math.floor(Math.log(Math.max(Math.abs(aim.yminval), Math.abs(aim.ymaxval))) / Math.log(aim.base));
            }
            aim.magfact = Math.pow(aim.base, digits);
            if (((digits + symbcenter) < SYMBOLS.length) && ((digits + symbcenter) >= 0)) {
                aim.symbol = SYMBOLS[(int) digits + symbcenter];
            }
            else {
                aim.symbol = '?';
            }
        }
    }

    private void findMinMaxValues() {
        double [] minval = new double[gdef.valueAxisDefs.size()];
        for (int i = 0; i<minval.length; i++) {
            minval[i] = Double.NaN;
        }
        double [] maxval = minval.clone();

        for (Map.Entry<PlotElement, Integer> entry : gdef.plotElements.entrySet())
        {
            PlotElement pe = entry.getKey();
            int axis = entry.getValue();


            if (pe instanceof SourcedPlotElement) {
                minval[axis] = Util.min(((SourcedPlotElement) pe).getMinValue(), minval[axis]);
                maxval[axis] = Util.max(((SourcedPlotElement) pe).getMaxValue(), maxval[axis]);
            }
        }

        for (int axis=0; axis<gdef.valueAxisDefs.size(); axis++) {
            if (Double.isNaN(minval[axis])) {
                minval[axis] = 0D;
            }
            if (Double.isNaN(maxval[axis])) {
                maxval[axis] = 1D;
            }
            AxisImageParameters aim = im.axisImageParams[axis];
            aim.yminval = gdef.valueAxisDefs.get(axis).min;
            aim.ymaxval = gdef.valueAxisDefs.get(axis).max;

            adjustImageMinMaxValues(minval[axis], maxval[axis], aim);
        }
    }
    private void adjustImageMinMaxValues(double minval, double maxval, AxisImageParameters aim) {
        /* adjust min and max values */
        if (Double.isNaN(aim.yminval) || ((!aim.logarithmic && !gdef.rigid) && aim.yminval > minval)) {
            aim.yminval = minval;
        }
        if (Double.isNaN(aim.ymaxval) || (!gdef.rigid && aim.ymaxval < maxval)) {
            if (aim.logarithmic) {
                aim.ymaxval = maxval * 1.1;
            }
            else {
                aim.ymaxval = maxval;
            }
        }
        /* make sure min is smaller than max */
        if (aim.yminval > aim.ymaxval) {
            aim.yminval = 0.99 * aim.ymaxval;
        }
        /* make sure min and max are not equal */
        if (Math.abs(aim.yminval - aim.ymaxval) < .0000001)  {
            aim.ymaxval *= 1.01;
            if (!aim.logarithmic) {
                aim.yminval *= 0.99;
            }
            /* make sure min and max are not both zero */
            if (aim.ymaxval == 0.0) {
                aim.ymaxval = 1.0;
            }
        }
    }

    private void calculatePlotValues() {
        for (Map.Entry<PlotElement, Integer> entry : gdef.plotElements.entrySet())
        {
            PlotElement pe = entry.getKey();
            int axis = entry.getValue();
            if (pe instanceof SourcedPlotElement) {
                ((SourcedPlotElement) pe).assignValues(dproc);
            }
        }
    }

    private void resolveTextElements() {
        ValueScaler valueScaler = new ValueScaler(gdef.base);
        for (CommentText comment : gdef.comments) {
            comment.resolveText(gdef.locale, dproc, valueScaler);
        }
    }

    private void fetchData() throws IOException {
        dproc = new DataProcessor(gdef.startTime, gdef.endTime);
        dproc.setPoolUsed(gdef.poolUsed);
        if (gdef.step > 0) {
            dproc.setStep(gdef.step);
            dproc.setFetchRequestResolution(gdef.step); 
        }
        for (Source src : gdef.sources) {
            src.requestData(dproc);
        }
        dproc.processData();
        im.start = gdef.startTime;
        im.end = gdef.endTime;
    }

    private boolean lazyCheck() {
        // redraw if lazy option is not set or file does not exist
        if (!gdef.lazy || !Util.fileExists(gdef.filename)) {
            return false; // 'false' means 'redraw'
        }
        // redraw if not enough time has passed
        long secPerPixel = (gdef.endTime - gdef.startTime) / gdef.width;
        long elapsed = Util.getTimestamp() - Util.getLastModified(gdef.filename);
        return elapsed <= secPerPixel;
    }

    private void drawLegend() {
        if (!gdef.onlyGraph && !gdef.noLegend) {
            worker.setTextAntiAliasing(gdef.textAntiAliasing);
            int ascent = (int) worker.getFontAscent(gdef.getFont(FONTTAG_LEGEND));
            int box = (int) getBox(), boxSpace = (int) (getBoxSpace());
            for (CommentText c : gdef.comments) {
                if (c.isValidGraphElement()) {
                    int x = c.x, y = c.y + ascent;
                    if (c instanceof LegendText) {
                        // draw with BOX
                        worker.fillRect(x, y - box, box, box, gdef.colors[COLOR_FRAME]);
                        worker.fillRect(x + 1, y - box + 1, box - 2, box - 2, gdef.colors[COLOR_CANVAS]);
                        worker.fillRect(x + 1, y - box + 1, box - 2, box - 2, gdef.colors[COLOR_BACK]);
                        worker.fillRect(x + 1, y - box + 1, box - 2, box - 2, ((LegendText) c).legendColor);
                        worker.drawString(c.resolvedText, x + boxSpace, y, gdef.getFont(FONTTAG_LEGEND), gdef.colors[COLOR_FONT]);
                    }
                    else {
                        worker.drawString(c.resolvedText, x, y, gdef.getFont(FONTTAG_LEGEND), gdef.colors[COLOR_FONT]);
                    }
                }
            }
            worker.setTextAntiAliasing(false);
        }
    }

    // helper methods

    double getFontHeight(FontTag fonttag) {
        return worker.getFontHeight(gdef.getFont(fonttag));
    }

    double getFontCharWidth(FontTag fonttag) {
        return worker.getStringWidth("a", gdef.getFont(fonttag));
    }

    @Deprecated
    double getSmallFontHeight() {
        return getFontHeight(FONTTAG_LEGEND);
    }

    double getTitleFontHeight() {
        return getFontHeight(FONTTAG_TITLE);
    }

    double getInterlegendSpace() {
        return getFontCharWidth(FONTTAG_LEGEND) * LEGEND_INTERSPACING;
    }

    double getLeading() {
        return getFontHeight(FONTTAG_LEGEND) * LEGEND_LEADING;
    }

    double getSmallLeading() {
        return getFontHeight(FONTTAG_LEGEND) * LEGEND_LEADING_SMALL;
    }

    double getBoxSpace() {
        return Math.ceil(getFontHeight(FONTTAG_LEGEND) * LEGEND_BOX_SPACE);
    }

    private double getBox() {
        return getFontHeight(FONTTAG_LEGEND) * LEGEND_BOX;
    }

    double[] xtr(long[] timestamps) {
        /*
          double[] timestampsDev = new double[timestamps.length];
          for (int i = 0; i < timestamps.length; i++) {
              timestampsDev[i] = mapper.xtr(timestamps[i]);
          }
          return timestampsDev;
         */
        double[] timestampsDev = new double[2 * timestamps.length - 1];
        for (int i = 0, j = 0; i < timestamps.length; i += 1, j += 2) {
            timestampsDev[j] = mapper.xtr(timestamps[i]);
            if (i < timestamps.length - 1) {
                timestampsDev[j + 1] = timestampsDev[j];
            }
        }
        return timestampsDev;
    }

    double[] ytr(int yaxis, double[] values) {
        double[] valuesDev = new double[2 * values.length - 1];
        for (int i = 0, j = 0; i < values.length; i += 1, j += 2) {
            if (Double.isNaN(values[i])) {
                valuesDev[j] = Double.NaN;
            }
            else {
                valuesDev[j] = mapper.ytr(yaxis, values[i]);
            }
            if (j > 0) {
                valuesDev[j - 1] = valuesDev[j];
            }
        }
        return valuesDev;
    }

    double[] floor(double floor, double[] values) {
        double[] flooredValues = new double[values.length];
        for (int i = 0; i< values.length; i++) {
            if (Double.isNaN(values[i])) {
                flooredValues[i] = floor;
            } else {
                flooredValues[i] = values[i];
            }
        }
        return flooredValues;
    }

    /**
     * Renders this graph onto graphing device
     *
     * @param g Graphics handle
     */
    public void render(Graphics g) {
        byte[] imageData = getRrdGraphInfo().getBytes();
        ImageIcon image = new ImageIcon(imageData);
        image.paintIcon(null, g, 0, 0);
    }
}
