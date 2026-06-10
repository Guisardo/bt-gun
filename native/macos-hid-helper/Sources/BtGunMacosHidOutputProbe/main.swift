import CoreFoundation
import Foundation
import IOKit.hid

private let btGunVendorID = 0x1209
private let btGunProductID = 0xB707
private let btGunProductName = "BT Gun Virtual Joystick"
private let outputReportID = 0x02
private let outputReportVersion = 0x01
private let reportLength = 9

@main
private struct BtGunMacosHidOutputProbe {
    static func main() {
        do {
            let options = try ProbeOptions(arguments: Array(CommandLine.arguments.dropFirst()))
            let report = outputReport(strength: options.strength, durationMs: options.durationMs, ttlMs: options.ttlMs)
            let device = try findBtGunDevice()
            try open(device: device)
            let result = report.withUnsafeBufferPointer { buffer -> IOReturn in
                let baseAddress = buffer.baseAddress!
                return IOHIDDeviceSetReport(
                    device,
                    kIOHIDReportTypeOutput,
                    CFIndex(outputReportID),
                    baseAddress,
                    CFIndex(buffer.count)
                )
            }
            guard result == kIOReturnSuccess else {
                throw ProbeError.setReportFailed(result)
            }
            print("OK BtGunMacosHidOutputProbe IOHIDDeviceSetReport reportId=0x02 bytes=\(report.toHex())")
        } catch {
            fputs("ERR \(sanitized(error: error))\n", stderr)
            exit(1)
        }
    }
}

private struct ProbeOptions {
    let strength: UInt8
    let durationMs: UInt16
    let ttlMs: UInt16

    init(arguments: [String]) throws {
        strength = try parseUInt8(arguments: arguments, flag: "--strength", defaultValue: 128)
        durationMs = try parseUInt16(arguments: arguments, flag: "--duration-ms", defaultValue: 120)
        ttlMs = try parseUInt16(arguments: arguments, flag: "--ttl-ms", defaultValue: 500)
        guard durationMs > 0 && durationMs <= 1_000 else {
            throw ProbeError.invalidArgument("--duration-ms must be 1...1000")
        }
        guard ttlMs > 0 && ttlMs <= 2_000 else {
            throw ProbeError.invalidArgument("--ttl-ms must be 1...2000")
        }
    }
}

private func findBtGunDevice() throws -> IOHIDDevice {
    let manager = IOHIDManagerCreate(kCFAllocatorDefault, IOOptionBits(kIOHIDOptionsTypeNone))
    let match: [String: Any] = [
        kIOHIDVendorIDKey: btGunVendorID,
        kIOHIDProductIDKey: btGunProductID,
    ]
    IOHIDManagerSetDeviceMatching(manager, match as CFDictionary)
    let openResult = IOHIDManagerOpen(manager, IOOptionBits(kIOHIDOptionsTypeNone))
    guard openResult == kIOReturnSuccess else {
        throw ProbeError.managerOpenFailed(openResult)
    }
    guard let devices = IOHIDManagerCopyDevices(manager) as? Set<IOHIDDevice>, !devices.isEmpty else {
        throw ProbeError.deviceNotFound
    }

    let matchingDevices = devices.filter { device in
        let product = stringProperty(device: device, key: kIOHIDProductKey)
        let vendor = intProperty(device: device, key: kIOHIDVendorIDKey)
        let productID = intProperty(device: device, key: kIOHIDProductIDKey)
        return vendor == btGunVendorID &&
            productID == btGunProductID &&
            (product == btGunProductName || product?.contains("BT Gun") == true)
    }

    guard let device = matchingDevices.first ?? devices.first else {
        throw ProbeError.deviceNotFound
    }
    validateUsageOrDescriptorIfPresent(device: device)
    return device
}

