package com.recognition.software.jdeskew;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import org.opencv.core.Core;

import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

class View {

	private ImageView imgView;
	private ListView<String> imgList;
	private Button addBt, removeBt, startBt;
	private Label resultLb;

	static class Singleton {
		static View INSTANCE = new View();
	}

	public static View getInstance() {
		return Singleton.INSTANCE;
	}

	public ImageView getImgView() {
		return imgView;
	}

	public void setImgView(ImageView imgView) {
		this.imgView = imgView;
	}

	public ListView<String> getImgList() {
		return imgList;
	}

	public void setImgList(ListView<String> imgList) {
		this.imgList = imgList;
	}

	public Button getAddBt() {
		return addBt;
	}

	public void setAddBt(Button addBt) {
		this.addBt = addBt;
	}

	public Button getRemoveBt() {
		return removeBt;
	}

	public void setRemoveBt(Button removeBt) {
		this.removeBt = removeBt;
	}

	public Button getStartBt() {
		return startBt;
	}

	public void setStartBt(Button startBt) {
		this.startBt = startBt;
	}

	public Label getResultLb() {
		return resultLb;
	}

	public void setResultLb(Label resultLb) {
		this.resultLb = resultLb;
	}
}

interface EventInjector {

	public void injectView();

	public void injectEvent();

	public void clickAddBt(Stage stage);

	public void clickRemoveBt();

	public void clickStartBt();
}

public class Recognizer extends Application implements Initializable, EventInjector {

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	private Stage stage;
	private View view;

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
		view.setImgView(imgView);
		view.setImgList(imgList);
		view.setAddBt(addBt);
		view.setRemoveBt(removeBt);
		view.setStartBt(startBt);
		view.setResultLb(resultLb);
	}

	@Override
	public void injectEvent() {
		view.getAddBt().setOnMouseClicked(event -> this.clickAddBt(stage));
		view.getRemoveBt().setOnMouseClicked(event -> this.clickRemoveBt());
		view.getStartBt().setOnMouseClicked(event -> this.clickStartBt());
	}

	@Override
	public void clickAddBt(Stage stage) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("이미지를 선택하세요");
		List<File> fileList = fileChooser.showOpenMultipleDialog(stage);
		for(File curFile : fileList) {
			System.out.println(curFile.getAbsolutePath());
		}
	}

	@Override
	public void clickRemoveBt() {

	}

	@Override
	public void clickStartBt() {

	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		view = View.getInstance();
		injectView();
		injectEvent();
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		Parent fxml = FXMLLoader.load(getClass().getResource("gui.fxml"));
		primaryStage.setScene(new Scene(fxml));
		primaryStage.setTitle("Number plate recognizer by inzapp");
		primaryStage.setResizable(false);
		primaryStage.show();

		this.stage = primaryStage;
		this.view = View.getInstance();
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
