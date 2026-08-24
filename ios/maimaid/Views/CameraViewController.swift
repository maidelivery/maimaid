import UIKit
@preconcurrency import AVFoundation
import os

final class CameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onImageCaptured: ((UIImage) -> Void)?
    var onPhotoCaptured: ((Result<Data, Error>) -> Void)?
    var onQRCodeDetected: ((String) -> Void)?
    
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var photoOutput: AVCapturePhotoOutput?
    private var videoDevice: AVCaptureDevice?
    private var configuredPreviewSize: CGSize = .zero
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
        
        captureSession.sessionPreset = .high
        
        guard let videoDevice = Self.preferredBackCamera(),
              let videoInput = try? AVCaptureDeviceInput(device: videoDevice) else { return }
        self.videoDevice = videoDevice
        
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

        let metadataOutput = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(metadataOutput) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(self, queue: processingQueue)
            metadataOutput.metadataObjectTypes = [.qr]
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
        
        Task { @MainActor [weak self] in
            self?.onImageCaptured?(image)
        }
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
        configureSystemCameraFieldOfView()
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
        guard let output = photoOutput else {
            let error = NSError(
                domain: "CameraViewController",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: String(localized: "camera.error.outputUnavailable")]
            )
            onPhotoCaptured?(.failure(error))
            return
        }
        
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

    private static func preferredBackCamera() -> AVCaptureDevice? {
        AVCaptureDevice.default(.builtInTripleCamera, for: .video, position: .back)
            ?? AVCaptureDevice.default(.builtInDualWideCamera, for: .video, position: .back)
            ?? AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
    }

    private func configureSystemCameraFieldOfView() {
        let previewSize = view.bounds.size
        guard previewSize.width > 0,
              previewSize.height > 0,
              previewSize != configuredPreviewSize,
              let videoDevice else { return }
        configuredPreviewSize = previewSize

        let viewportAspect = max(previewSize.width, previewSize.height) / min(previewSize.width, previewSize.height)
        let videoAspect = CGFloat(16.0 / 9.0)
        let aspectFillCropFactor = max(1, viewportAspect / videoAspect)

        let wideCameraZoomFactor: CGFloat
        if videoDevice.isVirtualDevice,
           let wideCameraIndex = videoDevice.constituentDevices.firstIndex(where: { $0.deviceType == .builtInWideAngleCamera }),
           wideCameraIndex > 0,
           videoDevice.virtualDeviceSwitchOverVideoZoomFactors.indices.contains(wideCameraIndex - 1) {
            wideCameraZoomFactor = CGFloat(
                truncating: videoDevice.virtualDeviceSwitchOverVideoZoomFactors[wideCameraIndex - 1]
            )
        } else {
            wideCameraZoomFactor = 1
        }

        let compensatedZoomFactor = wideCameraZoomFactor / aspectFillCropFactor
        let availableZoomFactor = min(
            max(compensatedZoomFactor, videoDevice.minAvailableVideoZoomFactor),
            videoDevice.maxAvailableVideoZoomFactor
        )

        do {
            try videoDevice.lockForConfiguration()
            videoDevice.videoZoomFactor = availableZoomFactor
            videoDevice.unlockForConfiguration()
        } catch {
            return
        }
    }
}

extension CameraViewController: AVCaptureMetadataOutputObjectsDelegate {
    nonisolated func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let code = metadataObjects.compactMap({ $0 as? AVMetadataMachineReadableCodeObject }).first(where: { $0.type == .qr }),
              let value = code.stringValue,
              value.hasPrefix(SongCollectionCodec.prefix) else { return }
        Task { @MainActor [weak self] in self?.onQRCodeDetected?(value) }
    }
}

extension CameraViewController: AVCapturePhotoCaptureDelegate {
    nonisolated func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let result: Result<Data, Error>
        if let error {
            result = .failure(error)
        } else if let data = photo.fileDataRepresentation() {
            result = .success(data)
        } else {
            let error = NSError(
                domain: "CameraViewController",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: String(localized: "camera.error.dataUnavailable")]
            )
            result = .failure(error)
        }

        Task { @MainActor [weak self] in
            self?.onPhotoCaptured?(result)
        }
    }
}
