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
            } else if let url = URL(string: remoteUrl) {
                AsyncImage(url: url) { image in
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
            } else {
                ZStack {
                    Color.primary.opacity(0.05)
                    Image(systemName: "music.note")
                        .foregroundColor(.secondary.opacity(0.3))
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
        if let downloaded = ImageDownloader.shared.loadImage(imageName: imageName) {
            return downloaded
        }
        
        // Fallback: check if it was dragged as a simple asset catalog item
        if let assetImage = UIImage(named: imageName) {
            return assetImage
        }
        
        return nil
    }
}
