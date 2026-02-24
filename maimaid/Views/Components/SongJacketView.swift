import SwiftUI

struct SongJacketView: View {
    let imageName: String
    let remoteUrl: String
    var size: CGFloat = 60
    var cornerRadius: CGFloat = 12
    
    var body: some View {
        Group {
            if let uiImage = loadLocalImage() {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                AsyncImage(url: URL(string: remoteUrl)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    ZStack {
                        Color.primary.opacity(0.05)
                        ProgressView()
                            .scaleEffect(0.5)
                    }
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(Color.primary.opacity(0.1), lineWidth: 1)
        )
    }
    
    private func loadLocalImage() -> UIImage? {
        // Try to find the image in the "Covers" folder reference in the main bundle
        // The imageName is expected to be the SHA256 string (e.g. "abc...def.png")
        
        let path = "Covers/\(imageName)"
        if let bundlePath = Bundle.main.path(forResource: path, ofType: nil) {
            return UIImage(contentsOfFile: bundlePath)
        }
        
        // Fallback: check if it was dragged as a simple group/asset
        if let assetImage = UIImage(named: imageName) {
            return assetImage
        }
        
        return nil
    }
}
