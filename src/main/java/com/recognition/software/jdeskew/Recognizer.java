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

/**
 * recognizer 내부에 사용될 공유자원
 * @author inzapp
 *
 */
abstract class pRes {

	// 사용자가 선택한 파일들의 경로가 저장되는 리스트
	public static List<String> choosedFilePathList;
}

/**
 * FXML 이벤트 주입용 인터페이스
 * @author inzapp
 *
 */
interface EventInjector {

	// 뷰 주입
	public void injectView();

	// 이벤트 주입
	public void injectEvent();

	// 사진추가버튼 이벤트
	public void clickAddBt(Stage stage);

	// 제거버튼 이벤트
	public void clickRemoveBt();

	// 검출버튼 이벤트
	public void clickStartBt();

	// 리스트 한 개의 아이템 클릭 이벤트
	public void clickListMenu();
}

/**
 * 사진에서 번호판영역을 감지에 추출하는 클래스
 * @author inzapp
 *
 */
class ROIExtractor {

	/**
	 * ROI를 리턴받는다
	 * [0] : 실제  ROI 로서 OCR판독에 사용된다
	 * [1] : FX View에 표시될 이미지로서 contour rect가 표시되어있다
	 * @param raw
	 * 전처리 과정을 거치지 않은 이미지
	 * @return
	 */
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

	/**
	 * ROI추출을 위해 모든 contour들을 검출하고 그 중 명시된 번호판 조건에 부합하는 contour들을 추려내 리스트로 반환한다
	 * ROI를 추출하기 위한 1차적인 정제단계이다
	 * @param raw
	 * 전처리 과정을 거치지 않은 이미지
	 * @return
	 */
	private ArrayList<Rect> getPureBoundRectList(Mat raw) {
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

	/**
	 * 번호판 영역을 잘라내기 위한 번호판 첫째 자리와 마지막 자리의 좌표를 리턴한다
	 * getPureBoundRectList에서 1차적으로 정제된 contour rect들 중
	 * 실제 번호판 영역을 검출하기 위해 다음과 같은 과정을 따른다
	 * 1. pureBoundRectList의 x좌표를 기준으로 정렬한다
	 * 2. 모든 contour rect를 기준으로 우측에 몇 개의 contour rect가 연속으로 있는지 갯수를 세아린다
	 * 2-1. 이 때 우측 contour rect간의 거리과 기울기에 따라 더이상 세아릴지를 판단한다
	 * 3. 가장 많은 카운트를 가진 contour rect가 번호판의 첫째 자리이며 해당 카운트의 마지막번째가 번호판의 마지막 자리이다
	 * @param pureBoundRectList
	 * getPureBoundList메소드에서 리턴받는다
	 * @return
	 */
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

	/**
	 * 인자로 받은 Rect[]를 기준으로 번호판 영역을 잘라낼 구역을 리턴한다
	 * 번호판 영역이 대각선 모양으로 배치되어있는 경우(좌상우하, 좌하우상)의 경우를 모두 고려해 잘라낼 구역을 판단한다
	 * @param cutPointRects
	 * getCutPointRects메소드에서 리턴받는다
	 * [0] : 번호판 첫째 자리 좌표
	 * [1] : 번호판 마지막 자리 좌표
	 * @return
	 */
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

/**
 * Tesseract를 이용해 OCR결과를 리턴한다
 * @author inzapp
 *
 */
class OCRReader {

	private Tesseract tesseract;

	public OCRReader() {
		tesseract = new Tesseract();
		tesseract.setDatapath("src/main/resources/tessdata");
		tesseract.setLanguage("eng");
	}

	/**
	 * ROI를 인자로 받아 OCR 결과를 리턴한다
	 * @param roi
	 * ROIExtractor.getROI()[0]
	 * @return
	 */
	public String getOcrResult(Mat roi) {
		Imgcodecs.imwrite("tmp.jpg", roi);
		String tmpOcrResult = "";
		try {
			tmpOcrResult = tesseract.doOCR(new File("tmp.jpg"));
		} catch (TesseractException e) {
			e.printStackTrace();
		}

		// 영문 번호판의 경우 소문자가 없어 소문자의 경우 오판으로 인식해 제거한다
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

/**
 * 사진파일 추가시 확장자에 대한 유효성 검사를 하는 클래스
 * @author inzapp
 *
 */
class ValidExtensionChecker {

	// 사용 가능한 확장자
	private String[] validExtension = { "png", "jpg", "bmp", "jpeg", "PNG", "JPG", "BMP", "JPEG" };

	/**
	 * 파일의 절대경로를 인자로 받아 해당 확장자가 유효한지 리턴한다
	 * @param absPath
	 * File.getAbsolutePath()
	 * @return
	 */
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

/**
 * FXML 뷰 레퍼런스
 * @author inzapp
 *
 */
abstract class View {

	public static ImageView imgView, roiView;
	public static ListView<String> imgList;
	public static Button addBt, removeBt, startBt;
	public static Label resultLb;
}

/**
 * Entry 클래스
 * @author inzapp
 *
 */
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
						View.roiView.setImage(null);
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
