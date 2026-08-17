//
// maimaidistinguish_v1_2.swift
//
// This file was automatically generated and should not be edited.
//

@preconcurrency import CoreML


/// Model Prediction Input Type
@available(macOS 12.0, iOS 15.0, tvOS 15.0, watchOS 8.0, visionOS 1.0, *)
class maimaidistinguish_v1_2Input : MLFeatureProvider {

    /// image as color (kCVPixelFormatType_32BGRA) image buffer, 640 pixels wide by 640 pixels high
    var image: CVPixelBuffer

    var featureNames: Set<String> { ["image"] }

    func featureValue(for featureName: String) -> MLFeatureValue? {
        if featureName == "image" {
            return MLFeatureValue(pixelBuffer: image)
        }
        return nil
    }

    init(image: CVPixelBuffer) {
        self.image = image
    }

    convenience init(imageWith image: CGImage) throws {
        self.init(image: try MLFeatureValue(cgImage: image, pixelsWide: 640, pixelsHigh: 640, pixelFormatType: kCVPixelFormatType_32ARGB, options: nil).imageBufferValue!)
    }

    convenience init(imageAt image: URL) throws {
        self.init(image: try MLFeatureValue(imageAt: image, pixelsWide: 640, pixelsHigh: 640, pixelFormatType: kCVPixelFormatType_32ARGB, options: nil).imageBufferValue!)
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
class maimaidistinguish_v1_2Output : MLFeatureProvider {

    /// Source provided by CoreML
    private let provider : any MLFeatureProvider

    /// classLabel as string value
    var classLabel: String {
        provider.featureValue(for: "classLabel")!.stringValue
    }

    /// classLabel_probs as dictionary of strings to doubles
    var classLabel_probs: [String : Double] {
        provider.featureValue(for: "classLabel_probs")!.dictionaryValue as! [String : Double]
    }

    var featureNames: Set<String> {
        provider.featureNames
    }

    func featureValue(for featureName: String) -> MLFeatureValue? {
        provider.featureValue(for: featureName)
    }

    init(classLabel: String, classLabel_probs: [String : Double]) {
        self.provider = try! MLDictionaryFeatureProvider(dictionary: ["classLabel" : MLFeatureValue(string: classLabel), "classLabel_probs" : MLFeatureValue(dictionary: classLabel_probs as [AnyHashable : NSNumber])])
    }

    init(features: any MLFeatureProvider) {
        self.provider = features
    }
}


/// Class for model loading and prediction
@available(macOS 12.0, iOS 15.0, tvOS 15.0, watchOS 8.0, visionOS 1.0, *)
class maimaidistinguish_v1_2 {
    let model: MLModel

    /// URL of model assuming it was installed in the same bundle as this class
    class var urlOfModelInThisBundle : URL {
        let bundle = Bundle(for: self)
        return bundle.url(forResource: "maimaidistinguish v1.2", withExtension:"mlmodelc")!
    }

    /**
        Construct maimaidistinguish_v1_2 instance with an existing MLModel object.

        Usually the application does not use this initializer unless it makes a subclass of maimaidistinguish_v1_2.
        Such application may want to use `MLModel(contentsOfURL:configuration:)` and `maimaidistinguish_v1_2.urlOfModelInThisBundle` to create a MLModel object to pass-in.

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
        Construct maimaidistinguish_v1_2 instance with explicit path to mlmodelc file
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
        Construct maimaidistinguish_v1_2 instance asynchronously with optional configuration.

        Model loading may take time when the model content is not immediately available (e.g. encrypted model). Use this factory method especially when the caller is on the main thread.

        - parameters:
          - configuration: the desired model configuration
          - handler: the completion handler to be called when the model loading completes successfully or unsuccessfully
    */
    class func load(configuration: MLModelConfiguration = MLModelConfiguration(), completionHandler handler: @escaping (Swift.Result<maimaidistinguish_v1_2, any Error>) -> Void) {
        load(contentsOf: self.urlOfModelInThisBundle, configuration: configuration, completionHandler: handler)
    }

    /**
        Construct maimaidistinguish_v1_2 instance asynchronously with optional configuration.

        Model loading may take time when the model content is not immediately available (e.g. encrypted model). Use this factory method especially when the caller is on the main thread.

        - parameters:
          - configuration: the desired model configuration
    */
    class func load(configuration: MLModelConfiguration = MLModelConfiguration()) async throws -> maimaidistinguish_v1_2 {
        try await load(contentsOf: self.urlOfModelInThisBundle, configuration: configuration)
    }

    /**
        Construct maimaidistinguish_v1_2 instance asynchronously with URL of the .mlmodelc directory with optional configuration.

        Model loading may take time when the model content is not immediately available (e.g. encrypted model). Use this factory method especially when the caller is on the main thread.

        - parameters:
          - modelURL: the URL to the model
          - configuration: the desired model configuration
          - handler: the completion handler to be called when the model loading completes successfully or unsuccessfully
    */
    class func load(contentsOf modelURL: URL, configuration: MLModelConfiguration = MLModelConfiguration(), completionHandler handler: @escaping (Swift.Result<maimaidistinguish_v1_2, any Error>) -> Void) {
        MLModel.load(contentsOf: modelURL, configuration: configuration) { result in
            switch result {
            case .failure(let error):
                handler(.failure(error))
            case .success(let model):
                handler(.success(maimaidistinguish_v1_2(model: model)))
            }
        }
    }

    /**
        Construct maimaidistinguish_v1_2 instance asynchronously with URL of the .mlmodelc directory with optional configuration.

        Model loading may take time when the model content is not immediately available (e.g. encrypted model). Use this factory method especially when the caller is on the main thread.

        - parameters:
          - modelURL: the URL to the model
          - configuration: the desired model configuration
    */
    class func load(contentsOf modelURL: URL, configuration: MLModelConfiguration = MLModelConfiguration()) async throws -> maimaidistinguish_v1_2 {
        let model = try await MLModel.load(contentsOf: modelURL, configuration: configuration)
        return maimaidistinguish_v1_2(model: model)
    }

    /**
        Make a prediction using the structured interface

        It uses the default function if the model has multiple functions.

        - parameters:
           - input: the input to the prediction as maimaidistinguish_v1_2Input

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as maimaidistinguish_v1_2Output
    */
    func prediction(input: maimaidistinguish_v1_2Input) throws -> maimaidistinguish_v1_2Output {
        try prediction(input: input, options: MLPredictionOptions())
    }

    /**
        Make a prediction using the structured interface

        It uses the default function if the model has multiple functions.

        - parameters:
           - input: the input to the prediction as maimaidistinguish_v1_2Input
           - options: prediction options

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as maimaidistinguish_v1_2Output
    */
    func prediction(input: maimaidistinguish_v1_2Input, options: MLPredictionOptions) throws -> maimaidistinguish_v1_2Output {
        let outFeatures = try model.prediction(from: input, options: options)
        return maimaidistinguish_v1_2Output(features: outFeatures)
    }

    /**
        Make an asynchronous prediction using the structured interface

        It uses the default function if the model has multiple functions.

        - parameters:
           - input: the input to the prediction as maimaidistinguish_v1_2Input
           - options: prediction options

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as maimaidistinguish_v1_2Output
    */
    @available(macOS 14.0, iOS 17.0, tvOS 17.0, watchOS 10.0, visionOS 1.0, *)
    func prediction(input: maimaidistinguish_v1_2Input, options: MLPredictionOptions = MLPredictionOptions()) async throws -> maimaidistinguish_v1_2Output {
        let outFeatures = try await model.prediction(from: input, options: options)
        return maimaidistinguish_v1_2Output(features: outFeatures)
    }

    /**
        Make a prediction using the convenience interface

        It uses the default function if the model has multiple functions.

        - parameters:
            - image: color (kCVPixelFormatType_32BGRA) image buffer, 640 pixels wide by 640 pixels high

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as maimaidistinguish_v1_2Output
    */
    func prediction(image: CVPixelBuffer) throws -> maimaidistinguish_v1_2Output {
        let input_ = maimaidistinguish_v1_2Input(image: image)
        return try prediction(input: input_)
    }

    /**
        Make a batch prediction using the structured interface

        It uses the default function if the model has multiple functions.

        - parameters:
           - inputs: the inputs to the prediction as [maimaidistinguish_v1_2Input]
           - options: prediction options

        - throws: an NSError object that describes the problem

        - returns: the result of the prediction as [maimaidistinguish_v1_2Output]
    */
    func predictions(inputs: [maimaidistinguish_v1_2Input], options: MLPredictionOptions = MLPredictionOptions()) throws -> [maimaidistinguish_v1_2Output] {
        let batchIn = MLArrayBatchProvider(array: inputs)
        let batchOut = try model.predictions(from: batchIn, options: options)
        var results : [maimaidistinguish_v1_2Output] = []
        results.reserveCapacity(inputs.count)
        for i in 0..<batchOut.count {
            let outProvider = batchOut.features(at: i)
            let result =  maimaidistinguish_v1_2Output(features: outProvider)
            results.append(result)
        }
        return results
    }
}
