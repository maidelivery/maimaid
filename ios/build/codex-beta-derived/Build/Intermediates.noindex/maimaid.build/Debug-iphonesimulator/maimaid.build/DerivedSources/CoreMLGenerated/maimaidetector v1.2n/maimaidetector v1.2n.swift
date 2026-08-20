//
// maimaidetector_v1_2n.swift
//
// This file was automatically generated and should not be edited.
//

@preconcurrency import CoreML


/// Model Prediction Input Type
@available(macOS 12.0, iOS 15.0, tvOS 15.0, watchOS 8.0, visionOS 1.0, *)
class maimaidetector_v1_2nInput : MLFeatureProvider {

    /// Input image as color (kCVPixelFormatType_32BGRA) image buffer, 640 pixels wide by 640 pixels high
    var image: CVPixelBuffer

    /// (optional) IoU threshold override (default: 0.7) as double value
    var iouThreshold: Double

    /// (optional) Confidence threshold override (default: 0.25) as double value
    var confidenceThreshold: Double

    var featureNames: Set<String> { ["image", "iouThreshold", "confidenceThreshold"] }

    func featureValue(for featureName: String) -> MLFeatureValue? {
        if featureName == "image" {
            return MLFeatureValue(pixelBuffer: image)
        }
        if featureName == "iouThreshold" {
            return MLFeatureValue(double: iouThreshold)
        }
        if featureName == "confidenceThreshold" {
            return MLFeatureValue(double: confidenceThreshold)
        }
        return nil
    }

    init(image: CVPixelBuffer, iouThreshold: Double, confidenceThreshold: Double) {
        self.image = image
        self.iouThreshold = iouThreshold
        self.confidenceThreshold = confidenceThreshold
    }

    convenience init(imageWith image: CGImage, iouThreshold: Double, confidenceThreshold: Double) throws {
        self.init(image: try MLFeatureValue(cgImage: image, pixelsWide: 640, pixelsHigh: 640, pixelFormatType: kCVPixelFormatType_32ARGB, options: nil).imageBufferValue!, iouThreshold: iouThreshold, confidenceThreshold: confidenceThreshold)
    }

    convenience init(imageAt image: URL, iouThreshold: Double, confidenceThreshold: Double) throws {
        self.init(image: try MLFeatureValue(imageAt: image, pixelsWide: 640, pixelsHigh: 640, pixelFormatType: kCVPixelFormatType_32ARGB, options: nil).imageBufferValue!, iouThreshold: iouThreshold, confidenceThreshold: confidenceThreshold)
    }

    func setImage(with image: CGImage) throws  {
        self.image = try MLFeatureValue(cgImage: image, pixelsWide: 640, pixelsHigh: 640, pixelFormatType: kCVPixelFormatType_32ARGB, options: nil).imageBufferValue!
    }

    func setImage(with image: URL) throws  {
        self.image = try MLFeatureValue(imageAt: image, pixelsWide: 640, pixelsHigh: 640, pixelFormatType: kCVPixelFormatType_32ARGB, options: nil).imageBufferValue!
    }

}


/// Model Prediction Output Type
@available(macOS 12.0, iOS 15.0, tvOS 15.0, watchOS 8.0, visionOS 1.0, *)
class maimaidetector_v1_2nOutput : MLFeatureProvider {

    /// Source provided by CoreML
    private let provider : any MLFeatureProvider

    /// Boxes × Class confidence (see user-defined metadata "classes") as multidimensional array of floats
    var confidence: MLMultiArray {
        provider.featureValue(for: "confidence")!.multiArrayValue!
    }

    /// Boxes × Class confidence (see user-defined metadata "classes") as multidimensional array of floats
    var confidenceShapedArray: MLShapedArray<Float> {
        MLShapedArray<Float>(confidence)
    }

    /// Boxes × [x, y, width, height] (relative to image size) as multidimensional array of floats
    var coordinates: MLMultiArray {
        provider.featureValue(for: "coordinates")!.multiArrayValue!
    }

    /// Boxes × [x, y, width, height] (relative to image size) as multidimensional array of floats
    var coordinatesShapedArray: MLShapedArray<Float> {
        MLShapedArray<Float>(coordinates)
    }

    var featureNames: Set<String> {
        provider.featureNames
    }

