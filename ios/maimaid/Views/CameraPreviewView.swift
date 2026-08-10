import SwiftUI

struct CameraPreviewView: UIViewControllerRepresentable {
    var onImageCaptured: (UIImage) -> Void
    var onPhotoCaptured: (Result<Data, Error>) -> Void
    
    func makeUIViewController(context: Context) -> CameraViewController {
        let controller = CameraViewController()
        controller.onImageCaptured = onImageCaptured
        controller.onPhotoCaptured = onPhotoCaptured
        return controller
    }
    
    func updateUIViewController(_ uiViewController: CameraViewController, context: Context) {
        uiViewController.onImageCaptured = onImageCaptured
        uiViewController.onPhotoCaptured = onPhotoCaptured
    }
}
