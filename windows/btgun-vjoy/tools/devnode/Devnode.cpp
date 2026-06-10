#include <windows.h>
#include <setupapi.h>

#include <algorithm>
#include <cwchar>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "../../include/BtGunVJoyIoctl.h"

#pragma comment(lib, "setupapi.lib")

namespace {

std::wstring win32Hex(DWORD code) {
    std::wostringstream out;
    out << L"0x" << std::uppercase << std::hex << std::setw(8) << std::setfill(L'0') << code;
    return out.str();
}

std::wstring win32Message(DWORD code) {
    wchar_t* buffer = nullptr;
    DWORD chars = FormatMessageW(
        FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
        nullptr,
        code,
        0,
        reinterpret_cast<LPWSTR>(&buffer),
        0,
        nullptr);
    if (chars == 0 || buffer == nullptr) {
        return L"";
    }
    std::wstring message(buffer, chars);
    LocalFree(buffer);
    while (!message.empty() && (message.back() == L'\r' || message.back() == L'\n')) {
        message.pop_back();
    }
    return message;
}

void printError(const wchar_t* context, DWORD code) {
    std::wcerr << L"ERR " << context << L" " << win32Hex(code);
    std::wstring message = win32Message(code);
    if (!message.empty()) {
        std::wcerr << L" " << message;
    }
    std::wcerr << std::endl;
}

bool pathExists(const std::wstring& path) {
    DWORD attributes = GetFileAttributesW(path.c_str());
    return attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_DIRECTORY) == 0;
}

bool propertyMultiStringContains(
    HDEVINFO info,
    SP_DEVINFO_DATA& data,
    DWORD property,
    const std::wstring& expected) {
    DWORD type = 0;
    DWORD requiredBytes = 0;
    if (!SetupDiGetDeviceRegistryPropertyW(
            info,
            &data,
            property,
            &type,
            nullptr,
            0,
            &requiredBytes)) {
        DWORD error = GetLastError();
        if (error != ERROR_INSUFFICIENT_BUFFER || requiredBytes == 0) {
            return false;
        }
    }
    if (type != REG_MULTI_SZ && type != 0) {
        return false;
    }

    std::vector<wchar_t> buffer((requiredBytes / sizeof(wchar_t)) + 2, L'\0');
    if (!SetupDiGetDeviceRegistryPropertyW(
            info,
            &data,
            property,
            &type,
            reinterpret_cast<PBYTE>(buffer.data()),
            static_cast<DWORD>(buffer.size() * sizeof(wchar_t)),
            &requiredBytes)) {
        return false;
    }
    if (type != REG_MULTI_SZ) {
        return false;
    }

    for (const wchar_t* entry = buffer.data(); *entry != L'\0'; entry += std::wcslen(entry) + 1) {
        if (CompareStringOrdinal(entry, -1, expected.c_str(), -1, TRUE) == CSTR_EQUAL) {
            return true;
        }
    }
    return false;
}

bool findExistingDevnode(const std::wstring& hardwareId, std::wstring& instanceId) {
    HDEVINFO info = SetupDiGetClassDevsW(nullptr, nullptr, nullptr, DIGCF_ALLCLASSES);
    if (info == INVALID_HANDLE_VALUE) {
        printError(L"SetupDiGetClassDevsW", GetLastError());
        return false;
    }

    SP_DEVINFO_DATA data = {};
    data.cbSize = sizeof(data);
    bool found = false;

    for (DWORD index = 0; SetupDiEnumDeviceInfo(info, index, &data); ++index) {
        if (!propertyMultiStringContains(info, data, SPDRP_HARDWAREID, hardwareId)) {
            continue;
        }

        DWORD requiredChars = 0;
        SetupDiGetDeviceInstanceIdW(info, &data, nullptr, 0, &requiredChars);
        std::vector<wchar_t> id(requiredChars + 1, L'\0');
        if (requiredChars > 0 &&
            SetupDiGetDeviceInstanceIdW(info, &data, id.data(), static_cast<DWORD>(id.size()), nullptr)) {
            instanceId.assign(id.data());
        } else {
            instanceId = hardwareId;
        }
        found = true;
        break;
    }

    SetupDiDestroyDeviceInfoList(info);
    return found;
}

bool setStringProperty(
    HDEVINFO info,
    SP_DEVINFO_DATA& data,
    DWORD property,
    const std::wstring& value) {
    DWORD bytes = static_cast<DWORD>((value.size() + 1) * sizeof(wchar_t));
    return SetupDiSetDeviceRegistryPropertyW(
        info,
        &data,
        property,
        reinterpret_cast<const BYTE*>(value.c_str()),
        bytes) != FALSE;
}

