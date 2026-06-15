import CoreHaptics
import Foundation
import GameController
import IOKit.hid

private let schema = "btgun.phase7.gamecontroller_probe.v1"
private let defaultCaptureID = "phase7-macos-gamecontroller-live"
private let defaultDurationSeconds = 45.0
private let axisThreshold: Float = 0.20
private let hidAxisThreshold = 6_553
private let labelTokens = ["bt gun", "btgun", "android", "hid gamepad"]

private let options = ProbeOptions(arguments: Array(CommandLine.arguments.dropFirst()))
private let state = ProbeState(options: options)

GCController.shouldMonitorBackgroundEvents = true
GCController.startWirelessControllerDiscovery {
    state.emit(
        event: "wireless-discovery-completed",
        fields: ["status": "completed"]
    )
}

state.emit(
    event: "probe-start",
    fields: [
        "duration_seconds": options.durationSeconds,
        "target_label": "sanitized-bt-gun-android",
        "allow_generic_controller": options.allowGenericController,
        "status": "running",
    ]
)

private let timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
    state.scanControllers()
}
state.scanControllers()
state.startIOHIDFallback()

RunLoop.current.run(until: Date().addingTimeInterval(options.durationSeconds))
timer.invalidate()
GCController.stopWirelessControllerDiscovery()
state.emitSummary()

private final class ProbeOptions {
    let captureID: String
    let durationSeconds: TimeInterval
    let probeHaptics: Bool
    let allowGenericController: Bool

    init(arguments: [String]) {
        captureID = valueAfter("--capture-id", in: arguments) ?? defaultCaptureID
        durationSeconds = TimeInterval(valueAfter("--duration-seconds", in: arguments) ?? "") ?? defaultDurationSeconds
        probeHaptics = !arguments.contains("--no-haptics")
        allowGenericController = arguments.contains("--allow-generic-controller")
    }
}

private final class ProbeState {
    private let options: ProbeOptions
    private var selectedController: GCController?
    private var selectedSlot: Int?
    private var selectedLabelMatch = "none"
    private var emittedKeys: Set<String> = []
    private var observed: Set<String> = []
    private var hapticsAttempted = false
    private var hidFallbackVisible = false
    private var hidManager: IOHIDManager?
    private var selectedHIDDevicePointer: UnsafeMutableRawPointer?

    init(options: ProbeOptions) {
        self.options = options
    }

    func scanControllers() {
        let controllers = GCController.controllers()
        guard !controllers.isEmpty else {
            emitOnce(
                key: "no-controller-visible",
                event: "controller-visible",
                fields: [
                    "controller_count": 0,
                    "label_match": "none",
                    "status": "not-visible",
                ]
            )
            return
        }

        if selectedController == nil || selectedController?.extendedGamepad == nil {
            let ranked = controllers.enumerated()
                .map { entry -> (offset: Int, controller: GCController, score: Int) in
                    (offset: entry.offset, controller: entry.element, score: score(controller: entry.element))
                }
                .filter { candidate in
                    candidate.score > 0 || options.allowGenericController
                }
                .sorted { left, right in
                    if left.score == right.score {
                        return left.offset < right.offset
                    }
                    return left.score > right.score
                }
            guard let selected = ranked.first else {
                emitOnce(
                    key: "no-btgun-controller-visible",
                    event: "controller-visible",
                    fields: [
                        "controller_count": controllers.count,
                        "label_match": "generic-controller-only",
                        "allow_generic_controller": options.allowGenericController,
                        "status": "not-visible",
                    ]
                )
                return
            }
            selectedController = selected.controller
            selectedSlot = selected.offset
            selectedLabelMatch = selected.score > 0 ? matchClass(controller: selected.controller) : "generic-controller-allowed"
            attach(to: selected.controller, slot: selected.offset, labelMatch: selectedLabelMatch)
        }

        if options.probeHaptics, !hapticsAttempted, let controller = selectedController {
            hapticsAttempted = true
            attemptHaptics(controller: controller)
        }
    }

    func emitSummary() {
        emit(
            event: "probe-summary",
            fields: [
                "controller_slot": optionalInt(selectedSlot),
                "label_match": selectedLabelMatch,
                "allow_generic_controller": options.allowGenericController,
                "observed_controls": observed.sorted(),
                "status": selectedController == nil && !hidFallbackVisible ? "no-controller-visible" : "completed",
            ]
        )
    }