    func featureValue(for featureName: String) -> MLFeatureValue? {
        provider.featureValue(for: featureName)
    }

    init(confidence: MLMultiArray, coordinates: MLMultiArray) {
        self.provider = try! MLDictionaryFeatureProvider(dictionary: ["confidence" : MLFeatureValue(multiArray: confidence), "coordinates" : MLFeatureValue(multiArray: coordinates)])
    }

    init(features: any MLFeatureProvider) {
        self.provider = features
    }
}


/// Class for model loading and prediction
@available(macOS 12.0, iOS 15.0, tvOS 15.0, watchOS 8.0, visionOS 1.0, *)
class maimaidetector_v1_2n {
    let model: MLModel

    /// URL of model assuming it was installed in the same bundle as this class
    class var urlOfModelInThisBundle : URL {
        let bundle = Bundle(for: self)
        return bundle.url(forResource: "maimaidetector v1.2n", withExtension:"mlmodelc")!
    }

    /**
        Construct maimaidetector_v1_2n instance with an existing MLModel object.

        Usually the application does not use this initializer unless it makes a subclass of maimaidetector_v1_2n.
        Such application may want to use `MLModel(contentsOfURL:configuration:)` and `maimaidetector_v1_2n.urlOfModelInThisBundle` to create a MLModel object to pass-in.

        - parameters:
          - model: MLModel object
    */
    init(model: MLModel) {
        self.model = model
    }

    /**
        Construct a model with configuration

        - parameters:
           - configuration: the desired model configuration

        - throws: an NSError object that describes the problem
    */
    convenience init(configuration: MLModelConfiguration = MLModelConfiguration()) throws {
        try self.init(contentsOf: type(of:self).urlOfModelInThisBundle, configuration: configuration)
    }

    /**
        Construct maimaidetector_v1_2n instance with explicit path to mlmodelc file
        - parameters:
           - modelURL: the file url of the model

        - throws: an NSError object that describes the problem
    */
    convenience init(contentsOf modelURL: URL) throws {
        try self.init(model: MLModel(contentsOf: modelURL))
    }

    /**
        Construct a model with URL of the .mlmodelc directory and configuration

        - parameters:
           - modelURL: the file url of the model
           - configuration: the desired model configuration

        - throws: an NSError object that describes the problem
    */
    convenience init(contentsOf modelURL: URL, configuration: MLModelConfiguration) throws {
        try self.init(model: MLModel(contentsOf: modelURL, configuration: configuration))
    }

    /**
        Construct maimaidetector_v1_2n instance asynchronously with optional configuration.

        Model loading may take time when the model content is not immediately available (e.g. encrypted model). Use this factory method especially when the caller is on the main thread.

        - parameters:
          - configuration: the desired model configuration
          - handler: the completion handler to be called when the model loading completes successfully or unsuccessfully
    */
    class func load(configuration: MLModelConfiguration = MLModelConfiguration(), completionHandler handler: @escaping (Swift.Result<maimaidetector_v1_2n, any Error>) -> Void) {
        load(contentsOf: self.urlOfModelInThisBundle, configuration: configuration, completionHandler: handler)
    }

    /**
        Construct maimaidetector_v1_2n instance asynchronously with optional configuration.

        Model loading may take time when the model content is not immediately available (e.g. encrypted model). Use this factory method especially when the caller is on the main thread.

        - parameters:
          - configuration: the desired model configuration
    */
    class func load(configuration: MLModelConfiguration = MLModelConfiguration()) async throws -> maimaidetector_v1_2n {
        try await load(contentsOf: self.urlOfModelInThisBundle, configuration: configuration)
    }

    /**
        Construct maimaidetector_v1_2n instance asynchronously with URL of the .mlmodelc directory with optional configuration.

        Model loading may take time when the model content is not immediately available (e.g. encrypted model). Use this factory method especially when the caller is on the main thread.

        - parameters:
          - modelURL: the URL to the model
          - configuration: the desired model configuration
          - handler: the completion handler to be called when the model loading completes successfully or unsuccessfully
    */
    class func load(contentsOf modelURL: URL, configuration: MLModelConfiguration = MLModelConfiguration(), completionHandler handler: @escaping (Swift.Result<maimaidetector_v1_2n, any Error>) -> Void) {
        MLModel.load(contentsOf: modelURL, configuration: configuration) { result in
            switch result {
            case .failure(let error):
                handler(.failure(error))
            case .success(let model):
                handler(.success(maimaidetector_v1_2n(model: model)))
            }
        }
    }

