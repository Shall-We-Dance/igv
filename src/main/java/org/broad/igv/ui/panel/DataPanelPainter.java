/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


package org.broad.igv.ui.panel;

//~--- non-JDK imports --------------------------------------------------------

import org.broad.igv.logging.*;
import org.broad.igv.sam.AlignmentTrack;
import org.broad.igv.sam.InsertionMarker;
import org.broad.igv.track.*;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.UIConstants;

import java.awt.*;
import java.util.*;
import java.util.List;


public class DataPanelPainter {

    private static Logger log = LogManager.getLogger(DataPanelPainter.class);

    public synchronized void paint(List<Track> tracks,
                                   RenderContext context,
                                   Color background,
                                   Rectangle visibleRect) {

        Graphics2D graphics2D = context.getGraphics2D("BACKGROUND");
        graphics2D.setBackground(background);
        graphics2D.clearRect(visibleRect.x, visibleRect.y, visibleRect.width, visibleRect.height);
        graphics2D.setColor(Color.BLACK);

        final ReferenceFrame referenceFrame = context.getReferenceFrame();
        InsertionMarker i = referenceFrame.getExpandedInsertion(); //insertionManager.getSelectedInsertion(referenceFrame.getChrName());

        if (i != null) {

            Collection<AlignmentTrack> alignmentTracks = IGV.getInstance().getAlignmentTracks();
            int maxVizWindow = alignmentTracks.size() == 0 ? 0 : alignmentTracks.stream().mapToInt(t -> t.getVisibilityWindow()).max().getAsInt();

            final double start = referenceFrame.getOrigin();
            final double frameExtent = referenceFrame.getEnd() - referenceFrame.getOrigin();
            double scale = referenceFrame.getScale();
            int insertionPixelPosition = (int) ((i.position - start) / scale);
            int insertionPixelWidth = (int) Math.ceil(i.size / context.getScale());


            if (frameExtent < maxVizWindow &&
                    insertionPixelWidth > 1 &&
                    insertionPixelPosition < visibleRect.width &&
                    insertionPixelPosition + insertionPixelWidth > 0) {

                context.expandedInsertionPosition = i.position;

                // Paint section left of insertion
                if (insertionPixelPosition > 0) {
                    int width = insertionPixelPosition;
                    RenderContext leftContext = shiftRenderContext(context, context.getOrigin(), 0, width);
                    paintFrame(tracks, leftContext);
                }

                // Paint expanded insertion
                RenderContext insertionContext = shiftRenderContext(context, i.position, insertionPixelPosition, insertionPixelWidth);
                paintExpandedInsertion(i, tracks, insertionContext);

                // Paint section to right of insertion
                int p0 = insertionPixelPosition + insertionPixelWidth;
                int w = visibleRect.width - p0;
                if (w > 0) {
                    RenderContext rightContext = shiftRenderContext(context, i.position, p0, w);
                    rightContext.multiframe = true;
                    paintFrame(tracks, rightContext);
                }

            } else {
                // Insertion is out of view
                paintFrame(tracks, context);
            }
        } else {
            // No expanded insertion
            paintFrame(tracks, context);
        }

    }


    private RenderContext shiftRenderContext(RenderContext ctx, double position, int translateX, int pixelWidth) {
        RenderContext newContext = new RenderContext(ctx);
        newContext.getReferenceFrame().widthInPixels = pixelWidth;
        newContext.getReferenceFrame().origin = position;
        newContext.visibleRect = new Rectangle(0, ctx.visibleRect.y, pixelWidth, ctx.visibleRect.height);
        newContext.translateX = translateX;

        Graphics2D dG = newContext.getGraphics();
        dG.translate(translateX, 0);
        dG.setClip(newContext.visibleRect);

        return newContext;
    }


    private void paintFrame(List<Track> tracks, RenderContext dContext) {

        int trackX = 0;
        int trackY = 0;
        Rectangle dRect = dContext.visibleRect;

        List<Track> trackList = tracks;
        synchronized (trackList) {
            for (Track track : trackList) {

                if (track == null || !track.isVisible()) continue;

                int trackHeight = track.getContentHeight();
                if (dRect != null) {
                    if (trackY > dRect.y + dRect.height) {
                        break;
                    } else if (trackY + trackHeight < dRect.y) {
                        if (track.isVisible()) {
                            trackY += trackHeight;
                        }
                        continue;
                    }
                }

                Rectangle rect = new Rectangle(trackX, trackY, dRect.width, trackHeight);
                draw(track, rect, dContext);
                trackY += trackHeight;
            }
        }
    }


    private void paintExpandedInsertion(InsertionMarker insertionMarker, List<Track> trackList, RenderContext context) {

        int trackY = 0;
        Rectangle dRect = context.getVisibleRect();

        for (Track track : trackList) {
            if (track == null || !track.isVisible()) continue;
            int trackHeight = track.getContentHeight();
            if (dRect != null) {
                if (trackY > dRect.y + dRect.height) {
                    break;
                } else if (trackY + trackHeight < dRect.y) {
                    if (track.isVisible()) {
                        trackY += trackHeight;
                    }
                    continue;
                }
            }

            if (track instanceof AlignmentTrack) {
                Rectangle rect = new Rectangle(dRect.x, trackY, dRect.width, trackHeight);
                ((AlignmentTrack) track).renderExpandedInsertion(insertionMarker, context, rect);
            }

            trackY += trackHeight;

        }
    }


    final private void draw(Track track, Rectangle rect, RenderContext context) {

        track.render(context, rect);

        // Get overlays
        List<Track> overlayTracks = IGV.getInstance().getOverlayTracks(track);
        if (overlayTracks != null) {
            for (Track overlayTrack : overlayTracks) {

                // Don't overlay on self
                if (overlayTrack != track) {
                    overlayTrack.overlay(context, rect);
                }
            }
        }

    }


}


