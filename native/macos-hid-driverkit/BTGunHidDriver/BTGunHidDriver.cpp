#include "BTGunHidDriver.h"

#include <DriverKit/IOMemoryMap.h>
#include <string.h>

OSDefineMetaClassAndStructors(BTGunHidDriver, IOHIDDevice)

namespace {
constexpr uint8_t kNeutralOutputReport[9] = {0x02, 0x01, 0x00, 0x64, 0x00, 0xf4, 0x01, 0x00, 0x00};
}

kern_return_t BTGunHidDriver::Start(IOService* provider)
{
    memcpy(lastOutputReport_, kNeutralOutputReport, sizeof(lastOutputReport_));
    outputReportSeen_ = false;

    kern_return_t result = IOHIDDevice::Start(provider);
    if (result != kIOReturnSuccess) {
        return result;
    }

    RegisterService();
    return kIOReturnSuccess;
}

kern_return_t BTGunHidDriver::Stop(IOService* provider)
{
    return IOHIDDevice::Stop(provider);
}

kern_return_t BTGunHidDriver::handleReport(uint64_t timestamp,
                                           IOMemoryDescriptor* report,
                                           uint32_t reportLength,
                                           IOHIDReportType reportType,
                                           IOOptionBits options)
{
    if (report == nullptr || reportType != kIOHIDReportTypeInput || reportLength != kInputReportLength) {
        return kIOReturnBadArgument;
    }

    return IOHIDDevice::handleReport(timestamp, report, reportLength, reportType, options);
}

kern_return_t BTGunHidDriver::SubmitInputReportFromHost(IOMemoryDescriptor* report,
                                                        const uint8_t* reportBytes,
                                                        uint32_t reportLength,
                                                        uint64_t timestamp)
{
    if (!IsValidInputReport(reportBytes, reportLength)) {
        return kIOReturnBadArgument;
    }

    return handleReport(timestamp, report, reportLength, kIOHIDReportTypeInput, 0);
}

kern_return_t BTGunHidDriver::getReport(IOMemoryDescriptor* report,
                                        IOHIDReportType reportType,
                                        IOOptionBits options,
                                        uint32_t completionTimeout,
                                        OSAction* action)
{
    (void)completionTimeout;

    const uint8_t reportId = static_cast<uint8_t>(options & 0xff);
    if (report == nullptr || action == nullptr || reportType != kIOHIDReportTypeOutput || reportId != kOutputReportId) {
        if (action != nullptr) {
            CompleteReport(action, kIOReturnBadArgument, 0);
        }
        return kIOReturnBadArgument;
    }

    kern_return_t result = CopyToDescriptor(report, lastOutputReport_, kOutputReportLength);
    CompleteReport(action, result, result == kIOReturnSuccess ? kOutputReportLength : 0);
    return result;
}

kern_return_t BTGunHidDriver::setReport(IOMemoryDescriptor* report,
                                        IOHIDReportType reportType,
                                        IOOptionBits options,
                                        uint32_t completionTimeout,
                                        OSAction* action)
{
    (void)completionTimeout;

    const uint8_t reportId = static_cast<uint8_t>(options & 0xff);
    if (report == nullptr || action == nullptr || reportType != kIOHIDReportTypeOutput || reportId != kOutputReportId) {
        if (action != nullptr) {
            CompleteReport(action, kIOReturnBadArgument, 0);
        }
        return kIOReturnBadArgument;
    }

    uint8_t candidate[kOutputReportLength] = {};
    kern_return_t result = CopyFromDescriptor(report, candidate, kOutputReportLength);
    if (result == kIOReturnSuccess && !IsValidOutputReport(candidate, kOutputReportLength)) {
        result = kIOReturnBadArgument;
    }

    if (result == kIOReturnSuccess) {
        memcpy(lastOutputReport_, candidate, kOutputReportLength);
        outputReportSeen_ = true;
    }

    CompleteReport(action, result, result == kIOReturnSuccess ? kOutputReportLength : 0);
    return result;
}

bool BTGunHidDriver::IsValidInputReport(const uint8_t* reportBytes, uint32_t reportLength)
{
    return reportBytes != nullptr &&
           reportLength == kInputReportLength &&
           reportBytes[0] == kInputReportId;
}

bool BTGunHidDriver::IsValidOutputReport(const uint8_t* reportBytes, uint32_t reportLength)
{
    return reportBytes != nullptr &&
           reportLength == kOutputReportLength &&
           reportBytes[0] == kOutputReportId &&
           reportBytes[1] == kOutputVersion &&
           reportBytes[7] == 0x00 &&
           reportBytes[8] == 0x00;
}

kern_return_t BTGunHidDriver::CopyFromDescriptor(IOMemoryDescriptor* descriptor,
                                                 uint8_t* destination,
                                                 uint32_t destinationLength)
{
    if (descriptor == nullptr || destination == nullptr) {
        return kIOReturnBadArgument;
    }

    uint64_t descriptorLength = 0;
    kern_return_t result = descriptor->GetLength(&descriptorLength);
    if (result != kIOReturnSuccess || descriptorLength < destinationLength) {
        return result == kIOReturnSuccess ? kIOReturnBadArgument : result;
    }

    IOMemoryMap* map = nullptr;
    result = descriptor->CreateMapping(kIOMemoryMapReadOnly, 0, 0, destinationLength, 0, &map);
    if (result != kIOReturnSuccess || map == nullptr) {
        return result == kIOReturnSuccess ? kIOReturnNoMemory : result;
    }

    memcpy(destination, reinterpret_cast<const void*>(map->GetAddress()), destinationLength);
    map->release();
    return kIOReturnSuccess;
}

kern_return_t BTGunHidDriver::CopyToDescriptor(IOMemoryDescriptor* descriptor,
                                               const uint8_t* source,
                                               uint32_t sourceLength)
{
    if (descriptor == nullptr || source == nullptr) {
        return kIOReturnBadArgument;
    }

    uint64_t descriptorLength = 0;
    kern_return_t result = descriptor->GetLength(&descriptorLength);
    if (result != kIOReturnSuccess || descriptorLength < sourceLength) {
        return result == kIOReturnSuccess ? kIOReturnBadArgument : result;
    }

    IOMemoryMap* map = nullptr;
    result = descriptor->CreateMapping(0, 0, 0, sourceLength, 0, &map);
    if (result != kIOReturnSuccess || map == nullptr) {
        return result == kIOReturnSuccess ? kIOReturnNoMemory : result;
    }

    memcpy(reinterpret_cast<void*>(map->GetAddress()), source, sourceLength);
    map->release();
    return kIOReturnSuccess;
}
