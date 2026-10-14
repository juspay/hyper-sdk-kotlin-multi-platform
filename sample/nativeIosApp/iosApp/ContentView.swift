import SwiftUI
import UIKit
import SampleCommon

/// Native SwiftUI screen over the shared `Checkout` logic from the Kotlin `SampleCommon` framework.
final class CheckoutModel: ObservableObject {
    @Published var events: [String] = []
    @Published var initialised = false
    private var checkout: Checkout?

    func initiate() {
        guard let viewController = Self.rootViewController() else {
            events.insert("error  no root view controller to present from", at: 0)
            return
        }
        checkout?.terminate()
        do {
            // Platform code creates the session, because it needs a host view controller, and hands it to shared code
            let hyperSdk = try HyperSdk_iosKt.HyperSdk(viewController: viewController, clientId: nil, tenantId: nil)
            let checkout = Checkout(hyperSdk: hyperSdk) { [weak self] line in
                self?.events.insert(line, at: 0)
            }
            self.checkout = checkout
            checkout.initiate()
            initialised = checkout.isInitialised
        } catch {
            events.insert("error  \(error.localizedDescription)", at: 0)
        }
    }

    func pay() {
        checkout?.pay()
    }

    func terminate() {
        checkout?.terminate()
        initialised = false
    }

    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }
            .first
    }
}

struct ContentView: View {
    @StateObject private var model = CheckoutModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("HyperSDK KMP native sample")
                .font(.title2)
            HStack {
                Button("Initiate") { model.initiate() }
                    .disabled(model.initialised)
                Button("Pay ₹1.00") { model.pay() }
                    .disabled(!model.initialised)
                Button("Terminate") { model.terminate() }
                    .disabled(!model.initialised)
            }
            .buttonStyle(.borderedProminent)
            List(model.events, id: \.self) { line in
                Text(line).font(.caption)
            }
            .listStyle(.plain)
        }
        .padding()
    }
}
