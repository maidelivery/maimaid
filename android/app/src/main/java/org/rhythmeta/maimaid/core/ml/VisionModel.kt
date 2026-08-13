package org.rhythmeta.maimaid.core.ml

enum class VisionModel(val assetPath: String) {
    ScoreReader("models/vision/maimaid-v141n.onnx"),
    RegionDetector("models/vision/maimaidetector-v12n.onnx"),
    ScreenClassifier("models/vision/maimaidistinguish-v12n.onnx"),
    TextRecognizer("models/ocr/ppocr-v6-tiny-rec.onnx"),
    JapaneseTextRecognizer("models/ocr/japan-ppocr-v3-mobile-rec.onnx"),
}