bool setHardwareId(HDEVINFO info, SP_DEVINFO_DATA& data, const std::wstring& hardwareId) {
    std::vector<wchar_t> multiString(hardwareId.size() + 2, L'\0');
    std::copy(hardwareId.begin(), hardwareId.end(), multiString.begin());
    return SetupDiSetDeviceRegistryPropertyW(
        info,
        &data,
        SPDRP_HARDWAREID,
        reinterpret_cast<const BYTE*>(multiString.data()),
        static_cast<DWORD>(multiString.size() * sizeof(wchar_t))) != FALSE;
}

int createRootDevnode(
    const std::wstring& infPath,
    const std::wstring& hardwareId,
    const std::wstring& deviceName) {
    GUID classGuid = {};
    wchar_t className[MAX_CLASS_NAME_LEN] = {};
    if (!SetupDiGetINFClassW(infPath.c_str(), &classGuid, className, MAX_CLASS_NAME_LEN, nullptr)) {
        printError(L"SetupDiGetINFClassW", GetLastError());
        return 1;
    }

    HDEVINFO info = SetupDiCreateDeviceInfoList(&classGuid, nullptr);
    if (info == INVALID_HANDLE_VALUE) {
        printError(L"SetupDiCreateDeviceInfoList", GetLastError());
        return 1;
    }

    SP_DEVINFO_DATA data = {};
    data.cbSize = sizeof(data);
    if (!SetupDiCreateDeviceInfoW(
            info,
            L"BTGunVJoy",
            &classGuid,
            deviceName.c_str(),
            nullptr,
            DICD_GENERATE_ID,
            &data)) {
        DWORD error = GetLastError();
        SetupDiDestroyDeviceInfoList(info);
        printError(L"SetupDiCreateDeviceInfoW", error);
        return 1;
    }

    if (!setHardwareId(info, data, hardwareId)) {
        DWORD error = GetLastError();
        SetupDiDestroyDeviceInfoList(info);
        printError(L"SetupDiSetDeviceRegistryPropertyW(SPDRP_HARDWAREID)", error);
        return 1;
    }
    if (!setStringProperty(info, data, SPDRP_DEVICEDESC, deviceName)) {
        DWORD error = GetLastError();
        SetupDiDestroyDeviceInfoList(info);
        printError(L"SetupDiSetDeviceRegistryPropertyW(SPDRP_DEVICEDESC)", error);
        return 1;
    }
    if (!setStringProperty(info, data, SPDRP_FRIENDLYNAME, deviceName)) {
        DWORD error = GetLastError();
        SetupDiDestroyDeviceInfoList(info);
        printError(L"SetupDiSetDeviceRegistryPropertyW(SPDRP_FRIENDLYNAME)", error);
        return 1;
    }

    if (!SetupDiCallClassInstaller(DIF_REGISTERDEVICE, info, &data)) {
        DWORD error = GetLastError();
        SetupDiDestroyDeviceInfoList(info);
        printError(L"SetupDiCallClassInstaller(DIF_REGISTERDEVICE)", error);
        return 1;
    }

    wchar_t instanceId[512] = {};
    if (SetupDiGetDeviceInstanceIdW(info, &data, instanceId, ARRAYSIZE(instanceId), nullptr)) {
        std::wcout << L"OK created " << instanceId << std::endl;
    } else {
        std::wcout << L"OK created " << hardwareId << std::endl;
    }

    SetupDiDestroyDeviceInfoList(info);
    return 0;
}

void printUsage() {
    std::wcerr
        << L"ERR usage: btgun-devnode.exe --inf <btgunvjoy.inf> "
        << L"[--hardware-id Root\\BTGunVJoy] [--device-name \"BT Gun VJoy\"]"
        << std::endl;
}

} // namespace

int wmain(int argc, wchar_t** argv) {
    std::wstring infPath;
    std::wstring hardwareId = BTGVJOY_HARDWARE_ID_W;
    std::wstring deviceName = BTGVJOY_DEVICE_NAME_W;

    for (int i = 1; i < argc; ++i) {
        std::wstring arg(argv[i]);
        if (arg == L"--inf" && i + 1 < argc) {
            infPath = argv[++i];
        } else if (arg == L"--hardware-id" && i + 1 < argc) {
            hardwareId = argv[++i];
        } else if (arg == L"--device-name" && i + 1 < argc) {
            deviceName = argv[++i];
        } else {
            printUsage();
            return 2;
        }
    }

    if (infPath.empty() || hardwareId.empty() || deviceName.empty()) {
        printUsage();
        return 2;
    }
    if (!pathExists(infPath)) {
        std::wcerr << L"ERR inf_not_found " << infPath << std::endl;
        return 2;
    }

    std::wstring existingInstanceId;
    if (findExistingDevnode(hardwareId, existingInstanceId)) {
        std::wcout << L"OK exists " << existingInstanceId << std::endl;
        return 0;
    }

    return createRootDevnode(infPath, hardwareId, deviceName);
}
