import CoreHID
import Darwin
import Foundation

private let btGunVendorID: UInt32 = 0x1209
private let btGunProductID: UInt32 = 0xB707
private let inputReportID: UInt8 = 0x01
private let outputReportID: UInt8 = 0x02
private let inputReportLength = 10
private let outputReportLength = 9
private let protocolVersion = 1

@available(macOS 15.0, *)
private final class HelperState: @unchecked Sendable {
    private let lock = NSLock()
    private var outputReports: [[UInt8]] = []
    private(set) var inputReportsSubmitted: UInt64 = 0
    private(set) var malformedInputReports: UInt64 = 0
    private(set) var malformedOutputReports: UInt64 = 0
    private(set) var setReportCallbackSeen = false

    func recordSubmittedInput() {
        lock.withLock {
            inputReportsSubmitted += 1
        }
    }

    func recordMalformedInput() {
        lock.withLock {
            malformedInputReports += 1
        }
    }

    func enqueueOutputReport(type: HIDReportType, id: HIDReportID?, data: Data) {
        lock.withLock {
            guard type == .output else {
                malformedOutputReports += 1
                return
            }
            let raw = [UInt8](data)
            let report: [UInt8]
            if raw.count == outputReportLength && raw.first == outputReportID {
                report = raw
            } else if id?.rawValue == outputReportID && raw.count == outputReportLength - 1 {
                report = [outputReportID] + raw
            } else {
                malformedOutputReports += 1
                return
            }
            setReportCallbackSeen = true
            outputReports.append(report)
        }
    }

    func dequeueOutputReport() -> [UInt8]? {
        lock.withLock {
            guard !outputReports.isEmpty else {
                return nil
            }
            return outputReports.removeFirst()
        }
    }

    func status(deviceActive: Bool) -> String {
        lock.withLock {
            """
            STATUS {"version":\(protocolVersion),"deviceActive":\(deviceActive),"osVisible":false,"setReportCallbackSeen":\(setReportCallbackSeen),"inputReportsSubmitted":\(inputReportsSubmitted),"outputReportsQueued":\(outputReports.count),"malformedInputReports":\(malformedInputReports),"malformedOutputReports":\(malformedOutputReports)}
            """
        }
    }
}

@available(macOS 15.0, *)
private final class BtGunVirtualDeviceDelegate: HIDVirtualDeviceDelegate {
    private let state: HelperState

    init(state: HelperState) {
        self.state = state
    }

    func hidVirtualDevice(
        _ device: HIDVirtualDevice,
        receivedSetReportRequestOfType type: HIDReportType,
        id: HIDReportID?,
        data: Data
    ) async throws {
        state.enqueueOutputReport(type: type, id: id, data: data)
    }

    func hidVirtualDevice(
        _ device: HIDVirtualDevice,
        receivedGetReportRequestOfType type: HIDReportType,
        id: HIDReportID?,
        maxSize: Int
    ) async throws -> Data {
        if type == .output && id?.rawValue == outputReportID {
            return Data([outputReportID, 0x01, 0x00, 0x64, 0x00, 0xF4, 0x01, 0x00, 0x00])
        }
        return Data()
    }
}

@main
private struct BtGunMacosHidHelper {
    static func main() async {
        guard #available(macOS 15.0, *) else {
            fputs("ERR unsupported-macos\n", stderr)
            exit(78)
        }

        do {
            try await run()
        } catch {
            fputs("ERR \(sanitized(error: error))\n", stderr)
            exit(1)
        }
    }

    @available(macOS 15.0, *)
    private static func run() async throws {
        let arguments = CommandLine.arguments.dropFirst()
        let probeMode = arguments.contains("--probe")
        let holdSeconds = parseHoldSeconds(arguments: Array(arguments)) ?? (probeMode ? 5 : 0)
        let runtime = try await createRuntime()

        if probeMode {
            try await runtime.device.dispatchInputReport(data: neutralInputReport(), timestamp: SuspendingClock().now)
            print("READY name=\"BT Gun Virtual Joystick\" vendor=0x1209 product=0xB707 inputReport=0x01 outputReport=0x02")
            fflush(stdout)
            if holdSeconds > 0 {
                try await Task.sleep(nanoseconds: UInt64(holdSeconds) * 1_000_000_000)
            }
            return
        }

        runLineProtocol(runtime: runtime)
    }

    @available(macOS 15.0, *)
    private static func createRuntime() async throws -> HelperRuntime {
        let properties = HIDVirtualDevice.Properties(
            descriptor: Data(btGunGamepadDescriptor),
            vendorID: btGunVendorID,
            productID: btGunProductID,
            transport: .usb,
            product: "BT Gun Virtual Joystick",
            manufacturer: "BT Gun",
            modelNumber: "BTGUN-MACOS-COREHID-PROTOCOL",
            versionNumber: 1,
            serialNumber: "BTGUN-PHASE7-PROTOCOL",
            uniqueID: "BTGUN-PHASE7-COREHID",
            locationID: 1
        )

        guard let device = HIDVirtualDevice(properties: properties) else {
            throw HelperError.virtualDeviceCreateFailed
        }

        let state = HelperState()
        let delegate = BtGunVirtualDeviceDelegate(state: state)
        await device.activate(delegate: delegate)
        return HelperRuntime(device: device, delegate: delegate, state: state)
    }

    @available(macOS 15.0, *)
    private static func runLineProtocol(runtime: HelperRuntime) {
        while let line = readLine(strippingNewline: true) {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                print("ERR empty-command")
                fflush(stdout)
                continue
            }

            if trimmed == "HELLO 1" {
                print("OK")
            } else if trimmed.hasPrefix("SUBMIT_INPUT ") {
                handleSubmitInput(trimmed, runtime: runtime)
            } else if trimmed == "READ_OUTPUT" {
                if let report = runtime.state.dequeueOutputReport() {
                    print("OUTPUT \(report.toHex())")
                } else {
                    print("OK")
                }
            } else if trimmed == "STATUS" {
                print(runtime.state.status(deviceActive: true))
            } else if trimmed == "QUIT" {
                print("OK")
                fflush(stdout)
                return
            } else {
                print("ERR unknown-command")
            }
            fflush(stdout)
        }
    }

    @available(macOS 15.0, *)
    private static func handleSubmitInput(_ command: String, runtime: HelperRuntime) {
        let payload = String(command.dropFirst("SUBMIT_INPUT ".count))
        guard let bytes = payload.hexToBytes(),
              bytes.count == inputReportLength,
              bytes.first == inputReportID else {
            runtime.state.recordMalformedInput()
            print("ERR bad-input")
            return
        }

        Task {
            do {
                try await runtime.device.dispatchInputReport(data: Data(bytes), timestamp: SuspendingClock().now)
                runtime.state.recordSubmittedInput()
                print("OK")
            } catch {
                runtime.state.recordMalformedInput()
                print("ERR dispatch-failed")
            }
            fflush(stdout)
        }
    }
}

