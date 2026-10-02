import ui.LeafVisionApp;
import javafx.application.Application;

/**
 * Program launcher.
 */

public class Launcher {
    public static void main(String[] args) {
        // Entry point of the program.
        // JavaFX applications normally start from an Application subclass,
        // so this launcher simply delegates startup to LeafVisionApp.
        Application.launch(LeafVisionApp.class, args);
    }
}
