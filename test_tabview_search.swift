import SwiftUI

struct TestView: View {
    @State private var searchText = ""
    var body: some View {
        TabView {
            Tab("Songs", systemImage: "music.note.list") {
                Text("Songs Tab")
            }
        }
        .searchable(text: $searchText)
    }
}
