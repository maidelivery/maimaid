package org.rhythmeta.maimaid.core.ml

enum class VisionModel(val asset: ModelAsset) {
    ScoreReader(ModelAsset.ScoreReader),
    RegionDetector(ModelAsset.RegionDetector),
    ScreenClassifier(ModelAsset.ScreenClassifier),
    TextRecognizer(ModelAsset.TextRecognizer),
}