    func startIOHIDFallback() {
        let manager = IOHIDManagerCreate(kCFAllocatorDefault, IOOptionBits(kIOHIDOptionsTypeNone))
        let matching: [String: Any] = [
            kIOHIDDeviceUsagePageKey: kHIDPage_GenericDesktop,
            kIOHIDDeviceUsageKey: kHIDUsage_GD_GamePad,
        ]
        IOHIDManagerSetDeviceMatching(manager, matching as CFDictionary)
        let opened = IOHIDManagerOpen(manager, IOOptionBits(kIOHIDOptionsTypeNone))
        guard opened == kIOReturnSuccess else {
            return
        }
        hidManager = manager

        if let devices = IOHIDManagerCopyDevices(manager) as? Set<IOHIDDevice>, !devices.isEmpty {
            let ranked = Array(devices).enumerated()
                .map { entry -> (offset: Int, device: IOHIDDevice, score: Int, labelMatch: String) in
                    let labelMatch = matchClass(device: entry.element)
                    return (
                        offset: entry.offset,
                        device: entry.element,
                        score: labelMatch == "bt-gun-or-android-label" ? 100 : 0,
                        labelMatch: labelMatch
                    )
                }
                .filter { candidate in
                    candidate.score > 0 || options.allowGenericController
                }
                .sorted { left, right in
                    if left.score == right.score {
                        return left.offset < right.offset
                    }
                    return left.score > right.score
                }
            guard let selected = ranked.first else {
                emitOnce(
                    key: "iohid-generic-controller-only",
                    event: "controller-visible",
                    fields: [
                        "controller_count": devices.count,
                        "label_match": "generic-controller-only",
                        "allow_generic_controller": options.allowGenericController,
                        "status": "inconclusive",
                    ]
                )
                IOHIDManagerScheduleWithRunLoop(manager, CFRunLoopGetCurrent(), CFRunLoopMode.defaultMode.rawValue)
                return
            }
            hidFallbackVisible = true
            selectedSlot = selectedSlot ?? selected.offset
            selectedHIDDevicePointer = hidDevicePointer(selected.device)
            selectedLabelMatch = selected.score > 0 ? selected.labelMatch : "generic-controller-allowed"
            emit(
                event: "controller-visible",
                fields: [
                    "controller_slot": optionalInt(selectedSlot),
                    "label_match": selectedLabelMatch,
                    "allow_generic_controller": options.allowGenericController,
                    "product_category_class": "game-controller",
                    "status": "visible",
                ]
            )
            emit(
                event: "extended-gamepad-available",
                fields: [
                    "controller_slot": optionalInt(selectedSlot),
                    "label_match": selectedLabelMatch,
                    "allow_generic_controller": options.allowGenericController,
                    "extended_gamepad": false,
                    "status": "iohid-fallback",
                ]
            )
            if options.probeHaptics && !hapticsAttempted {
                hapticsAttempted = true
                emit(
                    event: "haptic-output-probe-attempted",
                    fields: [
                        "controller_slot": optionalInt(selectedSlot),
                        "label_match": selectedLabelMatch,
                        "allow_generic_controller": options.allowGenericController,
                        "haptics_available": false,
                        "status": "no-gamecontroller-haptics",
                    ]
                )
            }
        }

        let context = Unmanaged.passUnretained(self).toOpaque()
        IOHIDManagerRegisterInputValueCallback(manager, { context, _, _, value in
            guard let context else {
                return
            }
            let state = Unmanaged<ProbeState>.fromOpaque(context).takeUnretainedValue()
            guard state.isSelectedHIDValue(value) else {
                return
            }
            state.handleHIDValue(value)
        }, context)
        IOHIDManagerScheduleWithRunLoop(manager, CFRunLoopGetCurrent(), CFRunLoopMode.defaultMode.rawValue)
    }

    private func handleHIDValue(_ value: IOHIDValue) {
        let element = IOHIDValueGetElement(value)
        let usagePage = Int(IOHIDElementGetUsagePage(element))
        let usage = Int(IOHIDElementGetUsage(element))
        let intValue = IOHIDValueGetIntegerValue(value)
        let slot = selectedSlot ?? 0

        if usagePage == kHIDPage_Button, intValue != 0 {
            if let button = hidButton(usage) {
                emitObservation(
                    key: button.event,
                    event: button.event,
                    fields: [
                        "controller_slot": slot,
                        "label_match": selectedLabelMatch,
                        "control": button.control,
                        "control_source": "iohid-button-\(usage)",
                        "value_bucket": "active",
                        "status": "observed",
                    ]
                )
            }
            return
        }

        guard usagePage == kHIDPage_GenericDesktop else {
            return
        }
        guard abs(intValue) >= hidAxisThreshold else {
            return
        }
        if let axis = hidAxis(usage) {
            emitObservation(
                key: axis.event,
                event: axis.event,
                fields: [
                    "controller_slot": slot,
                    "label_match": selectedLabelMatch,
                    "axis": axis.axis,
                    "axis_source": "iohid-axis-\(usage)",
                    "value_bucket": signedIntegerBucket(intValue),
                    "status": "observed",
                ]
            )
        }
    }

