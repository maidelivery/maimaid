import SwiftUI

struct LetterGamePointsText: View {
    let points: Int

    var body: some View {
        if points == 1 {
            Text("letterGame.point \(points)")
        } else {
            Text("letterGame.points \(points)")
        }
    }
}
