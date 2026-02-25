import SwiftUI

struct HomeView: View {
    let columns = [
        GridItem(.flexible(), spacing: 16),
        GridItem(.flexible(), spacing: 16)
    ]
    
    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 16) {
                    functionCard(
                        icon: "chart.bar.fill",
                        title: "成绩分析",
                        subtitle: "查看你的成绩趋势",
                        gradient: [Color.blue, Color.cyan]
                    )
                    
                    functionCard(
                        icon: "star.fill",
                        title: "DX Rating",
                        subtitle: "计算你的 Rating",
                        gradient: [Color.orange, Color.yellow]
                    )
                    
                    functionCard(
                        icon: "wand.and.stars",
                        title: "歌曲推荐",
                        subtitle: "根据实力推荐谱面",
                        gradient: [Color.purple, Color.pink]
                    )
                    
                    functionCard(
                        icon: "trophy.fill",
                        title: "排行榜",
                        subtitle: "查看好友排名",
                        gradient: [Color.green, Color.mint]
                    )
                    
                    functionCard(
                        icon: "target",
                        title: "目标管理",
                        subtitle: "设定分数目标",
                        gradient: [Color.red, Color.orange]
                    )
                    
                    functionCard(
                        icon: "square.and.arrow.up.fill",
                        title: "成绩导出",
                        subtitle: "导出为图片或文件",
                        gradient: [Color.indigo, Color.blue]
                    )
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("主页")
        }
    }
    
    private func functionCard(icon: String, title: String, subtitle: String, gradient: [Color]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 28))
                .foregroundStyle(
                    LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing)
                )
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.primary)
                
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
}