    private func attach(to controller: GCController, slot: Int, labelMatch: String) {
        let hasExtendedGamepad = controller.extendedGamepad != nil
        emit(
            event: "controller-visible",
            fields: [
                "controller_slot": slot,
                "label_match": labelMatch,
                "allow_generic_controller": options.allowGenericController,
                "product_category_class": productCategoryClass(controller.productCategory),
                "status": "visible",
            ]
        )
        emit(
            event: "extended-gamepad-available",
            fields: [
                "controller_slot": slot,
                "label_match": labelMatch,
                "allow_generic_controller": options.allowGenericController,
                "extended_gamepad": hasExtendedGamepad,
                "status": hasExtendedGamepad ? "available" : "missing",
            ]
        )

        guard let gamepad = controller.extendedGamepad else {
            return
        }

        watchButton(gamepad.rightTrigger, slot: slot, labelMatch: labelMatch, event: "trigger-observed", control: "trigger", source: "rightTrigger")
        watchButton(gamepad.rightShoulder, slot: slot, labelMatch: labelMatch, event: "trigger-observed", control: "trigger", source: "rightShoulder")
        watchButton(gamepad.leftShoulder, slot: slot, labelMatch: labelMatch, event: "reload-observed", control: "reload", source: "leftShoulder")
        watchButton(gamepad.leftTrigger, slot: slot, labelMatch: labelMatch, event: "reload-observed", control: "reload", source: "leftTrigger")
        watchButton(gamepad.buttonX, slot: slot, labelMatch: labelMatch, event: "button-x-observed", control: "x", source: "buttonX")
        watchButton(gamepad.buttonY, slot: slot, labelMatch: labelMatch, event: "button-y-observed", control: "y", source: "buttonY")
        watchButton(gamepad.buttonA, slot: slot, labelMatch: labelMatch, event: "button-a-observed", control: "a", source: "buttonA")
        watchButton(gamepad.buttonB, slot: slot, labelMatch: labelMatch, event: "button-b-observed", control: "b", source: "buttonB")

        watchAxes(gamepad.leftThumbstick, slot: slot, labelMatch: labelMatch, xEvent: "stickX-observed", yEvent: "stickY-observed", source: "leftThumbstick")
        watchAxes(gamepad.dpad, slot: slot, labelMatch: labelMatch, xEvent: "stickX-observed", yEvent: "stickY-observed", source: "dpad")
        watchAxes(gamepad.rightThumbstick, slot: slot, labelMatch: labelMatch, xEvent: "aimX-observed", yEvent: "aimY-observed", source: "rightThumbstick")
    }

    private func watchButton(
        _ button: GCControllerButtonInput,
        slot: Int,
        labelMatch: String,
        event: String,
        control: String,
        source: String
    ) {
        button.pressedChangedHandler = { [weak self] _, value, pressed in
            guard pressed else {
                return
            }
            self?.emitObservation(
                key: event,
                event: event,
                fields: [
                    "controller_slot": slot,
                    "label_match": labelMatch,
                    "control": control,
                    "control_source": source,
                    "value_bucket": valueBucket(value),
                    "status": "observed",
                ]
            )
        }
    }

    private func watchAxes(
        _ pad: GCControllerDirectionPad,
        slot: Int,
        labelMatch: String,
        xEvent: String,
        yEvent: String,
        source: String
    ) {
        pad.valueChangedHandler = { [weak self] _, xValue, yValue in
            if abs(xValue) >= axisThreshold {
                self?.emitObservation(
                    key: xEvent,
                    event: xEvent,
                    fields: [
                        "controller_slot": slot,
                        "label_match": labelMatch,
                        "axis": xEvent.replacingOccurrences(of: "-observed", with: ""),
                        "axis_source": source,
                        "value_bucket": signedValueBucket(xValue),
                        "status": "observed",
                    ]
                )
            }
            if abs(yValue) >= axisThreshold {
                self?.emitObservation(
                    key: yEvent,
                    event: yEvent,
                    fields: [
                        "controller_slot": slot,
                        "label_match": labelMatch,
                        "axis": yEvent.replacingOccurrences(of: "-observed", with: ""),
                        "axis_source": source,
                        "value_bucket": signedValueBucket(yValue),
                        "status": "observed",
                    ]
                )
            }
        }
    }

