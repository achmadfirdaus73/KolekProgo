import SwiftUI

struct ContentView: View {
    @State private var tag: String = "KolekPro - Penagihan"
    @State private var locationText: String = "-6.175392, 106.827153 (DKI Jakarta)"
    @State private var isAuthentic: Bool = true
    @State private var isSyncing: Bool = false
    @State private var syncStatus: String = "Siap mengambil foto tervalidasi"

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 0.05, green: 0.07, blue: 0.12)
                    .ignoresSafeArea()

                VStack(spacing: 20) {
                    // Camera Watermark Preview Mock Frame
                    ZStack(alignment: .bottomLeading) {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color(red: 0.1, green: 0.15, blue: 0.22))
                            .overlay(
                                Image(systemName: "camera.viewfinder")
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 80, height: 80)
                                    .foregroundColor(.gray.opacity(0.4))
                            )
                            .frame(height: 320)

                        // Watermark Overlay Banner
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(tag)
                                    .font(.caption.bold())
                                    .foregroundColor(.white)
                                Spacer()
                                Label("GPS ASLI", systemImage: "checkmark.shield.fill")
                                    .font(.caption2.bold())
                                    .foregroundColor(.green)
                            }

                            Text("Waktu: 13 Okt 2026 12:00 WIB")
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.8))

                            Text("Koordinat: \(locationText)")
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.8))
                        }
                        .padding(12)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(10)
                        .padding(12)
                    }
                    .padding(.horizontal)

                    // Details Card
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "shield.lefthalf.filled")
                                .foregroundColor(.cyan)
                            Text("Anti-Fake GPS Engine")
                                .font(.headline)
                                .foregroundColor(.white)
                            Spacer()
                            Text("Terverifikasi")
                                .font(.subheadline)
                                .foregroundColor(.green)
                        }

                        Divider().background(Color.gray.opacity(0.3))

                        HStack {
                            Image(systemName: "tablecells")
                                .foregroundColor(.yellow)
                            Text("Google Sheets Sync")
                                .font(.subheadline)
                                .foregroundColor(.white)
                            Spacer()
                            Text("Terhubung")
                                .font(.caption)
                                .foregroundColor(.green)
                        }
                    }
                    .padding(16)
                    .background(Color(red: 0.12, green: 0.16, blue: 0.24))
                    .cornerRadius(12)
                    .padding(.horizontal)

                    Spacer()

                    // Action Button
                    Button(action: {
                        syncStatus = "Foto disimpan & disinkronkan ke Google Sheets!"
                    }) {
                        HStack {
                            Image(systemName: "camera.fill")
                            Text("Ambil Foto Tervalidasi")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.cyan)
                        .foregroundColor(.black)
                        .cornerRadius(12)
                    }
                    .padding(.horizontal)

                    Text(syncStatus)
                        .font(.footnote)
                        .foregroundColor(.gray)
                        .padding(.bottom, 8)
                }
                .padding(.top)
            }
            .navigationTitle("KolekPro Cam iOS")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
