#include "BtGunVJoy.h"

static
BOOLEAN
BtGunVJoyValidateInputReport(
    _In_ const BTGVJOY_INPUT_REPORT* Report)
{
    if (Report->Size != sizeof(BTGVJOY_INPUT_REPORT)) {
        return FALSE;
    }
    if (Report->Version != BTGVJOY_ABI_VERSION) {
        return FALSE;
    }
    if (Report->HidReport[0] != BTGVJOY_INPUT_REPORT_ID) {
        return FALSE;
    }
    if ((Report->HidReport[1] & ~BTGVJOY_INPUT_BUTTON_MASK) != 0) {
        return FALSE;
    }
    return TRUE;
}

static
VOID
BtGunVJoyRecordMalformedInput(
    _In_ PBTGVJOY_DEVICE_CONTEXT Context,
    _In_ NTSTATUS Status)
{
    Context->MalformedInputReports += 1;
    Context->LastNtStatus = Status;
}

VOID
BtGunVJoyEvtIoDeviceControl(
    _In_ WDFQUEUE Queue,
    _In_ WDFREQUEST Request,
    _In_ size_t OutputBufferLength,
    _In_ size_t InputBufferLength,
    _In_ ULONG IoControlCode)
{
    WDFDEVICE device;
    PBTGVJOY_DEVICE_CONTEXT context;

    device = WdfIoQueueGetDevice(Queue);
    context = BtGunVJoyGetDeviceContext(device);

    switch (IoControlCode) {
    case IOCTL_BTGVJOY_SUBMIT_INPUT:
        BtGunVJoySubmitInputReport(context, Request, InputBufferLength);
        break;
    case IOCTL_BTGVJOY_READ_OUTPUT:
        BtGunVJoyReadOutputReport(context, Request, OutputBufferLength);
        break;
    case IOCTL_BTGVJOY_GET_STATUS:
        BtGunVJoyGetStatus(context, Request, OutputBufferLength);
        break;
    default:
        WdfRequestComplete(Request, STATUS_INVALID_DEVICE_REQUEST);
        break;
    }
}

VOID
BtGunVJoySubmitInputReport(
    _In_ PBTGVJOY_DEVICE_CONTEXT Context,
    _In_ WDFREQUEST Request,
    _In_ size_t InputBufferLength)
{
    BTGVJOY_INPUT_REPORT* report;
    HID_XFER_PACKET packet;
    NTSTATUS status;

    if (InputBufferLength != sizeof(BTGVJOY_INPUT_REPORT)) {
        BtGunVJoyRecordMalformedInput(Context, STATUS_INFO_LENGTH_MISMATCH);
        WdfRequestComplete(Request, STATUS_INFO_LENGTH_MISMATCH);
        return;
    }

    status = WdfRequestRetrieveInputBuffer(
        Request,
        sizeof(BTGVJOY_INPUT_REPORT),
        (PVOID*)&report,
        NULL);
    if (!NT_SUCCESS(status)) {
        BtGunVJoyRecordMalformedInput(Context, status);
        WdfRequestComplete(Request, status);
        return;
    }

    if (!BtGunVJoyValidateInputReport(report)) {
        BtGunVJoyRecordMalformedInput(Context, STATUS_INVALID_PARAMETER);
        WdfRequestComplete(Request, STATUS_INVALID_PARAMETER);
        return;
    }

    if (Context->VhfHandle == NULL || !Context->VhfStarted) {
        Context->LastNtStatus = STATUS_DEVICE_NOT_READY;
        WdfRequestComplete(Request, STATUS_DEVICE_NOT_READY);
        return;
    }

    RtlZeroMemory(&packet, sizeof(packet));
    packet.reportId = BTGVJOY_INPUT_REPORT_ID;
    packet.reportBuffer = report->HidReport;
    packet.reportBufferLen = BTGVJOY_INPUT_REPORT_LENGTH_BYTES;

    status = VhfReadReportSubmit(Context->VhfHandle, &packet);
    Context->LastNtStatus = status;
    if (NT_SUCCESS(status)) {
        Context->SubmittedInputReports += 1;
        Context->LastInputSequence = report->SourceSequence;
    }

    WdfRequestComplete(Request, status);
}

VOID
BtGunVJoyReadOutputReport(
    _In_ PBTGVJOY_DEVICE_CONTEXT Context,
    _In_ WDFREQUEST Request,
    _In_ size_t OutputBufferLength)
{
    BTGVJOY_OUTPUT_REPORT* output;
    ULONG nextHead;
    NTSTATUS status;

    if (OutputBufferLength < sizeof(BTGVJOY_OUTPUT_REPORT)) {
        WdfRequestComplete(Request, STATUS_BUFFER_TOO_SMALL);
        return;
    }

    status = WdfRequestRetrieveOutputBuffer(
        Request,
        sizeof(BTGVJOY_OUTPUT_REPORT),
        (PVOID*)&output,
        NULL);
    if (!NT_SUCCESS(status)) {
        WdfRequestComplete(Request, status);
        return;
    }

    ExAcquireFastMutex(&Context->OutputQueueLock);
    if (Context->OutputQueueCount == 0) {
        ExReleaseFastMutex(&Context->OutputQueueLock);
        WdfRequestComplete(Request, STATUS_NO_MORE_ENTRIES);
        return;
    }

    RtlCopyMemory(output, &Context->OutputQueue[Context->OutputQueueHead], sizeof(*output));
    nextHead = (Context->OutputQueueHead + 1) % BTGVJOY_OUTPUT_QUEUE_CAPACITY;
    Context->OutputQueueHead = nextHead;
    Context->OutputQueueCount -= 1;
    Context->LastOutputSequence = output->OutputSequence;
    ExReleaseFastMutex(&Context->OutputQueueLock);

    WdfRequestCompleteWithInformation(Request, STATUS_SUCCESS, sizeof(*output));
}

