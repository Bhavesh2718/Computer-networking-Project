import java.io.Serializable;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Point; // <-- IMPORT Point
import java.util.List;  // <-- IMPORT List

/**
 * DrawableShape: A serializable class that encapsulates all the information
 * needed to draw a single shape on the canvas.
 *
 * This class now supports two types of shapes:
 * 1. Two-point shapes (Line, Rect, Oval) using x1, y1, x2, y2.
 * 2. Multi-point path shapes (Pencil, Eraser) using pathPoints.
 */
public class DrawableShape implements Serializable {

    // Unique ID for serialization
    private static final long serialVersionUID = 7526471155622776147L; // New UID due to structure change

    // --- Fields ---
    private final int x1, y1, x2, y2;
    private final List<Point> pathPoints; // <-- ADDED: For pencil/eraser paths
    
    private final Color color;
    private final float strokeWidth;
    private final String shapeType;

    /**
     * Constructor for two-point shapes (Line, Rect, Oval).
     */
    public DrawableShape(int x1, int y1, int x2, int y2, Color color, float stroke, String type) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.color = color;
        this.strokeWidth = stroke;
        this.shapeType = type;
        this.pathPoints = null; // Not used for this type
    }

    /**
     * NEW Constructor for multi-point path shapes (Pencil, Eraser).
     */
    public DrawableShape(List<Point> pathPoints, Color color, float stroke, String type) {
        this.pathPoints = pathPoints;
        this.color = color;
        this.strokeWidth = stroke;
        this.shapeType = type;
        // Set default values for unused fields
        this.x1 = 0;
        this.y1 = 0;
        this.x2 = 0;
        this.y2 = 0;
    }

    // --- Getters ---
    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public Color getColor() { return color; }
    public float getStrokeWidth() { return strokeWidth; }
    public String getShapeType() { return shapeType; }
    public List<Point> getPathPoints() { return pathPoints; } // <-- ADDED Getter
}