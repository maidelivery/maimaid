import SwiftUI
struct TestView: View {
    @State private var text = ""
    var body: some View {
        TabView {
            Tab("S", systemImage: "star") { Text("A") }
        }
        .searchable(text: $text)
    }
}
