import Foundation
import SystemExtensions

private let extensionIdentifier = "com.btgun.driver.BTGunHidDriver"

private final class ExtensionRequestDelegate: NSObject, OSSystemExtensionRequestDelegate {
    func requestNeedsUserApproval(_ request: OSSystemExtensionRequest) {
        print("USER APPROVAL REQUIRED: approve \(request.identifier) in System Settings")
    }

    func request(_ request: OSSystemExtensionRequest, didFinishWithResult result: OSSystemExtensionRequest.Result) {
        print("system-extension-result \(request.identifier) \(result.rawValue)")
        CFRunLoopStop(CFRunLoopGetMain())
    }

    func request(_ request: OSSystemExtensionRequest, didFailWithError error: Error) {
        print("system-extension-error \(request.identifier) \(sanitized(error))")
        CFRunLoopStop(CFRunLoopGetMain())
    }

    func request(_ request: OSSystemExtensionRequest, actionForReplacingExtension existing: OSSystemExtensionProperties, withExtension newExtension: OSSystemExtensionProperties) -> OSSystemExtensionRequest.ReplacementAction {
        print("system-extension-replace \(request.identifier) \(existing.bundleShortVersion) -> \(newExtension.bundleShortVersion)")
        return .replace
    }
}

private func sanitized(_ error: Error) -> String {
    let raw = String(describing: error)
    let allowed = raw.map { character -> Character in
        if character.isLetter || character.isNumber || "-_.:".contains(character) {
            return character
        }
        return "-"
    }
    let token = String(allowed.prefix(160))
    return token.isEmpty ? "system-extension-error" : token
}

private func submit(_ request: OSSystemExtensionRequest, delegate: ExtensionRequestDelegate) {
    request.delegate = delegate
    OSSystemExtensionManager.shared.submitRequest(request)
    CFRunLoopRun()
}

let delegate = ExtensionRequestDelegate()
let command = CommandLine.arguments.dropFirst().first ?? "status"

switch command {
case "activate":
    submit(OSSystemExtensionRequest.activationRequest(forExtensionWithIdentifier: extensionIdentifier, queue: .main), delegate: delegate)
case "deactivate":
    submit(OSSystemExtensionRequest.deactivationRequest(forExtensionWithIdentifier: extensionIdentifier, queue: .main), delegate: delegate)
case "status":
    print("BT Gun HID Driver host app scaffold")
    print("extension-id \(extensionIdentifier)")
    print("commands: activate | deactivate | status")
default:
    print("usage: BTGunHidHostApp activate|deactivate|status")
    exit(64)
}
