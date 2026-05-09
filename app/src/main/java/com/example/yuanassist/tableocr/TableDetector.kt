package com.example.yuanassist.tableocr

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object TableDetector {

    fun toBinary(mat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blurred, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            31, 15.0
        )
        gray.release()
        blurred.release()
        return binary
    }

    fun extractTableRegion(mat: Mat): Pair<Mat, Rect> {
        val binary = toBinary(mat)
        val height = binary.rows()
        val width = binary.cols()

        val kernelW = maxOf(5, width / 30)
        val kernelH = maxOf(5, height / 30)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kernelW.toDouble(), kernelH.toDouble()))
        val merged = Mat()
        Imgproc.morphologyEx(binary, merged, Imgproc.MORPH_CLOSE, kernel, Point(-1.0, -1.0), 2)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(merged, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        merged.release()
        binary.release()

        if (contours.isEmpty()) {
            return Pair(mat.clone(), Rect(0, 0, width, height))
        }

        val minArea = maxOf(1, (height * width * 0.1).toInt())
        var bestRect: Rect? = null
        var bestArea = 0

        for (contour in contours) {
            val rect = Imgproc.boundingRect(contour)
            val area = rect.width * rect.height
            if (area >= minArea && area > bestArea) {
                bestRect = rect
                bestArea = area
            }
        }

        if (bestRect == null) {
            return Pair(mat.clone(), Rect(0, 0, width, height))
        }

        val r = bestRect
        return Pair(Mat(mat, r).clone(), r)
    }

    fun toBinaryGray(mat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blurred, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            31, 15.0
        )
        gray.release()
        blurred.release()
        return binary
    }
}
