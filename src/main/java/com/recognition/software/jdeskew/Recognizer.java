package com.recognition.software.jdeskew;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.opencv.core.Core;

import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

class pRes {
	
	public static List<String> choosedFilePathList;
}

abstract class View {

	public static ImageView imgView;
	public static ListView<String> imgList;
	public static Button addBt, removeBt, startBt;
	public static Label resultLb;
}

interface EventInjector {

	public void injectView();

	public void injectEvent();

	public void clickAddBt(Stage stage);

	public void clickRemoveBt();

	public void clickStartBt();
}

class ROIExtractor {
	
//	public boolean is
}

public class Recognizer extends Application implements Initializable, EventInjector {

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}
	
	private String[] validExtension = {
			"png", "jpg", "bmp", "jpeg"
	};

	private Stage stage;

	@FXML
	private ImageView imgView;

	@FXML
	private ListView<String> imgList;

	@FXML
	private Button addBt, removeBt, startBt;

	@FXML
	private Label resultLb;

	@Override
	public void injectView() {
		View.imgView = this.imgView;
		View.imgList = this.imgList;
		View.addBt = this.addBt;
		View.removeBt = this.removeBt;
		View.startBt = this.startBt;
		View.resultLb = this.resultLb;
	}

	@Override
	public void injectEvent() {
		View.addBt.setOnMouseClicked(event -> this.clickAddBt(stage));
		View.removeBt.setOnMouseClicked(event -> this.clickRemoveBt());
		View.startBt.setOnMouseClicked(event -> this.clickStartBt());
	}

	@Override
	public void clickAddBt(Stage stage) {
		FileChooser fileChooser = new FileChooser();
		List<File> fileList = fileChooser.showOpenMultipleDialog(stage);
		if(fileList == null) {
			return;
		}
		
		String absPath = null;
		String invalidPath = "";
		for(File curFile : fileList) {
			absPath = curFile.getAbsolutePath();
			if(!isValidExtension(absPath)) {
				invalidPath += absPath + System.getProperty("line.separator");
				continue;
			}
			
			pRes.choosedFilePathList.add(absPath);
			View.imgList.getItems().add(curFile.getName());
		}
		
		if(!invalidPath.equals("")) {
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Error");
			alert.setHeaderText("추가하신 파일 중 사진 파일이 아닌 것이 포함되어있습니다.\n해당 파일은 추가될 수 없습니다.");
			alert.setContentText(invalidPath);
			alert.showAndWait();
		}
	}
	
	private boolean isValidExtension(String absPath) {
		int dotIdx = -1;
		for(int i=absPath.length() - 1; i>=0; --i) {
			if(absPath.charAt(i) == '.') {
				dotIdx = i;
				break;
			}
		}
		
		if(dotIdx == -1) {
			return false;
		}
		
		String extension = absPath.substring(dotIdx + 1);
		System.out.println(extension);
		for(String curValidExtension : this.validExtension) {
			if(extension.equals(curValidExtension)) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public void clickRemoveBt() {
		int removeIdx = View.imgList.getFocusModel().getFocusedIndex();
		if(removeIdx == -1) {
			return;
		}
		
		View.imgList.getItems().remove(removeIdx);
		pRes.choosedFilePathList.remove(removeIdx);
		
		for(String curPath : pRes.choosedFilePathList) {
			System.out.println(curPath);
		}
	}

	@Override
	public void clickStartBt() {

	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		injectView();
		injectEvent();
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		Parent fxml = FXMLLoader.load(getClass().getResource("gui.fxml"));
		primaryStage.setScene(new Scene(fxml));
		primaryStage.setTitle("Number plate recognizer by inzapp");
		primaryStage.setResizable(false);
		primaryStage.setOnCloseRequest(event -> System.exit(0));
		primaryStage.show();

		pRes.choosedFilePathList = new ArrayList<>();
		this.stage = primaryStage;
	}

	public static void main(String[] args) {
		launch(args);

		// File img = new File("1.bmp");
		// Tesseract ts = new Tesseract();
		// ts.setDatapath("src/main/resources/tessdata");
		// try {
		// System.out.println(ts.doOCR(img));
		// } catch (TesseractException e) {
		// e.printStackTrace();
		// }

		// Mat mat = new Mat();
	}
}
