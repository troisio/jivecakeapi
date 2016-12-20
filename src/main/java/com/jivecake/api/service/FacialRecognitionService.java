package com.jivecake.api.service;

import static org.bytedeco.javacpp.opencv_imgcodecs.imread;
import static org.bytedeco.javacpp.opencv_imgcodecs.imwrite;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.RectVector;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_objdetect.CascadeClassifier;

public class FacialRecognitionService {
    private final CascadeClassifier selfieClassifier;

    @Inject
    public FacialRecognitionService(CascadeClassifier selfieClassifier) {
        this.selfieClassifier = selfieClassifier;
    }

    public List<File> getSelfies(File file) throws IOException {
        Mat image = imread(file.getAbsolutePath());

        int factor = 5;

        RectVector faces = new RectVector();
        this.selfieClassifier.detectMultiScale(
            image,
            faces,
            1.1,
            5,
            0,
            new Size(image.rows() / factor, image.cols() / factor),
            new Size(image.rows(), image.cols())
        );

        List<File> result = new ArrayList<>();

        for (int index = 0; index < faces.size(); index++) {
            Rect rectangle = faces.get(0);
            Mat face = new Mat(image, rectangle);

            File temporaryFile = File.createTempFile(UUID.randomUUID().toString(), ".jpg");
            temporaryFile.deleteOnExit();
            imwrite(temporaryFile.getAbsolutePath(), face);

            result.add(temporaryFile);
        }

        return result;
    }
}