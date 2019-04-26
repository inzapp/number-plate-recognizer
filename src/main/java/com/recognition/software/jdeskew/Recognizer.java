package com.recognition.software.jdeskew;

import java.net.URL;
import java.util.ResourceBundle;

import org.opencv.core.Core;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Recognizer extends Application implements Initializable {

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
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
	public void initialize(URL location, ResourceBundle resources) {
		
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		Parent fxml = FXMLLoader.load(getClass().getResource("gui.fxml"));
		primaryStage.setScene(new Scene(fxml));
		primaryStage.setResizable(false);
		primaryStage.show();
	}
}
