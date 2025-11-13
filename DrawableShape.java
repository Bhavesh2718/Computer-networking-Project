import java.io.Serializable;
import java.awt.Color;
import java.awt.BasicStroke;

/**
 * DrawableShape: A serializable class that encapsulates all the information
 * needed to draw a single shape on the canvas. This object is what gets
 * sent from the client to the server and broadcast to all other clients.
 * * It implements Serializable so it can be sent over an ObjectOutputStream.
 */
public class DrawableShape implements Serializable {

    // Unique ID for serialization to prevent version conflicts
    private static final long serialVersionUID = 6529685098267757690L;

    // Coordinates
    private final int x1, y1, x2, y2;
    
    // Drawing properties
    private final Color color;
    private final float strokeWidth;
    
    /**
     * Type of shape.
     * Examples: "Pencil", "Line", "Rect", "Oval", "Eraser"
     */
    private final String shapeType;

    public DrawableShape(int x1, int y1, int x2, int y2, Color color, float stroke, String type) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.color = color;
        this.strokeWidth = stroke;
        this.shapeType = type;
    }

    // --- Getters ---
    // These are used by the client's DrawingPanel to render the shape

    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public Color getColor() { return color; }
    public float getStrokeWidth() { return strokeWidth; }
    public String getShapeType() { return shapeType; }
}