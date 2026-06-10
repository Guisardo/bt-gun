#pragma once

#if defined(_KERNEL_MODE) || defined(_NTDDK_) || defined(_WDMDDK_) || defined(WINNT)
#define BTGVJOY_KERNEL_MODE 1
#endif

#ifdef BTGVJOY_KERNEL_MODE
typedef UINT8 BTGVJOY_UINT8;
typedef UINT16 BTGVJOY_UINT16;
typedef UINT32 BTGVJOY_UINT32;
typedef UINT64 BTGVJOY_UINT64;
typedef INT32 BTGVJOY_INT32;
#else
#include <stdint.h>
typedef uint8_t BTGVJOY_UINT8;
typedef uint16_t BTGVJOY_UINT16;
typedef uint32_t BTGVJOY_UINT32;
typedef uint64_t BTGVJOY_UINT64;
typedef int32_t BTGVJOY_INT32;
#endif

#if defined(_WIN32) && !defined(BTGVJOY_KERNEL_MODE)
#include <guiddef.h>
#endif

#ifndef CTL_CODE
#define CTL_CODE(DeviceType, Function, Method, Access) \
    (((DeviceType) << 16) | ((Access) << 14) | ((Function) << 2) | (Method))
#endif

#ifndef METHOD_BUFFERED
#define METHOD_BUFFERED 0
#endif

#ifndef FILE_WRITE_DATA
#define FILE_WRITE_DATA 0x0002
#endif

#ifndef FILE_READ_DATA
#define FILE_READ_DATA 0x0001
#endif

#ifdef __cplusplus
extern "C" {
#endif

// {6A0931C4-791E-4C08-9F39-0AC1891BD2B2}
#ifdef DEFINE_GUID
DEFINE_GUID(
    GUID_DEVINTERFACE_BTGUNVJOY,
    0x6a0931c4,
    0x791e,
    0x4c08,
    0x9f,
    0x39,
    0x0a,
    0xc1,
    0x89,
    0x1b,
    0xd2,
    0xb2);
#endif

// Hardware ID: Root\BTGunVJoy
#define BTGVJOY_HARDWARE_ID_A "Root\\BTGunVJoy"
#define BTGVJOY_HARDWARE_ID_W L"Root\\BTGunVJoy"
#define BTGVJOY_DEVICE_NAME_A "BT Gun VJoy"
#define BTGVJOY_DEVICE_NAME_W L"BT Gun VJoy"
#define BTGVJOY_VENDOR_ID 0x1209u
#define BTGVJOY_PRODUCT_ID 0xB706u
#define BTGVJOY_VERSION_NUMBER 0x0602u

#define BTGVJOY_ABI_VERSION 1u

#define BTGVJOY_INPUT_REPORT_ID 1u
#define BTGVJOY_INPUT_REPORT_LENGTH_BYTES 10u
#define BTGVJOY_INPUT_BUTTON_TRIGGER 0x01u
#define BTGVJOY_INPUT_BUTTON_RELOAD 0x02u
#define BTGVJOY_INPUT_BUTTON_X 0x04u
#define BTGVJOY_INPUT_BUTTON_Y 0x08u
#define BTGVJOY_INPUT_BUTTON_A 0x10u
#define BTGVJOY_INPUT_BUTTON_B 0x20u
#define BTGVJOY_INPUT_BUTTON_MASK 0x3fu

#define BTGVJOY_OUTPUT_REPORT_ID 2u
#define BTGVJOY_OUTPUT_REPORT_VERSION 1u
#define BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES 9u

#define FILE_DEVICE_BT_GUN_VJOY 0x8000u

#define IOCTL_BTGVJOY_SUBMIT_INPUT \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x801, METHOD_BUFFERED, FILE_WRITE_DATA)
#define IOCTL_BTGVJOY_READ_OUTPUT \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x802, METHOD_BUFFERED, FILE_READ_DATA)
#define IOCTL_BTGVJOY_GET_STATUS \
    CTL_CODE(FILE_DEVICE_BT_GUN_VJOY, 0x803, METHOD_BUFFERED, FILE_READ_DATA)

#pragma pack(push, 1)

typedef struct _BTGVJOY_INPUT_REPORT {
    BTGVJOY_UINT16 Size;
    BTGVJOY_UINT16 Version;
    BTGVJOY_UINT64 SourceSequence;
    BTGVJOY_UINT8 HidReport[BTGVJOY_INPUT_REPORT_LENGTH_BYTES];
} BTGVJOY_INPUT_REPORT;

typedef struct _BTGVJOY_OUTPUT_REPORT {
    BTGVJOY_UINT16 Size;
    BTGVJOY_UINT16 Version;
    BTGVJOY_UINT64 OutputSequence;
    BTGVJOY_UINT8 HidReport[BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES];
} BTGVJOY_OUTPUT_REPORT;

typedef struct _BTGVJOY_STATUS {
    BTGVJOY_UINT16 Size;
    BTGVJOY_UINT16 Version;
    BTGVJOY_UINT8 DriverStarted;
    BTGVJOY_UINT8 VhfStarted;
    BTGVJOY_UINT32 QueueDepth;
    BTGVJOY_UINT64 LastInputSequence;
    BTGVJOY_UINT64 LastOutputSequence;
    BTGVJOY_UINT64 SubmittedInputReports;
    BTGVJOY_UINT64 QueuedOutputReports;
    BTGVJOY_UINT64 DroppedOutputReports;
    BTGVJOY_UINT64 MalformedInputReports;
    BTGVJOY_UINT64 MalformedOutputReports;
    BTGVJOY_INT32 LastNtStatus;
} BTGVJOY_STATUS;

#pragma pack(pop)

#ifdef __cplusplus
}
#endif
