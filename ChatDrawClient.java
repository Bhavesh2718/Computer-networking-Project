import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ChatDrawClient: The main client application.
 * (No changes in this part of the class)
 */
public class ChatDrawClient {
    // ... (fields are all the same) ...
// --- GUI Components ---
    private JFrame frame;
    private DrawingPanel drawingPanel;
    private JTextArea chatArea;
    private JTextField chatField;
    private JButton sendButton;

    // --- Drawing State ---
    private String currentTool = "Pencil";
    private Color currentColor = Color.BLACK;
    private float currentStroke = 2.0f;

    // --- Network Components ---
    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private String serverAddress;
    private int serverPort;
    private String userName; // <-- ADDED

    public static void main(String[] args) {
        // Run the GUI creation and server connection in the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            new ChatDrawClient().createAndShowGUI();
        });
    }

    /**
     * Prompts the user for server details and initializes the GUI.
     */
    private void createAndShowGUI() {
        // --- 1. Get Server Info ---
        serverAddress = JOptionPane.showInputDialog(null, "Enter server IP address:", "localhost");
        if (serverAddress == null) System.exit(0); // User cancelled
        try {
            serverPort = Integer.parseInt(JOptionPane.showInputDialog(null, "Enter server port:", "12345"));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Invalid port. Exiting.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // --- 1b. Get User Name ---
        userName = JOptionPane.showInputDialog(null, "Enter your name:", "User");
        if (userName == null || userName.trim().isEmpty()) {
            // Provide a simple default/fallback name if cancelled or empty
            userName = "User" + (int)(Math.random() * 1000);
        }


        // --- 2. Setup Main Frame ---
        frame = new JFrame("Collaborative Chat & Draw");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLayout(new BorderLayout());

        // --- 3. Create Toolbar ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton pencilBtn = new JButton("Pencil");
        pencilBtn.addActionListener(e -> currentTool = "Pencil");
        toolBar.add(pencilBtn);

        JButton lineBtn = new JButton("Line");
        lineBtn.addActionListener(e -> currentTool = "Line");
        toolBar.add(lineBtn);

        JButton rectBtn = new JButton("Rectangle");
        rectBtn.addActionListener(e -> currentTool = "Rect");
        toolBar.add(rectBtn);

        JButton ovalBtn = new JButton("Oval");
        ovalBtn.addActionListener(e -> currentTool = "Oval");
        toolBar.add(ovalBtn);

        JButton eraserBtn = new JButton("Eraser");
        eraserBtn.addActionListener(e -> currentTool = "Eraser");
        toolBar.add(eraserBtn);

        toolBar.addSeparator();

        JButton colorBtn = new JButton("Color");
        colorBtn.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(frame, "Choose Color", currentColor);
            if (newColor != null) {
                currentColor = newColor;
            }
        });
        toolBar.add(colorBtn);

        // Simple stroke selection
        String[] strokeSizes = {"1.0", "2.0", "4.0", "8.0", "16.0"};
        JComboBox<String> strokeBox = new JComboBox<>(strokeSizes);
        strokeBox.setSelectedItem("2.0");
        strokeBox.setMaximumSize(new Dimension(60, 30));
        strokeBox.addActionListener(e -> currentStroke = Float.parseFloat((String) strokeBox.getSelectedItem()));
        toolBar.add(new JLabel(" Stroke: "));
        toolBar.add(strokeBox);

        toolBar.addSeparator();

        JButton clearBtn = new JButton("Clear Canvas");
        clearBtn.addActionListener(e -> {
            // Confirm before clearing
            int confirm = JOptionPane.showConfirmDialog(frame, 
                "This will clear the canvas for everyone. Are you sure?", 
                "Confirm Clear", JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
                sendObject("CLEAR_CANVAS");
            }
        });
        toolBar.add(clearBtn);

        frame.add(toolBar, BorderLayout.NORTH);

        // --- 4. Create Drawing Panel ---
        drawingPanel = new DrawingPanel();
        frame.add(drawingPanel, BorderLayout.CENTER);

        // --- 5. Create Chat Panel ---
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setPreferredSize(new Dimension(0, 150)); // Height for chat
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);

        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatField = new JTextField();
        chatField.addActionListener(e -> sendMessage()); // Send on Enter key
        chatInputPanel.add(chatField, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        chatInputPanel.add(sendButton, BorderLayout.EAST);

        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        frame.add(chatPanel, BorderLayout.SOUTH);

        // --- 6. Finalize Frame and Connect ---
        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);

        // Connect to server (must be after GUI is visible)
        connectToServer();
    }
    
    // ... (sendMessage, sendObject, connectToServer methods are unchanged) ...
    /**
     * Handles sending the chat message from the chatField.
     */
    private void sendMessage() {
        String message = chatField.getText().trim();
        if (!message.isEmpty()) {
            sendObject(userName + ": " + message); // <-- MODIFIED
            chatField.setText("");
        }
    }

    /**
     * A synchronized helper method to send any object to the server.
     *
     * @param obj The object (String or DrawableShape) to send.
     */
    private synchronized void sendObject(Object obj) {
        if (oos != null) {
            try {
                oos.writeObject(obj);
                oos.flush();
            } catch (IOException e) {
                System.err.println("Error sending object: " + e.getMessage());
                // Show error message and close
                JOptionPane.showMessageDialog(frame, 
                    "Failed to send message. Connection to server lost.", 
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }
    }

    /**
     * Establishes the connection to the server and starts the listener thread.
     */
    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, serverPort);
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            // --- Send username to server ---
            // This MUST be the first object sent
            oos.writeObject(userName);
            oos.flush();
            
            // Start the dedicated thread to listen for server messages
            new Thread(new ListenFromServer()).start();
            
            frame.setTitle("Chat & Draw - " + userName + " - Connected to " + serverAddress); // <-- MODIFIED

        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(frame, "Unknown server: " + serverAddress, "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not connect to server: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }


    /**
     * ListenFromServer: An inner class that runs on a separate thread
     * (No changes in this inner class)
     */
    private class ListenFromServer implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Object obj = ois.readObject();

                    // Use SwingUtilities.invokeLater to update the GUI from this thread
                    SwingUtilities.invokeLater(() -> {
                        if (obj instanceof String) {
                            String msg = (String) obj;
                            if (msg.equals("CLEAR_CANVAS")) {
                                drawingPanel.clearCanvas();
                            } else {
                                chatArea.append(msg + "\n");
                                // Auto-scroll chat to bottom
                                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                            }
                        } else if (obj instanceof DrawableShape) {
                            drawingPanel.addShape((DrawableShape) obj);
                        } else if (obj instanceof ArrayList) {
                            // This is the initial drawing history
                            drawingPanel.setShapes((ArrayList<Object>) obj);
                        }
                    });
                }
            } catch (EOFException | SocketException e) {
                // Server has closed the connection or disconnected
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Connection to server lost.", "Disconnected", JOptionPane.ERROR_MESSAGE);
                    frame.setTitle("Collaborative Chat & Draw - DISCONNECTED");
                    sendButton.setEnabled(false);
                    chatField.setEnabled(false);
                });
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error receiving from server: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * DrawingPanel: An inner class that serves as the canvas.
     * This class contains all the logic changes.
     */
    private class DrawingPanel extends JPanel {
        
        private final List<Object> shapes = new CopyOnWriteArrayList<>();
        
        // --- MODIFIED FIELDS ---
        private Point startPoint; // For Line/Rect/Oval
        private Point endPoint;   // For Line/Rect/Oval
        private List<Point> currentPathPoints; // <-- ADDED: To store pencil/eraser path

        public DrawingPanel() {
            setBackground(Color.WHITE);
            
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    startPoint = e.getPoint();
                    endPoint = e.getPoint();
                    
                    // --- MODIFIED ---
                    // If pencil or eraser, start a new path list
                    if (currentTool.equals("Pencil") || currentTool.equals("Eraser")) {
                        currentPathPoints = new ArrayList<>();
                        currentPathPoints.add(startPoint);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    endPoint = e.getPoint();
                    
                    // --- MODIFIED ---
                    if (currentTool.equals("Pencil") || currentTool.equals("Eraser")) {
                        // Just add points to our local path list and repaint for preview
                        currentPathPoints.add(endPoint);
                        repaint(); 
                        
                        // We NO LONGER send small segments here
                    
                    } else {
                        // For Line/Rect/Oval, just repaint locally for preview
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    endPoint = e.getPoint();
                    
                    // --- MODIFIED ---
                    if (currentTool.equals("Pencil") || currentTool.equals("Eraser")) {
                        // Mouse released, so the path is done.
                        // Send the *entire path* as one DrawableShape object.
                        Color drawColor = currentTool.equals("Eraser") ? getBackground() : currentColor;
                        float drawStroke = currentTool.equals("Eraser") ? 16.0f : currentStroke;
                        
                        // Create a new shape using the path constructor
                        DrawableShape shape = new DrawableShape(
                            new ArrayList<>(currentPathPoints), // Send a copy of the list
                            drawColor, drawStroke, currentTool
                        );
                        sendObject(shape);
                        
                        currentPathPoints = null; // Clear the temporary path
                    
                    } else {
                        // Old logic for Line/Rect/Oval
                        DrawableShape shape = new DrawableShape(
                            startPoint.x, startPoint.y, endPoint.x, endPoint.y,
                            currentColor, currentStroke, currentTool
                        );
                        sendObject(shape);
                    }
                    
                    // Clear temporary points
                    startPoint = null;
                    endPoint = null;
                    repaint(); // Clear the temporary preview shape
                }
            };
            
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        // ... (clearCanvas, addShape, setShapes are unchanged) ...
        /**
         * Clears the canvas.
         */
        public void clearCanvas() {
            shapes.clear();
            repaint();
        }

        /**
         * Adds a single shape received from the server.
         */
        public void addShape(DrawableShape shape) {
            shapes.add(shape);
            repaint();
        }

        /**
         * Sets the entire shape list (used for initial sync).
         */
        public void setShapes(List<Object> shapeList) {
            shapes.clear();
            shapes.addAll(shapeList);
            repaint();
        }


        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. Draw all historical shapes
            for (Object obj : shapes) {
                if (obj instanceof DrawableShape) {
                    drawShape(g2, (DrawableShape) obj);
                }
            }

            // 2. Draw the temporary shape if the user is currently drawing
            // --- MODIFIED ---
            if (startPoint != null && endPoint != null) {
                g2.setColor(currentColor);
                
                if (currentTool.equals("Pencil") || currentTool.equals("Eraser")) {
                    // Draw the temporary path as it's being built
                    if (currentPathPoints != null && currentPathPoints.size() > 1) {
                        Color drawColor = currentTool.equals("Eraser") ? getBackground() : currentColor;
                        float drawStroke = currentTool.equals("Eraser") ? 16.0f : currentStroke;
                        
                        g2.setColor(drawColor);
                        g2.setStroke(new BasicStroke(drawStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        
                        for (int i = 0; i < currentPathPoints.size() - 1; i++) {
                            Point p1 = currentPathPoints.get(i);
                            Point p2 = currentPathPoints.get(i + 1);
                            g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                        }
                    }
                } else {
                    // Old logic for drawing temporary Line/Rect/Oval
                    g2.setStroke(new BasicStroke(currentStroke));
                    DrawableShape tempShape = new DrawableShape(
                        startPoint.x, startPoint.y, endPoint.x, endPoint.y,
                        currentColor, currentStroke, currentTool
                    );
                    // Use the helper, but only for the non-path logic
                    drawShape(g2, tempShape);
                }
            }
        }

        /**
         * A helper method to draw a single DrawableShape.
         * --- THIS IS HEAVILY MODIFIED ---
         */
        private void drawShape(Graphics2D g2, DrawableShape shape) {
            // Set properties from the shape
            g2.setColor(shape.getColor());
            g2.setStroke(new BasicStroke(shape.getStrokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            String type = shape.getShapeType();

            if (type.equals("Pencil") || type.equals("Eraser")) {
                // --- NEW LOGIC ---
                // Draw the shape from its saved path points
                List<Point> path = shape.getPathPoints();
                if (path != null && path.size() > 1) {
                    for (int i = 0; i < path.size() - 1; i++) {
                        Point p1 = path.get(i);
                        Point p2 = path.get(i + 1);
                        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                    }
                }
            } else if (type.equals("Line")) {
                // --- OLD LOGIC (but now in its own 'else if') ---
                g2.drawLine(shape.getX1(), shape.getY1(), shape.getX2(), shape.getY2());
            
            } else if (type.equals("Rect") || type.equals("Oval")) {
                // --- OLD LOGIC ---
                // Calculate correct top-left corner and width/height
                int x = Math.min(shape.getX1(), shape.getX2());
                int y = Math.min(shape.getY1(), shape.getY2());
                int width = Math.abs(shape.getX1() - shape.getX2());
                int height = Math.abs(shape.getY1() - shape.getY2());

                if (type.equals("Rect")) {
                    g2.drawRect(x, y, width, height);
                } else if (type.equals("Oval")) {
                    g2.drawOval(x, y, width, height);
                }
            }
        }
    }
}