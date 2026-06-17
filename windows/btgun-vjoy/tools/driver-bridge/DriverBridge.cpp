#define INITGUID
#define NOMINMAX

#include <windows.h>
#include <setupapi.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <iomanip>
#include <iostream>
#include <limits>
#include <sstream>
#include <string>
#include <vector>

#include "../../include/BtGunVJoyIoctl.h"

#pragma comment(lib, "setupapi.lib")

namespace {

std::string errorCode(DWORD code) {
    std::ostringstream out;
    out << "0x" << std::uppercase << std::hex << std::setw(8) << std::setfill('0') << code;
    return out.str();
}

void printError(DWORD code) {
    std::cout << "ERR " << errorCode(code) << std::endl;
}

bool hexValue(char c, uint8_t& value) {
    if (c >= '0' && c <= '9') {
        value = static_cast<uint8_t>(c - '0');
        return true;
    }
    if (c >= 'a' && c <= 'f') {
        value = static_cast<uint8_t>(10 + c - 'a');
        return true;
    }
    if (c >= 'A' && c <= 'F') {
        value = static_cast<uint8_t>(10 + c - 'A');
        return true;
    }
    return false;
}

bool parseHexReport(const std::string& text, std::vector<uint8_t>& bytes) {
    std::string compact;
    compact.reserve(text.size());
    for (char c : text) {
        if (!std::isspace(static_cast<unsigned char>(c))) {
            compact.push_back(c);
        }
    }
    if ((compact.size() % 2) != 0) {
        return false;
    }

    bytes.clear();
    bytes.reserve(compact.size() / 2);
    for (size_t i = 0; i < compact.size(); i += 2) {
        uint8_t hi = 0;
        uint8_t lo = 0;
        if (!hexValue(compact[i], hi) || !hexValue(compact[i + 1], lo)) {
            return false;
        }
        bytes.push_back(static_cast<uint8_t>((hi << 4) | lo));
    }
    return true;
}

bool parseUInt64(const std::string& text, uint64_t& value) {
    if (text.empty()) {
        return false;
    }
    uint64_t parsed = 0;
    for (char c : text) {
        if (c < '0' || c > '9') {
            return false;
        }
        const uint64_t digit = static_cast<uint64_t>(c - '0');
        if (parsed > (std::numeric_limits<uint64_t>::max() - digit) / 10u) {
            return false;
        }
        parsed = (parsed * 10u) + digit;
    }
    value = parsed;
    return true;
}

std::string toHex(const uint8_t* bytes, size_t length) {
    std::ostringstream out;
    out << std::hex << std::nouppercase << std::setfill('0');
    for (size_t i = 0; i < length; ++i) {
        out << std::setw(2) << static_cast<unsigned int>(bytes[i]);
    }
    return out.str();
}

HANDLE openFirstDeviceInterface() {
    HDEVINFO info = SetupDiGetClassDevsW(
        &GUID_DEVINTERFACE_BTGUNVJOY,
        nullptr,
        nullptr,
        DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
    if (info == INVALID_HANDLE_VALUE) {
        return INVALID_HANDLE_VALUE;
    }

    SP_DEVICE_INTERFACE_DATA interfaceData;
    interfaceData.cbSize = sizeof(interfaceData);
    HANDLE handle = INVALID_HANDLE_VALUE;

    for (DWORD index = 0; SetupDiEnumDeviceInterfaces(
             info,
             nullptr,
             &GUID_DEVINTERFACE_BTGUNVJOY,
             index,
             &interfaceData);
         ++index) {
        DWORD requiredBytes = 0;
        SetupDiGetDeviceInterfaceDetailW(info, &interfaceData, nullptr, 0, &requiredBytes, nullptr);
        if (requiredBytes == 0) {
            continue;
        }

        std::vector<uint8_t> detailBytes(requiredBytes);
        auto* detail = reinterpret_cast<SP_DEVICE_INTERFACE_DETAIL_DATA_W*>(detailBytes.data());
        detail->cbSize = sizeof(SP_DEVICE_INTERFACE_DETAIL_DATA_W);
        if (!SetupDiGetDeviceInterfaceDetailW(
                info,
                &interfaceData,
                detail,
                requiredBytes,
                nullptr,
                nullptr)) {
            continue;
        }

        handle = CreateFileW(
            detail->DevicePath,
            GENERIC_READ | GENERIC_WRITE,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            nullptr,
            OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL,
            nullptr);
        if (handle != INVALID_HANDLE_VALUE) {
            break;
        }
    }

    SetupDiDestroyDeviceInfoList(info);
    return handle;
}

void printStatusJson(const BTGVJOY_STATUS& status) {
    std::cout << "STATUS {"
              << "\"driverStarted\":" << (status.DriverStarted ? "true" : "false") << ","
              << "\"vhfStarted\":" << (status.VhfStarted ? "true" : "false") << ","
              << "\"queueDepth\":" << status.QueueDepth << ","
              << "\"lastInputSequence\":" << status.LastInputSequence << ","
              << "\"lastOutputSequence\":" << status.LastOutputSequence << ","
              << "\"submittedInputReports\":" << status.SubmittedInputReports << ","
              << "\"queuedOutputReports\":" << status.QueuedOutputReports << ","
              << "\"droppedOutputReports\":" << status.DroppedOutputReports << ","
              << "\"malformedInputReports\":" << status.MalformedInputReports << ","
              << "\"malformedOutputReports\":" << status.MalformedOutputReports << ","
              << "\"lastNtStatus\":" << status.LastNtStatus
              << "}" << std::endl;
}

bool submitInput(HANDLE device, uint64_t sourceSequence, const std::string& hex) {
    std::vector<uint8_t> bytes;
    if (!parseHexReport(hex, bytes) || bytes.size() != BTGVJOY_INPUT_REPORT_LENGTH_BYTES) {
        printError(ERROR_INVALID_PARAMETER);
        return false;
    }
    if (bytes[0] != BTGVJOY_INPUT_REPORT_ID) {
        printError(ERROR_INVALID_DATA);
        return false;
    }

    BTGVJOY_INPUT_REPORT report = {};
    report.Size = sizeof(report);
    report.Version = BTGVJOY_ABI_VERSION;
    report.SourceSequence = sourceSequence;
    std::copy(bytes.begin(), bytes.end(), report.HidReport);

    DWORD bytesReturned = 0;
    if (!DeviceIoControl(
            device,
            IOCTL_BTGVJOY_SUBMIT_INPUT,
            &report,
            sizeof(report),
            nullptr,
            0,
            &bytesReturned,
            nullptr)) {
        printError(GetLastError());
        return false;
    }

    std::cout << "OK" << std::endl;
    return true;
}

bool readOutput(HANDLE device) {
    BTGVJOY_OUTPUT_REPORT report = {};
    DWORD bytesReturned = 0;
    if (!DeviceIoControl(
            device,
            IOCTL_BTGVJOY_READ_OUTPUT,
            nullptr,
            0,
            &report,
            sizeof(report),
            &bytesReturned,
            nullptr)) {
        if (GetLastError() == ERROR_NO_MORE_ITEMS) {
            std::cout << "NO_OUTPUT" << std::endl;
            return true;
        }
        printError(GetLastError());
        return false;
    }
    if (bytesReturned != sizeof(report) ||
        report.Size != sizeof(report) ||
        report.Version != BTGVJOY_ABI_VERSION ||
        report.HidReport[0] != BTGVJOY_OUTPUT_REPORT_ID) {
        printError(ERROR_INVALID_DATA);
        return false;
    }

    std::cout << "OUTPUT " << toHex(report.HidReport, BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES) << std::endl;
    return true;
}

bool readStatus(HANDLE device) {
    BTGVJOY_STATUS status = {};
    DWORD bytesReturned = 0;
    if (!DeviceIoControl(
            device,
            IOCTL_BTGVJOY_GET_STATUS,
            nullptr,
            0,
            &status,
            sizeof(status),
            &bytesReturned,
            nullptr)) {
        printError(GetLastError());
        return false;
    }
    if (bytesReturned != sizeof(status) ||
        status.Size != sizeof(status) ||
        status.Version != BTGVJOY_ABI_VERSION) {
        printError(ERROR_INVALID_DATA);
        return false;
    }

    printStatusJson(status);
    return true;
}

} // namespace

int main() {
    HANDLE device = openFirstDeviceInterface();
    if (device == INVALID_HANDLE_VALUE) {
        printError(GetLastError());
        return 1;
    }

    std::string line;
    while (std::getline(std::cin, line)) {
        if (line == "QUIT") {
            std::cout << "OK" << std::endl;
            break;
        }
        if (line == "READ_OUTPUT") {
            readOutput(device);
            continue;
        }
        if (line == "STATUS") {
            readStatus(device);
            continue;
        }

        const std::string prefix = "SUBMIT_INPUT ";
        if (line.rfind(prefix, 0) == 0) {
            std::istringstream args(line.substr(prefix.size()));
            std::string sequenceToken;
            std::string hex;
            std::string extra;
            uint64_t sourceSequence = 0;
            if (!(args >> sequenceToken >> hex) ||
                (args >> extra) ||
                !parseUInt64(sequenceToken, sourceSequence)) {
                printError(ERROR_INVALID_PARAMETER);
                continue;
            }
            submitInput(device, sourceSequence, hex);
            continue;
        }

        printError(ERROR_INVALID_PARAMETER);
    }

    CloseHandle(device);
    return 0;
}
