package org.rhythmeta.maimaid.core.ml

enum class ModelAsset(
    val filename: String,
    val legacyFilename: String?,
) {
    ScoreReader("maimaid-v141n.onnx", "scorereader.onnx"),
    RegionDetector("maimaidetector-v12n.onnx", "regiondetector.onnx"),
    ScreenClassifier("maimaidistinguish-v12n.onnx", "screenclassifier.onnx"),
    TextRecognizer("ppocr-v6-small-rec.onnx", "textrecognizer.onnx"),
    TextCharacters("ppocr-v6-small-chars.json", null),
}