    /**
        Construct maimaidetector_v1_2n instance asynchronously with URL of the .mlmodelc directory with optional configuration.

        Model loading may take time when the model content is not immediately available (e.g. encrypted model). Use this factory method especially when the caller is on the main thread.

        - parameters:
          - modelURL: the URL to the model
          - configuration: the desired model configuration
    */
    class func load(contentsOf modelURL: URL, configuration: MLModelConfiguration = MLModelConfiguration()) async throws -> maimaidetector_v1_2n {
        let model = try await MLModel.load(contentsOf: modelURL, configuration: configuration)
        return maimaidetector_v1_2n(model: model)
    }

    /**
        Make a prediction using the structured interface

        It uses the default function if the model has multiple functions.

        - parameters:
           - input: the input to the prediction as maimaidetector_v1_2nInput

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as maimaidetector_v1_2nOutput
    */
    func prediction(input: maimaidetector_v1_2nInput) throws -> maimaidetector_v1_2nOutput {
        try prediction(input: input, options: MLPredictionOptions())
    }

    /**
        Make a prediction using the structured interface

        It uses the default function if the model has multiple functions.

        - parameters:
           - input: the input to the prediction as maimaidetector_v1_2nInput
           - options: prediction options

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as maimaidetector_v1_2nOutput
    */
    func prediction(input: maimaidetector_v1_2nInput, options: MLPredictionOptions) throws -> maimaidetector_v1_2nOutput {
        let outFeatures = try model.prediction(from: input, options: options)
        return maimaidetector_v1_2nOutput(features: outFeatures)
    }

    /**
        Make an asynchronous prediction using the structured interface

        It uses the default function if the model has multiple functions.

        - parameters:
           - input: the input to the prediction as maimaidetector_v1_2nInput
           - options: prediction options

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as maimaidetector_v1_2nOutput
    */
    @available(macOS 14.0, iOS 17.0, tvOS 17.0, watchOS 10.0, visionOS 1.0, *)
    func prediction(input: maimaidetector_v1_2nInput, options: MLPredictionOptions = MLPredictionOptions()) async throws -> maimaidetector_v1_2nOutput {
        let outFeatures = try await model.prediction(from: input, options: options)
        return maimaidetector_v1_2nOutput(features: outFeatures)
    }

    /**
        Make a prediction using the convenience interface

        It uses the default function if the model has multiple functions.

        - parameters:
            - image: Input image as color (kCVPixelFormatType_32BGRA) image buffer, 640 pixels wide by 640 pixels high
            - iouThreshold: (optional) IoU threshold override (default: 0.7) as double value
            - confidenceThreshold: (optional) Confidence threshold override (default: 0.25) as double value

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as maimaidetector_v1_2nOutput
    */
    func prediction(image: CVPixelBuffer, iouThreshold: Double, confidenceThreshold: Double) throws -> maimaidetector_v1_2nOutput {
        let input_ = maimaidetector_v1_2nInput(image: image, iouThreshold: iouThreshold, confidenceThreshold: confidenceThreshold)
        return try prediction(input: input_)
    }

    /**
        Make a batch prediction using the structured interface

        It uses the default function if the model has multiple functions.

        - parameters:
           - inputs: the inputs to the prediction as [maimaidetector_v1_2nInput]
           - options: prediction options

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as [maimaidetector_v1_2nOutput]
    */
    func predictions(inputs: [maimaidetector_v1_2nInput], options: MLPredictionOptions = MLPredictionOptions()) throws -> [maimaidetector_v1_2nOutput] {
        let batchIn = MLArrayBatchProvider(array: inputs)
        let batchOut = try model.predictions(from: batchIn, options: options)
        var results : [maimaidetector_v1_2nOutput] = []
        results.reserveCapacity(inputs.count)
        for i in 0..<batchOut.count {
            let outProvider = batchOut.features(at: i)
            let result =  maimaidetector_v1_2nOutput(features: outProvider)
            results.append(result)
        }
        return results
    }
}
