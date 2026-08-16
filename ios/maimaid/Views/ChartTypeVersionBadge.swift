import SwiftUI

struct ChartTypeVersionBadge: View {
    let text: String
    let chartTypes: [String]
    @Environment(\.colorScheme) private var colorScheme

    private var colors: [Color] {
        chartTypes.map { ThemeUtils.badgeColorForChartType($0, colorScheme) }
    }

    var body: some View {
        Text(text)
            .font(.system(size: 9, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background {
                Canvas { context, size in
                    let background = Path(CGRect(origin: .zero, size: size))
                    context.fill(
                        background,
                        with: .color(colors.first ?? ThemeUtils.badgeColorForChartType("std", colorScheme))
                    )
                    if colors.count > 1 {
                        var split = Path()
                        split.move(to: CGPoint(x: size.width * 0.44, y: 0))
                        split.addLine(to: CGPoint(x: size.width, y: 0))
                        split.addLine(to: CGPoint(x: size.width, y: size.height))
                        split.addLine(to: CGPoint(x: size.width * 0.56, y: size.height))
                        split.closeSubpath()
                        context.fill(split, with: .color(colors[1]))
                    }
                }
            }
            .clipShape(.rect(cornerRadius: 4))
    }
}
