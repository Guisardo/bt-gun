import CoreHID
import Darwin
import Foundation

private let btGunVendorID: UInt32 = 0x1209
private let btGunProductID: UInt32 = 0xB707
private let inputReportID: UInt8 = 0x01
private let outputReportID: UInt8 = 0x02
private let inputReportLength = 10

@available(macOS 15.0, *)
private final class BtGunVirtualDeviceDelegate: HIDVirtualDeviceDelegate {
    func hidVirtualDevice(
        _ device: HIDVirtualDevice,
        receivedSetReportRequestOfType type: HIDReportType,
        id: HIDReportID?,
        data: Data
    ) async throws {
        print("OUTPUT_REPORT type=\(type) id=\(id?.rawValue ?? 0) length=\(data.count)")
    }

    func hidVirtualDevice(
        _ device: HIDVirtualDevice,
        receivedGetReportRequestOfType type: HIDReportType,
        id: HIDReportID?,
        maxSize: Int
    ) async throws -> Data {
        print("GET_REPORT type=\(type) id=\(id?.rawValue ?? 0) maxSize=\(maxSize)")
        if id?.rawValue == outputReportID {
            return Data([outputReportID, 0x01, 0x00, 0x64, 0x00, 0xF4, 0x01, 0x00, 0x00])
        }
        return Data()
    }
}

@main
private struct BtGunMacosHidHelper {
    static func main() async {
        guard #available(macOS 15.0, *) else {
            fputs("ERR macOS 15 or newer required for CoreHID HIDVirtualDevice\n", stderr)
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

        let properties = HIDVirtualDevice.Properties(
            descriptor: Data(btGunGamepadDescriptor),
            vendorID: btGunVendorID,
            productID: btGunProductID,
            transport: .usb,
            product: "BT Gun Virtual Joystick",
            manufacturer: "BT Gun",
            modelNumber: "BTGUN-MACOS-COREHID-PROBE",
            versionNumber: 1,
            serialNumber: "BTGUN-PHASE7-PROBE",
            uniqueID: "BTGUN-PHASE7-COREHID",
            locationID: 1
        )

        guard let device = HIDVirtualDevice(properties: properties) else {
            throw HelperError.virtualDeviceCreateFailed
        }

        let delegate = BtGunVirtualDeviceDelegate()
        await device.activate(delegate: delegate)
        try await device.dispatchInputReport(data: neutralInputReport(), timestamp: SuspendingClock().now)
        print("READY name=\"BT Gun Virtual Joystick\" vendor=0x1209 product=0xB707 inputReport=0x01 outputReport=0x02")

        if holdSeconds > 0 {
            try await Task.sleep(nanoseconds: UInt64(holdSeconds) * 1_000_000_000)
        }
    }
}

private enum HelperError: Error {
    case virtualDeviceCreateFailed
}

private func sanitized(error: Error) -> String {
    let text = String(describing: error)
    return text
        .replacingOccurrences(
            of: #"[A-Fa-f0-9]{40}"#,
            with: "<redacted-signing-identity-hash>",
            options: .regularExpression
        )
        .replacingOccurrences(
            of: #"/Users/[^ ]+"#,
            with: "<redacted-user-path>",
            options: .regularExpression
        )
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
