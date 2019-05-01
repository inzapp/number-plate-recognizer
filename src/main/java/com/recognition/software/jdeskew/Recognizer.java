package com.recognition.software.jdeskew;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
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

	public Mat[] getROI(Mat raw) {
		ArrayList<Rect> pureBoundRectList = getPureBoundRectList(raw);
		Mat view = raw.clone();
		for (int i = 0; i < pureBoundRectList.size(); ++i) {
			Imgproc.rectangle(view, pureBoundRectList.get(i), new Scalar(0, 255, 0), 1, 8, 0);
		}

		Imgproc.cvtColor(raw, raw, Imgproc.COLOR_BGR2GRAY);
		Rect[] cutPointRects = getCutPointRects(pureBoundRectList);
		Rect roiRect = getRoiRect(cutPointRects);
		Mat roi = raw.submat(roiRect);
		view = view.submat(roiRect);
		return new Mat[] { roi, view };
	}

	protected ArrayList<Rect> getPureBoundRectList(Mat raw) {
		Mat processed = new Mat();
		Imgproc.blur(raw, processed, new Size(2, 2));
		Imgproc.cvtColor(processed, processed, Imgproc.COLOR_BGR2GRAY);
		Imgproc.Canny(processed, processed, 200, 300);
		ArrayList<MatOfPoint> contourList = new ArrayList<>();
		Imgproc.findContours(processed, contourList, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

		int contourListSize = contourList.size();
		Rect[] allBoundRects = new Rect[contourListSize];
		for (int i = 0; i < contourListSize; ++i) {
			allBoundRects[i] = new Rect();
		}

		ArrayList<Rect> pureBoundRectList = new ArrayList<>();
		MatOfPoint2f curContourPoly = new MatOfPoint2f();
		for (int i = 0; i < contourListSize; ++i) {
			Imgproc.approxPolyDP(new MatOfPoint2f(contourList.get(i).toArray()), curContourPoly, 1, true);
			allBoundRects[i] = Imgproc.boundingRect(curContourPoly);
		}

		double ratio = -1;
		for (int i = 0; i < contourListSize; ++i) {
			ratio = (double) allBoundRects[i].height / allBoundRects[i].width;
			if ((0.5 <= ratio) && (ratio <= 2.5)) {
				if ((150 <= allBoundRects[i].area()) && (allBoundRects[i].area() <= 1200)) {
					pureBoundRectList.add(allBoundRects[i]);
				}
			}
		}

		return pureBoundRectList;
	}

	private Rect[] getCutPointRects(ArrayList<Rect> pureBoundRectList) {
		pureBoundRectList.sort((prev, next) -> {
			return Double.compare(prev.tl().x, next.tl().x);
		});

		int maxCount = 0;
		double deltaX = 0;
		double deltaY = 0;
		double gradient = 0;
		final double toleranceAreaRatio = 0.25;
		Rect maxCountRect = null;
		Rect endRectOfMaxCountRect = null;
		for (int i = 0; i < pureBoundRectList.size(); ++i) {
			int curCount = 0;
			double curStdArea = pureBoundRectList.get(i).area();
			double tolerance = curStdArea * toleranceAreaRatio;
			double minArea = curStdArea - tolerance;
			double maxArea = curStdArea + tolerance;

			for (int j = i + 1; j < pureBoundRectList.size(); ++j) {
				deltaX = Math.abs(pureBoundRectList.get(j).tl().x - pureBoundRectList.get(i).tl().x);
				if (150 < deltaX) {
					break;
				}

				deltaY = Math.abs(pureBoundRectList.get(j).tl().y - pureBoundRectList.get(i).tl().y);
				if (deltaX == 0) {
					deltaX = 1;
				}

				if (deltaY == 0) {
					deltaY = 1;
				}

				gradient = deltaY / deltaX;
				double curArea = pureBoundRectList.get(j).area();
				if (gradient < 0.12 && minArea <= curArea && curArea <= maxArea) {
					++curCount;
					if (maxCount <= curCount) {
						endRectOfMaxCountRect = pureBoundRectList.get(j);
					}
				}
			}

			if (maxCount < curCount) {
				maxCount = curCount;
				maxCountRect = pureBoundRectList.get(i);
			}
		}

		return new Rect[] { maxCountRect, endRectOfMaxCountRect };
	}

	private Rect getRoiRect(Rect[] cutPointRects) {
		Rect startRect = cutPointRects[0];
		Rect endRect = cutPointRects[1];
		double ltlx = startRect.tl().x;
		double ltly = startRect.tl().y;
		double lbry = startRect.br().y;
		double rtly = endRect.tl().y;
		double rbrx = endRect.br().x;
		double rbry = endRect.br().y;
		double widthVariation = 1;
		double heightVariation = 1;

		Point roiStartPoint = null;
		Point roiEndPoint = null;
		if (rtly <= ltly) {
			roiStartPoint = new Point(ltlx - widthVariation, rtly - heightVariation);
			roiEndPoint = new Point(rbrx + widthVariation, lbry + heightVariation);
		} else {
			roiStartPoint = new Point(ltlx - widthVariation, ltly - heightVariation);
			roiEndPoint = new Point(rbrx + widthVariation, rbry + heightVariation);
		}

		return new Rect(roiStartPoint, roiEndPoint);
	}
}

