#include <windows.h>
#include <setupapi.h>
#include <initguid.h>
#include <hidsdi.h>
#include <hidclass.h>

#include <algorithm>
#include <cstdint>
#include <cwchar>
#include <iostream>
#include <string>
#include <vector>

#include "../../include/BtGunVJoyIoctl.h"

#pragma comment(lib, "hid.lib")
#pragma comment(lib, "setupapi.lib")

namespace {

bool parseInteger(const wchar_t* text, unsigned long& value) {
    wchar_t* end = nullptr;
    value = wcstoul(text, &end, 10);
    return end != text && *end == L'\0';
}

bool parseStrength(const wchar_t* text, uint8_t& strength) {
    wchar_t* end = nullptr;
    double parsed = wcstod(text, &end);
    if (end == text || *end != L'\0') {
        return false;
    }
    if (parsed >= 0.0 && parsed <= 1.0) {
        strength = static_cast<uint8_t>(parsed * 255.0 + 0.5);
        return true;
    }
    if (parsed >= 0.0 && parsed <= 255.0) {
        strength = static_cast<uint8_t>(parsed);
        return true;
    }
    return false;
}

void putUInt16Le(uint8_t* bytes, size_t offset, unsigned long value) {
    bytes[offset] = static_cast<uint8_t>(value & 0xffu);
    bytes[offset + 1] = static_cast<uint8_t>((value >> 8) & 0xffu);
}

bool productMatches(HANDLE handle) {
    wchar_t product[128] = {};
    if (!HidD_GetProductString(handle, product, sizeof(product))) {
        return false;
    }
    return wcsstr(product, BTGVJOY_DEVICE_NAME_W) != nullptr;
}

HANDLE openByPath(const std::wstring& path) {
    return CreateFileW(
        path.c_str(),
        GENERIC_READ | GENERIC_WRITE,
        FILE_SHARE_READ | FILE_SHARE_WRITE,
        nullptr,
        OPEN_EXISTING,
        FILE_ATTRIBUTE_NORMAL,
        nullptr);
}

HANDLE openFirstMatchingHid() {
    HDEVINFO info = SetupDiGetClassDevsW(
        &GUID_DEVINTERFACE_HID,
        nullptr,
        nullptr,
        DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
    if (info == INVALID_HANDLE_VALUE) {
        return INVALID_HANDLE_VALUE;
    }

    SP_DEVICE_INTERFACE_DATA interfaceData;
    interfaceData.cbSize = sizeof(interfaceData);
    HANDLE result = INVALID_HANDLE_VALUE;

    for (DWORD index = 0; SetupDiEnumDeviceInterfaces(
             info,
             nullptr,
             &GUID_DEVINTERFACE_HID,
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

        HANDLE candidate = openByPath(detail->DevicePath);
        if (candidate == INVALID_HANDLE_VALUE) {
            continue;
        }
        if (productMatches(candidate)) {
            result = candidate;
            break;
        }
        CloseHandle(candidate);
    }

    SetupDiDestroyDeviceInfoList(info);
    return result;
}

void printUsage() {
    std::wcout
        << L"ERR usage: btgun-hid-output-sender.exe --strength <0..1|0..255> "
        << L"--duration-ms <1..1000> --ttl-ms <1..2000> [--path <hid-path>]"
        << std::endl;
}

} // namespace

int wmain(int argc, wchar_t** argv) {
    uint8_t strength = 0;
    unsigned long durationMs = 0;
    unsigned long ttlMs = 0;
    std::wstring explicitPath;

    for (int i = 1; i < argc; ++i) {
        std::wstring arg(argv[i]);
        if (arg == L"--strength" && i + 1 < argc) {
            if (!parseStrength(argv[++i], strength)) {
                printUsage();
                return 2;
            }
        } else if (arg == L"--duration-ms" && i + 1 < argc) {
            if (!parseInteger(argv[++i], durationMs)) {
                printUsage();
                return 2;
            }
        } else if (arg == L"--ttl-ms" && i + 1 < argc) {
            if (!parseInteger(argv[++i], ttlMs)) {
                printUsage();
                return 2;
            }
        } else if (arg == L"--path" && i + 1 < argc) {
            explicitPath = argv[++i];
        } else {
            printUsage();
            return 2;
        }
    }

    if (durationMs < 1 || durationMs > 1000 || ttlMs < 1 || ttlMs > 2000) {
        printUsage();
        return 2;
    }

    HANDLE handle = explicitPath.empty() ? openFirstMatchingHid() : openByPath(explicitPath);
    if (handle == INVALID_HANDLE_VALUE) {
        std::wcout << L"ERR open_failed" << std::endl;
        return 1;
    }

    uint8_t report[BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES] = {};
    report[0] = BTGVJOY_OUTPUT_REPORT_ID;
    report[1] = BTGVJOY_OUTPUT_REPORT_VERSION;
    report[2] = strength;
    putUInt16Le(report, 3, durationMs);
    putUInt16Le(report, 5, ttlMs);
    report[7] = 0;
    report[8] = 0;

    if (!HidD_SetOutputReport(handle, report, sizeof(report))) {
        CloseHandle(handle);
        std::wcout << L"ERR send_failed" << std::endl;
        return 1;
    }

    CloseHandle(handle);
    std::wcout << L"OK" << std::endl;
    return 0;
}
