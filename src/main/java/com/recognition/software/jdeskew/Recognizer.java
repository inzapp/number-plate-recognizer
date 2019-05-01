package com.recognition.software.jdeskew;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javafx.application.Application;
import javafx.application.Platform;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

abstract class pRes {

	public static List<String> choosedFilePathList;
}

interface EventInjector {

	public void injectView();

	public void injectEvent();

	public void clickAddBt(Stage stage);

	public void clickRemoveBt();

	public void clickStartBt();

	public void clickListMenu();
}

class ROIExtractor {
	
	public Mat getROI(Mat rawImg) {
		Mat processed = new Mat();
		Imgproc.blur(rawImg, processed, new Size(2, 2));
		Imgproc.cvtColor(processed, processed, Imgproc.COLOR_BGR2GRAY);
		Imgproc.Canny(processed, processed, 200, 300);
		ArrayList<MatOfPoint> contourList = new ArrayList<>();
		Imgproc.findContours(processed, contourList, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

		int contourListSize = contourList.size();
		Rect[] allBoundRects = new Rect[contourListSize];
		Rect[] pureBoundRects = new Rect[contourListSize];

		for (int i = 0; i < contourListSize; ++i) {
			allBoundRects[i] = new Rect();
			pureBoundRects[i] = new Rect();
		}

		MatOfPoint2f curContourPoly = new MatOfPoint2f();
		for (int i = 0; i < contourListSize; ++i) {
			Imgproc.approxPolyDP(new MatOfPoint2f(contourList.get(i).toArray()), curContourPoly, 1, true);
			allBoundRects[i] = Imgproc.boundingRect(curContourPoly);
		}

		double ratio = -1;
		int pureCount = 0;
		for (int i = 0; i < contourListSize; ++i) {
			ratio = (double) allBoundRects[i].height / allBoundRects[i].width;
			if ((0.5 <= ratio) && (ratio <= 2.5)) {
				if ((150 <= allBoundRects[i].area()) && (allBoundRects[i].area() <= 1200)) {
					pureBoundRects[pureCount] = allBoundRects[i];
					++pureCount;
				}
			}
		}

		Arrays.sort(pureBoundRects, (a, b) -> {
			return Double.compare(a.tl().x, b.tl().x);
		});

		int maxCount = 0;
		double deltaX = 0;
		double deltaY = 0;
		double gradient = 0;
		final double toleranceAreaRatio = 0.25;
		Rect maxCountRect = null;
		Rect endRectOfMaxCountRect = null;
		for (int i = 0; i < pureBoundRects.length; ++i) {
			int curCount = 0;
			double curStdArea = pureBoundRects[i].area();
			double tolerance = curStdArea * toleranceAreaRatio;
			double minArea = curStdArea - tolerance;
			double maxArea = curStdArea + tolerance;
			
			for (int j = i + 1; j < pureBoundRects.length; ++j) {
				deltaX = Math.abs(pureBoundRects[j].tl().x - pureBoundRects[i].tl().x);
				if (150 < deltaX) {
					break;
				}

				deltaY = Math.abs(pureBoundRects[j].tl().y - pureBoundRects[i].tl().y);
				if (deltaX == 0) {
					deltaX = 1;
				}

				if (deltaY == 0) {
					deltaY = 1;
				}

				gradient = deltaY / deltaX;
//				System.out.println("gradient : " + gradient);
				double curArea = pureBoundRects[j].area();
				if (gradient < 0.12 && minArea <= curArea && curArea <= maxArea) {
					++curCount;
					if (maxCount <= curCount) {
						endRectOfMaxCountRect = pureBoundRects[j];
//						System.out.println("Bang!!!");
					}
				}
			}

			if (maxCount < curCount) {
				maxCount = curCount;
				maxCountRect = pureBoundRects[i];
			}
			
//			System.out.println("\n--------------------\n");
		}

//		Imgproc.rectangle(rawImg, maxCountRect, new Scalar(0, 255, 0), 3, 8, 0);
//		Imgproc.rectangle(rawImg, endRectOfMaxCountRect, new Scalar(0, 255, 0), 3, 8, 0);

		for (int i = 0; i < pureBoundRects.length; ++i) {
//			Imgproc.drawContours(rawImg, contourList, i, new Scalar(0, 255, 255), 1, 8, new Mat(), 0, new Point());
//			Imgproc.rectangle(rawImg, pureBoundRects[i], new Scalar(0, 0, 255), 2, 8, 0);
		}
		
//		HighGui.imshow("processed", rawImg);
//
		final double widthPaddingRatio = 0.5;
		final double heightPaddingRatio = 1;
		double ltlx = maxCountRect.tl().x;
		double ltly = maxCountRect.tl().y;
//		double lbrx = maxCountRect.br().x;
		double lbry = maxCountRect.br().y;
//		double rtlx = endRectOfMaxCountRect.tl().x;
		double rtly = endRectOfMaxCountRect.tl().y;
		double rbrx = endRectOfMaxCountRect.br().x;
		double rbry = endRectOfMaxCountRect.br().y;
		double width = Math.abs(rbrx - ltlx);
		double height = Math.abs(ltly - rbry);
		double widthVariation = (width * widthPaddingRatio) / 2;
		double heightVariation = (height * heightPaddingRatio) / 2;

		Point roiStartPoint = null;
		Point roiEndPoint = null;
		if(rtly <= ltly) {

			roiStartPoint = new Point(ltlx - widthVariation, rtly - heightVariation);
			roiEndPoint = new Point(rbrx + widthVariation, lbry + heightVariation);
		} else {
			roiStartPoint = new Point(ltlx - widthVariation, ltly - heightVariation);
			roiEndPoint = new Point(rbrx + widthVariation, rbry + heightVariation);
		}
		
		Rect roiRect = new Rect(roiStartPoint, roiEndPoint);
		Mat roi = rawImg.submat(roiRect);
//		HighGui.imshow("img", roi);
//		HighGui.waitKey(0);	
		return roi;
	}
}

class OCRReader {
	
