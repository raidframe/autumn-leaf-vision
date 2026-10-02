package ui;

import models.AnalysisResult;
import models.ClusterBox;
import image.ImageAnalyzer;
import algorithms.RoutePlanner;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main JavaFX application.
 *
 * This class acts as the central controller of the program:
 *
 * - builds the window and all controls
 * - keeps track of the current image and latest analysis result
 * - delegates image processing to ImageAnalyzer
 * - delegates route construction to RoutePlanner
 * - redraws the canvases whenever the state changes
 *
 * In other words, the UI talks to this class, and this class coordinates
 * the rest of the application.
 */
public class LeafVisionApp extends Application {
    private static final double CANVAS_W = 620;
    private static final double CANVAS_H = 520;

    // Core services used by the UI.
    private final ImageAnalyzer analyzer = new ImageAnalyzer();
    private final RoutePlanner routePlanner = new RoutePlanner();

    // User-selected reference colours that describe "leaf-like" pixels.
    private final List<Color> sampledColours = new ArrayList<>();

    // Main visual widgets shown in the window.
    private final Canvas originalCanvas = new Canvas(CANVAS_W, CANVAS_H);
    private final Canvas binaryCanvas = new Canvas(CANVAS_W, CANVAS_H);
    private final Label statusLabel = new Label("Load an image to begin.");
    private final Label clusterCountLabel = new Label("Clusters: 0");
    private final Label estimatedLeavesLabel = new Label("Estimated leaves: 0");
    private final Label leafPixelsLabel = new Label("Leaf pixels: 0");
    private final Label selectedClusterLabel = new Label("Selected cluster: none");
    private final FlowPane colourPane = new FlowPane(6, 6);

    // Analysis controls. These values directly affect segmentation behaviour.
    private final CheckBox showNumbersBox = new CheckBox("Show numbers");
    private final Slider hueToleranceSlider = new Slider(5, 60, 20);
    private final Slider saturationSlider = new Slider(0.1, 0.9, 0.35);
    private final Slider brightnessSlider = new Slider(0.1, 0.9, 0.30);
    private final Spinner<Integer> targetWidthSpinner = new Spinner<>(200, 900, 420, 20);
    private final Spinner<Integer> minPixelsSpinner = new Spinner<>(1, 1000, 25, 5);
    private final Spinner<Integer> maxPixelsSpinner = new Spinner<>(50, 50000, 5000, 50);

    // Current application state.
    private Image currentImage;
    private AnalysisResult currentResult;
    private ClusterBox selectedCluster;
    private Timeline animation;

    @Override
    public void start(Stage stage) {
        // Build the main JavaFX layout:
        // top controls, two image canvases in the centre, and a status bar at the bottom.
        BorderPane root = new BorderPane();
        root.setTop(buildTopBar(stage));
        root.setCenter(buildCentre());
        root.setBottom(buildStatusBar());

        // Before processing, a click samples leaf colour. After processing, it selects a cluster.
        originalCanvas.setOnMouseClicked(event -> {
            if (currentImage == null) return;
            if (currentResult == null) {
                sampleColourFromOriginal(event.getX(), event.getY());
            } else {
                selectClusterFromCanvas(event.getX(), event.getY(), originalCanvas);
            }
        });

        binaryCanvas.setOnMouseClicked(event -> {
            if (currentResult == null) return;
            selectClusterFromCanvas(event.getX(), event.getY(), binaryCanvas);
        });

        Scene scene = new Scene(root, 1320, 760);
        stage.setTitle("Autumn Leaf Vision");
        stage.setScene(scene);
        stage.show();

        // Draw empty placeholders before an image is loaded, so both canvases
        // have a clear initial state.
        redrawOriginal(null, List.of());
        redrawBinary(null, null);
    }