    private func attemptHaptics(controller: GCController) {
        guard #available(macOS 11.0, *) else {
            emit(
                event: "haptic-output-probe-attempted",
                fields: [
                    "controller_slot": optionalInt(selectedSlot),
                    "label_match": selectedLabelMatch,
                    "haptics_available": false,
                    "status": "unsupported-macos",
                ]
            )
            return
        }

        guard let haptics = controller.haptics else {
            emit(
                event: "haptic-output-probe-attempted",
                fields: [
                    "controller_slot": optionalInt(selectedSlot),
                    "label_match": selectedLabelMatch,
                    "haptics_available": false,
                    "status": "no-gamecontroller-haptics",
                ]
            )
            return
        }

        guard let engine = haptics.createEngine(withLocality: GCHapticsLocality.default) else {
            emit(
                event: "haptic-output-probe-attempted",
                fields: [
                    "controller_slot": optionalInt(selectedSlot),
                    "label_match": selectedLabelMatch,
                    "haptics_available": true,
                    "haptic_engine_created": false,
                    "status": "engine-unavailable",
                ]
            )
            return
        }

        do {
            let event = CHHapticEvent(
                eventType: .hapticTransient,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 0.6),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.4),
                ],
                relativeTime: 0
            )
            let pattern = try CHHapticPattern(events: [event], parameters: [])
            let player = try engine.makePlayer(with: pattern)
            try engine.start()
            try player.start(atTime: 0)
            emit(
                event: "haptic-output-probe-attempted",
                fields: [
                    "controller_slot": optionalInt(selectedSlot),
                    "label_match": selectedLabelMatch,
                    "haptics_available": true,
                    "haptic_engine_created": true,
                    "status": "pattern-started",
                ]
            )
        } catch {
            emit(
                event: "haptic-output-probe-attempted",
                fields: [
                    "controller_slot": optionalInt(selectedSlot),
                    "label_match": selectedLabelMatch,
                    "haptics_available": true,
                    "haptic_engine_created": true,
                    "status": "pattern-failed",
                    "blocking_notes": sanitizedError(error),
                ]
            )
        }
    }

    private func emitObservation(key: String, event: String, fields: [String: Any]) {
        guard !observed.contains(key) else {
            return
        }
        observed.insert(key)
        emit(event: event, fields: fields)
    }

    private func isSelectedHIDValue(_ value: IOHIDValue) -> Bool {
        guard let selectedHIDDevicePointer else {
            return false
        }
        let element = IOHIDValueGetElement(value)
        let device = IOHIDElementGetDevice(element)
        return hidDevicePointer(device) == selectedHIDDevicePointer
    }

    private func emitOnce(key: String, event: String, fields: [String: Any]) {
        guard !emittedKeys.contains(key) else {
            return
        }
        emittedKeys.insert(key)
        emit(event: event, fields: fields)
    }

    func emit(event: String, fields: [String: Any]) {
        var payload: [String: Any] = [
            "schema": schema,
            "capture_id": options.captureID,
            "event": event,
            "sanitized": true,
        ]
        fields.forEach { key, value in
            if let optional = value as? OptionalValue, optional.isNil {
                payload[key] = NSNull()
            } else {
                payload[key] = value
            }
        }
        payload = sanitizePayload(payload)

        guard JSONSerialization.isValidJSONObject(payload) else {
            writeStderr("probe emit serialization failed: invalid JSON object for event \(event)")
            return
        }

        do {
            let data = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
            guard let line = String(data: data, encoding: .utf8) else {
                writeStderr("probe emit serialization failed: UTF-8 conversion failed for event \(event)")
                return
            }
            FileHandle.standardOutput.write(Data((line + "\n").utf8))
        } catch {
            writeStderr("probe emit serialization failed: \(sanitizedError(error))")
        }
    }
}

private protocol OptionalValue {
    var isNil: Bool { get }
}

extension Optional: OptionalValue {
    var isNil: Bool {
        self == nil
    }
}

private func optionalInt(_ value: Int?) -> Any {
    value.map { $0 as Any } ?? NSNull()
}

