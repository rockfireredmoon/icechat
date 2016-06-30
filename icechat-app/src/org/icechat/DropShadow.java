/**
 * 
 */
package org.icechat;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;

import javax.swing.JWindow;

import com.sun.jna.platform.WindowUtils;

public class DropShadow extends JWindow {
    private static final float SHADOW_ALPHA = .25f;
    private static final float YSCALE = .80f;
    private static final double ANGLE = 2*Math.PI/24;
    private static final Point OFFSET = new Point(8, 8);
    private static final Color COLOR = new Color(0, 0, 0, 50);

    private Shape parentMask;
    private ComponentListener listener;
    public DropShadow(final Window parent, Shape mask) {
        super(parent);
        setFocusableWindowState(false);
        setName("###overrideRedirect###");

        Point where = parent.isShowing()
            ? parent.getLocationOnScreen() : parent.getLocation();
        setLocation(where.x + OFFSET.x, where.y + OFFSET.y);
        setBackground(COLOR);
        getContentPane().setBackground(COLOR);

        parentMask = mask;
        parent.addComponentListener(listener = new ComponentAdapter() {
            public void componentMoved(ComponentEvent e) {
                Point where = getOwner().isShowing()
                    ? getOwner().getLocationOnScreen()
                    : getOwner().getLocation();
                setLocation(where.x + OFFSET.x, where.y + OFFSET.y);
            }
            public void componentResized(ComponentEvent e) {
                Component c = e.getComponent();
                int extra = c.getWidth() + (int)Math.sin(ANGLE)*c.getHeight();
                setSize(c.getWidth() + extra, c.getHeight());
                WindowUtils.setWindowMask(DropShadow.this, getMask());
            }
            public void componentShown(ComponentEvent e) {
                if (!isVisible()) {
                    pack();
                    setVisible(true);
                }
            }
        });
        addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                if (listener != null) {
                    parent.removeComponentListener(listener);
                    listener = null;
                }
            }
        });
        WindowUtils.setWindowMask(DropShadow.this, getMask());
        WindowUtils.setWindowAlpha(DropShadow.this, SHADOW_ALPHA);
        if (parent.isVisible()) {
            pack();
            setVisible(true);
        }
    }
    
    public void paint(Graphics graphics) {
        Graphics2D g = (Graphics2D)graphics.create();
        g.setPaint(new GradientPaint(0, getHeight()/2, new Color(0,0,0,0), getWidth(), getHeight()/2, new Color(0,0,0,255)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }

    public Dimension getPreferredSize() {
        Dimension size = getOwner().getPreferredSize();
        size.width += 100;
        size.height += 100;
        return size;
    }
    
    private Shape getMask() {
        Area area = new Area(parentMask);
        Area clip = new Area(parentMask);

        AffineTransform tx = new AffineTransform();
        tx.translate(Math.sin(ANGLE)*getOwner().getHeight(), 0);
        tx.shear(-Math.tan(ANGLE), 0);
        tx.scale(1, YSCALE);
        tx.translate(0, (1-YSCALE)*getOwner().getHeight());
        area.transform(tx);
        tx = new AffineTransform();
        tx.translate(-OFFSET.x, -OFFSET.y);
        clip.transform(tx);
        area.subtract(clip);
        return area;
    }
}