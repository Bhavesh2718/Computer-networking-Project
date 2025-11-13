import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.awt.Color; // Although server doesn't use it, it might be needed if DrawableShape is an inner class.

/**
 * ChatDrawServer: The main server for the collaborative chat and drawing application.
 * It listens for client connections on a specified port and creates a new
 * ClientHandler thread for each connected client. It maintains a list of all
 * client output streams for broadcasting messages and a history of all drawing
 * actions to synchronize new clients.
 */
public class ChatDrawServer {

    private static final int PORT = 12345;

    /**
     * A thread-safe list to store ObjectOutputStreams for all connected clients.
     * This allows the server to broadcast messages to all clients.
     * CopyOnWriteArrayList is used for thread safety, especially when iterating
     * for broadcasts while clients may be connecting/disconnecting.
     */
    private static List<ObjectOutputStream> clientOutputStreams = new CopyOnWriteArrayList<>();

    /**
     * A thread-safe list to store the history of all drawing objects.
     * This is sent to new clients when they connect to synchronize their canvas.
     */
    private static List<Object> drawingHistory = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        System.out.println("Server starting on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Wait for a client to connect
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());

                // Create a new thread to handle this client
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Broadcasts a message (String or DrawableShape) to all connected clients.
     *
     * @param message The object to broadcast (either a String chat or a DrawableShape).
     */
    private static void broadcast(Object message) {
        // Iterate over a snapshot of the list
        for (ObjectOutputStream oos : clientOutputStreams) {
            try {
                oos.writeObject(message);
                oos.flush();
            } catch (IOException e) {
                // Client is likely disconnected. Remove it from the list.
                System.err.println("Failed to broadcast to a client, removing it. " + e.getMessage());
                clientOutputStreams.remove(oos);
            }
        }
    }

    /**
     * A specialized broadcast for drawing actions.
     * It adds the shape to the history *before* broadcasting it.
     *
     * @param shape The DrawableShape object to broadcast.
     */
    private static void broadcastDrawing(Object shape) {
        drawingHistory.add(shape); // Add to history for new clients
        broadcast(shape); // Broadcast to all current clients
    }

    /**
     * ClientHandler: A nested class to handle communication with a single client
     * in a separate thread.
     */
    private static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectOutputStream oos;
        private ObjectInputStream ois;
        private String userName = "A user"; // <-- ADDED (with default for safety)

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                // Setup streams
                oos = new ObjectOutputStream(socket.getOutputStream());
                ois = new ObjectInputStream(socket.getInputStream());

                // --- 1. Read the username ---
                // The first object sent by the client MUST be their username.
                this.userName = (String) ois.readObject();

                // --- 2. Add this client's output stream to the broadcast list ---
                clientOutputStreams.add(oos);

                // --- 3. Canvas Data Synchronization ---
                // Send the entire drawing history to the newly connected client
                // We send a copy to avoid concurrent modification issues
                oos.writeObject(new ArrayList<>(drawingHistory));
                oos.flush();

                // --- 4. Broadcast a connection message to all clients ---
                broadcast(new String(this.userName + " has joined.")); // <-- MODIFIED

                // Main loop: Read objects from the client
                while (true) {
                    Object message = ois.readObject();

                    if (message instanceof String) {
                        String text = (String) message;
                        if (text.equals("CLEAR_CANVAS")) {
                            drawingHistory.clear(); // Clear server history
                            broadcast(text); // Broadcast the clear command
                        } else {
                            // Broadcast chat message
                            broadcast(text);
                        }
                    } else if (message instanceof DrawableShape) {
                        // Broadcast drawing action
                        broadcastDrawing((DrawableShape) message);
                    }
                }
            } catch (EOFException | SocketException e) {
                // Client disconnected (reached end of stream or connection reset)
                System.out.println("Client disconnected: " + socket.getInetAddress());
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error handling client: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // --- Cleanup ---
                // Remove the client's output stream from the broadcast list
                if (oos != null) {
                    clientOutputStreams.remove(oos);
                }
                // Broadcast a disconnection message
                broadcast(new String(this.userName + " has left.")); // <-- MODIFIED
                // Close the socket
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}