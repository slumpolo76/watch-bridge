import SwiftUI

struct ContentView: View {
    @State private var bleManager = BLEManager()

    var body: some View {
        VStack(spacing: 8) {
            if bleManager.isConnected {
                Text(bleManager.statusMessage)
                    .font(.headline)
            } else {
                ProgressView()
                Text(bleManager.statusMessage)
                    .font(.caption)
                    .multilineTextAlignment(.center)
            }
        }
        .padding()
        .onAppear {
            bleManager.startScanning()
        }
    }
}

#Preview {
    ContentView()
}
