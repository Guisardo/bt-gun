#pragma once

#include <ntddk.h>
#include <wdf.h>
#include <vhf.h>

#include "..\include\BtGunVJoyIoctl.h"

#define BTGVJOY_POOL_TAG 'JvgB'
#define BTGVJOY_OUTPUT_QUEUE_CAPACITY 16u

extern const UCHAR BtGunVJoyReportDescriptor[];
extern const ULONG BtGunVJoyReportDescriptorLength;

typedef struct _BTGVJOY_DEVICE_CONTEXT {
    WDFDEVICE Device;
    WDFQUEUE IoctlQueue;
    VHFHANDLE VhfHandle;
    BOOLEAN DriverStarted;
    BOOLEAN VhfStarted;
    FAST_MUTEX OutputQueueLock;
    BTGVJOY_OUTPUT_REPORT OutputQueue[BTGVJOY_OUTPUT_QUEUE_CAPACITY];
    ULONG OutputQueueHead;
    ULONG OutputQueueCount;
    ULONG64 NextOutputSequence;
    ULONG64 LastInputSequence;
    ULONG64 LastOutputSequence;
    ULONG64 SubmittedInputReports;
    ULONG64 QueuedOutputReports;
    ULONG64 DroppedOutputReports;
    ULONG64 MalformedInputReports;
    ULONG64 MalformedOutputReports;
    NTSTATUS LastNtStatus;
} BTGVJOY_DEVICE_CONTEXT, *PBTGVJOY_DEVICE_CONTEXT;

WDF_DECLARE_CONTEXT_TYPE_WITH_NAME(BTGVJOY_DEVICE_CONTEXT, BtGunVJoyGetDeviceContext)

DRIVER_INITIALIZE DriverEntry;
EVT_WDF_DRIVER_DEVICE_ADD BtGunVJoyEvtDeviceAdd;
EVT_WDF_OBJECT_CONTEXT_CLEANUP BtGunVJoyEvtDeviceContextCleanup;
EVT_WDF_IO_QUEUE_IO_DEVICE_CONTROL BtGunVJoyEvtIoDeviceControl;
EVT_VHF_ASYNC_OPERATION BtGunVJoyHandleWriteReport;

NTSTATUS
BtGunVJoyCreateDevice(
    _Inout_ PWDFDEVICE_INIT DeviceInit);

VOID
BtGunVJoySubmitInputReport(
    _In_ PBTGVJOY_DEVICE_CONTEXT Context,
    _In_ WDFREQUEST Request,
    _In_ size_t InputBufferLength);

VOID
BtGunVJoyReadOutputReport(
    _In_ PBTGVJOY_DEVICE_CONTEXT Context,
    _In_ WDFREQUEST Request,
    _In_ size_t OutputBufferLength);

VOID
BtGunVJoyGetStatus(
    _In_ PBTGVJOY_DEVICE_CONTEXT Context,
    _In_ WDFREQUEST Request,
    _In_ size_t OutputBufferLength);
