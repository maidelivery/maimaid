import UIKit
@preconcurrency import AVFoundation
import os

final class CameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onImageCaptured: ((UIImage) -> Void)?
    
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var photoOutput: AVCapturePhotoOutput?
    private let processingQueue = DispatchQueue(label: "com.maimaid.camera.queue", qos: .userInteractive)
    private let frameCounter = OSAllocatedUnfairLock(initialState: 0)
    nonisolated private static let rawContext = CIContext(options: [.useSoftwareRenderer: false])
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupCaptureSession()
        NotificationCenter.default.addObserver(self, selector: #selector(handleTakePhoto), name: Notification.Name("TakeScannerPhoto"), object: nil)
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
    
    private func setupCaptureSession() {
        captureSession = AVCaptureSession()
        guard let captureSession = captureSession else { return }
        
        captureSession.sessionPreset = .hd1280x720
        
        guard let videoDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let videoInput = try? AVCaptureDeviceInput(device: videoDevice) else { return }
        
        if captureSession.canAddInput(videoInput) { captureSession.addInput(videoInput) }
        
        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(self, queue: processingQueue)
        if captureSession.canAddOutput(videoOutput) { captureSession.addOutput(videoOutput) }
        
        let photoOut = AVCapturePhotoOutput()
        if captureSession.canAddOutput(photoOut) {
            captureSession.addOutput(photoOut)
            if #available(iOS 16.0, *) {
                photoOut.maxPhotoDimensions = videoDevice.activeFormat.supportedMaxPhotoDimensions.last ?? CMVideoDimensions(width: 0, height: 0)
            } else {
                photoOut.isHighResolutionCaptureEnabled = true
            }
            photoOutput = photoOut
        }
        
        if let connection = videoOutput.connection(with: .video) {
            if #available(iOS 17.0, *) {
                if connection.isVideoRotationAngleSupported(90) {
                    connection.videoRotationAngle = 90
                }
            } else if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
        }
        
        previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer?.frame = view.layer.bounds
        previewLayer?.videoGravity = .resizeAspectFill
        if let previewLayer { view.layer.addSublayer(previewLayer) }
        
        DispatchQueue.global(qos: .userInitiated).async {
            captureSession.startRunning()
        }
    }
    
    nonisolated func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let count = frameCounter.withLock { value -> Int in
            value += 1
            return value
        }
        guard count.isMultiple(of: 10),
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = Self.rawContext.createCGImage(ciImage, from: ciImage.extent) else { return }
        let image = UIImage(cgImage: cgImage)
        
        DispatchQueue.main.async {
            self.onImageCaptured?(image)
        }
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if let captureSession, !captureSession.isRunning {
            DispatchQueue.global(qos: .userInitiated).async {
                captureSession.startRunning()
            }
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        captureSession?.stopRunning()
    }
    
    @objc private func handleTakePhoto() {
        guard let output = photoOutput else { return }
        
        let settings = AVCapturePhotoSettings()
        if let connection = output.connection(with: .video) {
            if #available(iOS 17.0, *) {
                if connection.isVideoRotationAngleSupported(90) {
                    connection.videoRotationAngle = 90
                }
            } else if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
        }
        
        if output.availablePhotoCodecTypes.contains(.jpeg) {
            if #available(iOS 16.0, *) {
                settings.maxPhotoDimensions = output.maxPhotoDimensions
            } else {
                settings.isHighResolutionPhotoEnabled = true
            }
        }
        
        output.capturePhoto(with: settings, delegate: self)
    }
}

extension CameraViewController: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard let data = photo.fileDataRepresentation(),
              let image = UIImage(data: data) else { return }
        
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: Notification.Name("ScannerPhotoCaptured"), object: image)
        }
    }
}
