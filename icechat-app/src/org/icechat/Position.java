/**
 * 
 */
package org.icechat;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Point;

public enum Position {
    topLeft, topRight, bottomLeft, bottomRight;

    public Point getPosition(Component component, GraphicsDevice device) {
        GraphicsConfiguration genv = device == null ? component.getGraphicsConfiguration() : device.getDefaultConfiguration();
        device = device == null ? genv.getDevice() : device;
        DisplayMode mode = device.getDisplayMode();
        Dimension d = new Dimension(mode.getWidth(), mode.getHeight());
        Dimension componentSize = component.getSize();

        // Calculate the starting position
        int wy = 0;
        switch (this) {
            case topLeft:
            case topRight:
                wy = 16;
                break;
            case bottomLeft:
            case bottomRight:
                wy = d.height - 48;
        }

        // Calculate the x (and y where appropriate) position
        int wx = 0;
        switch (this) {
            case topLeft:
                wx = 16;
                break;
            case bottomLeft:
                wy = wy - componentSize.height;
                wx = 16;
                break;
            case topRight:
                wx = d.width - componentSize.width - 16;
                break;
            case bottomRight:
                wy = wy - componentSize.height;
                wx = d.width - componentSize.width - 16;
                break;
        }

        // Increment the y position
        switch (this) {
            case topLeft:
            case topRight:
                wy += 16;
                break;
            case bottomLeft:
            case bottomRight:
                wy -= 16;
                break;
        }
        
        wx += genv.getBounds().x;
        wy += genv.getBounds().y;
        
        return new Point(wx, wy);
    }
    
    public String getName() {
        switch(this) {
            case topLeft:
                return "Top Left";
            case topRight:
                return "Top Right";
            case bottomLeft:
                return "Bottom Left";
            default:
                return "Bottom Right";
        }
    }
}