private func validateUsageOrDescriptorIfPresent(device: IOHIDDevice) {
    let usagePage = intProperty(device: device, key: kIOHIDPrimaryUsagePageKey)
    let usage = intProperty(device: device, key: kIOHIDPrimaryUsageKey)
    if let usagePage, let usage {
        guard usagePage == 0x01 && usage == 0x05 else {
            fputs("WARN usage mismatch page=0x\(String(usagePage, radix: 16)) usage=0x\(String(usage, radix: 16))\n", stderr)
            return
        }
    }
    if let descriptor = IOHIDDeviceGetProperty(device, kIOHIDReportDescriptorKey as CFString) as? Data,
       !descriptor.contains(UInt8(outputReportID)) {
        fputs("WARN report descriptor missing output report id 0x02\n", stderr)
    }
}

private func open(device: IOHIDDevice) throws {
    let result = IOHIDDeviceOpen(device, IOOptionBits(kIOHIDOptionsTypeNone))
    guard result == kIOReturnSuccess else {
        throw ProbeError.deviceOpenFailed(result)
    }
}

private func outputReport(strength: UInt8, durationMs: UInt16, ttlMs: UInt16) -> [UInt8] {
    [
        UInt8(outputReportID),
        UInt8(outputReportVersion),
        strength,
        UInt8(durationMs & 0x00ff),
        UInt8((durationMs >> 8) & 0x00ff),
        UInt8(ttlMs & 0x00ff),
        UInt8((ttlMs >> 8) & 0x00ff),
        0,
        0,
    ]
}

private func parseUInt8(arguments: [String], flag: String, defaultValue: UInt8) throws -> UInt8 {
    guard let value = valueAfter(flag: flag, arguments: arguments) else {
        return defaultValue
    }
    guard let parsed = UInt8(value) else {
        throw ProbeError.invalidArgument("\(flag) must be 0...255")
    }
    return parsed
}

private func parseUInt16(arguments: [String], flag: String, defaultValue: UInt16) throws -> UInt16 {
    guard let value = valueAfter(flag: flag, arguments: arguments) else {
        return defaultValue
    }
    guard let parsed = UInt16(value) else {
        throw ProbeError.invalidArgument("\(flag) must be 0...65535")
    }
    return parsed
}

private func valueAfter(flag: String, arguments: [String]) -> String? {
    guard let index = arguments.firstIndex(of: flag) else {
        return nil
    }
    let valueIndex = arguments.index(after: index)
    guard valueIndex < arguments.endIndex else {
        return nil
    }
    return arguments[valueIndex]
}

private func stringProperty(device: IOHIDDevice, key: String) -> String? {
    IOHIDDeviceGetProperty(device, key as CFString) as? String
}

private func intProperty(device: IOHIDDevice, key: String) -> Int? {
    if let number = IOHIDDeviceGetProperty(device, key as CFString) as? NSNumber {
        return number.intValue
    }
    if let value = IOHIDDeviceGetProperty(device, key as CFString) as? Int {
        return value
    }
    return nil
}

private enum ProbeError: Error, CustomStringConvertible {
    case invalidArgument(String)
    case managerOpenFailed(IOReturn)
    case deviceNotFound
    case deviceOpenFailed(IOReturn)
    case setReportFailed(IOReturn)

    var description: String {
        switch self {
        case .invalidArgument(let message):
            message
        case .managerOpenFailed(let code):
            "IOHIDManagerOpen failed \(code)"
        case .deviceNotFound:
            "BT Gun Virtual Joystick 0x1209:0xB707 not found"
        case .deviceOpenFailed(let code):
            "IOHIDDeviceOpen failed \(code)"
        case .setReportFailed(let code):
            "IOHIDDeviceSetReport failed \(code)"
        }
    }
}

private func sanitized(error: Error) -> String {
    let text = String(describing: error)
    return text
        .replacingOccurrences(
            of: #"/Users/[^ ]+"#,
            with: "redacted",
            options: .regularExpression
        )
        .replacingOccurrences(
            of: #"[^A-Za-z0-9_.: -]"#,
            with: "-",
            options: .regularExpression
        )
}

private extension Array where Element == UInt8 {
    func toHex() -> String {
        map { String(format: "%02x", $0) }.joined()
    }
}