class OCRReader extends ROIExtractor {

	private Tesseract tesseract;

	public OCRReader() {
		tesseract = new Tesseract();
		tesseract.setDatapath("src/main/resources/tessdata");
		tesseract.setLanguage("eng");
	}

	public String getOcrResult(Mat roi) {
		Imgcodecs.imwrite("tmp.jpg", roi);
		String tmpOcrResult = "";
		try {
			tmpOcrResult = tesseract.doOCR(new File("tmp.jpg"));
		} catch (TesseractException e) {
			e.printStackTrace();
		}

		String ocrResult = "";
		char[] iso = tmpOcrResult.toCharArray();
		for (char c : iso) {
			if (isPlateResult(c)) {
				ocrResult += c;
			}
		}

		return ocrResult;
	}

	private boolean isPlateResult(char c) {
		return ('0' <= c && c <= '9') || ('A' <= c && c <= 'Z');
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

	private OCRReader ocrReader;

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
					Mat curRaw = Imgcodecs.imread(pRes.choosedFilePathList.get(i));
					Mat[] roi = roiExtractor.getROI(curRaw);
					String viewName = "tmpView";
					String viewPath = "tmp/" + viewName + ".jpg";
					Imgcodecs.imwrite(viewPath, roi[1]);
					try {
						Image image = new Image(new FileInputStream(viewPath));
						View.roiView.setImage(image);
						setImageCenter(View.roiView, image);
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}

					Platform.runLater(() -> {
						String ocrResult = ocrReader.getOcrResult(roi[0]);
						System.out.println(ocrResult);
						View.resultLb.setText(ocrResult);
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
		Image image = null;
		try {
			image = new Image(new FileInputStream(absPath));
			View.imgList.scrollTo(clickedIdx);
		} catch (FileNotFoundException e) {
			System.out.println("file not found");
			return;
		}

		View.imgView.setImage(image);
		setImageCenter(View.imgView, image);
	}

	private void setImageCenter(ImageView imageView, Image draggedImage) {
		double ratioX = imageView.getFitWidth() / draggedImage.getWidth();
		double ratioY = imageView.getFitHeight() / draggedImage.getHeight();

		double reducCoeff;
		if (ratioX >= ratioY) {
			reducCoeff = ratioY;
		} else {
			reducCoeff = ratioX;
		}

		double w = draggedImage.getWidth() * reducCoeff;
		double h = draggedImage.getHeight() * reducCoeff;

		imageView.setX((imageView.getFitWidth() - w) / 2);
		imageView.setY((imageView.getFitHeight() - h) / 2);
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		validExtensionChecker = new ValidExtensionChecker();
		roiExtractor = new ROIExtractor();
		ocrReader = new OCRReader();
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
	}
}