	private Mat raw;
	private Rect roi;
	
	public OCRReader (Mat raw, Rect roi) {
		this.roi = roi;
	}
	
	public String getOcrResult() {
		String result = "";
		return result;
	}
	
	public Mat getRoiMat() {
		return this.raw.submat(this.roi);
	}
}

class ValidExtensionChecker {

	private String[] validExtension = { "png", "jpg", "bmp", "jpeg", "PNG", "JPG", "BMP", "JPEG" };

	public boolean isValidExtension(String absPath) {
		int dotIdx = -1;
		for (int i = absPath.length() - 1; i >= 0; --i) {
			if (absPath.charAt(i) == '.') {
				dotIdx = i;
				break;
			}
		}

		if (dotIdx == -1) {
			return false;
		}

		String extension = absPath.substring(dotIdx + 1);
		System.out.println(extension);
		for (String curValidExtension : this.validExtension) {
			if (extension.equals(curValidExtension)) {
				return true;
			}
		}

		return false;
	}
}

abstract class View {

	public static ImageView imgView, roiView;
	public static ListView<String> imgList;
	public static Button addBt, removeBt, startBt;
	public static Label resultLb;
}

public class Recognizer extends Application implements Initializable, EventInjector {

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	private ValidExtensionChecker validExtensionChecker;

	private ROIExtractor roiExtractor;

	private Tesseract tesseract;

	private Stage stage;

	@FXML
	private ImageView imgView;
	
	@FXML
	private ImageView roiView;

	@FXML
	private ListView<String> imgList;

	@FXML
	private Button addBt, removeBt, startBt;

	@FXML
	private Label resultLb;

	@Override
	public void injectView() {
		View.imgView = this.imgView;
		View.roiView = this.roiView;
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
		View.imgList.setOnMouseClicked(event -> this.clickListMenu());
	}