VOID
BtGunVJoyGetStatus(
    _In_ PBTGVJOY_DEVICE_CONTEXT Context,
    _In_ WDFREQUEST Request,
    _In_ size_t OutputBufferLength)
{
    BTGVJOY_STATUS* statusReport;
    NTSTATUS status;

    if (OutputBufferLength < sizeof(BTGVJOY_STATUS)) {
        WdfRequestComplete(Request, STATUS_BUFFER_TOO_SMALL);
        return;
    }

    status = WdfRequestRetrieveOutputBuffer(
        Request,
        sizeof(BTGVJOY_STATUS),
        (PVOID*)&statusReport,
        NULL);
    if (!NT_SUCCESS(status)) {
        WdfRequestComplete(Request, status);
        return;
    }

    ExAcquireFastMutex(&Context->OutputQueueLock);
    RtlZeroMemory(statusReport, sizeof(*statusReport));
    statusReport->Size = sizeof(BTGVJOY_STATUS);
    statusReport->Version = BTGVJOY_ABI_VERSION;
    statusReport->DriverStarted = Context->DriverStarted ? 1 : 0;
    statusReport->VhfStarted = Context->VhfStarted ? 1 : 0;
    statusReport->QueueDepth = Context->OutputQueueCount;
    statusReport->LastInputSequence = Context->LastInputSequence;
    statusReport->LastOutputSequence = Context->LastOutputSequence;
    statusReport->SubmittedInputReports = Context->SubmittedInputReports;
    statusReport->QueuedOutputReports = Context->QueuedOutputReports;
    statusReport->DroppedOutputReports = Context->DroppedOutputReports;
    statusReport->MalformedInputReports = Context->MalformedInputReports;
    statusReport->MalformedOutputReports = Context->MalformedOutputReports;
    statusReport->LastNtStatus = Context->LastNtStatus;
    ExReleaseFastMutex(&Context->OutputQueueLock);

    WdfRequestCompleteWithInformation(Request, STATUS_SUCCESS, sizeof(*statusReport));
}

VOID
BtGunVJoyHandleWriteReport(
    _In_ PVOID VhfClientContext,
    _In_ VHFOPERATIONHANDLE VhfOperationHandle,
    _In_opt_ PVOID VhfOperationContext,
    _In_ PHID_XFER_PACKET HidTransferPacket)
{
    PBTGVJOY_DEVICE_CONTEXT context;
    BTGVJOY_OUTPUT_REPORT outputReport;
    ULONG writeIndex;
    NTSTATUS status;

    UNREFERENCED_PARAMETER(VhfOperationContext);

    context = (PBTGVJOY_DEVICE_CONTEXT)VhfClientContext;
    status = STATUS_SUCCESS;

    if (context == NULL || HidTransferPacket == NULL || HidTransferPacket->reportBuffer == NULL) {
        status = STATUS_INVALID_PARAMETER;
        goto CompleteOperation;
    }

    if (HidTransferPacket->reportId != BTGVJOY_OUTPUT_REPORT_ID) {
        status = STATUS_INVALID_PARAMETER;
        goto RecordMalformed;
    }

    RtlZeroMemory(&outputReport, sizeof(outputReport));
    outputReport.Size = sizeof(BTGVJOY_OUTPUT_REPORT);
    outputReport.Version = BTGVJOY_ABI_VERSION;

    if (HidTransferPacket->reportBufferLen == BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES &&
        HidTransferPacket->reportBuffer[0] == BTGVJOY_OUTPUT_REPORT_ID) {
        RtlCopyMemory(
            outputReport.HidReport,
            HidTransferPacket->reportBuffer,
            BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES);
    } else if (HidTransferPacket->reportBufferLen == (BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES - 1)) {
        outputReport.HidReport[0] = BTGVJOY_OUTPUT_REPORT_ID;
        RtlCopyMemory(
            &outputReport.HidReport[1],
            HidTransferPacket->reportBuffer,
            BTGVJOY_OUTPUT_REPORT_LENGTH_BYTES - 1);
    } else {
        status = STATUS_INFO_LENGTH_MISMATCH;
        goto RecordMalformed;
    }

    if (outputReport.HidReport[1] != BTGVJOY_OUTPUT_REPORT_VERSION) {
        status = STATUS_INVALID_PARAMETER;
        goto RecordMalformed;
    }

    ExAcquireFastMutex(&context->OutputQueueLock);
    outputReport.OutputSequence = ++context->NextOutputSequence;
    if (context->OutputQueueCount == BTGVJOY_OUTPUT_QUEUE_CAPACITY) {
        context->DroppedOutputReports += 1;
        context->LastNtStatus = STATUS_BUFFER_OVERFLOW;
        status = STATUS_BUFFER_OVERFLOW;
    } else {
        writeIndex = (context->OutputQueueHead + context->OutputQueueCount) %
            BTGVJOY_OUTPUT_QUEUE_CAPACITY;
        RtlCopyMemory(&context->OutputQueue[writeIndex], &outputReport, sizeof(outputReport));
        context->OutputQueueCount += 1;
        context->QueuedOutputReports += 1;
        context->LastOutputSequence = outputReport.OutputSequence;
        context->LastNtStatus = STATUS_SUCCESS;
    }
    ExReleaseFastMutex(&context->OutputQueueLock);
    goto CompleteOperation;

RecordMalformed:
    ExAcquireFastMutex(&context->OutputQueueLock);
    context->MalformedOutputReports += 1;
    context->LastNtStatus = status;
    ExReleaseFastMutex(&context->OutputQueueLock);

CompleteOperation:
    VhfAsyncOperationComplete(VhfOperationHandle, status);
}