    private VBox buildTopBar(Stage stage) {
        // Top bar contains user actions and analysis parameters.
        // The sliders affect colour filtering, while the spinners affect
        // image scale and cluster-size filtering.
        Button loadButton = new Button("Load image");
        loadButton.setOnAction(e -> loadImage(stage));

        Button processButton = new Button("Process");
        processButton.setOnAction(e -> processImage());

        Button clearSamplesButton = new Button("Clear samples");
        clearSamplesButton.setOnAction(e -> {
            sampledColours.clear();
            refreshColourChips();
            statusLabel.setText("Colour samples cleared.");
        });

        Button animateButton = new Button("Animate route");
        animateButton.setOnAction(e -> animateRoute());

        ToolBar toolBar = new ToolBar(
                loadButton,
                processButton,
                animateButton,
                new Separator(),
                clearSamplesButton,
                showNumbersBox,
                new Separator(),
                new Label("Picked colours:"),
                colourPane
        );

        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(8);
        controls.setPadding(new Insets(10));
        controls.addRow(0, new Label("Hue tolerance"), hueToleranceSlider,
                new Label("Target width"), targetWidthSpinner,
                new Label("Min pixels"), minPixelsSpinner);
        controls.addRow(1, new Label("Min saturation"), saturationSlider,
                new Label("Min brightness"), brightnessSlider,
                new Label("Max pixels"), maxPixelsSpinner);

        for (Slider slider : List.of(hueToleranceSlider, saturationSlider, brightnessSlider)) {
            slider.setShowTickLabels(true);
            slider.setShowTickMarks(true);
        }

        VBox statsPanel = buildStatsPanel();

        HBox controlsRow = new HBox(24, controls, statsPanel);
        controlsRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(controls, Priority.ALWAYS);

        VBox top = new VBox(toolBar, controlsRow);
        top.setSpacing(4);
        return top;
    }

    private VBox buildStatsPanel() {
        Label title = new Label("Overall image stats");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        clusterCountLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        estimatedLeavesLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        leafPixelsLabel.setStyle("-fx-font-size: 13px;");
        selectedClusterLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        VBox statsPanel = new VBox(8, title, clusterCountLabel, estimatedLeavesLabel, leafPixelsLabel, selectedClusterLabel);
        statsPanel.setMinWidth(300);
        statsPanel.setPadding(new Insets(12));
        statsPanel.setStyle("-fx-background-color: #fafafa; -fx-border-color: #d0d0d0; -fx-border-radius: 8; -fx-background-radius: 8;");
        return statsPanel;
    }

    private Node buildCentre() {
        // The centre area shows the original image beside the generated binary mask.
        VBox left = canvasPane("Original image", originalCanvas,
                "Click the image to sample leaf colours.");
        VBox right = canvasPane("Black / white mask", binaryCanvas,
                "White pixels are treated as leaf pixels.");

        HBox centre = new HBox(12, left, right);
        centre.setPadding(new Insets(12));
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        return centre;
    }

    private VBox canvasPane(String title, Canvas canvas, String helpText) {
        // Reusable panel for one canvas plus its title and short instruction text.
        Label label = new Label(title);
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        StackPane holder = new StackPane(canvas);
        holder.setPrefSize(CANVAS_W, CANVAS_H);
        holder.setStyle("-fx-background-color: #202020; -fx-border-color: #404040;");

        Label help = new Label(helpText);
        help.setTextFill(Color.DIMGRAY);

        VBox box = new VBox(8, label, holder, help);
        box.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(holder, Priority.ALWAYS);
        return box;
    }

    private HBox buildStatusBar() {
        // Status bar displays only the current action result.
        HBox bar = new HBox(20, statusLabel);
        bar.setPadding(new Insets(10));
        bar.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #d0d0d0  transparent transparent transparent;");
        return bar;
    }

    private void loadImage(Stage stage) {
        // Ask the user to choose an image file and reset all previous analysis results.
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a leaf image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        currentImage = new Image(file.toURI().toString());
        currentResult = null;
        selectedCluster = null;
        stopAnimation();
        redrawOriginal(null, List.of());
        redrawBinary(null, null);
        statusLabel.setText("Loaded: " + file.getName());
        clusterCountLabel.setText("Clusters: 0");
        estimatedLeavesLabel.setText("Estimated leaves: 0");
        leafPixelsLabel.setText("Leaf pixels: 0");
        selectedClusterLabel.setText("Selected cluster: none");
    }