	@Override
	public void clickAddBt(Stage stage) {
		FileChooser fileChooser = new FileChooser();
		List<File> fileList = fileChooser.showOpenMultipleDialog(stage);
		if (fileList == null) {
			return;
		}

		String absPath = null;
		String invalidPath = "";
		for (File curFile : fileList) {
			absPath = curFile.getAbsolutePath();
			if (!validExtensionChecker.isValidExtension(absPath)) {
				invalidPath += absPath + System.getProperty("line.separator");
				continue;
			}

			pRes.choosedFilePathList.add(absPath);
			View.imgList.getItems().add(curFile.getName());
		}

		if (!invalidPath.equals("")) {
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Error");
			alert.setHeaderText("추가된 파일 중 사진 파일이 아닌 것이 포함되어있습니다.\n해당 파일은 추가될 수 없습니다.");
			alert.setContentText(invalidPath);
			alert.showAndWait();
		}
	}

	@Override
	public void clickRemoveBt() {
		int removeIdx = View.imgList.getFocusModel().getFocusedIndex();
		if (removeIdx == -1) {
			return;
		}

		View.imgList.getItems().remove(removeIdx);
		pRes.choosedFilePathList.remove(removeIdx);
		if (pRes.choosedFilePathList.size() == 0) {
			View.imgView.setImage(null);
			View.roiView.setImage(null);
			View.resultLb.setText(null);
		}

		for (String curPath : pRes.choosedFilePathList) {
			System.out.println(curPath);
		}
	}

	@Override
	public void clickStartBt() {
		Platform.runLater(() -> {
			new Thread(() -> {
				for (int i = 0; i < pRes.choosedFilePathList.size(); ++i) {
					View.imgList.getSelectionModel().select(i);
					clickListMenu();
					Mat curRawImg = Imgcodecs.imread(pRes.choosedFilePathList.get(i));
					Mat roi = roiExtractor.getROI(curRawImg);
					
					/**
					 * 
					 * 
					 * 
					 * 수정해야함
					 * 
					 * 
					 * 
					 * 
					 * 
					 * 
					 */
					String fileName = "tmp";
					String roiPath = "tmp/" + fileName + ".jpg";
					Imgcodecs.imwrite(roiPath, roi);
					try {
						View.roiView.setImage(new Image(new FileInputStream(roiPath)));
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}
					File img = new File(roiPath);
					Platform.runLater(() -> {
						try {
							System.out.println(tesseract.doOCR(img));
							View.resultLb.setText(tesseract.doOCR(img));
						} catch (TesseractException e) {
							e.printStackTrace();
						}
					});

					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}).start();
		});
	}

	@Override
	public void clickListMenu() {
		int clickedIdx = View.imgList.getFocusModel().getFocusedIndex();
		if (clickedIdx == -1) {
			return;
		}

		String absPath = pRes.choosedFilePathList.get(clickedIdx);
//		String roiPath = "tmp/" + getRoiName(absPath);
		Image image = null;
		Image roi = null;
		try {
			image = new Image(new FileInputStream(absPath));
//			roi = new Image(new FileInputStream(roiPath));
		} catch (FileNotFoundException e) {
			System.out.println("file not found");
			return;
		}

		View.imgView.setImage(image);
//		View.roiView.setImage(roi);
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		validExtensionChecker = new ValidExtensionChecker();
		roiExtractor = new ROIExtractor();
		tesseract = new Tesseract();
		tesseract.setDatapath("src/main/resources/tessdata");
		tesseract.setLanguage("eng");
		injectView();
		injectEvent();
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		Parent fxml = FXMLLoader.load(getClass().getResource("gui.fxml"));
		primaryStage.setScene(new Scene(fxml));
		primaryStage.setTitle("Number plate recognizer by inzapp");
		primaryStage.setResizable(false);
		primaryStage.setOnCloseRequest(event -> {
			new File("tmp.jpg").delete();
			System.exit(0);
		});

		primaryStage.show();
		this.stage = primaryStage;
		pRes.choosedFilePathList = new ArrayList<>();
	}

	public static void main(String[] args) {
		launch(args);
//		ROIExtractor roi = new ROIExtractor();
//		Mat mat = Imgcodecs.imread("testdata/wut.jpg", Imgcodecs.IMREAD_ANYCOLOR);
//		roi.getROI(mat);
	}
}
