package com.recognizer;

import java.io.File;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class Recognizer {
    public static void main(String[] args) {
        File img = new File("1.bmp");
        Tesseract ts = new Tesseract();
        ts.setDatapath("src/main/resources/tessdata");
        try {
            System.out.println(ts.doOCR(img));
        } catch (TesseractException e) {
            e.printStackTrace();
        }
    }
}