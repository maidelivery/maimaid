import SwiftUI

struct ScannerDebugOverlayView: View {
    let showScannerBoundingBox: Bool
    let debugBoxes: [RecognizedBox]
    
    var body: some View {
        if showScannerBoundingBox {
            GeometryReader { geo in
                ZStack(alignment: .topLeading) {
                    ForEach(debugBoxes.indices, id: \.self) { i in
                        let box = debugBoxes[i]
                        let rect = box.rect
                        let x = rect.origin.x * geo.size.width
                        let y = (1 - rect.origin.y - rect.height) * geo.size.height
                        let w = rect.width * geo.size.width
                        let h = rect.height * geo.size.height
                        
                        Path { path in
                            path.addRect(CGRect(x: x, y: y, width: w, height: h))
                        }
                        .stroke(Color.green, lineWidth: 2)
                        
                        Text(box.label)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 2)
                            .background(Color.green)
                            .position(x: x + w / 2, y: max(10, y - 8))
                    }
                }
            }
            .allowsHitTesting(false)
            .ignoresSafeArea()
        }
    }
}