    private void processImage() {
        if (currentImage == null) {
            statusLabel.setText("Load an image first.");
            return;
        }

        // Run image segmentation and connected-component detection with the current controls.
        // The result contains a binary mask and a sorted list of detected clusters.
        currentResult = analyzer.analyse(
                currentImage,
                sampledColours,
                targetWidthSpinner.getValue(),
                hueToleranceSlider.getValue(),
                saturationSlider.getValue(),
                brightnessSlider.getValue(),
                minPixelsSpinner.getValue(),
                maxPixelsSpinner.getValue()
        );

        selectedCluster = currentResult.clusters().isEmpty() ? null : currentResult.clusters().getFirst();
        redrawOriginal(selectedCluster, List.of());
        redrawBinary(currentResult, selectedCluster);
        statusLabel.setText("Processing complete. Overall image estimate updated.");
        clusterCountLabel.setText("Clusters: " + currentResult.clusters().size());
        estimatedLeavesLabel.setText("Estimated leaves: " + currentResult.estimatedLeafCount());
        leafPixelsLabel.setText("Leaf pixels: " + currentResult.totalLeafPixels());
        updateSelectedClusterLabel();
    }

    private void sampleColourFromOriginal(double canvasX, double canvasY) {
        if (currentImage == null) return;
        // Convert the click position on the canvas back to a pixel in the original image.
        // The image is stretched to fit the canvas, so the coordinates must be scaled.
        int px = (int) Math.max(0, Math.min(currentImage.getWidth() - 1, canvasX * currentImage.getWidth() / originalCanvas.getWidth()));
        int py = (int) Math.max(0, Math.min(currentImage.getHeight() - 1, canvasY * currentImage.getHeight() / originalCanvas.getHeight()));
        Color color = currentImage.getPixelReader().getColor(px, py);
        sampledColours.add(color);
        refreshColourChips();
        statusLabel.setText(String.format("Sample added (hue %.1f).", color.getHue()));
    }

    private void refreshColourChips() {
        // Rebuild the small colour swatches that show which leaf colours were sampled.
        colourPane.getChildren().clear();
        for (Color color : sampledColours) {
            Region chip = new Region();
            chip.setPrefSize(18, 18);
            chip.setStyle(String.format("-fx-background-color: rgb(%d,%d,%d); -fx-border-color: black;",
                    roundToInt(color.getRed() * 255),
                    roundToInt(color.getGreen() * 255),
                    roundToInt(color.getBlue() * 255)));
            colourPane.getChildren().add(chip);
        }
    }

    private void selectClusterFromCanvas(double canvasX, double canvasY, Canvas canvas) {
        int maskX = (int) Math.max(0, Math.min(currentResult.mask().getWidth() - 1,
                canvasX * currentResult.mask().getWidth() / canvas.getWidth()));
        int maskY = (int) Math.max(0, Math.min(currentResult.mask().getHeight() - 1,
                canvasY * currentResult.mask().getHeight() / canvas.getHeight()));

        ClusterBox clicked = findClusterAt(maskX, maskY);
        if (clicked == null) {
            statusLabel.setText("No cluster at clicked position.");
            return;
        }

        stopAnimation();
        selectedCluster = clicked;
        updateSelectedClusterLabel();
        redrawOriginal(selectedCluster, List.of());
        redrawBinary(currentResult, selectedCluster);
        statusLabel.setText("Cluster #" + selectedCluster.getRank()
                + " selected. Size: " + selectedCluster.getPixels() + " pixels.");
    }

    private ClusterBox findClusterAt(int maskX, int maskY) {
        if (!currentResult.mask().isWhite(maskX, maskY)) {
            return null;
        }

        ClusterBox best = null;
        int bestArea = Integer.MAX_VALUE;
        for (ClusterBox box : currentResult.clusters()) {
            if (box.contains(maskX, maskY)) {
                int area = box.width() * box.height();
                if (area < bestArea) {
                    best = box;
                    bestArea = area;
                }
            }
        }
        return best;
    }

    private void updateSelectedClusterLabel() {
        if (selectedCluster == null) {
            selectedClusterLabel.setText("Selected cluster: none");
            return;
        }

        selectedClusterLabel.setText("Selected #" + selectedCluster.getRank()
                + " size: " + selectedCluster.getPixels() + " px");
    }

