package com.recognizer;

import java.io.File;

import org.opencv.core.Mat;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class Recognizer {

    static {
        System.load("/inz/lib/libopencv_java401.so");
    }
    public static void main(String[] args) {
        File img = new File("1.bmp");
        Tesseract ts = new Tesseract();
        ts.setDatapath("src/main/resources/tessdata");
        try {
            System.out.println(ts.doOCR(img));
        } catch (TesseractException e) {
            e.printStackTrace();
        }

        Mat mat = new Mat();
    }
}