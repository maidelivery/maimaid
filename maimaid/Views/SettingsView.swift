import SwiftUI
import SwiftData

struct SettingsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    
    private var config: SyncConfig? { configs.first }
    @State private var selectedTheme = 0
    @State private var notificationsEnabled = true
    @State private var autoSync = true
    @State private var hapticFeedback = true
    
    var body: some View {
        NavigationStack {
            List {
                // Account Section
                Section {
                    HStack(spacing: 14) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 44))
                            .foregroundStyle(.linearGradient(
                                colors: [.blue, .purple],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ))
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("未登录")
                                .font(.system(size: 17, weight: .semibold))
                            Text("登录以同步数据")
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                }
                
                // Data Section
                Section("数据") {
                    NavigationLink {
                        DivingFishImportView()
                    } label: {
                        settingsRowLabel(icon: "fish.fill", iconColor: .blue, title: "从 Diving Fish 导入")
                    }
                    
                    NavigationLink(destination: LxnsImportView()) {
                        HStack {
                            settingsRowLabel(icon: "snowflake", iconColor: .cyan, title: "从 LXNS 导入")
                            Spacer()
                            if let c = config, !c.lxnsRefreshToken.isEmpty {
                                Text("已绑定").font(.caption).foregroundColor(.green)
                            }
                        }
                    }
                    
                    Toggle(isOn: $autoSync) {
                        settingsRowLabel(icon: "icloud.and.arrow.down.fill", iconColor: .indigo, title: "iCloud 自动同步")
                    }
                }
                
                Section(header: Text("成绩同步"), footer: Text("开启后，在保存成绩时，App 会自动将新成绩同步上传到你已绑定的外部查分器。")) {
                    Toggle("自动同步成绩", isOn: Binding(
                        get: { config?.isAutoUploadEnabled ?? false },
                        set: { newValue in
                            if let c = config {
                                c.isAutoUploadEnabled = newValue
                            } else {
                                let newConfig = SyncConfig(isAutoUploadEnabled: newValue)
                                modelContext.insert(newConfig)
                            }
                        }
                    ))
                }
                
                // Appearance Section
                Section("外观") {
                    Picker(selection: $selectedTheme) {
                        Text("跟随系统").tag(0)
                        Text("浅色").tag(1)
                        Text("深色").tag(2)
                    } label: {
                        settingsRowLabel(icon: "moon.fill", iconColor: .indigo, title: "主题")
                    }
                    
                    Toggle(isOn: $hapticFeedback) {
                        settingsRowLabel(icon: "hand.tap.fill", iconColor: .orange, title: "触觉反馈")
                    }
                }
                
                // Notifications Section
                Section("通知") {
                    Toggle(isOn: $notificationsEnabled) {
                        settingsRowLabel(icon: "bell.badge.fill", iconColor: .red, title: "推送通知")
                    }
                }
                
                // About Section
                Section("关于") {
                    settingsRow(icon: "info.circle.fill", iconColor: .gray, title: "版本", value: "1.0.0")
                    
                    HStack {
                        settingsRowLabel(icon: "star.fill", iconColor: .yellow, title: "给 App 评分")
                        Spacer()
                        Image(systemName: "arrow.up.forward")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                    
                    HStack {
                        settingsRowLabel(icon: "envelope.fill", iconColor: .green, title: "反馈与建议")
                        Spacer()
                        Image(systemName: "arrow.up.forward")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("设置")
        }
    }
    
    private func settingsRow(icon: String, iconColor: Color, title: String, value: String) -> some View {
        HStack {
            settingsRowLabel(icon: icon, iconColor: iconColor, title: title)
            Spacer()
            Text(value)
                .font(.system(size: 15))
                .foregroundColor(.secondary)
        }
    }
    
    private func settingsRowLabel(icon: String, iconColor: Color, title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(.white)
                .frame(width: 28, height: 28)
                .background(iconColor, in: RoundedRectangle(cornerRadius: 6))
            
            Text(title)
                .font(.system(size: 16))
        }
    }
}

#Preview {
    SettingsView()
}