    private void redrawOriginal(ClusterBox highlight, List<ClusterBox> route) {
        // Redraw the original canvas from scratch.
        // This includes the loaded image, cluster rectangles, optional rank numbers,
        // and optionally the route between cluster centres.
        GraphicsContext gc = originalCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#1f1f1f"));
        gc.fillRect(0, 0, originalCanvas.getWidth(), originalCanvas.getHeight());

        if (currentImage == null) return;
        gc.drawImage(currentImage, 0, 0, originalCanvas.getWidth(), originalCanvas.getHeight());

        if (currentResult == null) return;

        double scaleX = originalCanvas.getWidth() / currentResult.mask().getWidth();
        double scaleY = originalCanvas.getHeight() / currentResult.mask().getHeight();

        // Draw one rectangle for every detected cluster.
        // The selected/highlighted cluster is drawn in gold, others in blue.
        gc.setLineWidth(2);
        for (ClusterBox box : currentResult.clusters()) {
            gc.setStroke(box == highlight ? Color.GOLD : Color.DODGERBLUE);
            gc.strokeRect(box.getMinX() * scaleX, box.getMinY() * scaleY, box.width() * scaleX, box.height() * scaleY);
            if (showNumbersBox.isSelected()) {
                gc.setFill(Color.BLUEVIOLET);
                gc.fillText(String.valueOf(box.getRank()), box.getMinX() * scaleX + 4, box.getMinY() * scaleY + 14);
            }
        }

        if (!route.isEmpty()) {
            // Connect cluster centres to show the planned traversal route.
            // The route is drawn after the boxes so it is visible on top of the image.
            gc.setStroke(Color.ORANGE);
            gc.setLineWidth(3);
            for (int i = 0; i < route.size() - 1; i++) {
                ClusterBox a = route.get(i);
                ClusterBox b = route.get(i + 1);
                gc.strokeLine(a.centreX() * scaleX, a.centreY() * scaleY, b.centreX() * scaleX, b.centreY() * scaleY);
            }
        }
    }

    private void redrawBinary(AnalysisResult result, ClusterBox highlight) {
        // Redraw the second canvas as a black/white segmentation preview.
        // White means "detected leaf pixel"; black means "background".
        // Pixels inside the highlighted cluster are coloured green.
        GraphicsContext gc = binaryCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#1f1f1f"));
        gc.fillRect(0, 0, binaryCanvas.getWidth(), binaryCanvas.getHeight());
        if (result == null) return;

        WritableImage img = new WritableImage(result.mask().getWidth(), result.mask().getHeight());
        PixelWriter writer = img.getPixelWriter();
        // Rebuild the visual mask pixel by pixel for display on the second canvas.
        // WritableImage is used because JavaFX Canvas cannot directly display a boolean mask.
        for (int y = 0; y < result.mask().getHeight(); y++) {
            for (int x = 0; x < result.mask().getWidth(); x++) {
                Color colour = result.mask().isWhite(x, y) ? Color.WHITE : Color.BLACK;
                if (highlight != null && highlight.contains(x, y) && result.mask().isWhite(x, y)) {
                    colour = Color.LIMEGREEN;
                }
                writer.setColor(x, y, colour);
            }
        }
        gc.drawImage(img, 0, 0, binaryCanvas.getWidth(), binaryCanvas.getHeight());
    }

    private void animateRoute() {
        // Animate the greedy route produced by RoutePlanner.
        // Each key frame reveals one more visited cluster and updates both canvases.
        if (currentResult == null || currentResult.clusters().isEmpty()) {
            statusLabel.setText("Process an image first.");
            return;
        }

        stopAnimation();
        List<ClusterBox> route = routePlanner.buildRoute(currentResult.clusters(), selectedCluster);
        if (route.size() < 2) {
            redrawOriginal(selectedCluster, List.of());
            return;
        }

        // Reveal the route gradually so the user can see the visiting order.
        // Total animation time is about five seconds, split across all route steps.
        animation = new Timeline();
        Duration step = Duration.millis(5000.0 / Math.max(1, route.size() - 1));

        for (int i = 0; i < route.size(); i++) {
            final int upto = i;
            animation.getKeyFrames().add(new KeyFrame(step.multiply(i), e -> {
                List<ClusterBox> partial = route.subList(0, Math.max(1, upto + 1));
                ClusterBox current = route.get(Math.min(upto, route.size() - 1));
                redrawOriginal(current, partial);
                redrawBinary(currentResult, current);
                statusLabel.setText("Route step " + (upto + 1) + " of " + route.size());
            }));
        }

        animation.setOnFinished(e -> statusLabel.setText("Route animation finished."));
        animation.playFromStart();
    }

    private void stopAnimation() {
        // Stop an existing animation before loading a new image or starting a new route.
        if (animation != null) {
            animation.stop();
            animation = null;
        }
    }

    private int roundToInt(double value) {
        if (value >= 0) {
            return (int) (value + 0.5);
        }
        return (int) (value - 0.5);
    }
}
