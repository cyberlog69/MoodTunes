import SwiftUI
import SharedCore

struct ContentView: View {
    @State private var selectedMood: SharedMoodType = .happy
    @State private var platformName: String = ""
    @State private var sampleSong: SharedSong = SharedSong(
        id: 1,
        title: "Midnight City",
        artist: "M83",
        album: "Hurry Up, We're Dreaming",
        duration: 243000,
        uri: "https://example.com/stream.mp3",
        albumArtUri: nil,
        genre: "Synthwave",
        isFavorite: true,
        moodTags: [.euphoric, .energetic],
        audioFormat: .flac,
        isStream: false,
        isPreview: false,
        playCount: 42,
        lastPlayedAt: 0
    )

    var body: some View {
        ZStack {
            // Background subtle gradient
            LinearGradient(
                colors: [
                    Color(hex: selectedMood.gradientStartHex).opacity(0.4),
                    Color(hex: selectedMood.gradientEndHex).opacity(0.8),
                    Color(hex: 0x0A0A16)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("MoodTunes iOS")
                                .font(.system(size: 28, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                            Text("Kotlin Multiplatform Powered • \(platformName)")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.white.opacity(0.7))
                        }
                        Spacer()
                        Circle()
                            .fill(Color(hex: selectedMood.gradientStartHex))
                            .frame(width: 44, height: 44)
                            .overlay(
                                Text(selectedMood.emoji)
                                    .font(.system(size: 22))
                            )
                            .shadow(color: Color(hex: selectedMood.gradientStartHex).opacity(0.5), radius: 10)
                    }
                    .padding(.horizontal)
                    .padding(.top, 16)

                    // Mood Selector Grid
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Select Mood")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal)

                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                            ForEach([SharedMoodType.happy, SharedMoodType.sad, SharedMoodType.energetic, SharedMoodType.calm, SharedMoodType.euphoric, SharedMoodType.sleep], id: \.displayName) { mood in
                                Button(action: {
                                    withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                                        selectedMood = mood
                                    }
                                }) {
                                    VStack(spacing: 6) {
                                        Text(mood.emoji)
                                            .font(.system(size: 28))
                                        Text(mood.displayName)
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.white)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(
                                        RoundedRectangle(cornerRadius: 16)
                                            .fill(selectedMood == mood ?
                                                  LinearGradient(colors: [Color(hex: mood.gradientStartHex), Color(hex: mood.gradientEndHex)], startPoint: .top, endPoint: .bottom) :
                                                  LinearGradient(colors: [Color.white.opacity(0.08), Color.white.opacity(0.04)], startPoint: .top, endPoint: .bottom))
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .stroke(selectedMood == mood ? Color.white.opacity(0.5) : Color.white.opacity(0.1), lineWidth: 1)
                                    )
                                }
                            }
                        }
                        .padding(.horizontal)
                    }

                    // Active Mood Banner
                    HStack(spacing: 16) {
                        Text(selectedMood.emoji)
                            .font(.system(size: 36))
                        VStack(alignment: .leading, spacing: 4) {
                            Text(selectedMood.displayName)
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)
                            Text(selectedMood.description_)
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.8))
                        }
                        Spacer()
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(LinearGradient(
                                colors: [Color(hex: selectedMood.gradientStartHex).opacity(0.6), Color(hex: selectedMood.gradientEndHex).opacity(0.6)],
                                startPoint: .leading,
                                endPoint: .trailing
                            ))
                    )
                    .padding(.horizontal)

                    // Now Playing Card (Using SharedSong & SharedAudioFormat)
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Text("Now Playing Preview")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white.opacity(0.8))
                            Spacer()
                            Text(sampleSong.audioFormat.displayName)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.black)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color(hex: sampleSong.audioFormat.badgeColorHex))
                                .cornerRadius(8)
                        }

                        HStack(spacing: 16) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 14)
                                    .fill(LinearGradient(colors: [Color(hex: 0x8E24AA), Color(hex: 0xE91E63)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 64, height: 64)
                                Image(systemName: "music.note")
                                    .font(.system(size: 26))
                                    .foregroundColor(.white)
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                Text(sampleSong.title)
                                    .font(.system(size: 17, weight: .bold))
                                    .foregroundColor(.white)
                                Text(sampleSong.artist + " • " + sampleSong.album)
                                    .font(.system(size: 13))
                                    .foregroundColor(.white.opacity(0.7))
                                Text(sampleSong.formattedDuration)
                                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                                    .foregroundColor(Color(hex: 0x00E676))
                            }
                            Spacer()
                        }
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.white.opacity(0.06))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.white.opacity(0.12), lineWidth: 1)
                    )
                    .padding(.horizontal)

                    // KMP Shared Capabilities Info Box
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Shared KMP Architecture")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                        VStack(alignment: .leading, spacing: 6) {
                            FeatureRow(icon: "checkmark.seal.fill", title: "Single Shared Core (:shared)")
                            FeatureRow(icon: "network", title: "Ktor 3 Multiplatform Engine (Darwin/OkHttp)")
                            FeatureRow(icon: "music.mic", title: "Unified Synced LRC & TTML Parser")
                            FeatureRow(icon: "gearshape.2.fill", title: "Automated GitHub Actions CI/CD IPA Export")
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.white.opacity(0.05))
                    )
                    .padding(.horizontal)

                    Spacer(minLength: 40)
                }
            }
        }
        .onAppear {
            let platform = PlatformKt.getPlatform()
            platformName = platform.name
        }
    }
}

struct FeatureRow: View {
    let icon: String
    let title: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundColor(Color(hex: 0x00E676))
                .font(.system(size: 14))
            Text(title)
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.85))
        }
    }
}

// SwiftUI Color extension for Hex Int64 support
extension Color {
    init(hex: Int64) {
        let red = Double((hex >> 16) & 0xFF) / 255.0
        let green = Double((hex >> 8) & 0xFF) / 255.0
        let blue = Double(hex & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue)
    }
}