private func score(controller: GCController) -> Int {
    matchClass(controller: controller) == "bt-gun-or-android-label" ? 100 : 0
}

private func matchClass(controller: GCController) -> String {
    let haystack = [
        controller.vendorName ?? "",
        controller.productCategory,
    ]
    .joined(separator: " ")
    .lowercased()

    if labelTokens.contains(where: { haystack.contains($0) }) {
        return "bt-gun-or-android-label"
    }
    return "generic-controller-label"
}

private func matchClass(device: IOHIDDevice) -> String {
    let haystack = [
        hidStringProperty(device, key: kIOHIDManufacturerKey),
        hidStringProperty(device, key: kIOHIDProductKey),
    ]
    .joined(separator: " ")
    .lowercased()

    if labelTokens.contains(where: { haystack.contains($0) }) {
        return "bt-gun-or-android-label"
    }
    return "generic-controller-label"
}

private func hidStringProperty(_ device: IOHIDDevice, key: String) -> String {
    guard let value = IOHIDDeviceGetProperty(device, key as CFString) else {
        return ""
    }
    return String(describing: value)
}

private func hidDevicePointer(_ device: IOHIDDevice) -> UnsafeMutableRawPointer {
    Unmanaged.passUnretained(device).toOpaque()
}

private func productCategoryClass(_ productCategory: String) -> String {
    let lowercased = productCategory.lowercased()
    if lowercased.contains("gamepad") || lowercased.contains("controller") {
        return "game-controller"
    }
    if lowercased.isEmpty {
        return "unknown"
    }
    return "other"
}

private func valueAfter(_ flag: String, in arguments: [String]) -> String? {
    guard let index = arguments.firstIndex(of: flag) else {
        return nil
    }
    let nextIndex = arguments.index(after: index)
    guard nextIndex < arguments.endIndex else {
        return nil
    }
    return arguments[nextIndex]
}

private func valueBucket(_ value: Float) -> String {
    if value >= 0.90 {
        return "high"
    }
    if value >= 0.20 {
        return "active"
    }
    return "low"
}

private func signedValueBucket(_ value: Float) -> String {
    if value <= -0.80 {
        return "negative-high"
    }
    if value < -axisThreshold {
        return "negative-active"
    }
    if value >= 0.80 {
        return "positive-high"
    }
    return "positive-active"
}

private func signedIntegerBucket(_ value: Int) -> String {
    if value <= -26_214 {
        return "negative-high"
    }
    if value < -hidAxisThreshold {
        return "negative-active"
    }
    if value >= 26_214 {
        return "positive-high"
    }
    return "positive-active"
}

private func hidButton(_ usage: Int) -> (event: String, control: String)? {
    switch usage {
    case 1:
        return ("button-a-observed", "a")
    case 2:
        return ("button-b-observed", "b")
    case 3:
        return ("button-x-observed", "x")
    case 4:
        return ("button-y-observed", "y")
    case 7:
        return ("reload-observed", "reload")
    case 8:
        return ("trigger-observed", "trigger")
    default:
        return nil
    }
}

private func hidAxis(_ usage: Int) -> (event: String, axis: String)? {
    switch usage {
    case kHIDUsage_GD_X:
        return ("stickX-observed", "stickX")
    case kHIDUsage_GD_Y:
        return ("stickY-observed", "stickY")
    case kHIDUsage_GD_Z:
        return ("aimX-observed", "aimX")
    case kHIDUsage_GD_Rx:
        return ("aimY-observed", "aimY")
    default:
        return nil
    }
}

private func sanitizePayload(_ payload: [String: Any]) -> [String: Any] {
    payload.mapValues { value in
        if let text = value as? String {
            return sanitizeText(text)
        }
        if let strings = value as? [String] {
            return strings.map(sanitizeText)
        }
        return value
    }
}

private func sanitizedError(_ error: Error) -> String {
    sanitizeText(String(describing: error))
}

private func writeStderr(_ message: String) {
    FileHandle.standardError.write(Data((sanitizeText(message) + "\n").utf8))
}

private func sanitizeText(_ text: String) -> String {
    text
        .replacingOccurrences(
            of: #"([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"#,
            with: "redacted-bluetooth-address",
            options: .regularExpression
        )
        .replacingOccurrences(
            of: #"/Users/[^ "']+"#,
            with: "redacted-user-path",
            options: .regularExpression
        )
        .replacingOccurrences(
            of: #"[^A-Za-z0-9_.:/,=+ -]"#,
            with: "-",
            options: .regularExpression
        )
}
