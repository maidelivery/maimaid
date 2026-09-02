import SwiftUI

struct ScannerDebugOverlayView: View {
    let showScannerBoundingBox: Bool
    let debugBoxes: [RecognizedBox]

    var body: some View {
        if showScannerBoundingBox {
            GeometryReader { geo in
                ZStack(alignment: .topLeading) {
                    ForEach(debugBoxes.indices, id: \.self) { index in
                        let box = debugBoxes[index]
                        let rect = box.rect
                        let originX = rect.origin.x * geo.size.width
                        let originY = (1 - rect.origin.y - rect.height) * geo.size.height
                        let width = rect.width * geo.size.width
                        let height = rect.height * geo.size.height

                        Path { path in
                            path.addRect(
                                CGRect(x: originX, y: originY, width: width, height: height)
                            )
                        }
                        .stroke(Color.green, lineWidth: 2)

                        Text(box.label)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 2)
                            .background(Color.green)
                            .position(x: originX + width / 2, y: max(10, originY - 8))
                    }
                }
            }
            .allowsHitTesting(false)
            .ignoresSafeArea()
        }
    }
}