@available(macOS 15.0, *)
private struct HelperRuntime {
    let device: HIDVirtualDevice
    let delegate: BtGunVirtualDeviceDelegate
    let state: HelperState
}

private enum HelperError: Error {
    case virtualDeviceCreateFailed
}

private func sanitized(error: Error) -> String {
    let text = String(describing: error)
    let token = text
        .replacingOccurrences(
            of: #"[A-Fa-f0-9]{40}"#,
            with: "redacted",
            options: .regularExpression
        )
        .replacingOccurrences(
            of: #"/Users/[^ ]+"#,
            with: "redacted",
            options: .regularExpression
        )
        .replacingOccurrences(
            of: #"[^A-Za-z0-9_.:-]"#,
            with: "-",
            options: .regularExpression
        )
    return token.isEmpty ? "helper-error" : token
}

private func parseHoldSeconds(arguments: [String]) -> Int? {
    guard let index = arguments.firstIndex(of: "--hold-seconds") else {
        return nil
    }
    let valueIndex = arguments.index(after: index)
    guard valueIndex < arguments.endIndex else {
        return nil
    }
    return Int(arguments[valueIndex])
}

private func neutralInputReport() -> Data {
    var report = Data(repeating: 0, count: inputReportLength)
    report[0] = inputReportID
    return report
}

private extension NSLock {
    func withLock<T>(_ body: () -> T) -> T {
        lock()
        defer { unlock() }
        return body()
    }
}

private extension Array where Element == UInt8 {
    func toHex() -> String {
        map { String(format: "%02x", $0) }.joined()
    }
}

private extension String {
    func hexToBytes() -> [UInt8]? {
        let compact = filter { !$0.isWhitespace }
        guard compact.count % 2 == 0 else {
            return nil
        }
        var bytes: [UInt8] = []
        bytes.reserveCapacity(compact.count / 2)
        var index = compact.startIndex
        while index < compact.endIndex {
            let next = compact.index(index, offsetBy: 2)
            guard let byte = UInt8(compact[index..<next], radix: 16) else {
                return nil
            }
            bytes.append(byte)
            index = next
        }
        return bytes
    }
}

private let btGunGamepadDescriptor: [UInt8] = [
    0x05, 0x01,       // Usage Page (Generic Desktop)
    0x09, 0x05,       // Usage (Game Pad)
    0xA1, 0x01,       // Collection (Application)
    0x85, 0x01,       //   Report ID (1)
    0x05, 0x09,       //   Usage Page (Button)
    0x19, 0x01,       //   Usage Minimum (Button 1)
    0x29, 0x06,       //   Usage Maximum (Button 6)
    0x15, 0x00,       //   Logical Minimum (0)
    0x25, 0x01,       //   Logical Maximum (1)
    0x75, 0x01,       //   Report Size (1)
    0x95, 0x06,       //   Report Count (6)
    0x81, 0x02,       //   Input (Data, Variable, Absolute)
    0x75, 0x01,       //   Report Size (1)
    0x95, 0x02,       //   Report Count (2)
    0x81, 0x03,       //   Input (Constant, Variable, Absolute)
    0x05, 0x01,       //   Usage Page (Generic Desktop)
    0x09, 0x30,       //   Usage (X)
    0x09, 0x31,       //   Usage (Y)
    0x09, 0x33,       //   Usage (Rx)
    0x09, 0x34,       //   Usage (Ry)
    0x16, 0x00, 0x80, //   Logical Minimum (-32768)
    0x26, 0xFF, 0x7F, //   Logical Maximum (32767)
    0x75, 0x10,       //   Report Size (16)
    0x95, 0x04,       //   Report Count (4)
    0x81, 0x02,       //   Input (Data, Variable, Absolute)
    0x85, 0x02,       //   Report ID (2)
    0x06, 0x00, 0xFF, //   Usage Page (Vendor Defined)
    0x09, 0x01,       //   Usage (1)
    0x15, 0x00,       //   Logical Minimum (0)
    0x26, 0xFF, 0x00, //   Logical Maximum (255)
    0x75, 0x08,       //   Report Size (8)
    0x95, 0x08,       //   Report Count (8)
    0x91, 0x02,       //   Output (Data, Variable, Absolute)
    0xC0,             // End Collection
]
