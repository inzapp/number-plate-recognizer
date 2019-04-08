package com.recognizer;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Recognizer extends Application implements Initializable {

    static {
        System.load("/inz/lib/libopencv_java401.so");
    }

    public static void main(String[] args) {
        launch(args);

        // File img = new File("1.bmp");
        // Tesseract ts = new Tesseract();
        // ts.setDatapath("src/main/resources/tessdata");
        // try {
        //     System.out.println(ts.doOCR(img));
        // } catch (TesseractException e) {
        //     e.printStackTrace();
        // }

        // Mat mat = new Mat();
    }

    @Override
    public void initialize(URL arg0, ResourceBundle arg1) {
    }

    @Override
    public void start(Stage stage) throws Exception {
        Parent fxml = FXMLLoader.load(getClass().getResource("test.fxml"));
        Scene scene = new Scene(fxml);
        
        stage.setScene(scene);
        stage.setTitle("Test title");
        stage.setResizable(false);
        stage.setOnCloseRequest(event -> System.exit(0));
        stage.show();
    }
}