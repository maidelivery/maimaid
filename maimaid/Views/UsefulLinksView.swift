import SwiftUI

struct MaimaiLink: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let url: String
    let icon: String
    let color: Color
}

struct UsefulLinksView: View {
    // MARK: - Link Data
    // To add more websites, simply add a new MaimaiLink item to this array!
    private let links = [
        MaimaiLink(
            title: String(localized: "links.dx_jp.title"),
            subtitle: String(localized: "links.dx_jp.subtitle"),
            url: "https://maimai.sega.jp/",
            icon: "safari.fill",
            color: .blue
        ),
        MaimaiLink(
            title: String(localized: "links.df.title"),
            subtitle: String(localized: "links.df.subtitle"),
            url: "https://www.maimai.cn/",
            icon: "chart.line.uptrend.xyaxis",
            color: .orange
        ),
        MaimaiLink(
            title: String(localized: "links.lxns.title"),
            subtitle: String(localized: "links.lxns.subtitle"),
            url: "https://maimai.lxns.net/",
            icon: "chart.line.uptrend.xyaxis",
            color: .purple
        ),
        MaimaiLink(
            title: String(localized: "links.gamerch.title"),
            subtitle: String(localized: "links.gamerch.subtitle"),
            url: "https://maimai.gamerch.com/",
            icon: "book.fill",
            color: .green
        ),
        MaimaiLink(
            title: String(localized: "links.dxrating.title"),
            subtitle: String(localized: "links.dxrating.subtitle"),
            url: "https://dxrating.net/",
            icon: "book.fill",
            color: .cyan
        ),
        MaimaiLink(
            title: String(localized: "links.mailv.title"),
            subtitle: String(localized: "links.mailv.subtitle"),
            url: "https://x.com/maiLv_Chihooooo",
            icon: "book.fill",
            color: .blue
        )
    ]
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                ForEach(links) { link in
                    LinkCard(link: link)
                }
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("links.title")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LinkCard: View {
    let link: MaimaiLink
    
    var body: some View {
        Link(destination: URL(string: link.url)!) {
            HStack(spacing: 16) {
                // Icon
                ZStack {
                    Circle()
                        .fill(link.color.opacity(0.1))
                        .frame(width: 48, height: 48)
                    
                    Image(systemName: link.icon)
                        .font(.system(size: 22))
                        .foregroundColor(link.color)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(link.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.primary)
                    
                    Text(link.subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                
                Spacer()
                
                Image(systemName: "arrow.up.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.secondary.opacity(0.3))
            }
            .padding(16)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.primary.opacity(0.05), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.02), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationStack {
        UsefulLinksView()
    }
}
